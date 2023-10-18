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

package com.exactpro.th2.sim.rule.impl;

import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

public abstract class MessagePredicateRule extends AbstractRule {
    protected Predicate<String> messageTypePredicate;
    protected Map<String, Predicate<Object>> fieldsPredicate;

    public void init(@Nullable Predicate<String> messageTypePredicate, @Nullable Map<String, Predicate<Object>> fieldsPredicate) {
        this.messageTypePredicate = ObjectUtils.defaultIfNull(messageTypePredicate, ignore -> true);
        this.fieldsPredicate = fieldsPredicate == null ? new HashMap<>() : fieldsPredicate;
    }

    @Override
    public boolean checkTriggered(@NotNull ParsedMessage message) {
        return messageTypePredicate.test(message.getType())
                && fieldsPredicate.entrySet().stream().allMatch((entry) -> entry.getValue().test(message.getBody().get(entry.getKey())));
    }
}
