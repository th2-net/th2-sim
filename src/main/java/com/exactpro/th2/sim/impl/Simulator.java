/*******************************************************************************
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.exactpro.th2.sim.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.common.grpc.ConnectionID;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.common.schema.factory.AbstractCommonFactory;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.sim.ISimulator;
import com.exactpro.th2.sim.grpc.RuleID;
import com.exactpro.th2.sim.grpc.RuleInfo;
import com.exactpro.th2.sim.grpc.RulesInfo;
import com.exactpro.th2.sim.grpc.SimGrpc;
import com.exactpro.th2.sim.rule.IRule;
import com.google.protobuf.Empty;
import com.google.protobuf.TextFormat;

import io.grpc.stub.StreamObserver;

/**
 * Default implementation of {@link ISimulator}.
 */
public class Simulator extends SimGrpc.SimImplBase implements ISimulator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final Map<String, Set<Integer>> connectivityRules = new ConcurrentHashMap<>();
    private final Map<Integer, SimulatorRule> ruleIds = new ConcurrentHashMap<>();

    private final AtomicInteger nextId = new AtomicInteger(0);

    private final Set<Integer> defaultsRules = Collections.synchronizedSet(new HashSet<>());
    private final Object lockCanUseDefaultRules = new Object();
    private Boolean canUseDefaultRules = true;

    private MessageRouter<MessageBatch> router;

    @Override
    public void init(@NotNull AbstractCommonFactory factory) throws Exception {
        this.router = factory.getMessageRouterParsedBatch();
        router.subscribeAll((consumerTag, batch) -> {
            for (Message message : batch.getMessagesList()) {
                String sessionAlias = message.getMetadata().getId().getConnectionId().getSessionAlias();
                if (StringUtils.isNotEmpty(sessionAlias)) {
                    try {
                        handleMessage(sessionAlias, message);
                    } catch (Exception e) {
                        logger.error("Can not handle message = {}", TextFormat.shortDebugString(message), e);
                    }
                } else {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Skip message, because session alias is empty. Message = {}", TextFormat.shortDebugString(message));
                    }
                }
            }
        }, "first", "subscribe", "parsed");
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull String sessionAlias) {
        if (logger.isDebugEnabled()) {
            logger.debug("Try to add rule '{}' for session alias '{}'", rule.getClass().getName(), sessionAlias);
        }

        int id = nextId.incrementAndGet();
        synchronized (lockCanUseDefaultRules) {
            ruleIds.put(id, new SimulatorRule(rule, sessionAlias));
            connectivityRules.computeIfAbsent(sessionAlias, key -> Collections.synchronizedSet(new HashSet<>())).add(id);
            canUseDefaultRules = false;
        }

        logger.info("Rule from class '{}' was added to simulator for session alias '{}' with id = {}",
                rule.getClass().getName(),
                sessionAlias,
                id);

        return RuleID.newBuilder().setId(id).build();
    }

    @Override
    public void addDefaultRule(RuleID ruleID) {
        defaultsRules.add(ruleID.getId());
        logger.debug("Added default rule with id = {}", ruleID.getId());
        updatePossibleUseDefaultRules();
    }

    @Override
    public void removeRule(RuleID id, StreamObserver<Empty> responseObserver) {

        logger.debug("Try to remove rule with id = {}", id.getId());

        SimulatorRule rule = ruleIds.remove(id.getId());

        if (rule != null) {
            Set<Integer> ids = connectivityRules.get(rule.getSessionAlias());
            if (ids != null) {
                ids.remove(id.getId());
            }

            if (defaultsRules.remove(id.getId())) {
                logger.warn("Removed default rule with id = {}", id.getId());
            }

            updatePossibleUseDefaultRules();

            logger.info("Rule with id '{}' was removed", id.getId());
        }

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    private void updatePossibleUseDefaultRules() {
        synchronized (lockCanUseDefaultRules) {
            if (defaultsRules.size() > 0 && defaultsRules.containsAll(ruleIds.keySet())) {
                canUseDefaultRules = true;
            } else {
                canUseDefaultRules = false;
            }
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

    private RuleInfo createRuleInfo(int ruleId) {
        SimulatorRule rule = ruleIds.get(ruleId);
        if (rule == null) {
            return RuleInfo.newBuilder().setId(RuleID.newBuilder().setId(-1).build()).build();
        }

        return RuleInfo.newBuilder()
                .setId(RuleID.newBuilder().setId(ruleId).build())
                .setClassName(rule.getRule().getClass().getName())
                .setConnectionId(ConnectionID.newBuilder().setSessionAlias(rule.getSessionAlias()).build())
                .build();
    }

    public void handleMessage(String sessionAlias, Message message) {

        Map<String, MessageBatch.Builder> answerMessagesBatches = new HashMap<>();

        if (logger.isDebugEnabled()) {
            logger.debug("Handle message from session alias '{}' = {}", sessionAlias, message.getMetadata().getMessageType());
        }

        Iterator<Integer> iterator = connectivityRules.getOrDefault(sessionAlias, Collections.emptySet()).iterator();

        Set<Integer> triggeredRules = new HashSet<>();

        boolean canUseDefaultRulesLocal;
        synchronized (lockCanUseDefaultRules) {
            canUseDefaultRulesLocal = canUseDefaultRules;
        }

        while (iterator.hasNext()) {
            Integer id = iterator.next();

            SimulatorRule rule = ruleIds.get(id);

            if (rule == null || rule.getRule() == null) {
                logger.warn("Skip rule with id '{}', because it is already removed", id);

                iterator.remove();
                continue;
            }

            if (defaultsRules.contains(id) && !canUseDefaultRulesLocal) {
                logger.debug("Skip rule with id '{}', because it is default rule", id);
                continue;
            }

            if (rule.getRule().checkTriggered(message)) {
                try {
                    if (logger.isTraceEnabled()) {
                        logger.trace("Process message by rule with ID '{}' = {}", id, TextFormat.shortDebugString(message));
                    }

                    var messageListToResponse = rule.getRule().handle(message);

                    for (Message responseMessage : messageListToResponse) {
                        String responseSessionAlias = StringUtils.defaultIfEmpty(responseMessage.getMetadata().getId().getConnectionId().getSessionAlias(), sessionAlias);
                        answerMessagesBatches.computeIfAbsent(responseSessionAlias, key -> MessageBatch.newBuilder()).addMessages(responseMessage);
                    }

                    triggeredRules.add(id);

                    if (logger.isTraceEnabled()) {
                        StringBuilder builder = new StringBuilder();
                        builder.append("[");
                        for (int i = 0; i < messageListToResponse.size(); i++) {
                            builder.append(TextFormat.shortDebugString(messageListToResponse.get(i)));
                            if (i != messageListToResponse.size() - 1) {
                                builder.append(";");
                            }
                        }
                        builder.append("]");

                        logger.trace("Rule with id '{}' generate messages '{}'", id, builder.toString());
                    }

                    logger.debug("Rule with ID '{}' has returned '{}' message(s)", id, messageListToResponse.size());

                } catch (Exception e) {
                    logger.error("Can not handle message in rule with id = {}", id, e);
                }
            } else {
                logger.trace("Skip rule with id = '{}', because not triggered", id);
            }

            logger.debug("Triggered on message rules with ids = {}, count batches messages to respond = {}", triggeredRules, answerMessagesBatches.size());

            if (triggeredRules.size() > 1) {
                logger.info("Triggered on message more one rule. Rules ids = {}", triggeredRules);
            }
        }

        answerMessagesBatches.forEach((session, builder) -> {
            MessageBatch batch = builder.build();
            try {
                router.send(batch, "second", "send", "parsed", session);
            } catch (Exception e) {
                logger.error("Can not send batch with session alias '{}' = {}", session, TextFormat.shortDebugString(batch), e);
            }
        });
    }

    @Override
    public void close() {
        connectivityRules.clear();
        ruleIds.clear();
    }

    private class SimulatorRule {
        private final IRule rule;
        private final String sessionAlias;

        public SimulatorRule(IRule rule, String sessionAlias) {
            this.rule = rule;
            this.sessionAlias = sessionAlias;
        }

        public IRule getRule() {
            return rule;
        }

        public String getSessionAlias() {
            return sessionAlias;
        }
    }
}
