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
package com.exactpro.th2.sim;

import com.exactpro.th2.common.schema.factory.AbstractCommonFactory;
import com.exactpro.th2.sim.impl.Simulator;
import com.exactpro.th2.sim.impl.SimulatorServer;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;

/**
 * Interface of {@link Simulator} server.
 * @see SimulatorServer
 */
public interface ISimulatorServer extends Closeable {

    void init(@NotNull AbstractCommonFactory factory, @NotNull Class<? extends ISimulator> simulatorServer);

    /**
     * Start server
     */
    void start();

    /**
     * Wait while server is running
     * @throws InterruptedException
     */
    void blockUntilShutdown() throws InterruptedException;
}
