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
package com.exactpro.th2.simulator.impl;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.infra.grpc.MessageBatch;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;
import com.exactpro.th2.simulator.configuration.SimulatorConfiguration;
import com.exactpro.th2.simulator.grpc.RuleID;
import com.exactpro.th2.simulator.grpc.RuleInfo;
import com.exactpro.th2.simulator.grpc.RulesInfo;
import com.exactpro.th2.simulator.grpc.ServiceSimulatorGrpc;
import com.exactpro.th2.simulator.rule.IRule;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.rabbitmq.client.Delivery;

import io.grpc.stub.StreamObserver;

/**
 * Default implementation of {@link ISimulator}.
 */
public class Simulator extends ServiceSimulatorGrpc.ServiceSimulatorImplBase implements ISimulator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final Map<String, Set<Integer>> connectivityRules = new ConcurrentHashMap<>();
    private final Map<String, IAdapter> connectivityAdapters = new ConcurrentHashMap<>();
    private final Map<Integer, SimulatorRule> ruleIds = new ConcurrentHashMap<>();

    private final AtomicInteger nextId = new AtomicInteger(0);

    private final Set<Integer> defaultsRules = Collections.synchronizedSet(new HashSet<>());
    private final Object lockCanUseDefaultRules = new Object();
    private Boolean canUseDefaultRules = true;

    private MicroserviceConfiguration configuration;
    private Class<? extends IAdapter> adapterClass;

    @Override
    public void init(@NotNull SimulatorConfiguration configuration, @NotNull Class<? extends IAdapter> adapterClass) throws Exception {
        this.configuration = configuration;
        this.adapterClass = adapterClass;
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull String sessionAlias) {
        return addRule(rule, sessionAlias, false);
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull String sessionAlias, boolean parseBatch) {
       return addRule(rule, sessionAlias, parseBatch, false);
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull String sessionAlias, boolean receiveBatch, boolean sendBatch) {
        if (logger.isDebugEnabled()) {
            logger.debug("Try to add rule '{}' for session alias '{}'. Input type: '{}'. Output type: '{}'",
                    rule.getClass().getName(),
                    sessionAlias,
                    receiveBatch ? "BATCH" : "SINGLE",
                    sendBatch ? "BATCH" : "SINGLE");
        }
        IAdapter adapter = createAdapterIfAbsent(sessionAlias);
        if (adapter != null) {

            adapter.startListen((consumerTag, message) -> handleDelivery(sessionAlias, consumerTag, message));

            int id = nextId.incrementAndGet();
            synchronized (lockCanUseDefaultRules) {
                ruleIds.put(id, new SimulatorRule(rule, sessionAlias, receiveBatch, sendBatch));
                connectivityRules.computeIfAbsent(sessionAlias, key -> Collections.synchronizedSet(new HashSet<>())).add(id);
                canUseDefaultRules = false;
            }

            logger.info("Rule from class '{}' was added to simulator for session alias '{}' with input type '{}' and output type '{}' with id = {}",
                    rule.getClass().getName(),
                    receiveBatch ? "BATCH" : "SINGLE",
                    sendBatch ? "BATCH" : "SINGLE",
                    sessionAlias,
                    id);

            return RuleID.newBuilder().setId(id).build();
        } else {
            return RuleID.newBuilder().setId(-1).build();
        }
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

    public void handleDelivery(String sessionAlias, String consumerTag, Delivery delivery) {

        Map<String, List<Message>> singleMessages = new HashMap<>();
        Map<String, MessageBatch.Builder> batchMessages = new HashMap<>();

        Message message = null;
        MessageBatch batch = null;

        logger.debug("Get delivery from session alias '{}' with consumer tag '{}'", sessionAlias, consumerTag);

        Iterator<Integer> iterator = connectivityRules.getOrDefault(sessionAlias, Collections.emptySet()).iterator();

        Set<Integer> triggeredRules = new HashSet<>();

        boolean canUseDefaultRulesLocal;
        synchronized (lockCanUseDefaultRules) {
            canUseDefaultRulesLocal = canUseDefaultRules;
        }

        while (iterator.hasNext()) {
            Integer id = iterator.next();

            if (defaultsRules.contains(id) && !canUseDefaultRulesLocal) {
                logger.debug("Skip rule with id '{}', because it is default rule", id);
                continue;
            }

            SimulatorRule rule = ruleIds.get(id);

            if (rule == null || rule.getRule() == null) {
                logger.warn("Skip rule with id '{}', because it is already removed", id);

                iterator.remove();
                continue;
            }

            List<Message> triggerMessages;
            if (rule.isParseBatch()) {
                if (batch == null) {
                    try {
                        batch = MessageBatch.parseFrom(delivery.getBody());

                        if (logger.isTraceEnabled()) {
                            logger.trace("Parse delivery to message bath for rule with id '{}' = '{}'", id, TextFormat.shortDebugString(batch));
                        }
                    } catch (InvalidProtocolBufferException e) {
                        logger.error("Skip rule with id = '{}', because can not parse message batch from delivery", id);
                        continue;
                    }
                }

                triggerMessages = batch.getMessagesList();
            } else {
                if (message == null) {
                    try {
                        message = Message.parseFrom(delivery.getBody());
                    } catch (InvalidProtocolBufferException e) {
                        logger.error("Skip rule with id = '{}', because can not parse single message from delivery", id);
                        continue;
                    }
                }

                triggerMessages = Collections.singletonList(message);
            }

            for (Message triggerMessage : triggerMessages) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Handle message name = {}", triggerMessage.getMetadata().getMessageType());
                }
                if (rule.getRule().checkTriggered(triggerMessage)) {
                    try {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Process message by rule with ID '{}' = {}", id, TextFormat.shortDebugString(triggerMessage));
                        }
                        var messageListToResponse = rule.getRule().handle(triggerMessage);

                        for (Message responseMessage : messageListToResponse) {
                            String responseSessionAlias = StringUtils.defaultIfEmpty(responseMessage.getMetadata().getId().getConnectionId().getSessionAlias(), sessionAlias);
                            if (rule.isSendBatch()) {
                                batchMessages.computeIfAbsent(responseSessionAlias, key -> MessageBatch.newBuilder()).addMessages(responseMessage);
                            } else {
                                singleMessages.computeIfAbsent(responseSessionAlias, key -> new ArrayList<>()).add(responseMessage);
                            }
                        }

                        triggeredRules.add(id);
                        logger.debug("Rule with ID '{}' has returned '{}' message(s)", id, messageListToResponse.size());
                        if (logger.isTraceEnabled()) {
                            StringBuilder builder = new StringBuilder();
                            builder.append("{");
                            for (Message messageList : messageListToResponse) {
                                builder.append(TextFormat.shortDebugString(messageList));
                                builder.append(";");
                            }
                            builder.append("}");

                            logger.trace("Rule with id '{}' generate messages '{}'", id, builder.toString());
                        }
                    } catch (Exception e) {
                        logger.error("Can not handle message in rule with id = {}", id, e);
                    }
                } else {
                    logger.trace("Skip rule with id = '{}', because not triggered", id);
                }
            }

            logger.debug("Triggered on message rules with ids = {}, count single messages to respond = {}, count batches messages to respond = {}", triggeredRules, singleMessages.size(), batchMessages.size());

            if (triggeredRules.size() > 1) {
                logger.info("Triggered on message more one rule. Rules ids = {}", triggeredRules);
            }
        }

        singleMessages.forEach((connectivityAlias, messages) -> {
            IAdapter adapter = createAdapterIfAbsent(connectivityAlias);

            if (adapter == null) {
                logger.error("Can not send single messages to session alias '{}'. Can no create adapter", sessionAlias);
                return;
            }

            for (Message singleMessage : messages) {
                try {
                    adapter.send(singleMessage);
                } catch (IOException e) {
                    logger.error("Can not send single message to session alias '{}'. Single message = {}", connectivityAlias, singleMessage, e);
                }
            }
        });

        batchMessages.forEach((connectivityAlias, batchBuilder) -> {
            IAdapter adapter = createAdapterIfAbsent(connectivityAlias);

            if (adapter == null) {
                logger.error("Can not send batch message to session alias '{}'. Can no create adapter", sessionAlias);
                return;
            }

            MessageBatch batchMessage = batchBuilder.build();
            try {
                adapter.send(batchMessage);
            } catch (IOException e) {
                logger.error("Can not send batch message to session alias '{}'. Batch message = {}", connectivityAlias, batchMessage, e);
            }
        });
    }

    @Override
    public void close() {
        for (Entry<String, IAdapter> entry : connectivityAdapters.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                logger.error("Can not close adapter for connectivity = {}", entry.getKey(), e);
            }
        }
        connectivityAdapters.clear();
        connectivityRules.clear();
        ruleIds.clear();
    }

    private class SimulatorRule {
        private final IRule rule;
        private final String sessionAlias;
        private final boolean parseBatch;
        private final boolean sendBatch;

        public SimulatorRule(IRule rule, String sessionAlias, boolean parseBatch, boolean sendBatch) {
            this.rule = rule;
            this.sessionAlias = sessionAlias;
            this.parseBatch = parseBatch;
            this.sendBatch = sendBatch;
        }

        public IRule getRule() {
            return rule;
        }

        public String getSessionAlias() {
            return sessionAlias;
        }

        public boolean isParseBatch() {
            return parseBatch;
        }

        public boolean isSendBatch() {
            return sendBatch;
        }
    }

    private IAdapter createAdapterIfAbsent(String sessionAlias) {
        try {
            return connectivityAdapters.computeIfAbsent(sessionAlias, (key) -> {
                try {
                    IAdapter adapter = adapterClass.getConstructor().newInstance();
                    adapter.init(configuration, sessionAlias);

                    logger.info("Create adapter for connection '{}'", sessionAlias);

                    return adapter;
                } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                    throw new IllegalStateException("Can not create adapter for session alias: " + sessionAlias, e);
                }
            });
        } catch (Exception e) {
            logger.error("Can not get adapter for session alias: " + sessionAlias, e);
        }
        return null;
    }
}
