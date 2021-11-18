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

package com.exactpro.th2.sim.rule;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;

import com.exactpro.th2.common.event.Event;
import com.exactpro.th2.common.grpc.Message;
import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.sim.rule.action.IAction;
import com.exactpro.th2.sim.rule.action.ICancellable;

public interface IRuleContext {
    void send(@NotNull Message msg);

    void send(@NotNull MessageBatch batch);

    void send(@NotNull Message msg, long delay, TimeUnit timeUnit);

    void send(@NotNull MessageBatch batch, long delay, TimeUnit timeUnit);

    ICancellable execute(@NotNull IAction action);

    ICancellable execute(long delay, @NotNull IAction action);

    ICancellable execute(long delay, long period, @NotNull IAction action);

    String getRootEventId();

    void sendEvent(Event event);

    void removeRule();
}