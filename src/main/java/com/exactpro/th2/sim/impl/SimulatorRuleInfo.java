/*******************************************************************************
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
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

    public SimulatorRuleInfo(int id, @NotNull IRule rule, boolean isDefault, @NotNull String sessionAlias, @NotNull MessageRouter<MessageBatch> router, @NotNull ScheduledExecutorService scheduledExecutorService) {
        this.id = id;
        this.isDefault = isDefault;
        this.rule = Objects.requireNonNull(rule, "Rule can not be null");
        this.sessionAlias = Objects.requireNonNull(sessionAlias, "Session alias can not be null");
        this.router = Objects.requireNonNull(router, "Router can not be null");
        this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService, "Scheduler can not be null");
    }

    public int getId() {
        return id;
    }

    public @NotNull IRule getRule() {
        return rule;
    }

    public @NotNull String getSessionAlias() {
        return sessionAlias;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void handle(@NotNull Message message) {
        rule.handle(this, Objects.requireNonNull(message, "Message can not be null"));
    }

    public void touch(@NotNull Map<String, String> args) {
        rule.touch(this, Objects.requireNonNull(args, "Arguments can not be null"));
    }

    @Override
    public void send(@NotNull Message msg) {
        Objects.requireNonNull(msg, "Message can not be null");
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Process message by rule with ID '{}' = {}", id, TextFormat.shortDebugString(msg));
        }

        Message finalMessage = StringUtils.isEmpty(msg.getMetadata().getId().getConnectionId().getSessionAlias()) ? addSessionAlias(msg) : msg;
        String sessionAlias = finalMessage.getMetadata().getId().getConnectionId().getSessionAlias();

        MessageBatch batch = MessageBatch.newBuilder().addMessages(finalMessage).build();
        try {
            router.send(batch, "second", "publish", "parsed", sessionAlias);
        } catch (Exception e) {
            LOGGER.error("Can not send message with session alias '{}' = {}", sessionAlias, TextFormat.shortDebugString(msg), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void send(@NotNull Message msg, long delay, @NotNull TimeUnit timeUnit) {
        Objects.requireNonNull(msg, "Message can not be null");
        scheduledExecutorService.schedule(() -> send(msg), delay, Objects.requireNonNull(timeUnit, "Time unit can not be null"));
    }

    private Message addSessionAlias(Message msg) {
        Message.Builder builder = msg.toBuilder();
        builder.getMetadataBuilder().getIdBuilder().getConnectionIdBuilder().setSessionAlias(sessionAlias);
        return builder.build();
    }
}