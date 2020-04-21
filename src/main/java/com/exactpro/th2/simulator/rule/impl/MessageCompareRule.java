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
package com.exactpro.th2.simulator.rule.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.NullValue;
import com.exactpro.evolution.api.phase_1.Value;

public abstract class MessageCompareRule extends AbstractRule {
    public static final String MESSAGE_NAME = "#MessageName";

    private final String messageName;
    private final Map<String, Value> fieldsValue;

    public MessageCompareRule(int id, @NotNull String messageName, @Nullable Map<String, Value> fieldsValue) {
        super(id);
        this.messageName = Objects.requireNonNull(messageName, "Message name can not be null");
        this.fieldsValue = fieldsValue == null || fieldsValue.size() < 1 ? Collections.emptyMap() : fieldsValue;
    }

    @Override
    public boolean checkTriggered(@NotNull Message message) {
        if (!message.getMetadata().getMessageType().equals(messageName)) {
            return false;
        }
        return fieldsValue.entrySet().stream().allMatch(entry -> {
            Value fieldValue = message.getFieldsOrDefault(entry.getKey(), nullValue());
            return entry.getValue().equals(fieldValue);
        });
    }

    @Override
    public @NotNull List<Message> handle(@NotNull Message message) {
        if (checkTriggered(message)) {
            return handleTriggered(message);
        } else {
            return Collections.emptyList();
        }
    }

    public abstract @NotNull List<Message> handleTriggered(@NotNull Message message);

    private Value nullValue() {
        return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    }
}
