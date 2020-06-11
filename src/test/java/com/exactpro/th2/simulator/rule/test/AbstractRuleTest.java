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

package com.exactpro.th2.simulator.rule.test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.configuration.MicroserviceConfiguration;
import com.exactpro.th2.infra.grpc.ConnectionID;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.simulator.ISimulator;
import com.exactpro.th2.simulator.adapter.EmptyAdapter;
import com.exactpro.th2.simulator.impl.Simulator;
import com.exactpro.th2.simulator.rule.IRule;
import com.google.protobuf.TextFormat;

/**
 * Class for test work {@link IRule} in {@link ISimulator}
 */
public abstract class AbstractRuleTest {

    private static final ConnectionID DEFAULT_CONNECTIVITY_ID = ConnectionID.newBuilder().setSessionAlias("default_connectivity_for_test").build();

    private Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());
    private MicroserviceConfiguration configuration  = new MicroserviceConfiguration();
    private ISimulator simulator = new Simulator();

    /**
     * Create message for index from builder.
     * @param index message's index
     * @param builder
     * @return Message for this index
     */
    protected abstract @NotNull Message createMessage(int index, @NotNull Message.Builder builder);

    /**
     * @return Max count for messages
     */
    protected abstract int getCountMessages();

    /**
     * Create rule for {@link ISimulator}
     * @return all rules for test
     */
    protected abstract @NotNull List<IRule> createRules();

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
    protected boolean checkResultMessages(int index, List<Message> messages) {
        return true;
    }

    @Before
    public void setUp() throws Exception {
        logger.debug("Simulator is initializing");
        simulator.init(configuration, EmptyAdapter.class);
        logger.info("Simulator was init");

        for (IRule rule : createRules()) {
            simulator.addRule(rule, DEFAULT_CONNECTIVITY_ID);
        }
        logger.info("Rules was added to simulator");
    }

    @Test
    public void testRule() {
        logger.debug("Messages starting create");
        List<Message> messages = createMessages();
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

        List<List<Message>> resultMessages = new ArrayList<>(getCountMessages());
        List<Long> timeEachMessage = new ArrayList<>(getCountMessages());

        logger.debug("Test start");
        long timeStart = System.currentTimeMillis();
        for (int i = 0; i < messages.size(); i++) {
            long timeStartRule = System.nanoTime();
            List<Message> result = simulator.handle(DEFAULT_CONNECTIVITY_ID, messages.get(i));
            long timeEndRule = System.nanoTime();
            if (logWriter != null) {
                try {
                    String inMessageString = (shortMessageFormat()
                            ? TextFormat.shortDebugString(messages.get(i))
                            : messages.get(i).toString())
                            .replace("\n", "\n;");

                    logWriter.append(i + "\n;").append(inMessageString);
                    for (Message tmp : result) {
                        String resultString = (shortMessageFormat()
                                 ? TextFormat.shortDebugString(tmp).replace("\n","\n;")
                                 : tmp.toString()).replace("\n", "\n;");
                        logWriter.append("\n;;").append(resultString);
                    }
                    logWriter.append("\n");
                } catch (IOException e) {
                    logger.error("Can not log messages with index: " + i, e);

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
            resultMessages.add(result);
            timeEachMessage.add(timeEndRule - timeStartRule);
        }
        long timeEnd = System.currentTimeMillis();
        logger.info("Test end");

        for (int i = 0; i < resultMessages.size(); i++) {
            if (!checkResultMessages(i, resultMessages.get(i))) {
                logger.info("Check was failed on index {}", i);
                logger.debug("Check was failed on index {} with messages {}", i, resultMessages.get(i));
            }
        }

        long totalTime = timeEachMessage.get(0);
        logger.debug("Message with index {} take {} ns", 0, timeEachMessage.get(0));
        for (int i = 1; i < timeEachMessage.size(); i++) {
            Long time = timeEachMessage.get(i);
            logger.debug("Message with index {} take {} ns", i, time);
            totalTime += time;
        }

        logger.info("Average time with rules take {} ns", totalTime / getCountMessages());
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
    }

    /**
     * Method for create all messages
     * @return all messages for test
     */
    private @NotNull List<Message> createMessages() {
        List<Message> result = new ArrayList<>(getCountMessages());
        for (int i = 0; i < getCountMessages(); i++) {
            result.add(createMessage(i, Message.newBuilder()));
        }
        return result;
    }
}
