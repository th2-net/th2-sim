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
package com.exactpro.th2.sim.adapter;

import static java.lang.String.format;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.RabbitMqMessageBatchSender;
import com.exactpro.th2.RabbitMqMessageSender;
import com.exactpro.th2.RabbitMqSubscriber;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.configuration.RabbitMQConfiguration;
import com.exactpro.th2.configuration.Th2Configuration.QueueNames;
import com.exactpro.th2.sim.IAdapter;
import com.rabbitmq.client.DeliverCallback;

/**
 * Implementation {@link IAdapter} for connect to rabbit mq.
 */
public class RabbitMQAdapter implements IAdapter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final AtomicReference<RabbitMQConfiguration> rabbitMQConfiguration = new AtomicReference<>();

    private final AtomicReference<RabbitMqSubscriber> subscriber = new AtomicReference<>();
    private final AtomicReference<RabbitMqMessageBatchSender> batchSender = new AtomicReference<>();
    private final AtomicReference<RabbitMqMessageSender> messageSender = new AtomicReference<>();

    private final AtomicReference<QueueNames> queueNames = new AtomicReference<>();

    private final AtomicReference<String> sessionAlias = new AtomicReference<>();

    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull String sessionAlias) {
        if (queueNames.getAndUpdate(names -> {
            if (names == null) {
                names = getQueueNames(configuration, sessionAlias);
            }
            return names;
        }) != null) {
            throw new IllegalStateException("RabbitMQAdapter already init");
        }

        this.sessionAlias.updateAndGet(alias -> alias == null ? sessionAlias : alias);

        rabbitMQConfiguration.updateAndGet(config -> config == null ? configuration.getRabbitMQ() : config);
    }

    @Override
    public void startListen(DeliverCallback deliverCallback) {
        subscriber.getAndUpdate(sub -> sub == null ? createSubscriber(deliverCallback) : sub);
    }

    @Override
    public void send(Message message) throws IOException {
        messageSender.updateAndGet(sender -> sender == null ? createMessageSender() : sender).send(message);
    }

    @Override
    public void send(MessageBatch batch) throws IOException {
        batchSender.updateAndGet(sender -> sender == null ? createBatchSender() : sender).send(batch);
    }

    private RabbitMqSubscriber createSubscriber(DeliverCallback deliverCallback) {
        QueueNames queueNames = this.queueNames.get();
        RabbitMQConfiguration config = rabbitMQConfiguration.get();
        String sessionAlias = this.sessionAlias.get();

        if (queueNames == null || config == null || sessionAlias == null) {
            throw new IllegalStateException("RabbitMQAdapter is not init");
        }

        RabbitMqSubscriber subscriber = new RabbitMqSubscriber(queueNames.getExchangeName(), deliverCallback, null, queueNames.getInQueueName());
        try {
            subscriber.startListening(config.getHost(), config.getVirtualHost(), config.getPort(), config.getUsername(), config.getPassword());
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("Can not start listening rabbit mq with session alias: " + sessionAlias, e);
        }
        return subscriber;
    }

    private RabbitMqMessageSender createMessageSender() {
        QueueNames queueNames = this.queueNames.get();
        RabbitMQConfiguration config = rabbitMQConfiguration.get();

        if (queueNames == null || config == null) {
            throw new IllegalStateException("RabbitMQAdapter is not init");
        }

        return new RabbitMqMessageSender(config, queueNames.getExchangeName(), queueNames.getToSendQueueName());
    }

    private RabbitMqMessageBatchSender createBatchSender() {
        QueueNames queueNames = this.queueNames.get();
        RabbitMQConfiguration config = rabbitMQConfiguration.get();

        if (queueNames == null || config == null) {
            throw new IllegalStateException("RabbitMQAdapter is not init");
        }

        return new RabbitMqMessageBatchSender(config, queueNames.getExchangeName(), queueNames.getToSendQueueName());
    }

    @Override
    public void close() {
        subscriber.updateAndGet(subscriber -> {
            if (subscriber != null) {
                try {
                    subscriber.close();
                } catch (Exception e) {
                    logger.error("Can not close rabbit mq subscriber", e);
                }
            }

            return null;
        });

        messageSender.updateAndGet(sender -> {
            if (sender != null) {
                try {
                    sender.close();
                } catch (Exception e) {
                    logger.error("Can not close rabbit mq message sender", e);
                }
            }
            return null;
        });

        batchSender.updateAndGet(sender -> {
            if (sender != null) {
                try {
                    sender.close();
                } catch (Exception e) {
                    logger.error("Can not close rabbit mq batch sender", e);
                }
            }
            return null;
        });


    }

    @NotNull
    private QueueNames getQueueNames(MicroserviceConfiguration configuration, String sessionAlias) {
        QueueNames queueNames = configuration.getTh2().getConnectivityQueueNames().get(sessionAlias);
        if (queueNames == null) {
            throw new IllegalArgumentException(format("Unknown connectionID '%s'. Please check your configuration", sessionAlias));
        }
        return queueNames;
    }
}
