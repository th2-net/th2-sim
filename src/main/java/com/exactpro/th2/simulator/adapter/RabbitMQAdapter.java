/*******************************************************************************
 *  Copyright 2020 Exactpro (Exactpro Systems Limited)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/
package com.exactpro.th2.simulator.adapter;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.RabbitMqMessageSender;
import com.exactpro.th2.RabbitMqSubscriber;
import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.configuration.RabbitMQConfiguration;
import com.exactpro.th2.configuration.Th2Configuration.QueueNames;
import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.Delivery;

/**
 * Implementation {@link IAdapter} for connect to rabbit mq.
 */
public class RabbitMQAdapter implements IAdapter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private ISimulator simulator;
    private RabbitMqSubscriber subscriber;
    private RabbitMqMessageSender sender;
    private ConnectionID connectionID;

    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull ConnectionID connectionID, @NotNull ISimulator simulator) {
        this.simulator = simulator;
        this.connectionID = connectionID;

        QueueNames queueInfo = getQueueNames(configuration, connectionID);

        subscriber = new RabbitMqSubscriber(queueInfo.getExchangeName(),
                this::processIncomingMessage,
                null,
                queueInfo.getInQueueName());

        sender = new RabbitMqMessageSender(configuration.getRabbitMQ(), connectionID.getSessionAlias(), queueInfo.getExchangeName(), queueInfo.getToSendQueueName());

        RabbitMQConfiguration rabbitConf = configuration.getRabbitMQ();
        try {
            subscriber.startListening(rabbitConf.getHost(),
                    rabbitConf.getVirtualHost(),
                    rabbitConf.getPort(),
                    rabbitConf.getUsername(),
                    rabbitConf.getPassword());
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("Can not start listening rabbit mq with connectivity id: " + connectionID, e);
        }
    }

    private void processIncomingMessage(String tag, Delivery delivery) {
        try {
            if (sender == null) {
                logger.error("Can not process message, because sender did not init");
                return;
            }

            Message message = Message.parseFrom(delivery.getBody());
            if (message == null) {
                return;
            }

            logger.debug("Handle message name = " + message.getMetadata().getMessageType());

            logger.trace("Handle message body = " + message.toString());

            for (Message messageToSend : simulator.handle(connectionID, message)) {
                try {
                    sender.send(messageToSend);
                } catch (Exception e) {
                    logger.error("Can not send message: " + messageToSend.toString(), e);
                }
            }
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        if (subscriber != null) {
            try {
                subscriber.close();
            } catch (Exception e) {
                logger.error("Can not close rabbit mq subscriber", e);
            }
        }
        if (sender != null) {
            try {
                sender.close();
            } catch (Exception e) {
                logger.error("Can not close rabbit mq sender", e);
            }
        }
    }

    private QueueNames getQueueNames(MicroserviceConfiguration configuration, ConnectionID connectionID) {
        return configuration.getTh2().getConnectivityQueueNames().get(connectionID.getSessionAlias());
    }
}
