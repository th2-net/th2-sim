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
package com.exactpro.th2.simulator.rule.impl;

import java.util.Collections;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.simulator.rule.IRule;

/**
 * Abstract implement of {@link IRule}
 */
public abstract class AbstractRule implements IRule {

    /**
     * Call {@link AbstractRule#handleTriggered(Message)} if rule will triggered on this message
     * @param message
     * @return Message which will send
     */
    @Override
    public @NotNull List<Message> handle(@NotNull Message message) {
        if (checkTriggered(message)) {
            return handleTriggered(message);
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Call this method with messages which trigger this rule
     * @param message
     * @return Message which will send
     */
    public abstract @NotNull List<Message> handleTriggered(@NotNull Message message);
}
