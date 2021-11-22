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

import org.jetbrains.annotations.NotNull;

import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.sim.rule.action.IAction;
import com.exactpro.th2.sim.rule.action.ICancellable;

public interface IRuleContext {

    /**
     * method to send Message
     * @param msg outgoing messages.
     */
    void send(@NotNull Message msg);

    /**
     * method to send MessageBatch
     * @param batch outgoing message batch.
     */
    void send(@NotNull MessageBatch batch);

    /**
     * method to send Message with delay
     * @param msg outgoing message.
     * @param delay value of delay to send batch.
     * @param timeUnit units of delay.
     */
    void send(@NotNull Message msg, long delay, TimeUnit timeUnit);

    /**
     * method to send MessageBatch with delay
     * @param batch outgoing message batch.
     * @param delay value of delay to send batch.
     * @param timeUnit units of delay.
     */
    void send(@NotNull MessageBatch batch, long delay, TimeUnit timeUnit);

    /**
     * method to execute block of code: execute {}
     * @param action executable block.
     */
    ICancellable execute(@NotNull IAction action);

    /**
     * method to execute block of code after delay: execute {}
     * @param delay value of delay to execute block.
     * @param action executable block.
     */
    ICancellable execute(long delay, @NotNull IAction action);

    /**
     * method to execute block of code after delay: execute {}
     * @param delay value of delay to execute block.
     * @param period period of each execution.
     * @param action executable block.
     */
    ICancellable execute(long delay, long period, @NotNull IAction action);

    String getRootEventId();

    /**
     * method to send Event
     * @param event outgoing message Event.
     */
    void sendEvent(Event event);

    void removeRule();
}
