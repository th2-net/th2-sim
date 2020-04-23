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
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.th2.simulator.CreateFixRule;
import com.exactpro.th2.simulator.IServiceSimulator;
import com.exactpro.th2.simulator.RuleID;
import com.exactpro.th2.simulator.RuleInfo;
import com.exactpro.th2.simulator.RuleInfo.RuleStatus;
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
    //private final Set<Integer> enableRules;

    private AtomicInteger nextId = new AtomicInteger(1);


    public ServiceSimulator() {
        ruleTypes = new ConcurrentHashMap<>();
        rules = new ConcurrentHashMap<>();
        //enableRules = new ConcurrentHashSet<>();
        loadTypes();
    }

    @Override
    public void createRuleFIX(CreateFixRule request, StreamObserver<RuleInfo> responseObserver) {
        Class<? extends IRule> ruleClass = getRuleClass("fix-rule");
        try {
            IRule rule = ruleClass.getConstructor(Integer.TYPE, Map.class).newInstance(nextId.getAndIncrement(), request.getMessageFieldsMap());
            rules.put(rule.getId(), rule);
            responseObserver.onNext(createRuleInfo(rule));
            responseObserver.onCompleted();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            responseObserver.onError(e);
        }
    }

    private Class<? extends IRule> getRuleClass(String type) {
        Class<? extends IRule> ruleClass = ruleTypes.get(type);
        if (ruleClass == null) {
            throw new IllegalArgumentException("Wrong type's name");
        }

        return ruleClass;
    }

    //    @Override
//    public void createRule(CreateRuleEvent request, StreamObserver<RuleInfo> responseObserver) {
//        Class<? extends IRule> ruleClass = ruleTypes.get(request.getType());
//        if (ruleClass == null) {
//            responseObserver.onError(new IllegalArgumentException("Wrong type's name"));
//        } else {
//            try {
//                IRule rule = ruleClass
//                        .getConstructor(Integer.TYPE, Map.class)
//                        .newInstance(nextId.getAndIncrement(), request.getArgumentsCount() > 0 ? new HashMap<>(request.getArgumentsMap()) : new HashMap<>());
//
//                rules.put(rule.getId(), rule);
//
//                //if (request.getAutoEnable()) {
//                    enableRules.add(rule.getId());
//                //}
//
//                responseObserver.onNext(createRuleInfo(rule));
//                responseObserver.onCompleted();
//            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
//                String errorMessage = "Can not create rule with type: " + request.getType();
//                logger.error(errorMessage);
//                responseObserver.onError(new IllegalStateException(errorMessage, e));
//                ruleTypes.remove(request.getType());
//            }
//        }
//    }

    @Override
    public void removeRule(RuleID request, StreamObserver<RuleInfo> responseObserver) {
        rules.remove(request.getId());
        //enableRules.remove(request.getId());
        responseObserver.onNext(createEmptyRuleInfo());
        responseObserver.onCompleted();
    }

//    @Override
//    public void enableRule(RuleID request, StreamObserver<RuleInfo> responseObserver) {
//        int ruleId = request.getId();
//        if (!rules.containsKey(ruleId)) {
//            responseObserver.onError(new IllegalArgumentException("Can not find rule with id: " + ruleId));
//        } else {
//            IRule rule = rules.get(ruleId);
//            if (!enableRules.contains(ruleId)) {
//                enableRules.add(ruleId);
//            }
//            responseObserver.onNext(createRuleInfo(rule));
//        }
//        responseObserver.onCompleted();
//    }
//
//    @Override
//    public void disableRule(RuleID request, StreamObserver<RuleInfo> responseObserver) {
//        int ruleId = request.getId();
//        if (!rules.containsKey(ruleId)) {
//            responseObserver.onError(new IllegalArgumentException("Can not find rule with id: " + ruleId));
//        } else {
//            enableRules.remove(ruleId);
//            responseObserver.onNext(createRuleInfo(rules.get(ruleId)));
//        }
//        responseObserver.onCompleted();
//    }

