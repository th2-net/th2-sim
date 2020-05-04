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

import static com.exactpro.th2.simulator.util.ValueUtils.nullValue;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.Value;
import com.exactpro.th2.simulator.rule.IRule;

/**
 * Abstract implementation {@link AbstractRule}
 *
 * Filter incoming message by type and fields.
 *
 * @see IRule
 */
public abstract class MessageCompareRule extends AbstractRule {
    private final String messageType;
    private final Map<String, Value> fieldsValue;

    /**
     * Create MessageCompareRule with arguments
     * @param messageType
     * @param fieldsValue
     */
    public MessageCompareRule(@NotNull String messageType, @Nullable Map<String, Value> fieldsValue) {
        this.messageType = Objects.requireNonNull(messageType, "Message name can not be null");
        this.fieldsValue = fieldsValue == null || fieldsValue.size() < 1 ? Collections.emptyMap() : fieldsValue;
    }

    @Override
    public boolean checkTriggered(@NotNull Message message) {
        if (!message.getMetadata().getMessageType().equals(messageType)) {
            return false;
        }
        return fieldsValue.entrySet().stream().allMatch(entry -> {
            Value fieldValue = message.getFieldsOrDefault(entry.getKey(), nullValue());
            return entry.getValue().equals(fieldValue);
        });
    }
}
