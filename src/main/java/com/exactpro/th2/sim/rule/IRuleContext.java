/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim.rule;

import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.EventId;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage;
import com.exactpro.th2.sim.rule.action.IAction;
import com.exactpro.th2.sim.rule.action.ICancellable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public interface IRuleContext {

    /**
     * Attempts to send a msg immediately
     */
    void send(@NotNull ParsedMessage msg);

    /**
     * Attempts to send a msg immediately
     */
    void send(@NotNull ParsedMessage.FromMapBuilder msg);

    /**
     * Attempts to send a raw msg immediately
     */
    void send(@NotNull RawMessage msg);

    /**
     * Attempts to send a raw msg immediately
     */
    void send(@NotNull RawMessage.Builder msg);

    /**
     * Attempts to send a group immediately
     */
    void send(@NotNull MessageGroup group);

    /**
     * Attempts to send a batch immediately
     */
    @Deprecated
    void send(@NotNull GroupBatch batch);

    /**
     * Attempts to send a raw msg after a specified delay
     */
    void send(@NotNull RawMessage msg, long delay, TimeUnit timeUnit);

    /**
     * Attempts to send a msg after a specified delay
     */
    void send(@NotNull ParsedMessage msg, long delay, TimeUnit timeUnit);

    /**
     * Attempts to send a group after a specified delay
     */
    void send(@NotNull MessageGroup group, long delay, TimeUnit timeUnit);

    /**
     * Attempts to send a batch after a specified delay
     */
    @Deprecated
    void send(@NotNull GroupBatch batch, long delay, TimeUnit timeUnit);

    /**
     * Attempts to execute action immediately
     * @return an entity which can be used cancel this operation
     */
    ICancellable execute(@NotNull IAction action);

    /**
     * Attempts to execute action after a specified delay
     * @return an entity which can be used cancel this operation
     */
    ICancellable execute(long delay, @NotNull IAction action);

    /**
     * Attempts to execute action after a specified delay and
     * then periodically using a specified period
     *
     * @return an entity which can be used cancel this operation
     */
    ICancellable execute(long delay, long period, @NotNull IAction action);

    EventID getRootEventIdProto();

    EventId getRootEventId();

    /**
     * Attempts to send an event immediately
     */
    void sendEvent(Event event);

    /**
     * Sends {@code event} immediately. Attaches it to the {@code parentId}.
     * @param event event to send
     * @param parentId event ID to attach the event
     */
    void sendEvent(Event event, EventID parentId);

    void removeRule();
}
