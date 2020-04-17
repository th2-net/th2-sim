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
package com.exactpro.th2.simulator.configuration;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.ObjectUtils;

import com.exactpro.evolution.configuration.MicroserviceConfiguration;

public class SimulatorConfiguration extends MicroserviceConfiguration {

    public String getConnectivityID() {
        return (String)ObjectUtils.defaultIfNull(System.getenv("ID"), "ID");
    }

    public static SimulatorConfiguration load(InputStream inputStream) throws IOException {
        return (SimulatorConfiguration)YAML_READER.readValue(inputStream, SimulatorConfiguration.class);
    }
}
