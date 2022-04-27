/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim.configuration;

import java.util.Collections;
import java.util.List;

public class SimulatorConfiguration {

    private DefaultRulesTurnOffStrategy strategyDefaultRules = DefaultRulesTurnOffStrategy.ON_TRIGGER;

    private List<DefaultRuleConfiguration> defaultRules = Collections.emptyList();

    private int executionPoolSize = 12;

    public List<DefaultRuleConfiguration> getDefaultRules() {
        return defaultRules;
    }

    public void setDefaultRules(List<DefaultRuleConfiguration> defaultRules) {
        this.defaultRules = defaultRules;
    }

    public DefaultRulesTurnOffStrategy getStrategyDefaultRules() {
        return strategyDefaultRules;
    }

    public void setStrategyDefaultRules(DefaultRulesTurnOffStrategy strategyDefaultRules) {
        this.strategyDefaultRules = strategyDefaultRules;
    }

    public int getExecutionPoolSize() {
        return executionPoolSize;
    }

    public void setExecutionPoolSize(int executionPoolSize) {
        this.executionPoolSize = executionPoolSize;
    }
}
