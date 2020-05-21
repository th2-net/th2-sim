/*******************************************************************************
 *  Copyright 2020 Exactpro (Exactpro Systems Limited)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/
package com.exactpro.th2.simulator.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.mina.util.ConcurrentHashSet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;
import com.exactpro.th2.simulator.grpc.RuleID;
import com.exactpro.th2.simulator.grpc.RuleInfo;
import com.exactpro.th2.simulator.grpc.RulesInfo;
import com.exactpro.th2.simulator.grpc.ServiceSimulatorGrpc;
import com.exactpro.th2.simulator.grpc.ServiceSimulatorGrpc;
import com.exactpro.th2.simulator.rule.IRule;
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

    private MicroserviceConfiguration configuration;
    private Class<? extends IAdapter> adapterClass;

    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull Class<? extends IAdapter> adapterClass) throws Exception {
        this.configuration = configuration;
        this.adapterClass = adapterClass;
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull ConnectionID connectionID) {
        if (createAdapterIfAbsent(connectionID)) {
            int id = nextId.incrementAndGet();
            ruleIds.put(id, rule);
            rulesConnectivity.put(id, connectionID);
            connectivityRules.computeIfAbsent(connectionID, (key) -> new ConcurrentHashSet<>()).add(id);
            logger.debug("Rule with class '{}', with id '{}' was added", rule.getClass().getName(), id);
            return RuleID.newBuilder().setId(id).build();
        } else {
            return RuleID.newBuilder().setId(-1).build();
        }
    }

    @Override
    public void removeRule(RuleID id, StreamObserver<Empty> responseObserver) {
        IRule rule = ruleIds.remove(id.getId());
        if (rule != null) {
            Set<Integer> ids = connectivityRules.get(rulesConnectivity.get(id.getId()));
            if (ids != null) {
                ids.remove(id.getId());
            }

            logger.debug("Rule with id {} was removed", id.getId());
        }
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
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
        boolean triggered = false;

        Iterator<Integer> iterator = connectivityRules.getOrDefault(connectionID, Collections.emptySet()).iterator();

        while (iterator.hasNext()) {
            Integer id = iterator.next();
            IRule rule = ruleIds.get(id);
            if (rule == null) {
                iterator.remove();
                continue;
            }

            if (rule.checkTriggered(message)) {
                if (triggered) {
                    logger.info("Triggered on message more one rule. Rule id: " + id);
                }

                result.addAll(rule.handle(message));
                triggered = true;
            }
        }

        return result;
    }

    @Override
    public void close() {
        for (Entry<ConnectionID, IAdapter> entry : connectivityAdapters.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                logger.error("Can not close adapter with connectivity id: " + entry.getKey(), e);
            }
        }
    }

    private boolean createAdapterIfAbsent(ConnectionID connectionID) {
        try {
            connectivityAdapters.computeIfAbsent(connectionID, (key) -> {
                try {
                    IAdapter iAdapter = adapterClass.newInstance();
                    iAdapter.init(configuration, connectionID, this);
                    logger.debug("Create adapter for ConnectionID: " + connectionID.getSessionAlias());
                    return iAdapter;
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new IllegalStateException("Can not create adapter with connectivity id: " + connectionID, e);
                }
            });
            return true;
        } catch (IllegalStateException e) {
            logger.error("Can not get adapter", e);
        }
        return false;
    }
}