//    @Override
//    public void getRuleTypes(Empty request, StreamObserver<RuleTypes> responseObserver) {
//        responseObserver.onNext(RuleTypes.newBuilder().addAllTypes(ruleTypes.keySet()).build());
//        responseObserver.onCompleted();
//    }

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

        Iterator<Entry<Integer, IRule>> iterator = rules.entrySet().iterator();
        while (iterator.hasNext()) {
            IRule rule = iterator.next().getValue();
            if (rule == null) {
                iterator.remove();
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
        if (rule == null || !rules.containsKey(rule.getId())) {
            return createEmptyRuleInfo();
        }

        return RuleInfo.newBuilder()
                .setId(RuleID.newBuilder().setId(rule.getId()).build())
                .setStatus(RuleStatus.ENABLE)
                .build();
    }

    private RuleInfo createEmptyRuleInfo() {
        return RuleInfo.newBuilder()
                .setId(RuleID.newBuilder().setId(-1).build())
                .setStatus(RuleStatus.NONE)
                .build();
    }

    private void loadTypes() {
        try {
            File fileOrDirectory = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            List<String> classesName = new ArrayList<>();
            if (fileOrDirectory.isDirectory()) {
                logger.info("Load from directory");
                classesName.addAll(loadClassesNameFromDirectory(fileOrDirectory));
            } else {
                logger.info("Load rule types from jar");
                classesName.addAll(loadClassesNameFromJar(fileOrDirectory));
            }

            ClassLoader loader = this.getClass().getClassLoader();

            for (String className : classesName) {
                try {
                    Class<?> _class = loader.loadClass(className);
                    checkClass(_class);
                } catch (ClassNotFoundException e) {
                    logger.error("Can not check class with name: " + className);
                }
            }
        } catch (URISyntaxException e) {
            logger.error("Can not load types", e);
        }
    }

    private Collection<? extends String> loadClassesNameFromJar(File fileOrDirectory) {
        List<String> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(fileOrDirectory))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.');
                    result.add(className.substring(0, className.length() - ".class".length()));
                }
            }

            return result;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private Collection<? extends String> loadClassesNameFromDirectory(File dir) {
        try {
            Enumeration<URL> resources = this.getClass().getClassLoader().getResources("");

            List<String> classesName = new ArrayList<>();
            while (resources.hasMoreElements()) {
                File tmp = new File(resources.nextElement().getFile());
                classesName.addAll(loadClasses(tmp, tmp));
            }

            return classesName;
        } catch (IOException e) {
            logger.error("Can not get classpath directory from class loader", e);
            return loadClasses(dir, dir);
        }
    }

    private Collection<? extends String> loadClasses(File mainDirectory, File directory) {
        List<String> list = new ArrayList<>();

        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                list.addAll(loadClasses(mainDirectory, file));
            } else if (file.getName().endsWith(".class") || file.getName().endsWith(".kt")) {
                String className = mainDirectory
                        .toPath()
                        .relativize(file.toPath())
                        .toString()
                        .replace('/', '.')
                        .replace('\\', '.')
                        .replace(".class", "");
                list.add(className);
            }
        }
        return list;
    }

    private void checkClass(Class<?> ruleClass) {
        if (ruleClass == null) {
            return;
        }

        SimulatorRule annotation = ruleClass.getAnnotation(SimulatorRule.class);

        if (annotation == null) {
            return;
        }

        if (IRule.class.isAssignableFrom(ruleClass)) {

            if (ruleTypes.containsKey(annotation.value())) {
                throw new IllegalStateException("Duplicate rule type's names: " + annotation.value());
            }

            ruleTypes.put(annotation.value(), (Class<? extends IRule>)ruleClass);
        } else {
            logger.error("Can not add rule with class name: " + ruleClass.getName());
        }
    }
}
