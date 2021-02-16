/*******************************************************************************
 * Copyright 2021-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim.impl;

import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.sim.rule.IRule;
import com.exactpro.th2.sim.rule.IRuleContext;
import com.google.protobuf.TextFormat;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SimulatorRuleInfo implements IRuleContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorRuleInfo.class);

    private final int id;
    private final IRule rule;
    private final boolean isDefault;
    private final String sessionAlias;
    private final MessageRouter<MessageBatch> router;
    private final ScheduledExecutorService scheduledExecutorService;

    public SimulatorRuleInfo(int id, IRule rule, boolean isDefault, String sessionAlias, MessageRouter<MessageBatch> router, ScheduledExecutorService scheduledExecutorService) {
        this.id = id;
        this.rule = rule;
        this.isDefault = isDefault;
        this.sessionAlias = sessionAlias;
        this.router = router;
        this.scheduledExecutorService = scheduledExecutorService;
    }

    public int getId() {
        return id;
    }

    public IRule getRule() {
        return rule;
    }

    public String getSessionAlias() {
        return sessionAlias;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void handle(Message message) {
        rule.handle(this, message);
    }

    @Override
    public void send(Message msg) {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Process message by rule with ID '{}' = {}", id, TextFormat.shortDebugString(msg));
        }
        String sessionAlias = StringUtils.defaultIfEmpty(msg.getMetadata().getId().getConnectionId().getSessionAlias(), this.sessionAlias);
        MessageBatch batch = MessageBatch.newBuilder().addMessages(msg).build();
        try {
            router.send(batch, "second", "publish", "parsed", sessionAlias);
        } catch (IOException e) {
            LOGGER.error("Can not send message with session alias '{}' = {}", sessionAlias, TextFormat.shortDebugString(msg), e);
        }
    }

    @Override
    public void send(Message msg, long delay, TimeUnit timeUnit) {
        scheduledExecutorService.schedule(() -> send(msg), delay, timeUnit);
    }
}