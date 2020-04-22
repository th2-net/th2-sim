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
import org.apache.commons.lang3.math.NumberUtils;

import com.exactpro.evolution.configuration.MicroserviceConfiguration;

public class SimulatorConfiguration extends MicroserviceConfiguration {

    private String connectivityID = getEnvConnectivityID();
    private String exchangeName = getEnvExchangeName();
    private String inMsgQueue = getEnvInMsgQueue();
    private String sendMsgQueue = getEnvSendMsgQueue();
    private String grpcHost = getEnvSimulatorGRPCHost();
    private int grpcPort = getEnvSimulatorGRPCPort();

    private int getEnvSimulatorGRPCPort() {
        return NumberUtils.toInt(ObjectUtils.defaultIfNull(System.getenv("TH2_SIMULATOR_GRPC_PORT"), "8080"));
    }

    private String getEnvSimulatorGRPCHost() {
        return (String)ObjectUtils.defaultIfNull(System.getenv("TH2_SIMULATOR_GRPC_HOST"), "localhost");
    }

    private String getEnvSendMsgQueue() {
        return (String)ObjectUtils.defaultIfNull(System.getenv("TH2_SIMULATOR_SEND_MSG_QUEUE"), null);
    }

    private String getEnvInMsgQueue() {
        return (String)ObjectUtils.defaultIfNull(System.getenv("TH2_SIMULATOR_IN_MSG_QUEUE"), null);
    }

    private String getEnvExchangeName() {
        return (String)ObjectUtils.defaultIfNull(System.getenv("TH2_SIMULATOR_EXCHANGE_NAME"), null);
    }

    private String getEnvConnectivityID() {
        return (String)ObjectUtils.defaultIfNull(System.getenv("TH2_SIMULATOR_CONNECTIVITY_ID"), "TH2_SIMULATOR_CONNECTIVITY_ID");
    }

    public String getConnectivityID() {
        return this.connectivityID;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getInMsgQueue() {
        return inMsgQueue;
    }

    public String getSendMsgQueue() {
        return sendMsgQueue;
    }

    public String getGrpcSimulatorHost() {
        return grpcHost;
    }

    public int getGrpcSimulatorPort() {
        return grpcPort;
    }

    public static SimulatorConfiguration load(InputStream inputStream) throws IOException {
        return (SimulatorConfiguration)YAML_READER.readValue(inputStream, SimulatorConfiguration.class);
    }
}
