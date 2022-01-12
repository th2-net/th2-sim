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

package com.exactpro.th2.sim.rule;

import java.util.concurrent.TimeUnit;

import com.exactpro.th2.common.grpc.MessageGroup;
import com.exactpro.th2.common.grpc.RawMessage;
import org.jetbrains.annotations.NotNull;

import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.sim.rule.action.IAction;
import com.exactpro.th2.sim.rule.action.ICancellable;

public interface IRuleContext {

    /**
     * Attempts to send a msg immediately
     */
    void send(@NotNull Message msg);

    /**
     * Attempts to send a raw msg immediately
     */
    void send(@NotNull RawMessage msg);

    /**
     * Attempts to send a group immediately
     */
    void send(@NotNull MessageGroup group);

    /**
     * Attempts to send a raw msg after a specified delay
     */
    void send(@NotNull RawMessage msg, long delay, TimeUnit timeUnit);

    /**
     * Attempts to send a msg after a specified delay
     */
    void send(@NotNull Message msg, long delay, TimeUnit timeUnit);

    /**
     * Attempts to send a group after a specified delay
     */
    void send(@NotNull MessageGroup group, long delay, TimeUnit timeUnit);

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
     * @return an entity which can be used cancel this operation
     */
    ICancellable execute(long delay, long period, @NotNull IAction action);

    String getRootEventId();

    /**
     * Attempts to send an event immediately
     */
    void sendEvent(Event event);

    void removeRule();
}
