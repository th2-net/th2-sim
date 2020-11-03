/*******************************************************************************
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim.rule.test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.exactpro.th2.common.grpc.MessageBatch;
import com.exactpro.th2.common.schema.factory.AbstractCommonFactory;
import com.exactpro.th2.common.schema.message.MessageListener;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.exactpro.th2.common.schema.message.SubscriberMonitor;
import com.exactpro.th2.common.schema.message.configuration.MessageRouterConfiguration;
import com.exactpro.th2.common.schema.message.impl.rabbitmq.connection.ConnectionManager;

public class TestCommonFactory extends AbstractCommonFactory {

    private final List<Consumer<MessageBatch>> consumers = new ArrayList<>();

    private final MessageRouter<MessageBatch> customMessageRouter = new TestMessageRouter();

    public void addConsumer(Consumer<MessageBatch> consumer) {
        consumers.add(consumer);
    }

    @Override
    public MessageRouter<MessageBatch> getMessageRouterParsedBatch() {
        return customMessageRouter;
    }


    @Override
    protected Path getPathToRabbitMQConfiguration() {
        return null;
    }

    @Override
    protected Path getPathToMessageRouterConfiguration() {
        return null;
    }

    @Override
    protected Path getPathToGrpcRouterConfiguration() {
        return null;
    }

    @Override
    protected Path getPathToCradleConfiguration() {
        return null;
    }

    @Override
    protected Path getPathToCustomConfiguration() {
        return null;
    }

    @Override
    protected Path getPathToDictionariesDir() {
        return null;
    }

    @Override
    protected Path getPathToPrometheusConfiguration() {
        return null;
    }

    private class TestMessageRouter implements MessageRouter<MessageBatch> {

        @Override
        public void init(@NotNull ConnectionManager connectionManager, @NotNull MessageRouterConfiguration configuration) {

        }

        @Override
        public @Nullable SubscriberMonitor subscribe(MessageListener<MessageBatch> callback, String... queueAttr) {
            return null;
        }

        @Override
        public @Nullable SubscriberMonitor subscribeAll(MessageListener<MessageBatch> callback) {
            return null;
        }

        @Override
        public @Nullable SubscriberMonitor subscribeAll(MessageListener<MessageBatch> callback, String... queueAttr) {
            return null;
        }

        @Override
        public void send(MessageBatch message) throws IOException {
            consumers.forEach(it -> it.accept(message));
        }

        @Override
        public void send(MessageBatch message, String... queueAttr) throws IOException {
            send(message);
        }

        @Override
        public void sendAll(MessageBatch message, String... queueAttr) throws IOException {
            send(message);
        }

        @Override
        public void close() throws Exception {

        }
    }
}
