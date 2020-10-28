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

package com.exactpro.th2.sim.rule.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
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

import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.sim.IAdapter;
import com.exactpro.th2.sim.ISimulator;
import com.exactpro.th2.sim.adapter.SupplierAdapter;
import com.exactpro.th2.sim.configuration.SimulatorConfiguration;
import com.exactpro.th2.sim.impl.Simulator;
import com.exactpro.th2.sim.rule.IRule;
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
    private Simulator simulator = new Simulator();

    private SupplierAdapter supplierAdapter;

    @Before
    public void setUp() throws Exception {

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
        logger.debug("Start create messages and messages batches");

        List<MessageLite> messages = createObjects();
        logger.info("Messages and messages batches was created");

        String logFile = getPathLoggingFile();
        OutputStreamWriter logWriter = null;

        if (StringUtils.isNotEmpty(logFile)) {

            logger.info("Try to use logging file");

            try {
                logger.trace("Preparing logging file");
                logWriter = new OutputStreamWriter(new FileOutputStream(logFile));
                logWriter.append("Index;In message;Out messages\n\n");
                logger.trace("Logging file was prepared");
                logger.info("Logging to file was enabled");
            } catch (IOException e) {
                logger.error("Can not use logging messages", e);
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

        logger.info("Start test");
        long timeStart = System.currentTimeMillis();
        AtomicInteger index = new AtomicInteger();

        Map<Integer, List<MessageOrBuilder>> results = new HashMap<>();


        supplierAdapter.addMessageListener(message -> results.computeIfAbsent(index.get(), key -> new ArrayList<>()).add(message));
        supplierAdapter.addMessageBatchListener(batch -> results.computeIfAbsent(index.get(), key -> new ArrayList<>()).add(batch));
        logger.trace("Added listeners to adapter");

        for (; index.get() < messages.size(); index.incrementAndGet()) {

            Delivery delivery = new Delivery(null, null, messages.get(index.get()).toByteArray());
            logger.trace("Created delivery for id = {}", index.get());

            long timeStartRule = System.nanoTime();

            try {
                simulator.handleDelivery(DEFAULT_SESSION_ALIAS, DEFAULT_CONSUMER_TAG, delivery);
            } catch (Exception e) {
                logger.error("Can not execute method with id = {}", index.get(), e);
                continue;
            }

            long timeEndRule = System.nanoTime();
            if (logWriter != null) {
                try {
                    String inMessageString = (useShortMessageFormat()
                            ? TextFormat.shortDebugString((MessageOrBuilder) messages.get(index.get()))
                            : messages.get(index.get()).toString())
                            .replace("\n", "\n" + CSV_CHAR);

                    logWriter.append(index.get() + "\n").append(CSV_CHAR).append(inMessageString);
                    List<MessageOrBuilder> indexResults = results.get(index.get());
                    if (indexResults != null && !indexResults.isEmpty()) {
                        for (MessageOrBuilder tmp : indexResults) {
                            String resultString = (useShortMessageFormat()
                                    ? TextFormat.shortDebugString(tmp)
                                    : tmp.toString()).replace("\n", "\n" + CSV_CHAR + CSV_CHAR);
                            logWriter.append("\n").append(CSV_CHAR).append(CSV_CHAR).append(resultString);
                        }
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
        logger.info("End test");

        boolean checkResultPassed = true;

        for (int i = 0; i < results.size(); i++) {
            List<MessageOrBuilder> list = results.get(i);
            List<Message> resultMessage = new ArrayList<>();
            List<MessageBatch> resultBatches = new ArrayList<>();

            if (list != null && !list.isEmpty()) {
                for (MessageOrBuilder messageOrBuilder : list) {
                    if (messageOrBuilder instanceof Message) {
                        resultMessage.add((Message)messageOrBuilder);
                    } else if (messageOrBuilder instanceof MessageBatch) {
                        resultBatches.add((MessageBatch)messageOrBuilder);
                    }
                }
            }

            if (!checkResultMessages(i, resultMessage, resultBatches)) {
                checkResultPassed = false;
                logger.info("Check was failed on index {}", i);
                logger.debug("Check was failed on index {} with single messages {} and batch messages {}", i, resultMessage, resultBatches);
            }
        }

        long totalTime = 0;
        TimeUnit loggingTimeUnit = getLoggingTimeUnit();
        for (int i = 0; i < timeEachMessage.size(); i++) {
            Long time = timeEachMessage.get(i);
            if (logger.isTraceEnabled()) {
                logger.trace("Message with index {} take {} {}", i, loggingTimeUnit.convert(time, TimeUnit.NANOSECONDS), getShortNameForTimeUnit(loggingTimeUnit));
            }
            totalTime += time;
        }

        logger.info("Average time with rules take {} {}", loggingTimeUnit.convert(totalTime / getCountObjects(), TimeUnit.NANOSECONDS), getShortNameForTimeUnit(loggingTimeUnit));
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

        Assert.assertTrue(isNegativeTest() != checkResultPassed);
    }

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

    protected boolean useShortMessageFormat() {
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

    protected boolean isNegativeTest() { return false; }

    protected TimeUnit getLoggingTimeUnit() { return TimeUnit.NANOSECONDS; }

    private String getShortNameForTimeUnit(TimeUnit loggingTimeUnit) {
        switch (loggingTimeUnit) {
        case NANOSECONDS: return  "ns";
        case MILLISECONDS: return  "ms";
        case MICROSECONDS: return  "mcs";
        case SECONDS: return  "s";
        case MINUTES: return  "minutes";
        case HOURS: return  "hours";
        case DAYS: return  "days";
        }
        return null;
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
