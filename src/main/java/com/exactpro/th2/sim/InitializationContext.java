/*
 * Copyright 2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim;

import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.grpc.MessageGroupBatch;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.sim.configuration.SimulatorConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class InitializationContext {
    @NotNull
    private final MessageRouter<MessageGroupBatch> batchRouter;
    @NotNull
    private final MessageRouter<EventBatch> eventRouter;
    @NotNull
    private final SimulatorConfiguration configuration;
    @NotNull
    private final EventID rootEventId;

    private InitializationContext(
            @NotNull MessageRouter<MessageGroupBatch> batchRouter,
            @NotNull MessageRouter<EventBatch> eventRouter,
            @NotNull SimulatorConfiguration configuration,
            @NotNull EventID rootEventId
    ) {
        this.batchRouter = Objects.requireNonNull(batchRouter, "'batchRouter' parameter");
        this.eventRouter = Objects.requireNonNull(eventRouter, "'eventRouter' parameter");
        this.configuration = Objects.requireNonNull(configuration, "'configuration' parameter");
        this.rootEventId = Objects.requireNonNull(rootEventId, "'rootEventId' parameter");
    }

    public MessageRouter<MessageGroupBatch> getBatchRouter() {
        return batchRouter;
    }

    public MessageRouter<EventBatch> getEventRouter() {
        return eventRouter;
    }

    public SimulatorConfiguration getConfiguration() {
        return configuration;
    }

    public EventID getRootEventId() {
        return rootEventId;
    }

    public static InitializationContextBuilder builder() {
        return new InitializationContextBuilder();
    }

    public static final class InitializationContextBuilder {
        private MessageRouter<MessageGroupBatch> batchRouter;
        private MessageRouter<EventBatch> eventRouter;
        private SimulatorConfiguration configuration;
        private EventID rootEventId;

        private InitializationContextBuilder() {
        }

        public InitializationContextBuilder withBatchRouter(MessageRouter<MessageGroupBatch> batchRouter) {
            this.batchRouter = batchRouter;
            return this;
        }

        public InitializationContextBuilder withEventRouter(MessageRouter<EventBatch> eventRouter) {
            this.eventRouter = eventRouter;
            return this;
        }

        public InitializationContextBuilder withConfiguration(SimulatorConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        public InitializationContextBuilder withRootEventId(EventID rootEventId) {
            this.rootEventId = rootEventId;
            return this;
        }

        public InitializationContext build() {
            return new InitializationContext(batchRouter, eventRouter, configuration, rootEventId);
        }
    }
}
