/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.exactpro.th2.sim.impl;

import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.exactpro.th2.common.grpc.AnyMessage;
import com.exactpro.th2.common.grpc.MessageGroup;
import com.exactpro.th2.common.grpc.MessageGroupBatch;
import com.exactpro.th2.sim.configuration.RuleConfiguration;
import com.exactpro.th2.sim.util.EventUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.exactpro.th2.common.grpc.ConnectionID;
import com.exactpro.th2.common.grpc.Event;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.sim.ISimulator;
import com.exactpro.th2.sim.configuration.DefaultRulesTurnOffStrategy;
import com.exactpro.th2.sim.configuration.SimulatorConfiguration;
import com.exactpro.th2.sim.grpc.RuleID;
import com.exactpro.th2.sim.grpc.RuleInfo;
import com.exactpro.th2.sim.grpc.RulesInfo;
import com.exactpro.th2.sim.grpc.SimGrpc;
import com.exactpro.th2.sim.grpc.TouchRequest;
import com.exactpro.th2.sim.rule.IRule;
import com.google.protobuf.Empty;
import com.google.protobuf.TextFormat;

import io.grpc.stub.StreamObserver;

/**
 * Default implementation of {@link ISimulator}.
 */
public class Simulator extends SimGrpc.SimImplBase implements ISimulator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final Map<Integer, SimulatorRuleInfo> ruleIds = new ConcurrentHashMap<>();

    private final AtomicInteger nextId = new AtomicInteger(0);
    private final AtomicInteger countDefaultRules = new AtomicInteger(0);

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    private DefaultRulesTurnOffStrategy strategy;
    private MessageRouter<MessageGroupBatch> batchRouter;
    private MessageRouter<EventBatch> eventRouter;
    private String rootEventId;

    @Override
    public void init(@NotNull MessageRouter<MessageGroupBatch> batchRouter, @NotNull MessageRouter<EventBatch> eventRouter, @NotNull SimulatorConfiguration configuration, @NotNull String rootEventId) {

        if (this.batchRouter != null) {
            throw new IllegalStateException("Simulator already init");
        }

        try {
            strategy = defaultIfNull(configuration.getStrategyDefaultRules(), DefaultRulesTurnOffStrategy.ON_TRIGGER);
        } catch (IllegalStateException e) {
            logger.info("Can not find custom configuration. Use '{}' for default rules strategy", this.strategy);
        }

        this.batchRouter = batchRouter;
        this.batchRouter.subscribeAll((consumerTag, batch) -> {
            for (MessageGroup messageGroup: batch.getGroupsList()) {
                for (AnyMessage anyMessage : messageGroup.getMessagesList()) {
                    if (anyMessage.getKindCase().equals(AnyMessage.KindCase.RAW_MESSAGE)) {
                        logger.debug("Unsupported format of incoming message: {}", AnyMessage.KindCase.RAW_MESSAGE.name());
                        continue;
                    }
                    Message message = anyMessage.getMessage();
                    try {
                        handleMessage(message);
                    } catch (Exception e) {
                        logger.error("Can not handle message = {}", TextFormat.shortDebugString(message), e);
                    }
                }
            }

        }, "first", "subscribe", "parsed");

        this.eventRouter = eventRouter;
        this.rootEventId = rootEventId;
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull String sessionAlias) {
        var configuration = new RuleConfiguration();
        configuration.setSessionAlias(sessionAlias);
        return this.addRule(rule, configuration);
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull RuleConfiguration configuration) {
        Objects.requireNonNull(rule, "Rule can not be null");
        Objects.requireNonNull(configuration, "RuleConfiguration can not be null");

        if (logger.isDebugEnabled()) {
            logger.debug("Try to add rule '{}' for session alias '{}'", rule.getClass().getName(), configuration.getSessionAlias());
        }

        int id = nextId.incrementAndGet();

        String infoMsg = String.format("%s [id:%s] [%s] rule was added to simulator", rule.getClass().getSimpleName(), id, LocalDateTime.now());

        Event event = EventUtils.sendEvent(
                eventRouter,
                infoMsg,
                String.format("Rule class = %s", rule.getClass().getName()),
                rootEventId
        );
        logger.info(infoMsg);

        ruleIds.put(id, new SimulatorRuleInfo(id, rule, false, configuration, batchRouter, eventRouter, event == null ? rootEventId : event.getId().getId(), scheduler, this::removeRule));

        return RuleID.newBuilder().setId(id).build();
    }

    @Override
    public void addDefaultRule(RuleID ruleID) {
        if (ruleIds.computeIfPresent(ruleID.getId(), (k, v) -> v.isDefault() ? v : new SimulatorRuleInfo(v.getId(), v.getRule(), true, v.getConfiguration(), batchRouter, eventRouter, v.getRootEventId(), scheduler, this::removeRule)) == null) {
            logger.warn("Can not toggle rule to default. Can not find rule with id = {}", ruleID.getId());
        } else {
            logger.debug("Added default rule with id = {}", ruleID.getId());

            countDefaultRules.incrementAndGet();
        }
    }

    @Override
    public void removeRule(RuleID id, StreamObserver<Empty> responseObserver) {
        logger.debug("Try to remove rule with id = {}", id.getId());

        SimulatorRuleInfo rule = ruleIds.remove(id.getId());

        if (rule != null) {
            rule.removeRule();
            EventUtils.sendEvent(eventRouter, String.format("Rule with id = '%d' was removed", id.getId()), (String) null, rule.getRootEventId());
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private void removeRule(SimulatorRuleInfo rule) {
        var id = rule.getId();
        if (ruleIds.remove(id) != null) {
            if (rule.isDefault()) {
                countDefaultRules.decrementAndGet();
                logger.warn("Removed default rule with id = {}", id);
            }

            EventUtils.sendEvent(eventRouter, String.format("Rule with id = '%d' was removed", id), (String) null, rule.getRootEventId());
        }
    }

    @Override
    public void getRulesInfo(Empty request, StreamObserver<RulesInfo> responseObserver) {
        responseObserver.onNext(RulesInfo
                .newBuilder()
                .addAllInfo(ruleIds.keySet().stream().map(this::createRuleInfo)
                        .collect(Collectors.toList())
                )
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void touchRule(TouchRequest request, StreamObserver<Empty> responseObserver) {
        SimulatorRuleInfo ruleInfo = ruleIds.get(request.getId().getId());
        if (ruleInfo == null) {
            responseObserver.onError(new IllegalArgumentException("Can not find rule with id = " + request.getId()));
            return;
        }

        logger.debug("Call touch on rule with id = {}", ruleInfo.getId());

        try {
            ruleInfo.touch(ObjectUtils.defaultIfNull(request.getArgsMap(), Collections.emptyMap()));
            responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(new Exception("Can not execute touch method on rule with id = " + request.getId(), e));
        }
    }

    public void handleMessage(Message message) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handle message: '{}'", message.getMetadata().getMessageType());
        }

        Set<SimulatorRuleInfo> triggeredRules = new HashSet<>();

        AtomicBoolean useDefault = new AtomicBoolean(strategy != DefaultRulesTurnOffStrategy.ON_ADD || countDefaultRules.get() == ruleIds.size());

        ruleIds.forEach((id, rule) -> {
            if (!checkAgainstMetadata(message, rule.getConfiguration())) {
                logger.trace("Skip rule with id = '{}' due configuration '{}'", id, rule.getConfiguration());
                return;
            }

            if (rule.getRule().checkTriggered(message)) {
                if (useDefault.get() || !rule.isDefault()) {
                    triggeredRules.add(rule);
                    if (!rule.isDefault()) {
                        useDefault.set(false);
                    }
                } else {
                    logger.debug("Skip rule with id '{}', because it is default rule", rule.getId());
                }
            } else {
                logger.trace("Skip rule with id = '{}', because not triggered", id);
            }
        });

        for (SimulatorRuleInfo triggeredRule : triggeredRules) {
            if (!useDefault.get() && triggeredRule.isDefault()) {
                logger.debug("Skip rule with id '{}', because it is default rule", triggeredRule.getId());
                continue;
            }
            try {
                triggeredRule.handle(message);
            } catch (Exception e) {
                EventUtils.sendErrorEvent(eventRouter, "Can not handle message " + message.getMetadata().getMessageType(), triggeredRule.getRootEventId(), e);
            }

        }

        loggingTriggeredRules(triggeredRules, useDefault.get());
    }

    @Override
    public void close() {
        scheduler.shutdown();
        ruleIds.clear();
    }

    private RuleInfo createRuleInfo(int ruleId) {
        SimulatorRuleInfo rule = ruleIds.get(ruleId);
        if (rule == null) {
            return RuleInfo.newBuilder().setId(RuleID.newBuilder().setId(-1).build()).build();
        }

        return RuleInfo.newBuilder()
                .setId(RuleID.newBuilder().setId(ruleId).build())
                .setClassName(rule.getRule().getClass().getName())
                .setConnectionId(ConnectionID.newBuilder().setSessionAlias(rule.getConfiguration().getSessionAlias()).build())
                .build();
    }

    private boolean checkAgainstMetadata(@NotNull Message message, @NotNull RuleConfiguration configuration) {
        if (configuration.getSessionAlias() == null) {
            return true;
        }
        return message.getMetadata().getId().getConnectionId().getSessionAlias().equals(configuration.getSessionAlias());
    }

    private void loggingTriggeredRules(Set<SimulatorRuleInfo> triggeredRules, boolean useDefault) {
        if (logger.isDebugEnabled() || logger.isInfoEnabled() && triggeredRules.size() > 1) {

            if (triggeredRules.isEmpty()) {
                logger.debug("No rules were triggered");
                return;
            }

            Stream<SimulatorRuleInfo> stream = triggeredRules.stream();
            if (!useDefault) {
                stream = stream.filter(it -> !it.isDefault());
            }

            String triggeredIdsString = stream.map(it -> String.valueOf(it.getId())).collect(Collectors.joining(";"));

            logger.debug("Triggered on message rules with ids = [{}]", triggeredIdsString);

            if (triggeredRules.size() > 1) {
                logger.info("Triggered on message more one rule. Rules ids = [{}]", triggeredIdsString);
            }
        }
    }
}
