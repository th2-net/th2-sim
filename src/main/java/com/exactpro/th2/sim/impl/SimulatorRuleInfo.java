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

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.common.message.MessageUtils;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.common.schema.message.QueueAttribute;
import com.exactpro.th2.sim.rule.IRule;
import com.exactpro.th2.sim.rule.IRuleContext;
import com.exactpro.th2.sim.rule.action.IAction;
import com.exactpro.th2.sim.rule.action.ICancellable;
import com.exactpro.th2.sim.rule.action.impl.ActionRunner;
import com.exactpro.th2.sim.rule.action.impl.MessageSender;
import com.google.protobuf.TextFormat;

public class SimulatorRuleInfo implements IRuleContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorRuleInfo.class);

    private final int id;
    private final IRule rule;
    private final boolean isDefault;
    private final String sessionAlias;
    private final MessageRouter<MessageBatch> router;
    private final ScheduledExecutorService scheduledExecutorService;
    private final MessageRouter<EventBatch> eventRouter;
    private final String rootEventId;
    private final Consumer<SimulatorRuleInfo> onRemove;
    private final Deque<ICancellable> cancellables = new ConcurrentLinkedDeque<>();
    private final MessageSender sender = new MessageSender(this::send, this::send);

    public SimulatorRuleInfo(
            int id,
            @NotNull IRule rule,
            boolean isDefault,
            @NotNull String sessionAlias,
            @NotNull MessageRouter<MessageBatch> router,
            @NotNull MessageRouter<EventBatch> eventRouter,
            @NotNull String rootEventId,
            @NotNull ScheduledExecutorService scheduledExecutorService,
            @NotNull Consumer<SimulatorRuleInfo> onRemove
    ) {
        this.id = id;
        this.isDefault = isDefault;
        this.rule = Objects.requireNonNull(rule, "Rule can not be null");
        this.sessionAlias = Objects.requireNonNull(sessionAlias, "Session alias can not be null");
        this.router = Objects.requireNonNull(router, "Router can not be null");
        this.eventRouter = Objects.requireNonNull(eventRouter, "Event router can not be null");
        this.rootEventId = Objects.requireNonNull(rootEventId, "Root event id can not be null");
        this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService, "Scheduler can not be null");
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove can not be null");
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

        Message finalMessage = prepareMessage(msg);
        String sessionAlias = finalMessage.getMetadata().getId().getConnectionId().getSessionAlias();

        sendBatch(MessageBatch.newBuilder().addMessages(finalMessage).build(), sessionAlias);
    }

    @Override
    public void send(@NotNull MessageBatch batch) {
        if (batch.getMessagesCount() < 1) {
            return;
        }

        String sessionAlias = getSessionAliasFromBatch(batch);
        MessageBatch batchForSend = prepareMessageBatch(batch);
        sendBatch(batchForSend, sessionAlias);
    }

    @Override
    public void send(@NotNull Message msg, long delay, @NotNull TimeUnit timeUnit) {
        Objects.requireNonNull(msg, "Message can not be null");
        scheduledExecutorService.schedule(() -> send(msg), delay, Objects.requireNonNull(timeUnit, "Time unit can not be null"));
    }

    @Override
    public void send(@NotNull MessageBatch batch, long delay, TimeUnit timeUnit) {
        Objects.requireNonNull(batch, "Message batch can not be null");
        if (batch.getMessagesCount() < 1) {
            return;
        }

        String sessionAlias = getSessionAliasFromBatch(batch);
        MessageBatch batchForSend = prepareMessageBatch(batch);
        scheduledExecutorService.schedule(() -> sendBatch(batchForSend, sessionAlias), delay, Objects.requireNonNull(timeUnit, "Time unit can not be null"));
    }

    private ICancellable registerCancellable(ICancellable cancellable) {
        cancellables.add(cancellable);
        return cancellable;
    }

    @Override
    public ICancellable execute(@NotNull IAction action) {
        Objects.requireNonNull(action, "Action can not be null");
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, action));
    }

    @Override
    public ICancellable execute(long delay, @NotNull IAction action) {
        Objects.requireNonNull(action, "Action can not be null");
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, delay, action));
    }

    @Override
    public ICancellable execute(long delay, long period, @NotNull IAction action) {
        Objects.requireNonNull(action, "Action can not be null");
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, delay, period, action));
    }

    @Override
    public String getRootEventId() {
        return rootEventId;
    }

    @Override
    public void sendEvent(Event event) {
        com.exactpro.th2.common.grpc.Event eventForSend = null;
        try {
            eventForSend = event.toProtoEvent(rootEventId);
            eventRouter.send(EventBatch.newBuilder().addEvents(eventForSend).build());
        } catch (IOException e) {
            String msg = String.format("Can not send event = %s", eventForSend != null ? MessageUtils.toJson(eventForSend) : "{null}");
            LOGGER.error(msg, e);
            throw new IllegalStateException(msg, e);
        }
    }

    @Override
    public void removeRule() {
        cancellables.forEach(cancellable -> {
            try {
                cancellable.cancel();
            } catch (RuntimeException e) {
                LOGGER.error("Failed to cancel", e);
            }
        });

        onRemove.accept(this);
    }

    private Message prepareMessage(Message msg) {
        Message.Builder resultBuilder = null;

        if (StringUtils.isEmpty(msg.getParentEventId().getId())) {
            resultBuilder = msg.toBuilder();
            resultBuilder.setParentEventId(EventID.newBuilder().setId(rootEventId).build());
        }

        if (StringUtils.isEmpty(msg.getMetadata().getId().getConnectionId().getSessionAlias())) {
            if (resultBuilder == null) {
                resultBuilder = msg.toBuilder();
            }
            resultBuilder.getMetadataBuilder().getIdBuilder().getConnectionIdBuilder().setSessionAlias(sessionAlias);
        }

        return resultBuilder == null ? msg : resultBuilder.build();
    }

    private MessageBatch prepareMessageBatch(MessageBatch batch) {
        MessageBatch.Builder builder = MessageBatch.newBuilder();
        for (Message message : batch.getMessagesList()) {
            builder.addMessages(prepareMessage(message));
        }
        return builder.build();
    }

    private String getSessionAliasFromBatch(MessageBatch batch) {
        String sessionAlias = null;
        for (Message message : batch.getMessagesList()) {
            String msgAlias = message.getMetadata().getId().getConnectionId().getSessionAlias();
            if (StringUtils.isEmpty(msgAlias)) {
                msgAlias = this.sessionAlias;
            }

            if (sessionAlias == null) {
                sessionAlias = msgAlias;
            }

            if (!sessionAlias.equals(msgAlias)) {
                throw new IllegalArgumentException("Messages have different session alias = [" + sessionAlias + "," + msgAlias + "]" );
            }
        }

        return sessionAlias;
    }

    private void sendBatch(MessageBatch batch, String sessionAlias) {
        try {
            router.send(batch, QueueAttribute.SECOND.getValue(), sessionAlias);
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Can not send message with session alias '{}' = {}", sessionAlias, MessageUtils.toJson(batch), e);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}