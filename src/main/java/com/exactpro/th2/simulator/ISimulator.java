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
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.simulator.grpc.RuleID;
import com.exactpro.th2.simulator.impl.Simulator;
import com.exactpro.th2.simulator.rule.IRule;

import io.grpc.BindableService;

/**
 * Simulator interface
 * @see Simulator
 */
public interface ISimulator extends BindableService, Closeable {

    void init(@NotNull MicroserviceConfiguration configuration, @NotNull Class<? extends IAdapter> adapterClass) throws Exception;

    /**
     * Add rule to simulator which listen connectivity with connectionID
     * @param rule
     * @param connectionID
     * @return Rule's id
     */
    RuleID addRule(@NotNull IRule rule, @NotNull ConnectionID connectionID);

    /**
     * Get incoming message
     * @param connectionID from connectivity with this id
     * @param message incoming message
     * @return Messages which will send
     */
    List<Message> handle(@NotNull ConnectionID connectionID, @NotNull Message message);

}
