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
package com.exactpro.th2.simulator;

import java.io.Closeable;

import org.jetbrains.annotations.NotNull;

import com.exactpro.evolution.configuration.MicroserviceConfiguration;
import com.exactpro.th2.simulator.impl.SimulatorServer;

/**
 * Interface of {@link Simulator} server.
 * @see SimulatorServer
 */
public interface ISimulatorServer extends Closeable {

    void init(@NotNull MicroserviceConfiguration configuration, @NotNull Class<? extends ISimulator> simulatorServer, @NotNull Class<? extends IAdapter> adapterServer);

    /**
     * Start server
     * @return server was start
     */
    boolean start();

    /**
     * Wait while server is running
     * @throws InterruptedException
     */
    void blockUntilShutdown() throws InterruptedException;
}
