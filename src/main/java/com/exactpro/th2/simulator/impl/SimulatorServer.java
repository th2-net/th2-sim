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
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;
import com.exactpro.th2.simulator.ISimulatorPart;
import com.exactpro.th2.simulator.ISimulatorServer;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;

/**
 * Default implementation {@link ISimulatorServer}.
 */
public class SimulatorServer implements ISimulatorServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final Set<Class<? extends ISimulatorPart>> simulatorParts = new HashSet<>();
    private MicroserviceConfiguration configuration;
    private ISimulator simulator;
    private Server server;

    public SimulatorServer() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null && !server.isShutdown()) {
                System.err.println("Stopping GRPC server in simulator");
                server.shutdownNow();
                System.err.println("GRPC server was stopped");
            }
        }));
    }

    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull Class<? extends ISimulator> simulatorClass, @NotNull Class<? extends IAdapter> adapterClass) {
        if (configuration.getTh2().getConnectivityAddresses().size() < 1) {
            throw new IllegalArgumentException("Connectivity addresses must contain at least 1 element");
        }

        this.configuration = configuration;
        try {
            simulator = simulatorClass.newInstance();
            simulator.init(configuration, adapterClass);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Can not create simulator with default constructor", e);
        } catch (Exception e) {
            throw new IllegalStateException("Can not init simulator", e);
        }
    }

    @Override
    public boolean start() {
        if (server != null) {
            return false;
        }

        NettyServerBuilder builder = NettyServerBuilder.forPort(configuration.getPort()).addService(simulator);
        for (ISimulatorPart tmp : ServiceLoader.load(ISimulatorPart.class)) {
            logger.info("Was loaded simulator part class with name: " + tmp.getClass());
            tmp.init(simulator);
            builder.addService(tmp);
        }
        server = builder.build();
        try {
            logger.debug("Simulator server is starting.");
            server.start();
            logger.info("Simulator server was started.");
            return true;
        } catch (IOException e) {
            logger.error("Can not start server", e);
            return false;
        }
    }

    @Override
    public void close() {
        if (server != null && !server.isShutdown()) {
            try {
                server.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.debug("Can not wait to terminate server", e);
            }
        }

        if (simulator != null) {
            try {
                simulator.close();
            } catch (IOException e) {
                logger.error("Can not close simulator" ,e);
            }
        }
    }

    @Override
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
}
