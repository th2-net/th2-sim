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
package com.exactpro.th2.sim.util;

import com.exactpro.th2.common.grpc.Event;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.grpc.EventID;
import com.exactpro.th2.common.message.MessageUtils;
import com.exactpro.th2.common.schema.message.MessageRouter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EventUtils {
    private static final Logger logger = LoggerFactory.getLogger(EventUtils.class);

    public static Event sendErrorEvent(MessageRouter<EventBatch> eventRouter, String name, EventID rootEventId, Throwable throwable) {
        var errorMessages = new HashSet<String>();

        var error = throwable;
        while (error != null) {
            errorMessages.add(ExceptionUtils.getMessage(error));
            error = error.getCause();
        }

        Event event = createEvent(name, errorMessages, rootEventId);
        if (event != null) {
            try {
                eventRouter.send(EventBatch.newBuilder().addEvents(event).build());
            } catch (IOException e) {
                logger.error("Can not send event = {}", MessageUtils.toJson(event), e);
                return null;
            }
        }
        return event;
    }

    @Nullable
    public static Event createEvent(String name, String body, EventID rootEventId) {
        if (body != null) {
            return createEvent(name, Collections.singleton(body), rootEventId);
        }
        return createEvent(name, Collections.emptySet(), rootEventId);
    }

    @Nullable
    public static Event createEvent(String name, @NotNull Set<String> body, EventID rootEventId) {
        try {
            var result = com.exactpro.th2.common.event.Event.start()
                    .endTimestamp()
                    .name(name)
                    .description(Instant.now().toString())
                    .type("event")
                    .status(com.exactpro.th2.common.event.Event.Status.PASSED);

            body.forEach(bodyText -> result.bodyData(com.exactpro.th2.common.event.EventUtils.createMessageBean(bodyText)));

            return result.toProto(rootEventId);
        } catch (IOException e) {
            logger.error("Can not create event for router with name '{}', body '{}' and rootEventId = {}", name, body, rootEventId, e);
        }

        return null;
    }

    public static Event sendEvent(MessageRouter<EventBatch> eventRouter, String name, String body, EventID rootEventId) {
        logger.info(name);

        Event event = createEvent(name, body, rootEventId);
        if (event != null) {
            try {
                eventRouter.send(EventBatch.newBuilder().addEvents(event).build());
            } catch (IOException e) {
                logger.error("Can not send event = {}", MessageUtils.toJson(event), e);
                return null;
            }
        }
        return event;
    }
}
