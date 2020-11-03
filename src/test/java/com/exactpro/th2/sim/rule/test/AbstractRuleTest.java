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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
import com.exactpro.th2.sim.ISimulator;
import com.exactpro.th2.sim.impl.Simulator;
import com.exactpro.th2.sim.rule.IRule;
import com.google.protobuf.TextFormat;

/**
 * Class for test work {@link IRule} in {@link ISimulator}
 */
public abstract class AbstractRuleTest {

    private static final String DEFAULT_SESSION_ALIAS = "default_connectivity_for_test";
    private static final String CSV_CHAR = ";";

    private Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());
    private Simulator simulator = new Simulator();
    private final TestCommonFactory factory = new TestCommonFactory();

    @Before
    public void setUp() throws Exception {

        logger.debug("Simulator is initializing");
        simulator.init(factory);
        logger.info("Simulator was init");

        addRules(simulator, DEFAULT_SESSION_ALIAS);
        logger.info("Rules was added to simulator");
    }

    @Test
    public void testRule() {
        logger.debug("Start create messages and messages batches");

        List<MessageBatch> messages = createMessageBatches();
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

        List<Long> timeEachMessage = new ArrayList<>(getCountMessageBatches());

        logger.info("Start test");
        long timeStart = System.currentTimeMillis();
        AtomicInteger index = new AtomicInteger();

        Map<Integer, List<MessageBatch>> results = new HashMap<>();


        factory.addConsumer(batch -> results.computeIfAbsent(index.get(), key -> new ArrayList<>()).add(batch));
        logger.trace("Added listeners to adapter");

        for (; index.get() < messages.size(); index.incrementAndGet()) {
            logger.trace("Created delivery for id = {}", index.get());
            MessageBatch batch = messages.get(index.get());

            Iterator<Message> iterator = batch.getMessagesList().iterator();

            long timeStartRule = System.nanoTime();

            while (iterator.hasNext()) {
                try {
                    simulator.handleMessage(DEFAULT_SESSION_ALIAS, iterator.next());
                } catch (Exception e) {
                    logger.error("Can not execute method with id = {}", index.get(), e);
                    continue;
                }
            }

            long timeEndRule = System.nanoTime();
            if (logWriter != null) {
                try {
                    String inMessageString = (useShortMessageFormat()
                            ? TextFormat.shortDebugString(messages.get(index.get()))
                            : messages.get(index.get()).toString())
                            .replace("\n", "\n" + CSV_CHAR);

                    logWriter.append(index.get() + "\n").append(CSV_CHAR).append(inMessageString);
                    List<MessageBatch> indexResults = results.get(index.get());
                    if (indexResults != null && !indexResults.isEmpty()) {
                        for (MessageBatch tmp : indexResults) {
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
            List<MessageBatch> list = results.get(i);

            if (!checkResultMessages(i, list)) {
                checkResultPassed = false;
                logger.info("Check was failed on index {}", i);
                logger.debug("Check was failed on index {} with batch messages {}", i, list);
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

        logger.info("Average time with rules take {} {}", loggingTimeUnit.convert(totalTime / getCountMessageBatches(), TimeUnit.NANOSECONDS), getShortNameForTimeUnit(loggingTimeUnit));
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
     * Create message batch for index.
     * @param index object index convert to delivery bytes
     * @return Message batch for this index, or null
     */
    protected abstract @Nullable MessageBatch createMessageBatch(int index);

    /**
     * @return Max count for delivery objects
     */
    protected abstract int getCountMessageBatches();

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
     * @param messageBatches
     * @return True, if messages is right
     */
    protected boolean checkResultMessages(int index, List<MessageBatch> messageBatches) {
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
    private @NotNull List<MessageBatch> createMessageBatches() {
        List<MessageBatch> result = new ArrayList<>(getCountMessageBatches());
        for (int i = 0; i < getCountMessageBatches(); i++) {
            MessageBatch batch = createMessageBatch(i);

            if (batch == null) {
                logger.warn("For index '{}' can not create object", i);
                result.add(MessageBatch.newBuilder().build());
            } else {
                result.add(batch);
            }
        }

        return result;
    }
}
