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
package com.exactpro.th2.simulator.adapter;

import static java.lang.String.format;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.RabbitMqMessageBatchSender;
import com.exactpro.th2.RabbitMqMessageSender;
import com.exactpro.th2.RabbitMqSubscriber;
import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.configuration.RabbitMQConfiguration;
import com.exactpro.th2.configuration.Th2Configuration.QueueNames;
import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.infra.grpc.MessageBatch;
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
    private RabbitMqMessageBatchSender batchSender;
    private RabbitMqMessageSender messageSender;
    private ConnectionID connectionId;
    private boolean parseBatch;
    private boolean sendBatch;

    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull ConnectionID connectionID, boolean parseBatch, boolean sendBatch, @NotNull ISimulator simulator) {
        this.simulator = simulator;
        this.connectionId = connectionID;
        this.parseBatch = parseBatch;
        this.sendBatch = sendBatch;

        QueueNames queueInfo = getQueueNames(configuration, connectionID);

        if (queueInfo == null) {
            throw new IllegalStateException("Can not find queues for connectionID: '" + connectionID + "'");
        }

        subscriber = new RabbitMqSubscriber(queueInfo.getExchangeName(),
                this::processIncomingMessage,
                null,
                queueInfo.getInQueueName());

        if (sendBatch) {
            batchSender = new RabbitMqMessageBatchSender(configuration.getRabbitMQ(), queueInfo.getExchangeName(), queueInfo.getToSendQueueName());
        } else {
            messageSender = new RabbitMqMessageSender(configuration.getRabbitMQ(), queueInfo.getExchangeName(), queueInfo.getToSendQueueName());
        }

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
            if (batchSender == null && messageSender == null) {
                logger.error("Can not process message, because sender did not init");
                return;
            }

            if (parseBatch) {
                logger.trace("Parsing input to message batch in connection = {}", connectionId.getSessionAlias());
                MessageBatch batch = MessageBatch.parseFrom(delivery.getBody());
                logger.debug("Parsed input to message batch in connection = {}", connectionId.getSessionAlias());
                for (Message message : batch.getMessagesList()) {
                    processSingleMessage(message);
                }
            } else {
                logger.trace("Parsing input to single message in connection = {}", connectionId.getSessionAlias());
                Message message = Message.parseFrom(delivery.getBody());
                logger.debug("Parsed input to single message in connection = {}", connectionId.getSessionAlias());
                processSingleMessage(message);
            }

        } catch (InvalidProtocolBufferException e) {
            logger.error("could not parse proto message", e);
        }
    }

    private void processSingleMessage(Message message) {
        if (message == null) {
            return;
        }

        logger.debug("Handle message name = {}", message.getMetadata().getMessageType());

        logger.trace("Handle message body = {}", message.toString());

        List<Message> messages = simulator.handle(connectionId, message);

        if (messages.size() > 0) {

            if (sendBatch) {
                MessageBatch batch = MessageBatch.newBuilder().addAllMessages(messages).build();

                try {
                    batchSender.send(batch);
                } catch (IOException e) {
                    logger.error("Can not send message batch to connection: {}.\n{}", connectionId.getSessionAlias(), batch, e);
                }
            } else {
                for (Message tmp : messages) {
                    try {
                        messageSender.send(tmp);
                    } catch (IOException e) {
                        logger.error("Can not send single message to connection: {}.\n{} ", connectionId.getSessionAlias(), tmp, e);
                    }
                }
            }
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
        if (batchSender != null) {
            try {
                batchSender.close();
            } catch (Exception e) {
                logger.error("Can not close rabbit mq batchSender", e);
            }
        }
    }

    private QueueNames getQueueNames(MicroserviceConfiguration configuration, ConnectionID connectionID) {
        QueueNames queueNames = configuration.getTh2().getConnectivityQueueNames().get(connectionID.getSessionAlias());
        if (queueNames == null) {
            throw new IllegalArgumentException(format("unknown connectionID '%s'", connectionID.getSessionAlias()));
        }
        return queueNames;
    }
}
