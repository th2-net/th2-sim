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

package com.exactpro.th2.simulator.configuration;

import static java.lang.System.getenv;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;

public class SimulatorConfiguration extends MicroserviceConfiguration {

    public static final String ENV_SIMULATOR_DEFAULT_RULES = "SIMULATOR_DEFAULT_RULES";
    public static final String DEFAULT_SIMULATOR_DEFAULT_RULES = "[]";

    public static List<DefaultRuleConfiguration> getEnvSimulatorDefaultRules() {
        String defaultRulesJson = defaultIfNull(getenv(ENV_SIMULATOR_DEFAULT_RULES), DEFAULT_SIMULATOR_DEFAULT_RULES);
        try {
            return ImmutableList.copyOf(JSON_READER.<List<DefaultRuleConfiguration>>readValue(defaultRulesJson, new TypeReference<>() {}));
        } catch (IOException e) {
            throw new IllegalArgumentException(ENV_SIMULATOR_DEFAULT_RULES + " environment variable value '" + defaultRulesJson + "' can't be read", e);
        }
    }


    private List<DefaultRuleConfiguration> defaultRules = getEnvSimulatorDefaultRules();

    public static SimulatorConfiguration load(InputStream inputStream) throws IOException {
        return YAML_READER.readValue(inputStream, SimulatorConfiguration.class);
    }

    public List<DefaultRuleConfiguration> getDefaultRules() {
        return defaultRules;
    }

    public void setDefaultRules(List<DefaultRuleConfiguration> defaultRules) {
        this.defaultRules = defaultRules;
    }
}
