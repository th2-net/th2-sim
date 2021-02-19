/*******************************************************************************
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.sim.rule.impl;

import com.exactpro.th2.sim.rule.IRule;
import com.exactpro.th2.sim.rule.IRuleContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Abstract implement of {@link IRule}
 */
public abstract class AbstractRule implements IRule {

    @Override
    public void touch(@NotNull IRuleContext ruleContext, @NotNull Map<String, String> args) {}
}
