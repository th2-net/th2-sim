package com.exactpro.th2.simulator.impl;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.evolution.RabbitMqMessageSender;
import com.exactpro.evolution.RabbitMqSubscriber;
import com.exactpro.evolution.api.phase_1.ConnectivityGrpc;
import com.exactpro.evolution.api.phase_1.ConnectivityGrpc.ConnectivityBlockingStub;
import com.exactpro.evolution.api.phase_1.ConnectivityId;
import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.QueueInfo;
import com.exactpro.evolution.api.phase_1.QueueRequest;
import com.exactpro.evolution.configuration.MicroserviceConfiguration;
import com.exactpro.evolution.configuration.RabbitMQConfiguration;
import com.exactpro.evolution.configuration.Th2Configuration.Address;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.Delivery;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class RabbitMQAdapter implements IAdapter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private ISimulator simulator;
    private RabbitMqSubscriber subscriber;
    private RabbitMqMessageSender sender;
    private ConnectivityId connectivityId;

    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull ConnectivityId connectivityId, @NotNull ISimulator simulator) {
        this.simulator = simulator;
        this.connectivityId = connectivityId;

        QueueInfo queueInfo = getQueueInfo(configuration, connectivityId);

        subscriber = new RabbitMqSubscriber(queueInfo.getExchangeName(),
                this::processIncomingMessage,
                null,
                queueInfo.getInMsgQueue());

        sender = new RabbitMqMessageSender(configuration.getRabbitMQ(), connectivityId.getConnectivityId(), queueInfo.getExchangeName(), queueInfo.getSendMsgQueue());

        RabbitMQConfiguration rabbitConf = configuration.getRabbitMQ();
        try {
            subscriber.startListening(rabbitConf.getHost(),
                    rabbitConf.getVirtualHost(),
                    rabbitConf.getPort(),
                    rabbitConf.getUsername(),
                    rabbitConf.getPassword());
        } catch (IOException | TimeoutException e) {
            throw new IllegalStateException("Can not start listening rabbit mq with connectivity id: " + connectivityId, e);
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

            //FIXME: move to debug section
            logger.debug("Handle message name = " + message.getMetadata().getMessageType());

            logger.trace("Handle message body = " + message.toString());

            for (Message messageToSend : simulator.handle(connectivityId, message)) {
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
    public void close() throws IOException {
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

    private QueueInfo getQueueInfo(MicroserviceConfiguration configuration, ConnectivityId connectivityId) {
        Address connectivityAddress = configuration.getTh2().getConnectivityAddresses().get(connectivityId.getConnectivityId());

        if (connectivityAddress == null) {
            throw new IllegalStateException("Can not get connectivity address with id:" + connectivityId.getConnectivityId());
        }

        return downloadQueueInfo(connectivityAddress);
    }

    private QueueInfo downloadQueueInfo(Address connectivityAddress) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(connectivityAddress.getHost(), connectivityAddress.getPort()).usePlaintext().build();
        try {
            ConnectivityBlockingStub blockingStub = ConnectivityGrpc.newBlockingStub(channel);
            return blockingStub.getQueueInfo(QueueRequest.newBuilder().build());
        } catch (Exception e) {
            throw new IllegalStateException("Can not download queue info", e);
        }
        finally {
            if (!channel.isShutdown()) {
                channel.shutdownNow();
            }
        }
    }
}
