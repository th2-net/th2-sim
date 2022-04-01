/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.common.schema.factory.CommonFactory;
import com.exactpro.th2.sim.impl.Simulator;
import com.exactpro.th2.sim.impl.SimulatorServer;

public class SimulatorServerMain {

    private final static Logger LOGGER = LoggerFactory.getLogger(SimulatorServerMain.class);

    public static void main(String[] args) {
        try {
            CommonFactory commonFactory = CommonFactory.createFromArguments(args);
            SimulatorServer server = new SimulatorServer();
            server.init(commonFactory, Simulator.class);
            addShutdownHook(server, commonFactory);
            server.start();
            server.blockUntilShutdown();
        } catch (Throwable th) {
            LOGGER.error(th.getMessage(), th);
            System.exit(-1);
        }
    }

    private static void addShutdownHook(SimulatorServer server, CommonFactory factory) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (factory; server) {} catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
            }
        }));
    }
}
