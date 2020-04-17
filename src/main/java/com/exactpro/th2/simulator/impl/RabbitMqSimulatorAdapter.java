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
import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.QueueInfo;
import com.exactpro.evolution.configuration.RabbitMQConfiguration;
import com.exactpro.sf.common.util.EPSCommonException;
import com.exactpro.th2.simulator.IServiceSimulator;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.Delivery;

public class RabbitMqSimulatorAdapter implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final IServiceSimulator simulator;
    private final RabbitMqSubscriber subscriber;
    private final RabbitMqMessageSender sender;

    public RabbitMqSimulatorAdapter(IServiceSimulator simulator, RabbitMQConfiguration rabbitConf, QueueInfo queueInfo, String connectivityId) {
        this.simulator = simulator;

        subscriber = new RabbitMqSubscriber(queueInfo.getExchangeName(),
                this::processIncomingMessage,
                null,
                queueInfo.getInMsgQueue());

        sender = new RabbitMqMessageSender(rabbitConf, connectivityId, queueInfo.getExchangeName(), queueInfo.getSendMsgQueue());

        try {
            subscriber.startListening(rabbitConf.getHost(),
                    rabbitConf.getVirtualHost(),
                    rabbitConf.getPort(),
                    rabbitConf.getUsername(),
                    rabbitConf.getPassword());
        } catch (IOException | TimeoutException e) {
            throw new EPSCommonException("Can not start listening rabbit mq", e);
        }
    }

    private void processIncomingMessage(String consumingTag, Delivery delivery) {
        try {
            Message message = Message.parseFrom(delivery.getBody());
            for (Message messageToSend : simulator.handle(message)) {
                try {
                    sender.send(messageToSend);
                } catch (IOException e) {
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
