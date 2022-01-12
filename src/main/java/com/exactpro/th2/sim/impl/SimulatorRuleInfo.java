/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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
 */

package com.exactpro.th2.sim.impl;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.exactpro.th2.common.grpc.AnyMessage;
import com.exactpro.th2.common.grpc.MessageGroup;
import com.exactpro.th2.common.grpc.MessageGroupBatch;
import com.exactpro.th2.common.grpc.RawMessage;
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
    private final MessageRouter<MessageGroupBatch> router;
    private final ScheduledExecutorService scheduledExecutorService;
    private final MessageRouter<EventBatch> eventRouter;
    private final String rootEventId;
    private final Consumer<SimulatorRuleInfo> onRemove;
    private final Deque<ICancellable> cancellables = new ConcurrentLinkedDeque<>();
    private final MessageSender sender = new MessageSender(this::send, this::send, this::send);

    public SimulatorRuleInfo(
            int id,
            @NotNull IRule rule,
            boolean isDefault,
            @NotNull String sessionAlias,
            @NotNull MessageRouter<MessageGroupBatch> router,
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
        send(AnyMessage.newBuilder().setMessage(msg).build());
    }

    @Override
    public void send(@NotNull RawMessage msg) {
        send(AnyMessage.newBuilder().setRawMessage(msg).build());
    }

    private void send(@NotNull AnyMessage msg) {
        Objects.requireNonNull(msg, () -> "Null message supplied from rule " + id);

        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("Process message by rule with ID '{}' = {}", id, TextFormat.shortDebugString(msg));
        }

        sendGroup(MessageGroup.newBuilder().addMessages(msg).build());
    }

    @Override
    public void send(@NotNull MessageGroup group) {
        Objects.requireNonNull(group, () -> "Null batch supplied from rule " + id);

        if (group.getMessagesCount() < 1) {
            return;
        }

        MessageGroup groupForSend = prepareMessageGroup(group);
        sendGroup(groupForSend);
    }

    private long checkDelay(long delay) {
        if(delay < 0) {
            throw new IllegalStateException("Negative delay in rule " + id + ": " + delay);
        }

        return delay;
    }

    private long checkPeriod(long period) {
        if(period <= 0) {
            throw new IllegalStateException("Non-positive period in rule " + id + ": " + period);
        }

        return period;
    }

    @Override
    public void send(@NotNull Message msg, long delay, @NotNull TimeUnit timeUnit) {
        Objects.requireNonNull(msg, () -> "Null message supplied from rule " + id);
        Objects.requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        scheduledExecutorService.schedule(() -> send(msg), checkDelay(delay), timeUnit);
    }

    @Override
    public void send(@NotNull RawMessage msg, long delay, TimeUnit timeUnit) {
        Objects.requireNonNull(msg, () -> "Null message supplied from rule " + id);
        Objects.requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        scheduledExecutorService.schedule(() -> send(msg), checkDelay(delay), timeUnit);
    }

    @Override
    public void send(@NotNull MessageGroup group, long delay, @NotNull TimeUnit timeUnit) {
        Objects.requireNonNull(group, () -> "Null group supplied from rule " + id);
        Objects.requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        checkDelay(delay);

        if (group.getMessagesCount() < 1) {
            return;
        }

        MessageGroup groupForSend = prepareMessageGroup(group);

        scheduledExecutorService.schedule(() -> sendGroup(groupForSend), delay, timeUnit);
    }

    private ICancellable registerCancellable(ICancellable cancellable) {
        cancellables.add(cancellable);
        return cancellable;
    }

    @Override
    public ICancellable execute(@NotNull IAction action) {
        Objects.requireNonNull(action, () -> "Null action supplied from rule " + id);
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, action));
    }

    @Override
    public ICancellable execute(long delay, @NotNull IAction action) {
        Objects.requireNonNull(action, () -> "Null action supplied from rule " + id);
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, checkDelay(delay), action));
    }

    @Override
    public ICancellable execute(long delay, long period, @NotNull IAction action) {
        Objects.requireNonNull(action, () -> "Null action supplied from rule " + id);
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, checkDelay(delay), checkPeriod(period), action));
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
                LOGGER.error("Failed to cancel sub-task of rule {}", id, e);
            }
        });

        onRemove.accept(this);
    }

    private AnyMessage prepareMessage(AnyMessage msg) {
        AnyMessage.Builder resultBuilder = null;

        switch (msg.getKindCase()) {
            case MESSAGE: {
                if (StringUtils.isEmpty(msg.getMessage().getParentEventId().getId())) {
                    resultBuilder = msg.toBuilder();
                    resultBuilder.getMessageBuilder().setParentEventId(EventID.newBuilder().setId(rootEventId).build());
                }
                if (StringUtils.isEmpty(msg.getMessage().getMetadata().getId().getConnectionId().getSessionAlias())) {
                    if (resultBuilder == null) {
                        resultBuilder = msg.toBuilder();
                    }
                    resultBuilder.getMessageBuilder().getMetadataBuilder().getIdBuilder().getConnectionIdBuilder().setSessionAlias(sessionAlias);
                }
                break;
            }
            case RAW_MESSAGE: {
                if (StringUtils.isEmpty(msg.getRawMessage().getParentEventId().getId())) {
                    resultBuilder = msg.toBuilder();
                    resultBuilder.getRawMessageBuilder().setParentEventId(EventID.newBuilder().setId(rootEventId).build());
                }
                if (StringUtils.isEmpty(msg.getRawMessage().getMetadata().getId().getConnectionId().getSessionAlias())) {
                    if (resultBuilder == null) {
                        resultBuilder = msg.toBuilder();
                    }
                    resultBuilder.getRawMessageBuilder().getMetadataBuilder().getIdBuilder().getConnectionIdBuilder().setSessionAlias(sessionAlias);
                }
                break;
            }
            default: {
                LOGGER.warn("Unsupported kind of message: {}", msg.getKindCase());
            }
        }

        return resultBuilder == null ? msg : resultBuilder.build();
    }

    private MessageGroup prepareMessageGroup(MessageGroup batch) {
        MessageGroup.Builder builder = MessageGroup.newBuilder();
        for (AnyMessage message : batch.getMessagesList()) {
            builder.addMessages(prepareMessage(message));
        }
        return builder.build();
    }

    private void sendGroup(MessageGroup group) {
        try {
            router.sendAll(MessageGroupBatch.newBuilder().addGroups(group).build(), QueueAttribute.SECOND.getValue());
        } catch (Exception e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Can not send message  {}", TextFormat.shortDebugString(group), e);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
