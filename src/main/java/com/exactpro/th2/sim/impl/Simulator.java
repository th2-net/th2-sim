/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import com.exactpro.th2.common.grpc.AnyMessage;
import com.exactpro.th2.common.grpc.MessageGroup;
import com.exactpro.th2.common.grpc.MessageGroupBatch;
import com.exactpro.th2.common.message.MessageUtils;
import com.exactpro.th2.common.schema.message.QueueAttribute;
import com.exactpro.th2.common.schema.message.SubscriberMonitor;
import com.exactpro.th2.common.utils.event.EventBatcher;
import com.exactpro.th2.sim.configuration.RuleConfiguration;
import com.exactpro.th2.sim.grpc.RuleRelation;
import com.exactpro.th2.sim.util.EventUtils;
import com.exactpro.th2.sim.util.MessageBatcher;
import kotlin.Unit;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();

    private final Map<Integer, SimulatorRuleInfo> ruleIds = new ConcurrentHashMap<>();
    private final Map<String, List<Integer>> relationToRuleId = new ConcurrentHashMap<>();

    private final AtomicInteger nextId = new AtomicInteger(0);
    private final AtomicInteger countDefaultRules = new AtomicInteger(0);

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    private DefaultRulesTurnOffStrategy strategy;
    private String rootEventId;

    private MessageRouter<MessageGroupBatch> batchRouter;
    private MessageRouter<EventBatch> eventRouter;

    private ExecutorService executorService;

    private MessageBatcher messageBatcher;
    private EventBatcher eventBatcher;

    @Override
    public void init(@NotNull MessageRouter<MessageGroupBatch> batchRouter, @NotNull MessageRouter<EventBatch> eventRouter, @NotNull SimulatorConfiguration configuration, @NotNull String rootEventId) {

        if (this.batchRouter != null) {
            throw new IllegalStateException("Simulator already init");
        }

        this.strategy = configuration.getStrategyDefaultRules();

        this.batchRouter = batchRouter;
        this.eventRouter = eventRouter;

        this.rootEventId = rootEventId;

        this.messageBatcher = new MessageBatcher(configuration.getMaxBatchSize(), configuration.getMaxFlushTime(), scheduler, (batch, relation) -> {
            try {
                batchRouter.sendAll(batch, QueueAttribute.SECOND.getValue(), relation);
            } catch (IOException e) {
                throw new IllegalStateException("Can not send message group batch: " + MessageUtils.toJson(batch), e);
            }
            return Unit.INSTANCE;
        });

        this.eventBatcher = new EventBatcher(configuration.getMaxBatchSize(), configuration.getMaxFlushTime(), scheduler, eventBatch -> {
            try {
                eventRouter.sendAll(eventBatch);
            } catch (IOException e) {
                throw new IllegalStateException("Can not send event batch: " + MessageUtils.toJson(eventBatch), e);
            }
            return Unit.INSTANCE;
        });

        var threadCount = new AtomicInteger(1);

        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(configuration.getExecutionPoolSize(), configuration.getExecutionPoolSize(), 0, TimeUnit.SECONDS, new SynchronousQueue<>(), runnable -> {
            var thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("th2-simulator-" + threadCount.incrementAndGet());
            return thread;
        });

        poolExecutor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
            try {
                // Wait until queue is ready, don`t throw reject
                threadPoolExecutor.getQueue().put(runnable);
                if (threadPoolExecutor.isShutdown()) {
                    throw new RejectedExecutionException("Task " + runnable + " rejected from " + threadPoolExecutor + " due shutdown");
                }
            } catch (InterruptedException e) {
                throw new RejectedExecutionException("Task " + runnable + " rejected from " + threadPoolExecutor, e);
            }
        });

        executorService = poolExecutor;

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

        logger.debug("Try to add rule '{}' for session alias '{}'", rule.getClass().getName(), configuration.getSessionAlias());

        int id = nextId.incrementAndGet();

        String infoMsg = String.format("%s [id:%s] [%s] rule was added to simulator", rule.getClass().getSimpleName(), id, LocalDateTime.now());

        Event event = EventUtils.sendEvent(
                eventRouter,
                infoMsg,
                rootEventId,
                "Rule class = " + rule.getClass().getName()
        );

        logger.info(infoMsg);

        var ruleInfo = new SimulatorRuleInfo(id, rule, configuration, messageBatcher, eventBatcher, event == null ? rootEventId : event.getId().getId(), scheduler, this::removeRule);

        String relation = configuration.getRelation();

        ruleIds.put(id, ruleInfo);
        List<Integer> relationRules = relationToRuleId.computeIfAbsent(relation, (key) -> new CopyOnWriteArrayList<>());
        if (!relationRules.contains(ruleInfo.getId())) {
            relationRules.add(ruleInfo.getId());
        }

        if (!subscriptions.contains(relation)) {
            SubscriberMonitor subscribe = this.batchRouter.subscribeAll((consumerTag, batch) -> handleBatch(batch, relation), QueueAttribute.FIRST.getValue(), QueueAttribute.SUBSCRIBE.getValue(), QueueAttribute.PARSED.getValue(), relation);
            if (subscribe==null) {
                logger.error("Cannot subscribe to queue with attributes: [\"first\", \"subscribe\", \"parsed\", \"{}\"]", relation);
            } else {
                subscriptions.add(relation);
                logger.debug("Created subscription for relation: '{}'", relation);
            }
        }

        return RuleID.newBuilder().setId(id).build();
    }

    @Override
    public void addDefaultRule(@NotNull RuleID ruleID) {
        if (ruleIds.computeIfPresent(ruleID.getId(), (id, rule) -> { rule.setDefault(true); return rule; }) == null) {
            logger.warn("Can not toggle rule to default. Can not find rule with id = {}", ruleID.getId());
        } else {
            logger.debug("Converted rule with id = {} to default state", ruleID.getId());
            countDefaultRules.incrementAndGet();
        }
    }

    @Override
    public void removeRule(@NotNull RuleID id, StreamObserver<Empty> responseObserver) {
        logger.debug("Try to remove rule with id = {}", id.getId());

        SimulatorRuleInfo rule = ruleIds.get(id.getId());

        if (rule != null) {
            rule.removeRule();
        }

        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    private void removeRule(@NotNull SimulatorRuleInfo rule) {
        var id = rule.getId();
        if (ruleIds.remove(id) != null) {
            if (rule.isDefault()) {
                countDefaultRules.decrementAndGet();
                logger.warn("Removed default rule with id = {}", id);
            }
            EventUtils.sendEvent(eventRouter, String.format("Rule with id = '%d' was removed", id), rule.getRootEventId());
        }
        relationToRuleId.forEach((relation, ids) -> {
            if (ids.remove((Integer) id)) {
                logger.debug("Removed rule with id = {} from relation {}", id, relation);
            }
        });
    }

    @Override
    public void getRulesInfo(Empty request, @NotNull StreamObserver<RulesInfo> responseObserver) {
        responseObserver.onNext(RulesInfo
                .newBuilder()
                .addAllInfo(ruleIds.keySet().stream()
                        .map(this::createRuleInfo)
                        .collect(Collectors.toList()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void getRelatedRules(RuleRelation request, StreamObserver<RulesInfo> responseObserver) {
        responseObserver.onNext(RulesInfo
                .newBuilder()
                .addAllInfo(relationToRuleId.get(request.getRelation()).stream()
                        .map(this::createRuleInfo)
                        .collect(Collectors.toList()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void touchRule(@NotNull TouchRequest request, StreamObserver<Empty> responseObserver) {
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
            String msg = "Can not execute touch method on rule [" + ruleInfo.getRule().getClass().getSimpleName() + "] with id = " + request.getId();
            logger.warn(msg);
            responseObserver.onError(new Exception(msg, e));
        }
    }

    public void handleBatch(MessageGroupBatch batch, String relation) {
        executorService.execute(() -> {
            try {
                List<MessageGroup> messageGroups = batch.getGroupsList();
                int messageGroupsSize = messageGroups.size();
                for (int messageGroupIndex = 0; messageGroupIndex < messageGroupsSize; messageGroupIndex++) {

                    List<AnyMessage> messages = messageGroups.get(messageGroupIndex).getMessagesList();
                    int messageGroupSize = messages.size();
                    for (int messageIndex = 0; messageIndex < messageGroupSize; messageIndex++) {

                        AnyMessage message = messages.get(messageIndex);
                        if (message.hasMessage()) {
                            handleMessage(message.getMessage(), relation);
                        } else {
                            logger.warn("Unsupported format of incoming message: {}", message.getKindCase().name());
                        }
                    }
                }
            } catch (Exception e) {
                if (logger.isErrorEnabled()) {
                    logger.error("Can`t handle batch = {}", TextFormat.shortDebugString(batch), e);
                }
            }
        });
    }

    private void handleMessage(Message message, String relation) {
        if (logger.isDebugEnabled()) {
            logger.debug("Handle message '{}' from relation '{}'", message.getMetadata().getMessageType(), relation);
        }

        List<SimulatorRuleInfo> triggeredRules = getTriggered(message, relation);

        if(triggeredRules.isEmpty()) {
            logger.debug("No rules were triggered");
            return;
        }

        int triggeredSize = triggeredRules.size();
        for (int i = 0; i < triggeredSize; i++) {
            SimulatorRuleInfo triggeredRule = triggeredRules.get(i);
            try {
                triggeredRule.handle(message);
            } catch (Exception e) {
                String msgType = message.getMetadata().getMessageType();
                logger.error("Rule id: " + triggeredRule.getId() + " can not handle message " + msgType, e);
                EventUtils.sendErrorEvent(eventRouter, "Can not handle message " + msgType, triggeredRule.getRootEventId(), e);
            }
        }
        logTriggeredRules(triggeredRules);
    }

    private List<SimulatorRuleInfo> getTriggered(Message message, String relation) {
        List<Integer> relationRules = relationToRuleId.get(relation);

        var messageType = message.getMetadata().getMessageType();
        if(relationRules == null) {
            logger.trace("No related rules was found for message: {}", messageType);
            return Collections.emptyList();
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Message {} checking against related rules: {}", messageType, relationRules.stream().map(integer -> ruleIds.get(integer).getRule().getClass().getSimpleName()).collect(Collectors.joining(", ")));
        }

        List<SimulatorRuleInfo> triggeredRules = new ArrayList<>();
        int relationSize = relationRules.size();
        for (int i = 0; i < relationSize; i++) {
            SimulatorRuleInfo rule = ruleIds.get(relationRules.get(i));
            if (rule.checkAlias(message) && rule.getRule().checkTriggered(message)) {
                triggeredRules.add(rule);
            }
        }

        int defaultCount = countDefaultRules.get();

        if (!triggeredRules.isEmpty() && defaultCount != 0 && defaultCount != ruleIds.size()) {
            List<SimulatorRuleInfo> nonDefaultRules = new ArrayList<>();
            for (SimulatorRuleInfo ruleInfo : triggeredRules) {
                if (!ruleInfo.isDefault()) {
                    nonDefaultRules.add(ruleInfo);
                }
            }
            if (strategy == DefaultRulesTurnOffStrategy.ON_ADD || !nonDefaultRules.isEmpty()) {
                logger.trace("Only non default rules will process message ({}) due strategy: {}", message.getMetadata().getMessageType(), strategy.name());
                triggeredRules = nonDefaultRules;
            }
        }

        return  triggeredRules;
    }

    @Override
    public void close() {
        messageBatcher.close();
        eventBatcher.close();
        scheduler.shutdown();
        ruleIds.clear();
    }

    private RuleInfo createRuleInfo(int ruleId) {
        SimulatorRuleInfo rule = ruleIds.get(ruleId);
        if (rule == null) {
            return RuleInfo.newBuilder().setId(RuleID.newBuilder().setId(-1).build()).build();
        }

        var result =  RuleInfo.newBuilder()
                .setId(RuleID.newBuilder().setId(ruleId).build())
                .setClassName(rule.getRule().getClass().getName())
                .setRelation(RuleRelation.newBuilder().setRelation(rule.getConfiguration().getRelation()));

        if (rule.getConfiguration().getSessionAlias() != null) {
            result.setAlias(rule.getConfiguration().getSessionAlias());
        }

        return result.build();
    }

    private void logTriggeredRules(List<SimulatorRuleInfo> triggeredRules) {
        if (logger.isDebugEnabled()) {
            if (triggeredRules.isEmpty()) {
                logger.debug("No rules were triggered");
                return;
            }

            String triggeredIdsString = triggeredRules.stream().map(it -> String.valueOf(it.getId())).collect(Collectors.joining(";"));
            logger.debug("Triggered on message rules with ids = [{}]", triggeredIdsString);
        }
    }
}
