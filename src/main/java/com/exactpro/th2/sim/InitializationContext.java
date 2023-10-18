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
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch;
import com.exactpro.th2.sim.configuration.SimulatorConfiguration;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public class InitializationContext {
    @NotNull
    private final MessageRouter<GroupBatch> batchRouter;
    @NotNull
    private final MessageRouter<EventBatch> eventRouter;
    @NotNull
    private final SimulatorConfiguration configuration;
    @NotNull
    private final EventID rootEventId;
    @NotNull
    private final String bookName;

    private InitializationContext(
            MessageRouter<GroupBatch> batchRouter,
            @NotNull MessageRouter<EventBatch> eventRouter,
            @NotNull SimulatorConfiguration configuration,
            @NotNull EventID rootEventId,
            @NotNull String bookName
    ) {
        this.batchRouter = requireNonNull(batchRouter, "'batchRouter' parameter");
        this.eventRouter = requireNonNull(eventRouter, "'eventRouter' parameter");
        this.configuration = requireNonNull(configuration, "'configuration' parameter");
        this.rootEventId = requireNonNull(rootEventId, "'rootEventId' parameter");
        this.bookName = requireNonNull(bookName, "'bookName' parameter");
        if (bookName.isBlank()) {
            throw new IllegalArgumentException("bookName is blank");
        }
    }

    public @NotNull MessageRouter<GroupBatch> getBatchRouter() {
        return batchRouter;
    }

    public @NotNull MessageRouter<EventBatch> getEventRouter() {
        return eventRouter;
    }

    public @NotNull SimulatorConfiguration getConfiguration() {
        return configuration;
    }

    public @NotNull EventID getRootEventId() {
        return rootEventId;
    }

    public @NotNull String getBookName() {
        return bookName;
    }

    public static InitializationContextBuilder builder() {
        return new InitializationContextBuilder();
    }

    public static final class InitializationContextBuilder {
        private MessageRouter<GroupBatch> batchRouter;
        private MessageRouter<EventBatch> eventRouter;
        private SimulatorConfiguration configuration;
        private EventID rootEventId;
        private String bookName;

        private InitializationContextBuilder() {
        }

        public InitializationContextBuilder withBatchRouter(MessageRouter<GroupBatch> batchRouter) {
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

        public InitializationContextBuilder withBookName(String bookName) {
            this.bookName = bookName;
            return this;
        }

        public InitializationContext build() {
            return new InitializationContext(batchRouter, eventRouter, configuration, rootEventId, bookName);
        }
    }
}
