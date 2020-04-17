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
package com.exactpro.th2.simulator.impl;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.evolution.RabbitMqMessageSender;
import com.exactpro.evolution.RabbitMqSubscriber;
import com.exactpro.evolution.api.phase_1.ConnectivityGrpc;
import com.exactpro.evolution.api.phase_1.ConnectivityGrpc.ConnectivityBlockingStub;
import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.QueueInfo;
import com.exactpro.evolution.api.phase_1.QueueRequest;
import com.exactpro.evolution.configuration.RabbitMQConfiguration;
import com.exactpro.evolution.configuration.Th2Configuration.Address;
import com.exactpro.th2.simulator.IServiceSimulator;
import com.exactpro.th2.simulator.configuration.SimulatorConfiguration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.Delivery;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class RabbitMqSimulatorAdapter implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final IServiceSimulator simulator;
    private final RabbitMqSubscriber subscriber;
    private final RabbitMqMessageSender sender;
    private final RabbitMQConfiguration rabbitConf;

    public RabbitMqSimulatorAdapter(IServiceSimulator simulator, SimulatorConfiguration configuration) {
        this.simulator = simulator;

        Address connectivityAddress = configuration.getTh2().getConnectivityAddresses().get(configuration.getConnectivityID());

        if (connectivityAddress == null) {
            throw new IllegalStateException("Please add connectivity address with name: " + configuration.getConnectivityID());
        }

        ManagedChannel channel = ManagedChannelBuilder.forAddress(connectivityAddress.getHost(), connectivityAddress.getPort()).usePlaintext().build();
        try {
            ConnectivityBlockingStub blockingStub = ConnectivityGrpc.newBlockingStub(channel);
            QueueInfo queueInfo = blockingStub.getQueueInfo(QueueRequest.newBuilder().build());

            subscriber = new RabbitMqSubscriber(queueInfo.getExchangeName(),
                    this::processIncomingMessage,
                    null,
                    queueInfo.getInMsgQueue());

            rabbitConf = configuration.getRabbitMQ();
            sender = new RabbitMqMessageSender(rabbitConf, configuration.getConnectivityID(), queueInfo.getExchangeName(), queueInfo.getSendMsgQueue());
        } finally {
            channel.shutdown();
        }
    }

    public void start() {
        try {
            subscriber.startListening(rabbitConf.getHost(),
                    rabbitConf.getVirtualHost(),
                    rabbitConf.getPort(),
                    rabbitConf.getUsername(),
                    rabbitConf.getPassword());
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("Can not start listening rabbit mq", e);
        }
    }

    private void processIncomingMessage(String consumingTag, Delivery delivery) {
        try {
            Message message = Message.parseFrom(delivery.getBody());
            if (message == null) {
                return;
            }

            for (Message messageToSend : simulator.handle(message)) {
                try {
                    sender.send(messageToSend);
                } catch (Exception e) {
                    logger.error("Can not send message", e);
                }
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws Exception {
        subscriber.close();
        sender.close();
    }

}
