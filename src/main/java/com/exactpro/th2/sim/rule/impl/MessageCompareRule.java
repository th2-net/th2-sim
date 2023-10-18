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

import com.exactpro.th2.sim.rule.IRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Abstract implementation {@link AbstractRule}
 *
 * Filter incoming message by type and fields.
 *
 * @see IRule
 */
public abstract class MessageCompareRule extends MessagePredicateRule {

    /**
     * Create MessageCompareRule with arguments
     */
    public void init(@NotNull String messageType, @Nullable Map<String, Object> fieldsValue) {
        Map<String, Predicate<Object>> tmp = new HashMap<>();
        if (fieldsValue != null) {
            fieldsValue.forEach((str, value) -> {
                if (value != null) {
                    tmp.put(str, value::equals);
                }
            });
        }
        super.init(messageType::equals, tmp);
    }
}
