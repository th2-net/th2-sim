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

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.exactpro.th2.simulator.rule.IRule;

public abstract class AbstractRule implements IRule {

    private int id;
    private Map<String, String> arguments;

    public AbstractRule(int id, @Nullable Map<String, String> arguments) {
        this.id = id;
        this.arguments = arguments == null ? new HashMap<>() : arguments;
        postInit(this.arguments);
    }

    protected void postInit(@NotNull Map<String, String> arguments) {}

    @Override
    public int getId() {
        return id;
    }

    @Override
    @NotNull
    public Map<String, String> getArguments() {
        return arguments;
    }
}
