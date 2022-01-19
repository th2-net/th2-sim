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

import com.exactpro.th2.common.schema.factory.AbstractCommonFactory;
import com.exactpro.th2.sim.ISimulator;
import com.exactpro.th2.sim.ISimulatorPart;
import com.exactpro.th2.sim.ISimulatorServer;
import com.exactpro.th2.sim.configuration.DefaultRuleConfiguration;
import com.exactpro.th2.sim.configuration.SimulatorConfiguration;
import com.exactpro.th2.sim.grpc.RuleID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * Default implementation {@link ISimulatorServer}.
 */
public class SimulatorServer implements ISimulatorServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private AbstractCommonFactory factory;
    private ISimulator simulator;
    private Server server;

    @Override
    public void init(@NotNull AbstractCommonFactory commonFactory, @NotNull Class<? extends ISimulator> simulatorClass) {

        this.factory = Objects.requireNonNull(commonFactory, "Common factory can not be null");
        Objects.requireNonNull(simulatorClass, "Simulator class can not be null");

        try {
            simulator = simulatorClass.getConstructor().newInstance();
            simulator.init(factory);
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalArgumentException("Can not create simulator with default constructor from class" + simulatorClass, e);
        } catch (Exception e) {
            throw new IllegalStateException("Can not init simulator from class " + simulatorClass.getTypeName(), e);
        }
    }

    @Override
    public boolean start() {
        logger.debug("Try to start simulator server");

        if (server != null) {
            logger.debug("Simulator server have already started");
            return false;
        }

        logger.debug("Try to get all defaults rules");
        SimulatorConfiguration customConfiguration = factory.getCustomConfiguration(SimulatorConfiguration.class);
        List<DefaultRuleConfiguration> defaultRules = customConfiguration == null
                ? emptyList()
                : customConfiguration.getDefaultRules()
                    .stream()
                    .filter(DefaultRuleConfiguration::isEnable)
                    .filter(it -> it.getMethodName() != null)
                    .collect(Collectors.toList());

        logger.info("Count default rules = {}", defaultRules.size());

        List<ISimulatorPart> simulatorsParts = new ArrayList<>();
        for (ISimulatorPart tmp : ServiceLoader.load(ISimulatorPart.class)) {
            logger.info("Was loaded simulator part class with name: " + tmp.getClass());
            tmp.init(simulator);

            addDefaultRules(tmp, defaultRules);

            simulatorsParts.add(tmp);
            logger.debug("Was added to gRPC simulator part class with name: " + tmp.getClass());
        }
        BindableService[] services = new BindableService[simulatorsParts.size() + 1];
        for (int i = 0; i < simulatorsParts.size(); i++) {
            services[i] = simulatorsParts.get(i);
        }

        services[services.length - 1] = simulator;

        server = factory.getGrpcRouter().startServer(services);

        try {
            logger.debug("Simulator server is starting.");
            server.start();
            logger.info("Simulator server was started.");
            return true;
        } catch (IOException e) {
            logger.error("Can not start simulator server.", e);
            return false;
        }
    }

    private void addDefaultRules(ISimulatorPart tmp, List<DefaultRuleConfiguration> defaultRules) {
        Class<? extends ISimulatorPart> serviceClass = tmp.getClass();
        Method[] methods = serviceClass.getDeclaredMethods();
        for (Method method : methods) {

            if (method.getParameterCount() == 2) {
                Class<?>[] parameterTypes = method.getParameterTypes();

                defaultRules.stream().filter(it -> it.getMethodName().equals(method.getName())).forEach(it -> {
                    DefaultSetterObserver defaultSetterObserver = new DefaultSetterObserver(simulator, logger, method.getName(), serviceClass);

                    Object request = null;
                    JsonNode ruleRequest = it.getSettings();

                    if (ruleRequest != null) {
                        try {
                            Builder builder = getBuilder(parameterTypes[0]);
                            if (builder != null) {
                                JsonFormat.parser().merge(ruleRequest.toString(), builder);
                                request = builder.build();
                            } else {
                                logger.warn("Can not build request for class: '{}'", parameterTypes[0]);
                            }
                        } catch (Exception e) {
                            logger.warn("Can not parse rule request to class: '{}'", parameterTypes[0], e);
                        }
                    }

                    if (request == null) {
                        logger.warn("Try to send null request in method '{}' in class '{}'", method.getName(), serviceClass);
                    }

                    try {
                        method.invoke(tmp, request, defaultSetterObserver);
                    } catch (Exception ex) {
                        logger.error("Can not execute method: '{}' in class '{}'", method.getName(), serviceClass, ex);
                        return;
                    }

                    defaultSetterObserver.waitFinished();
                });

            }
        }
    }

    private Builder getBuilder(Class<?> parameterType) {
        try {
            return (Builder) parameterType.getMethod("newBuilder").invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            logger.error("Can not create builder for class = {}", parameterType, e);
            return null;
        }

    }

    private static class DefaultSetterObserver implements StreamObserver<RuleID> {


        private final ISimulator simulator;
        private final Logger logger;
        private final String methodName;
        private final Class<?> serviceClass;
        private final AtomicBoolean isFinish = new AtomicBoolean(false);

        public DefaultSetterObserver(ISimulator simulator, Logger logger, String methodName, Class<?> serviceClass) {
            this.simulator = simulator;
            this.logger = logger;
            this.methodName = methodName;
            this.serviceClass = serviceClass;
        }

        @Override
        public void onNext(RuleID value) {
            simulator.addDefaultRule(value);
        }

        @Override
        public void onError(Throwable t) {
            isFinish.set(true);
            logger.error("Method with name '{}' in class '{}' was executed with error", methodName, serviceClass, t);
        }

        @Override
        public void onCompleted() {
            isFinish.set(true);
        }

        public void waitFinished() {
            while (!isFinish.get()) {
                Thread.yield();
            }
        }
    }

    @Override
    public void close() {
        logger.debug("Try to close simulator server");

        if (server != null && !server.isShutdown()) {
            try {
                logger.debug("Try to shutdown GRPC server");
                server.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.debug("Can not wait to terminate server", e);
            }
        }

        if (simulator != null) {
            try {
                logger.debug("Try to close simulator");
                simulator.close();
            } catch (IOException e) {
                logger.error("Can not close simulator" ,e);
            }
        }

        logger.info("Simulator server was closed");
    }

    @Override
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
