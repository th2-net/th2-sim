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

import org.jetbrains.annotations.NotNull;

import com.exactpro.th2.common.schema.factory.AbstractCommonFactory;
import com.exactpro.th2.sim.grpc.RuleID;
import com.exactpro.th2.sim.impl.Simulator;
import com.exactpro.th2.sim.rule.IRule;

import io.grpc.BindableService;

/**
 * Simulator interface
 * @see Simulator
 */
public interface ISimulator extends BindableService, Closeable {

    void init(@NotNull AbstractCommonFactory factory) throws Exception;

    /**
     * Add rule to simulator which listen connectivity with connectionID
     * Parse input to single message
     * Parse output to single message
     * @param rule
     * @param sessionAlias
     * @return Rule's id
     */
    RuleID addRule(@NotNull IRule rule, @NotNull String sessionAlias);

    /**
     * Add default rule to simulator
     * @param ruleID
     */
    void addDefaultRule(RuleID ruleID);

}
