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

import java.util.concurrent.TimeUnit;

import com.exactpro.th2.simulator.configuration.SimulatorConfiguration;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;

public class ServiceSimulatorServer {

    private final Server server;
    //private final RabbitMqSimulatorAdapter adapter;


    public ServiceSimulatorServer(SimulatorConfiguration configuration) {
        ServiceSimulator simulator = new ServiceSimulator();
        server = NettyServerBuilder.forPort(configuration.getPort()).addService(simulator).build();
        //adapter = new RabbitMqSimulatorAdapter(simulator, configuration);
    }

    public void start(){
        try {
            server.start();
            //adapter.start();
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    // Use stderr here since the logger may have been reset by its JVM shutdown hook.
                    System.err.println("*** shutting down gRPC server since JVM is shutting down");
                    ServiceSimulatorServer.this.stop();
                    System.err.println("*** server shut down");
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Can not start simulator server", e);
        }
    }

    public void stop() {
        try {
            if (server != null) {
                server.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            e.printStackTrace(System.err);
        }

//        if (adapter != null) {
//            try {
//                adapter.close();
//            } catch (Exception e) {
//                e.printStackTrace(System.err);
//            }
//        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

}
