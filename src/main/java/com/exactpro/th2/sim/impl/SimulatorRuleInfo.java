/*
 * Copyright 2020-2024 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.message.MessageUtils;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.EventId;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Message;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage;
import com.exactpro.th2.common.utils.message.transport.MessageBatcher;
import com.exactpro.th2.sim.rule.IRule;
import com.exactpro.th2.sim.rule.IRuleContext;
import com.exactpro.th2.sim.rule.action.IAction;
import com.exactpro.th2.sim.rule.action.ICancellable;
import com.exactpro.th2.sim.rule.action.impl.ActionRunner;
import com.exactpro.th2.sim.rule.action.impl.MessageSender;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.exactpro.th2.common.utils.event.EventUtilsKt.toTransport;
import static com.exactpro.th2.common.utils.message.transport.MessageUtilsKt.toBatch;
import static java.util.Objects.requireNonNull;

public class SimulatorRuleInfo implements IRuleContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorRuleInfo.class);

    private final int id;
    private final IRule rule;
    private final boolean isDefault;
    private final String sessionAlias;
    private final String bookName;
    private final MessageRouter<GroupBatch> router;
    private final MessageBatcher messageBatcher;
    private final ScheduledExecutorService scheduledExecutorService;
    private final MessageRouter<EventBatch> eventRouter;
    private final EventID rootEventIdProto;
    private final EventId rootEventId;
    private final Consumer<SimulatorRuleInfo> onRemove;
    private final Deque<ICancellable> cancellables = new ConcurrentLinkedDeque<>();
    private final MessageSender sender = new MessageSender(this::send, this::send, this::send);

    public SimulatorRuleInfo(
            int id,
            @NotNull IRule rule,
            boolean isDefault,
            @NotNull String sessionAlias,
            @NotNull String bookName,
            @NotNull MessageRouter<GroupBatch> router,
            @NotNull MessageBatcher messageBatcher,
            @NotNull MessageRouter<EventBatch> eventRouter,
            @NotNull EventID rootEventId,
            @NotNull ScheduledExecutorService scheduledExecutorService,
            @NotNull Consumer<SimulatorRuleInfo> onRemove
    ) {
        this.id = id;
        this.isDefault = isDefault;
        this.bookName = bookName;
        this.rule = requireNonNull(rule, "Rule can not be null");
        this.sessionAlias = requireNonNull(sessionAlias, "Session alias can not be null");
        this.router = requireNonNull(router, "Router can not be null");
        this.messageBatcher = requireNonNull(messageBatcher,  "Message batcher can not be null");
        this.eventRouter = requireNonNull(eventRouter, "Event router can not be null");
        this.rootEventIdProto = requireNonNull(rootEventId, "Root event id can not be null");
        this.rootEventId = toTransport(rootEventIdProto);
        this.scheduledExecutorService = requireNonNull(scheduledExecutorService, "Scheduler can not be null");
        this.onRemove = requireNonNull(onRemove, "onRemove can not be null");
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

    public @NotNull String getBookName() {
        return bookName;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void handle(@NotNull ParsedMessage message) {
        rule.handle(this, requireNonNull(message, "Message can not be null"));
    }

    public void touch(@NotNull Map<String, String> args) {
        rule.touch(this, requireNonNull(args, "Arguments can not be null"));
    }

    @Override
    public void send(@NotNull ParsedMessage msg) {
        requireNonNull(msg, () -> "Null parsed message supplied from rule " + id);
        LOGGER.trace("Process parsed message by rule with ID '{}' = {}", id, msg);
        checkMessage(msg);
        messageBatcher.onMessage(msg.toBuilder(), msg.getId().getSessionAlias());
    }

    @Override
    public void send(@NotNull ParsedMessage.FromMapBuilder msg) {
        requireNonNull(msg, () -> "Null parsed message builder supplied from rule " + id);
        LOGGER.trace("Process parsed message builder by rule with ID '{}' = {}", id, msg);
        completeMessage(msg);
        messageBatcher.onMessage(msg, msg.idBuilder().getSessionAlias());
    }

    @Override
    public void send(@NotNull RawMessage msg) {
        requireNonNull(msg, () -> "Null raw message supplied from rule " + id);
        LOGGER.trace("Process raw message by rule with ID '{}' = {}", id, msg);
        checkMessage(msg);
        messageBatcher.onMessage(msg.toBuilder(), msg.getId().getSessionAlias());
    }

    @Override
    public void send(@NotNull RawMessage.Builder msg) {
        requireNonNull(msg, () -> "Null raw message builder supplied from rule " + id);
        LOGGER.trace("Process raw message builder by rule with ID '{}' = {}", id, msg);
        completeMessage(msg);
        messageBatcher.onMessage(msg, msg.idBuilder().getSessionAlias());
    }

    @Override
    public void send(@NotNull MessageGroup group) {
        requireNonNull(group, () -> "Null group supplied from rule " + id);
        LOGGER.trace("Process group by rule with ID '{}' = {}", id, group);

        if (group.getMessages().isEmpty()) {
            LOGGER.warn("Skipping sending empty group. Rule ID = {}", id);
            return;
        }
        sendBatch(toBatch(checkGroup(group), bookName, ""));
    }

    @Override
    @Deprecated
    public void send(@NotNull GroupBatch batch) {
        requireNonNull(batch, () -> "Null batch supplied from rule " + id);
        LOGGER.trace("Process batch by rule with ID '{}' = {}", id, batch);
        sendBatch(checkBatch(batch));
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
    public void send(@NotNull ParsedMessage msg, long delay, @NotNull TimeUnit timeUnit) {
        requireNonNull(msg, () -> "Null message supplied from rule " + id);
        requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        scheduledExecutorService.schedule(() -> send(msg), checkDelay(delay), timeUnit);
    }

    @Override
    public void send(@NotNull RawMessage msg, long delay, TimeUnit timeUnit) {
        requireNonNull(msg, () -> "Null message supplied from rule " + id);
        requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        scheduledExecutorService.schedule(() -> send(msg), checkDelay(delay), timeUnit);
    }

    @Override
    public void send(@NotNull MessageGroup group, long delay, @NotNull TimeUnit timeUnit) {
        requireNonNull(group, () -> "Null group supplied from rule " + id);
        requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        checkDelay(delay);

        if (group.getMessages().isEmpty()) {
            LOGGER.warn("Skipping delayed sending empty group Rule ID = {}", id);
            return;
        }
        checkGroup(group);
        scheduledExecutorService.schedule(() -> send(toBatch(group, bookName, "")), delay, timeUnit);
    }

    /**
     * @deprecated Will be removed in future releases. Please use send(MessageGroup) to improve performance.
     */
    @Override
    @Deprecated
    public void send(@NotNull GroupBatch batch, long delay, TimeUnit timeUnit) {
        requireNonNull(batch, () -> "Null batch supplied from rule " + id);
        requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        checkDelay(delay);

        if (batch.getGroups().isEmpty()) {
            LOGGER.warn("Skipping delayed sending empty batch. Rule ID = {}", id);
            return;
        }

        checkBatch(batch);
        scheduledExecutorService.schedule(() -> send(batch), delay, timeUnit);
    }

    private ICancellable registerCancellable(ICancellable cancellable) {
        cancellables.add(cancellable);
        return cancellable;
    }

    @Override
    public ICancellable execute(@NotNull IAction action) {
        requireNonNull(action, () -> "Null action supplied from rule " + id);
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, action));
    }

    @Override
    public ICancellable execute(long delay, @NotNull IAction action) {
        requireNonNull(action, () -> "Null action supplied from rule " + id);
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, checkDelay(delay), action));
    }

    @Override
    public ICancellable execute(long delay, long period, @NotNull IAction action) {
        requireNonNull(action, () -> "Null action supplied from rule " + id);
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, checkDelay(delay), checkPeriod(period), action));
    }

    @Override
    public EventID getRootEventIdProto() {
        return rootEventIdProto;
    }

    public EventId getRootEventId() {
        return rootEventId;
    }

    @Override
    public void sendEvent(Event event) {
        sendEventInternal(event, rootEventIdProto);
    }

    @Override
    public void sendEvent(Event event, EventID parentId) {
        sendEventInternal(event, parentId);
    }

    private void sendEventInternal(Event event, EventID parentId) {
        com.exactpro.th2.common.grpc.Event eventForSend = null;
        try {
            eventForSend = event.toProto(parentId);
            eventRouter.send(EventBatch.newBuilder().addEvents(eventForSend).build());
        } catch (IOException e) {
            String msg = String.format("Can not send event = %s", eventForSend == null ? "{null}" : MessageUtils.toJson(eventForSend));
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

    private Message<?> checkMessage(Message<?> msg) {
        if (msg.getEventId() == null) {
            throw new IllegalStateException("Parent event id is null, msg " + msg);
        }
        if (msg.getId().getSessionAlias().isEmpty()) {
            throw new IllegalStateException("Session alias is empty, msg " + msg);
        }

        return msg;
    }

    private Message.Builder<?> completeMessage(Message.Builder<?> builder) {
        if (builder.getEventId() == null) {
            builder.setEventId(getRootEventId());
        }

        if (!builder.idBuilder().isSessionAliasSet()) {
            builder.idBuilder().setSessionAlias(sessionAlias);
        }

        return builder;
    }

    private MessageGroup checkGroup(MessageGroup batch) {
        for (Message<?> message : batch.getMessages()) {
            checkMessage(message);
        }
        return batch;
    }

    private GroupBatch checkBatch(GroupBatch batch) {
        if (bookName.equals(batch.getBook())) {
            throw new IllegalStateException("Book name isn't equal the '" + bookName + "' value, batch " + batch);
        }

        for (MessageGroup group : batch.getGroups()) {
            checkGroup(group);
        }
        return batch;
    }

    private void sendBatch(GroupBatch batch) {
        try {
            router.sendAll(batch);
        } catch (Exception e) {
            LOGGER.error("Can not send batch {}", batch, e);
        }
    }
}