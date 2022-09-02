/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.exactpro.th2.common.grpc.AnyMessage;
import com.exactpro.th2.common.grpc.MessageGroup;
import com.exactpro.th2.common.grpc.RawMessage;
import com.exactpro.th2.common.message.MessageUtils;
import com.exactpro.th2.common.utils.event.EventBatcher;
import com.exactpro.th2.sim.configuration.RuleConfiguration;
import com.exactpro.th2.sim.util.MessageBatcher;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.sim.rule.IRule;
import com.exactpro.th2.sim.rule.IRuleContext;
import com.exactpro.th2.sim.rule.action.IAction;
import com.exactpro.th2.sim.rule.action.ICancellable;
import com.exactpro.th2.sim.rule.action.impl.ActionRunner;
import com.exactpro.th2.sim.rule.action.impl.MessageSender;

public class SimulatorRuleInfo implements IRuleContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(SimulatorRuleInfo.class);

    private final IRule rule;

    private final int id;
    private final String rootEventId;

    private final RuleConfiguration configuration;

    private final EventBatcher eventBatcher;
    private final MessageBatcher messageBatcher;

    private final ScheduledExecutorService scheduledExecutorService;
    private final Deque<ICancellable> cancellables = new ConcurrentLinkedDeque<>();

    private final Consumer<SimulatorRuleInfo> onRemove;

    private final MessageSender sender = new MessageSender(this::send, this::send, this::send);

    private boolean isDefault = false;

    public SimulatorRuleInfo(
            int id,
            @NotNull IRule rule,
            @NotNull RuleConfiguration configuration,
            @NotNull MessageBatcher messageBatcher,
            @NotNull EventBatcher eventBatcher,
            @NotNull String rootEventId,
            @NotNull ScheduledExecutorService scheduledExecutorService,
            @NotNull Consumer<SimulatorRuleInfo> onRemove
    ) {
        this.id = id;
        this.rule = Objects.requireNonNull(rule, "Rule can not be null");
        this.configuration = Objects.requireNonNull(configuration, "RuleConfiguration can not be null");
        this.messageBatcher = Objects.requireNonNull(messageBatcher, "Router can not be null");
        this.eventBatcher = Objects.requireNonNull(eventBatcher, "Event router can not be null");
        this.rootEventId = Objects.requireNonNull(rootEventId, "Root event id can not be null");
        this.scheduledExecutorService = Objects.requireNonNull(scheduledExecutorService, "Scheduler can not be null");
        this.onRemove = Objects.requireNonNull(onRemove, "onRemove can not be null");
    }

    public int getId() {
        return id;
    }

    @NotNull
    public IRule getRule() {
        return rule;
    }

    @Override
    public String getRootEventId() {
        return rootEventId;
    }

    @NotNull
    public RuleConfiguration getConfiguration() {
        return configuration;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void setDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public void handle(@NotNull Message message) {
        rule.handle(this, Objects.requireNonNull(message, "Message can not be null"));
    }

    public void touch(@NotNull Map<String, String> args) {
        rule.touch(this, Objects.requireNonNull(args, "Arguments can not be null"));
    }

    @Override
    public void send(@NotNull Message msg) {
        Objects.requireNonNull(msg, () -> "Null message supplied from rule " + id);
        messageBatcher.onMessage(prepareMessage(AnyMessage.newBuilder().setMessage(msg).build()), configuration.getRelation());
    }

    @Override
    public void send(@NotNull RawMessage msg) {
        Objects.requireNonNull(msg, () -> "Null message supplied from rule " + id);
        messageBatcher.onMessage(prepareMessage(AnyMessage.newBuilder().setRawMessage(msg).build()), configuration.getRelation());
    }

    @Override
    public void send(@NotNull MessageGroup group) {
        Objects.requireNonNull(group, () -> "Null group supplied from rule " + id);
        if (group.getMessagesCount() < 1) {
            return;
        }
        messageBatcher.onGroup(prepareMessageGroup(group), configuration.getRelation());
    }

    @Override
    public void send(@NotNull Message msg, long delay, @NotNull TimeUnit timeUnit) {
        Objects.requireNonNull(msg, () -> "Null message supplied from rule " + id);
        Objects.requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        requireNonNegative(delay, () -> "Negative delay in rule " + id + ": " + delay);

        scheduledExecutorService.schedule(() -> send(msg), delay, timeUnit);
    }

    @Override
    public void send(@NotNull RawMessage msg, long delay, TimeUnit timeUnit) {
        Objects.requireNonNull(msg, () -> "Null message supplied from rule " + id);
        Objects.requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        requireNonNegative(delay, () -> "Negative delay in rule " + id + ": " + delay);

        scheduledExecutorService.schedule(() -> send(msg), delay, timeUnit);
    }

    @Override
    public void send(@NotNull MessageGroup group, long delay, @NotNull TimeUnit timeUnit) {
        Objects.requireNonNull(group, () -> "Null group supplied from rule " + id);
        Objects.requireNonNull(timeUnit, () -> "Null time unit supplied from rule " + id);
        requireNonNegative(delay, () -> "Negative delay in rule " + id + ": " + delay);

        if (group.getMessagesCount() < 1) {
            return;
        }

        scheduledExecutorService.schedule(() -> send(group), delay, timeUnit);
    }

    @Override
    public void sendEvent(@NotNull Event event) throws JsonProcessingException {
        eventBatcher.onEvent(event.toProtoEvent(rootEventId));
    }

    @Override
    public ICancellable execute(@NotNull IAction action) {
        Objects.requireNonNull(action, () -> "Null action supplied from rule " + id);
        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, action));
    }

    @Override
    public ICancellable execute(long delay, @NotNull IAction action) {
        Objects.requireNonNull(action, () -> "Null action supplied from rule " + id);
        requireNonNegative(delay, () -> "Negative delay in rule " + id + ": " + delay);

        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, delay, action));
    }

    @Override
    public ICancellable execute(long delay, long period, @NotNull IAction action) {
        Objects.requireNonNull(action, () -> "Null action supplied from rule " + id);
        requireNonNegative(delay, () -> "Negative delay in rule " + id + ": " + delay);
        requirePositive(period, () -> "Negative period in rule " + id + ": " + period);

        return registerCancellable(new ActionRunner(scheduledExecutorService, sender, delay, period, action));
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

    private MessageGroup prepareMessageGroup(MessageGroup batch) {
        MessageGroup.Builder builder = MessageGroup.newBuilder();
        for (AnyMessage message : batch.getMessagesList()) {
            builder.addMessages(prepareMessage(message));
        }
        return builder.build();
    }

    private AnyMessage prepareMessage(@NotNull AnyMessage msg) {
        AnyMessage.Builder resultBuilder = null;

        switch (msg.getKindCase()) {
            case MESSAGE: {
                Message parsedMessage = msg.getMessage();
                if (!parsedMessage.hasParentEventId()) {
                    resultBuilder = msg.toBuilder();
                    resultBuilder.getMessageBuilder().getParentEventIdBuilder().setId(rootEventId);
                }
                if (StringUtils.isEmpty(MessageUtils.getSessionAlias(parsedMessage)) && configuration.getSessionAlias() != null) {
                    if (resultBuilder == null) {
                        resultBuilder = msg.toBuilder();
                    }
                    MessageUtils.setSessionAlias(resultBuilder.getMessageBuilder(), configuration.getSessionAlias());
                }
                break;
            }
            case RAW_MESSAGE: {
                RawMessage rawMessage = msg.getRawMessage();
                if (!rawMessage.hasParentEventId()) {
                    resultBuilder = msg.toBuilder();
                    resultBuilder.getRawMessageBuilder().getParentEventIdBuilder().setId(rootEventId);
                }
                if (StringUtils.isEmpty(MessageUtils.getSessionAlias(rawMessage)) && configuration.getSessionAlias() != null) {
                    if (resultBuilder == null) {
                        resultBuilder = msg.toBuilder();
                    }
                    MessageUtils.setSessionAlias(resultBuilder.getRawMessageBuilder(), configuration.getSessionAlias());
                }
                break;
            }
            default: {
                LOGGER.warn("Unsupported kind of message: {}", msg.getKindCase());
            }
        }

        return resultBuilder == null ? msg : resultBuilder.build();
    }

    public boolean checkAlias(@NotNull Message message) {
        String alias = configuration.getSessionAlias();
        return alias == null || alias.isEmpty() || MessageUtils.getSessionAlias(message).equals(alias);
    }

    private void requireNonNegative(long value, Supplier<String> messageSupplier) {
        if (value < 0) {
            throw new IllegalStateException(messageSupplier == null ? null : messageSupplier.get());
        }
    }

    private void requirePositive(long value, Supplier<String> messageSupplier) {
        if (value <= 0) {
            throw new IllegalStateException(messageSupplier == null ? null : messageSupplier.get());
        }
    }

    private ICancellable registerCancellable(ICancellable cancellable) {
        cancellables.add(cancellable);
        return cancellable;
    }
}
