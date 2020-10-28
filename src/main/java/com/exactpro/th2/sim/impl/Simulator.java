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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.common.grpc.ConnectionID;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.sim.IAdapter;
import com.exactpro.th2.sim.ISimulator;
import com.exactpro.th2.sim.configuration.SimulatorConfiguration;
import com.exactpro.th2.sim.grpc.RuleID;
import com.exactpro.th2.sim.grpc.RuleInfo;
import com.exactpro.th2.sim.grpc.RulesInfo;
import com.exactpro.th2.sim.grpc.ServiceSimulatorGrpc;
import com.exactpro.th2.sim.rule.IRule;
import com.google.protobuf.Empty;

import io.grpc.stub.StreamObserver;

/**
 * Default implementation of {@link ISimulator}.
 */
public class Simulator extends ServiceSimulatorGrpc.ServiceSimulatorImplBase implements ISimulator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final Map<ConnectionID, Set<Integer>> connectivityRules = new ConcurrentHashMap<>();
    private final Map<ConnectionID, IAdapter> connectivityAdapters = new ConcurrentHashMap<>();
    private final Map<Integer, IRule> ruleIds = new ConcurrentHashMap<>();
    private final Map<Integer, ConnectionID> rulesConnectivity = new ConcurrentHashMap<>();

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
    public RuleID addRule(@NotNull IRule rule, @NotNull ConnectionID connectionID) {
        return addRule(rule, connectionID, false);
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull ConnectionID connectionID, boolean parseBatch) {
       return addRule(rule, connectionID, parseBatch, false);
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull ConnectionID connectionID, boolean receiveBatch, boolean sendBatch) {
        if (logger.isDebugEnabled()) {
            logger.debug("Try to add rule '{}' for connectionID '{}'. Input type: '{}'. Output type: '{}'",
                    rule.getClass().getName(),
                    connectionID.getSessionAlias(),
                    receiveBatch ? "BATCH" : "SINGLE",
                    sendBatch ? "BATCH" : "SINGLE");
        }
        if (createAdapterIfAbsent(connectionID, receiveBatch, sendBatch)) {
            int id = nextId.incrementAndGet();
            synchronized (lockCanUseDefaultRules) {
                ruleIds.put(id, rule);
                rulesConnectivity.put(id, connectionID);
                connectivityRules.computeIfAbsent(connectionID, (key) -> Collections.synchronizedSet(new HashSet<>())).add(id);
                canUseDefaultRules = false;
            }

            logger.info("Rule from class '{}' was added to simulator for connection '{}' with id = {}",
                    rule.getClass().getName(),
                    connectionID.getSessionAlias(),
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

        IRule rule = ruleIds.remove(id.getId());

        if (rule != null) {
            Set<Integer> ids = connectivityRules.get(rulesConnectivity.get(id.getId()));
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
        IRule rule = ruleIds.get(ruleId);
        if (rule == null) {
            return RuleInfo.newBuilder().setId(RuleID.newBuilder().setId(-1).build()).build();
        }

        return RuleInfo.newBuilder()
                .setId(RuleID.newBuilder().setId(ruleId).build())
                .setClassName(rule.getClass().getName())
                .setConnectionId(rulesConnectivity.get(ruleId))
                .build();
    }

    @Override
    public List<Message> handle(@NotNull ConnectionID connectionID, @NotNull Message message) {
        List<Message> result = new ArrayList<>();

        logger.debug("Get message from connection = {}", connectionID.getSessionAlias());
        logger.trace("Message from connection '{}' = {}", connectionID.getSessionAlias(), message);

        Iterator<Integer> iterator = connectivityRules.getOrDefault(connectionID, Collections.emptySet()).iterator();

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

            IRule rule = ruleIds.get(id);

            if (rule == null) {
                logger.warn("Skip rule with id '{}', because it is already removed", id);

                iterator.remove();
                continue;
            }

            try {
                if (rule.checkTriggered(message)) {
                    try {
                        logger.debug("Process message by rule with ID '{}'", id);
                        var messageListToRespond = rule.handle(message);

                        logger.debug("Rule with ID '{}' has returned '{}' message(s)", id, messageListToRespond.size());
                        result.addAll(messageListToRespond);
                        triggeredRules.add(id);
                    } catch (Exception e) {
                        logger.error("Can not handle message in rule with id = {}", id, e);
                    }
                }
            } catch (Exception e) {
                logger.error("Can not check trigger in rule with id = {}", id, e);
            }
        }

        logger.debug("Triggered on message rules with ids = {}, messages to respond = {}", triggeredRules, result.size());

        if (triggeredRules.size() > 1) {
            logger.info("Triggered on message more one rule. Rules ids = {}", triggeredRules);
        }

        return result;
    }

    @Override
    public void close() {
        for (Entry<ConnectionID, IAdapter> entry : connectivityAdapters.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                logger.error("Can not close adapter for connectivity = {}", entry.getKey(), e);
            }
        }
    }

    private boolean createAdapterIfAbsent(ConnectionID connectionID, boolean parseBatch, boolean sendBatch) {
        try {
            connectivityAdapters.computeIfAbsent(connectionID, (key) -> {
                try {
                    IAdapter adapter = adapterClass.getConstructor().newInstance();
                    adapter.init(configuration, connectionID, parseBatch, sendBatch, this);

                    logger.info("Create adapter for connection '{}'. Input Type = '{}'. Output Type = '{}'",
                            connectionID.getSessionAlias(),
                            parseBatch ? "BATCH" : "SINGLE",
                            sendBatch ? "BATCH" : "SINGLE");

                    return adapter;
                } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                    throw new IllegalStateException("Can not create adapter for connectivity id: " + connectionID, e);
                }
            });
            return true;
        } catch (Exception e) {
            logger.error("Can not get adapter", e);
        }
        return false;
    }
}
