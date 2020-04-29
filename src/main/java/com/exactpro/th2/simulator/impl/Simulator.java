/******************************************************************************
 * Copyright 2020 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.evolution.api.phase_1.ConnectivityId;
import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.configuration.MicroserviceConfiguration;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;
import com.exactpro.th2.simulator.RuleID;
import com.exactpro.th2.simulator.RuleInfo;
import com.exactpro.th2.simulator.RulesInfo;
import com.exactpro.th2.simulator.ServiceSimulatorGrpc;
import com.exactpro.th2.simulator.rule.IRule;
import com.google.protobuf.Empty;

import io.grpc.stub.StreamObserver;

public class Simulator extends ServiceSimulatorGrpc.ServiceSimulatorImplBase implements ISimulator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final Map<ConnectivityId, Set<Integer>> connectivityRules = new ConcurrentHashMap<>();
    private final Map<ConnectivityId, IAdapter> connectivityAdapters = new ConcurrentHashMap<>();
    private final Map<Integer, IRule> ruleIds = new ConcurrentHashMap<>();
    private final Map<Integer, ConnectivityId> rulesConnectivity = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(0);

    private MicroserviceConfiguration configuration;
    private Class<? extends IAdapter> adapterClass;

    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull Class<? extends IAdapter> adapterClass) throws Exception {
        this.configuration = configuration;
        this.adapterClass = adapterClass;
    }

    @Override
    public RuleID addRule(@NotNull IRule rule, @NotNull ConnectivityId connectivityId) {
        if (createAdapterIfAbsent(connectivityId)) {
            int id = nextId.incrementAndGet();
            ruleIds.put(id, rule);
            rulesConnectivity.put(id, connectivityId);
            connectivityRules.computeIfAbsent(connectivityId, (key) -> new ConcurrentHashSet<>()).add(id);
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
                .addAllInfo(ruleIds.entrySet().stream().map(entry -> RuleInfo
                        .newBuilder()
                        .setClassName(entry.getValue().getClass().getName())
                        .setId(RuleID
                                .newBuilder()
                                .setId(entry.getKey()).build())
                        .build())
                        .collect(Collectors.toList())
                )
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public List<Message> handle(@NotNull ConnectivityId connectivityId, @NotNull Message message) {
        List<Message> result = new ArrayList<>();
        boolean triggered = false;

        Iterator<Integer> iterator = connectivityRules.getOrDefault(connectivityId, Collections.emptySet()).iterator();

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
        for (Entry<ConnectivityId, IAdapter> entry : connectivityAdapters.entrySet()) {
            try {
                entry.getValue().close();
            } catch (IOException e) {
                logger.error("Can not close adapter with connectivity id: " + entry.getKey(), e);
            }
        }
    }

    private boolean createAdapterIfAbsent(ConnectivityId connectivityId) {
        try {
            connectivityAdapters.computeIfAbsent(connectivityId, (key) -> {
                try {
                    IAdapter iAdapter = adapterClass.newInstance();
                    iAdapter.init(configuration, connectivityId, this);
                    logger.debug("Create adapter for connectivityID: " + connectivityId.getConnectivityId());
                    return iAdapter;
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new IllegalStateException("Can not create adapter with connectivity id: " + connectivityId, e);
                }
            });
            return true;
        } catch (IllegalStateException e) {
            logger.error("Can not get adapter", e);
        }
        return false;
    }
}
