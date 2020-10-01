/*******************************************************************************
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.exactpro.th2.sim;

import java.io.Closeable;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.sim.configuration.SimulatorConfiguration;
import com.exactpro.th2.sim.grpc.RuleID;
import com.exactpro.th2.sim.impl.Simulator;
import com.exactpro.th2.sim.rule.IRule;

import io.grpc.BindableService;

/**
 * Simulator interface
 * @see Simulator
 */
public interface ISimulator extends BindableService, Closeable {

    void init(@NotNull SimulatorConfiguration configuration, @NotNull Class<? extends IAdapter> adapterClass) throws Exception;

    /**
     * Add rule to simulator which listen connectivity with connectionID
     * Parse input to single message
     * Parse output to single message
     * @param rule
     * @param connectionID
     * @return Rule's id
     * @see ISimulator#addRule(IRule, ConnectionID, boolean)
     * @see ISimulator#addRule(IRule, ConnectionID, boolean, boolean)
     */
    RuleID addRule(@NotNull IRule rule, @NotNull ConnectionID connectionID);

    /**
     * Add rule to simulator which listen connectivity with connectionID
     * Parse output to single message
     * @param rule
     * @param connectionID
     * @param parseBatch If true, parse input to message batch else to single message
     * @return Rule's id
     * @see ISimulator#addRule(IRule, ConnectionID, boolean, boolean)
     */
    RuleID addRule(@NotNull IRule rule, @NotNull ConnectionID connectionID, boolean parseBatch);

    /**
     * Add rule to simulator which listen connectivity with connectionID
     * @param rule
     * @param connectionID
     * @param parseBatch If true, parse input to message batch else to single message
     * @param sendBatch If true, parse output to message batch else to single message
     * @return Rule's id
     */
    RuleID addRule(@NotNull IRule rule, @NotNull ConnectionID connectionID, boolean parseBatch, boolean sendBatch);

    /**
     * Add default rule to simulator
     * @param ruleID
     */
    void addDefaultRule(RuleID ruleID);

    /**
     * Get incoming message
     * @param connectionID from connectivity with this id
     * @param message incoming message
     * @return Messages which will send
     */
    List<Message> handle(@NotNull ConnectionID connectionID, @NotNull Message message);

}
