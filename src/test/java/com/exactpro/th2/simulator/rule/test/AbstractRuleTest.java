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

package com.exactpro.th2.simulator.rule.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.infra.grpc.MessageBatch;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;
import com.exactpro.th2.simulator.adapter.SupplierAdapter;
import com.exactpro.th2.simulator.configuration.SimulatorConfiguration;
import com.exactpro.th2.simulator.impl.Simulator;
import com.exactpro.th2.simulator.rule.IRule;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageOrBuilder;
import com.google.protobuf.TextFormat;
import com.rabbitmq.client.Delivery;

/**
 * Class for test work {@link IRule} in {@link ISimulator}
 */
public abstract class AbstractRuleTest {

    private static final String DEFAULT_SESSION_ALIAS = "default_connectivity_for_test";
    private static final String DEFAULT_CONSUMER_TAG = "default_consumer_tag";
    private static final String CSV_CHAR = ";";

    private Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());
    private SimulatorConfiguration configuration  = new SimulatorConfiguration();
    private ISimulator simulator = new Simulator();


    /**
     * Create message for index.
     * @param index object index convert to delivery bytes
     * @return Message for this index, or null
     */
    protected abstract @Nullable Message createMessage(int index);

    /**
     * Create message batch for index.
     * @param index object index convert to delivery bytes
     * @return Message batch for this index, or null
     */
    protected abstract @Nullable MessageBatch createMessageBatch(int index);

    /**
     * @return Max count for delivery objects
     */
    protected abstract int getCountObjects();


    /**
     * Create rule for {@link ISimulator}
     * @return all rules for test
     */
    protected abstract void addRules(ISimulator simulator, String sessionAlias);

    protected @Nullable String getPathLoggingFile() {
        return null;
    }

    protected boolean shortMessageFormat() {
        return true;
    }

    /**
     * Check result's messages with index
     * @param index result's index
     * @param messages
     * @return True, if messages is right
     */
    protected boolean checkResultMessages(int index, List<Message> messages, List<MessageBatch> messageBatches) {
        return true;
    }

    Method handleMethod;
    SupplierAdapter supplierAdapter;

    @Before
    public void setUp() throws Exception {

        try {
            handleMethod = simulator.getClass().getDeclaredMethod("handleDelivery", String.class, String.class, Delivery.class);
            handleMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Can not start test, because can not find method \"handleDelivery\"", e);
        }

        logger.debug("Simulator is initializing");
        simulator.init(configuration, SupplierAdapter.class);
        logger.info("Simulator was init");

        addRules(simulator, DEFAULT_SESSION_ALIAS);
        logger.info("Rules was added to simulator");

        try {
            Field connectivityMap = simulator.getClass().getDeclaredField("connectivityAdapters");
            connectivityMap.setAccessible(true);
            IAdapter adapter = ((Map<String, IAdapter>) connectivityMap.get(simulator)).get(DEFAULT_SESSION_ALIAS);
            if (adapter instanceof SupplierAdapter) {
                this.supplierAdapter = (SupplierAdapter) adapter;
            } else {
                throw new IllegalStateException("Can not start test, because can not get supplier adapter");
            }
        } catch (NoSuchFieldException | ClassCastException | IllegalAccessException e) {
            throw new IllegalStateException("Can not start test, because can get map field \"connectivityAdapters\"", e);
        }
    }

    @Test
    public void testRule() {
        logger.debug("Messages starting create");

        List<MessageLite> messages = createObjects();
        logger.info("Messages was created");

        String logFile = getPathLoggingFile();
        OutputStreamWriter logWriter = null;

        if (StringUtils.isNotEmpty(logFile)) {
            try {
                logger.info("Prepare logging file");
                logWriter = new OutputStreamWriter(new FileOutputStream(logFile));
                logWriter.append("Index;In message;Out messages\n\n");
                logger.info("Logging messages was enable");
            } catch (IOException e) {
                logger.error("Can not enable logging messages", e);
                if (logWriter != null) {
                    try {
                        logWriter.flush();
                    } catch (IOException e1) {
                        logger.error("Can not flush log file", e1);
                    }

                    try {
                        logWriter.close();
                    } catch (IOException e1) {
                        logger.error("Can not close log file", e1);
                    }
                }
                logWriter = null;
            }
        }

        List<Long> timeEachMessage = new ArrayList<>(getCountObjects());

        logger.debug("Test start");
        long timeStart = System.currentTimeMillis();
        AtomicInteger index = new AtomicInteger();

        Map<Integer, List<MessageOrBuilder>> results = new HashMap<>();

        supplierAdapter.addMessageListener(message -> results.computeIfAbsent(index.get(), key -> new ArrayList<>()).add(message));
        supplierAdapter.addMessageBatchListener(batch -> results.computeIfAbsent(index.get(), key -> new ArrayList<>()).add(batch));

        for (; index.get() < messages.size(); index.incrementAndGet()) {
            long timeStartRule = System.nanoTime();

            try {
                handleMethod.invoke(simulator, DEFAULT_SESSION_ALIAS, DEFAULT_CONSUMER_TAG, new Delivery(null, null, messages.get(index.get()).toByteArray()));
            } catch (IllegalAccessException | InvocationTargetException e) {
                logger.error("Can not invoke method", e);
                continue;
            }
            long timeEndRule = System.nanoTime();
            if (logWriter != null) {
                try {
                    String inMessageString = (shortMessageFormat()
                            ? TextFormat.shortDebugString((MessageOrBuilder) messages.get(index.get()))
                            : messages.get(index.get()).toString())
                            .replace("\n", "\n;");

                    logWriter.append(index.get() + "\n;").append(inMessageString);
                    for (MessageOrBuilder tmp : results.get(index.get())) {
                        String resultString = (shortMessageFormat()
                                ? TextFormat.shortDebugString(tmp)
                                : tmp.toString()).replace("\n", "\n;;");
                        logWriter.append("\n;;").append(resultString);
                    }
                    logWriter.append("\n");
                } catch (IOException e) {
                    logger.error("Can not log messages with index: " + index, e);

                    try {
                        logWriter.flush();
                    } catch (IOException e1) {
                        logger.error("Can not flush log file", e1);
                    }

                    try {
                        logWriter.close();
                    } catch (IOException e1) {
                        logger.error("Can not close log file", e1);
                    }

                    logWriter = null;
                }
            }
            timeEachMessage.add(timeEndRule - timeStartRule);
        }
        long timeEnd = System.currentTimeMillis();
        logger.info("Test end");

        boolean checkResultPassed = true;

        for (int i = 0; i < results.size(); i++) {
            List<MessageOrBuilder> list = results.get(i);
            List<Message> resultMessage = new ArrayList<>();
            List<MessageBatch> resultBatches = new ArrayList<>();

            for (MessageOrBuilder messageOrBuilder : list) {
                if (messageOrBuilder instanceof  Message) {
                    resultMessage.add((Message)messageOrBuilder);
                } else if (messageOrBuilder instanceof MessageBatch) {
                    resultBatches.add((MessageBatch)messageOrBuilder);
                }
            }

            if (!checkResultMessages(i, resultMessage, resultBatches)) {
                checkResultPassed = false;
                logger.info("Check was failed on index {}", i);
                logger.debug("Check was failed on index {} with single messages {} and batch messages {}", i, resultMessage, resultBatches);
            }
        }

        long totalTime = timeEachMessage.get(0);
        logger.debug("Message with index {} take {} ns", 0, timeEachMessage.get(0));
        for (int i = 1; i < timeEachMessage.size(); i++) {
            Long time = timeEachMessage.get(i);
            logger.debug("Message with index {} take {} ns", i, time);
            totalTime += time;
        }

        logger.info("Average time with rules take {} ns", totalTime / getCountObjects());
        logger.info("All rules time take {} ms", TimeUnit.NANOSECONDS.toMillis(totalTime));
        logger.info("All test spend {} ms", timeEnd - timeStart);
        if (logWriter != null) {
            try {
                logWriter.flush();
            } catch (IOException e) {
                logger.error("Can not flush log file", e);
            }

            try {
                logWriter.close();
            } catch (IOException e) {
                logger.error("Can not close log file", e);
            }
        }

        Assert.assertTrue(checkResultPassed);
    }

    /**
     * Method for create all messages
     * @return all messages for test
     */
    private @NotNull List<MessageLite> createObjects() {
        List<MessageLite> result = new ArrayList<>(getCountObjects());
        for (int i = 0; i < getCountObjects(); i++) {
            Message message = createMessage(i);
            MessageBatch batch = createMessageBatch(i);

            if (message != null && batch != null) {
                throw new IllegalStateException("Created message and message batch for index = " + i);
            }

            if (message != null) {
                result.add(message);
            } else if (batch != null) {
                result.add(batch);
            } else {
                logger.warn("For index '{}' can not create object", i);
            }
        }

        return result;
    }
}
