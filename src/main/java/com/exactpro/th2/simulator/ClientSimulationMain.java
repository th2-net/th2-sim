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
package com.exactpro.th2.simulator;

import static java.time.LocalDateTime.now;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.evolution.ConfigurationUtils;
import com.exactpro.evolution.RabbitMqMessageSender;
import com.exactpro.evolution.RabbitMqSubscriber;
import com.exactpro.evolution.api.phase_1.ListValue;
import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.Metadata;
import com.exactpro.evolution.api.phase_1.Value;
import com.exactpro.evolution.configuration.RabbitMQConfiguration;
import com.exactpro.th2.simulator.ServiceSimulatorGrpc.ServiceSimulatorBlockingStub;
import com.exactpro.th2.simulator.configuration.SimulatorConfiguration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.rabbitmq.client.Delivery;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

//FIXME: Only for demo
public class ClientSimulationMain {

    private static final Logger logger = LoggerFactory.getLogger("Client");

    public static void main(String[] args) {
        SimulatorConfiguration configuration = readConfiguration(args);

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", configuration.getPort()).usePlaintext().build();
        ServiceSimulatorBlockingStub serviceSimulatorGrps = ServiceSimulatorGrpc.newBlockingStub(channel);
        RabbitMqSubscriber subscriber = createSubscriber(configuration);
        RabbitMqMessageSender sender = new RabbitMqMessageSender(configuration.getRabbitMQ(), "fix-client", "demo_exchange", "fix_client_to_send");

        //Send message without rule
        logger.info("Send message without rule");
        sendMessage(sender, createNewOrderSingle());
        waitResult(500);

        //Create rule
        logger.info("Create rule");
        RuleInfo info = serviceSimulatorGrps.createRule(CreateRuleEvent.newBuilder().setType("fix-rule").putArguments("ClOrdID", "order_id_2").build());
        logger.info("Rule status = " + info.getStatus());

        //Send message with disable rule
        logger.info("Send message with disable rule");
        sendMessage(sender, createNewOrderSingle("order_id_2"));
        waitResult(500);

        //Enable rule
        logger.info("Enable rule");
        info = serviceSimulatorGrps.enableRule(info.getId());
        logger.info("Rule status = " + info.getStatus());

        //Send message with wrong field's value with enable rule
        logger.info("Send message with wrong field's value with enable rule");
        sendMessage(sender, createNewOrderSingle());
        waitResult(500);

        //Send message with enable rule
        logger.info("Send message with enable rule");
        sendMessage(sender, createNewOrderSingle("order_id_2"));
        waitResult(1000);

        //Remove rule
        logger.info("Remove rule");
        info = serviceSimulatorGrps.removeRule(info.getId());
        logger.info("Rule status = " + info.getStatus());

        try {
            sender.close();
        } catch (IOException e) {
            logger.error("Can not close sender", e);
        }

        try {
            subscriber.close();
        } catch (IOException e) {
            logger.error("Can not close subscriber");
        }
    }

    private static RabbitMqSubscriber createSubscriber(SimulatorConfiguration configuration) {
        RabbitMqSubscriber subscriber = new RabbitMqSubscriber("demo_exchange", ClientSimulationMain::processMessage, null, "fix_client_in");
        RabbitMQConfiguration rabbitMQ = configuration.getRabbitMQ();
        try {
            subscriber.startListening(rabbitMQ.getHost(), rabbitMQ.getVirtualHost(), rabbitMQ.getPort(), rabbitMQ.getUsername(), rabbitMQ.getPassword());
        } catch (IOException | TimeoutException e) {
            logger.error("Can not listen fix-client");
        }
        return subscriber;
    }

    private static void sendMessage(RabbitMqMessageSender sender, Message newOrderSingle) {
        try {
            sender.send(newOrderSingle);
        } catch (IOException e) {
            logger.error("Can not send message", e);
        }
    }

    public static void processMessage(String consumingTag, Delivery delivery) {
        try {
            Message message = Message.parseFrom(delivery.getBody());

            if (message.getMetadata().getMessageType().equals("Heartbeat")) {
                return;
            }

            logger.info("Handle message name = " + message.getMetadata().getMessageType());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
    }

    private static void waitResult(int ms) {
        logger.info("Wait {} ms", ms);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            logger.info("Waited not {} ms", ms);
        }
        logger.info("Stop waiting");
    }

    private static SimulatorConfiguration readConfiguration(String[] args) {
        if (args.length > 0) {
            return ConfigurationUtils.safeLoad(SimulatorConfiguration::load, SimulatorConfiguration::new, args[0]);
        } else {
            return new SimulatorConfiguration();
        }
    }

    public static Message createNewOrderSingle() {
        return createNewOrderSingle("order_id_1");
    }

    private static Message createNewOrderSingle(String clOrdId) {
        return Message.newBuilder()
                .setMetadata(buildMetadata("NewOrderSingle"))
                .putFields("ClOrdID", buildValue(clOrdId))
                .putFields("SecurityID", buildValue("order_id_1_seq"))
                .putFields("SecurityIDSource", buildValue("G")) // COMMON by dictionary
                .putFields("OrdType", buildValue("1")) // MARKET by dictionary
                .putFields("Side", buildValue("1")) // BUY by dictionary
                .putFields("OrderQty", buildValue("10"))
                .putFields("DisplayQty", buildValue("10"))
                .putFields("AccountType", buildValue("1")) // CLIENT by dictionary
                .putFields("OrderCapacity", buildValue("A")) // AGENCY by dictionary
                .putFields("TradingParty", buildTradingParty())
                .putFields("TransactTime", buildValue(ISO_DATE_TIME.format(now())))
                .build();
    }

    private static Value buildTradingParty() {
        return Value.newBuilder().setMessageValue(
                Message.newBuilder()
                        .putFields("NoPartyIDs", Value.newBuilder()
                                .setListValue(buildParties())
                                .build())
                        .build())
                .build();
    }

    private static Metadata buildMetadata(String messageType) {
        return Metadata.newBuilder()
                .setMessageType(messageType).build();
    }

    private static ListValue buildParties() {
        return ListValue.newBuilder()
                .addValues(Value.newBuilder().setMessageValue(Message.newBuilder()
                        .setMetadata(buildMetadata("TradingParty_NoPartyIDs"))
                        .putFields("PartyID", buildValue("party_id_1"))
                        .putFields("PartyIDSource", buildValue("I")) // DIRECTED_BROKER by dictionary
                        .putFields("PartyRole", buildValue("1")) // EXECUTING_FIRM by dictionary
                        .build())
                        .build())
                .addValues(Value.newBuilder().setMessageValue(Message.newBuilder()
                        .setMetadata(buildMetadata("TradingParty_NoPartyIDs"))
                        .putFields("PartyID", buildValue("party_id_2"))
                        .putFields("PartyIDSource", buildValue("G")) // MIC by dictionary
                        .putFields("PartyRole", buildValue("2")) // BROKER_OF_CREDIT by dictionary
                        .build())
                        .build())
                .build();
    }

    private static Value buildValue(String value) {
        return Value.newBuilder().setSimpleValue(value).build();
    }
}
