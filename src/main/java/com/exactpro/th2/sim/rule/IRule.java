/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.sim.rule;

import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage;
import com.exactpro.th2.sim.ISimulator;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Interface for {@link ISimulator} rules
 */
public interface IRule {
    /**
     * @param message input message
     * @return True, if rule will be triggered on this message
     */
    boolean checkTriggered(@NotNull ParsedMessage message);

    /**
     * @param ruleContext context
     * @param message     input message
     */
    void handle(@NotNull IRuleContext ruleContext, @NotNull ParsedMessage message);

    /**
     * Called on grpc request touchRule
     * @param ruleContext context
     * @param args custom arguments
     */
    void touch(@NotNull IRuleContext ruleContext, @NotNull Map<String, String> args);

}
