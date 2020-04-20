/******************************************************************************
 * Copyright 2009-2020 Exactpro (Exactpro Systems Limited)
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.mina.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.th2.simulator.CreateRuleEvent;
import com.exactpro.th2.simulator.IServiceSimulator;
import com.exactpro.th2.simulator.RuleID;
import com.exactpro.th2.simulator.RuleInfo;
import com.exactpro.th2.simulator.RuleInfo.RuleStatus;
import com.exactpro.th2.simulator.RuleTypes;
import com.exactpro.th2.simulator.RulesInfo;
import com.exactpro.th2.simulator.ServiceSimulatorGrpc.ServiceSimulatorImplBase;
import com.exactpro.th2.simulator.rule.IRule;
import com.exactpro.th2.simulator.rule.SimulatorRule;
import com.google.protobuf.Empty;

import io.grpc.stub.StreamObserver;

public class ServiceSimulator extends ServiceSimulatorImplBase implements IServiceSimulator {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final Map<String, Class<? extends IRule>> ruleTypes;
    private final Map<Integer, IRule> rules;
    private final Set<Integer> enableRules;

    private AtomicInteger nextId = new AtomicInteger(1);


    public ServiceSimulator() {
        ruleTypes = new ConcurrentHashMap<>();
        rules = new ConcurrentHashMap<>();
        enableRules = new ConcurrentHashSet<>();
        loadTypes();
    }

    @Override
    public void createRule(CreateRuleEvent request, StreamObserver<RuleInfo> responseObserver) {
        Class<? extends IRule> ruleClass = ruleTypes.get(request.getType());
        if (ruleClass == null) {
            responseObserver.onError(new IllegalArgumentException("Wrong type's name"));
        } else {
            try {
                IRule rule = ruleClass
                        .getConstructor(Integer.TYPE, Map.class)
                        .newInstance(nextId.getAndIncrement(), request.getArgumentsCount() > 0 ? new HashMap<>(request.getArgumentsMap()) : new HashMap<>());

                rules.put(rule.getId(), rule);

                if (request.getAutoEnable()) {
                    enableRules.add(rule.getId());
                }

                responseObserver.onNext(createRuleInfo(rule));
                responseObserver.onCompleted();
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                String errorMessage = "Can not create rule with type: " + request.getType();
                logger.error(errorMessage);
                responseObserver.onError(new IllegalStateException(errorMessage, e));
                ruleTypes.remove(request.getType());
            }
        }
    }

    @Override
    public void removeRule(RuleID request, StreamObserver<RuleInfo> responseObserver) {
        rules.remove(request.getId());
        enableRules.remove(request.getId());
        responseObserver.onNext(createEmptyRuleInfo());
        responseObserver.onCompleted();
    }

    @Override
    public void enableRule(RuleID request, StreamObserver<RuleInfo> responseObserver) {
        int ruleId = request.getId();
        if (!rules.containsKey(ruleId)) {
            responseObserver.onError(new IllegalArgumentException("Can not find rule with id: " + ruleId));
        } else {
            IRule rule = rules.get(ruleId);
            if (!enableRules.contains(ruleId)) {
                enableRules.add(ruleId);
            }
            responseObserver.onNext(createRuleInfo(rule));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void disableRule(RuleID request, StreamObserver<RuleInfo> responseObserver) {
        int ruleId = request.getId();
        if (!rules.containsKey(ruleId)) {
            responseObserver.onError(new IllegalArgumentException("Can not find rule with id: " + ruleId));
        } else {
            enableRules.remove(ruleId);
            responseObserver.onNext(createRuleInfo(rules.get(ruleId)));
        }
        responseObserver.onCompleted();
    }

    @Override
    public void getRuleTypes(Empty request, StreamObserver<RuleTypes> responseObserver) {
        responseObserver.onNext(RuleTypes.newBuilder().addAllTypes(ruleTypes.keySet()).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getRulesInfo(Empty request, StreamObserver<RulesInfo> responseObserver) {
        responseObserver.onNext(RulesInfo.newBuilder()
                .addAllInfo(rules.values()
                        .stream()
                        .map(this::createRuleInfo)
                        .collect(Collectors.toList()))
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public List<Message> handle(Message message) {
        List<Message> result = new ArrayList<>();
        boolean triggered = false;
        Iterator<Integer> idIterator = enableRules.iterator();
        while (idIterator.hasNext()) {
            IRule rule = rules.get(idIterator.next());
            if (rule == null) {
                idIterator.remove();
                continue;
            }

            if (rule.checkTriggered(message)) {

                if (triggered) {
                    logger.error("One more rule with id '{}' triggered on message with id '{}'.", rule.getId(), message.getMetadata().getMessageId());
                }

                result.addAll(rule.handle(message));
                triggered = true;
            }
        }

        return result;
    }

    private RuleInfo createRuleInfo(IRule rule) {
        if (rule == null) {
            return createEmptyRuleInfo();
        }

        return RuleInfo.newBuilder()
                .setId(RuleID.newBuilder().setId(rule.getId()).build())
                .putAllArguments(rule.getArguments())
                .setType(rule.getType())
                .setStatus(enableRules.contains(rule.getId()) ? RuleStatus.EXECUTE : RuleStatus.EXIST)
                .build();
    }

    private RuleInfo createEmptyRuleInfo() {
        return RuleInfo.newBuilder()
                .setId(RuleID.newBuilder().setId(-1).build())
                .setStatus(RuleStatus.NONE)
                .build();
    }

    private void loadTypes(){
        try {
            Enumeration<URL> resources = this.getClass().getClassLoader().getResources("");

            List<File> dirs = new ArrayList<>();

            while (resources.hasMoreElements()) {
                URL res = resources.nextElement();
                dirs.add(new File(res.getFile()));
            }

            for (File dir : dirs) {
                loadClasses(dir, dir);
            }


        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadClasses(File mainDirectory, File directory) {
        if (!directory.exists()) {
            return;
        }

        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                loadClasses(mainDirectory, file);
            } else if (file.getName().endsWith(".class")) {
                Class<?> _class = null;
                String className = mainDirectory
                        .toPath()
                        .relativize(file.toPath())
                        .toString()
                        .replace('/', '.')
                        .replace('\\', '.')
                        .replace(".class", "");
                try {
                    _class = Class.forName(className);
                } catch (ClassNotFoundException e) {
                    try {
                        _class = this.getClass().getClassLoader().loadClass(className);
                    } catch (ClassNotFoundException ex) {
                        logger.error("Can not load class with name: " + className, ex);
                    }
                }

                if (_class == null) {
                    continue;
                }

                SimulatorRule annotation = _class.getAnnotation(SimulatorRule.class);

                if (annotation == null) {
                    continue;
                }

                if (IRule.class.isAssignableFrom(_class)) {

                    if (ruleTypes.containsKey(annotation.value())) {
                        throw new IllegalStateException("Duplicate rule type's names: " + annotation.value());
                    }

                    ruleTypes.put(annotation.value(), (Class<? extends IRule>) _class);
                } else {
                    logger.warn("Can not add rule with class name: " + className);
                }
            }
        }
    }
}
