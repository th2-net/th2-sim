package com.exactpro.th2.sim.util;

import com.exactpro.th2.common.grpc.Event;
import com.exactpro.th2.common.grpc.EventBatch;
import com.exactpro.th2.common.message.MessageUtils;
import com.exactpro.th2.common.schema.message.MessageRouter;
import com.fasterxml.jackson.core.JsonProcessingException;
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

    public static Event sendErrorEvent(MessageRouter<EventBatch> eventRouter, String name, String rootEventId, Throwable throwable) {
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
    public static Event createEvent(String name, String body, String rootEventId) {
        if (body != null) {
            return createEvent(name, Collections.singleton(body), rootEventId);
        }
        return createEvent(name, Collections.emptySet(), rootEventId);
    }

    @Nullable
    public static Event createEvent(String name, @NotNull Set<String> body, String rootEventId) {
        try {
            var result = com.exactpro.th2.common.event.Event.start()
                    .endTimestamp()
                    .name(name)
                    .description(Instant.now().toString())
                    .type("event")
                    .status(com.exactpro.th2.common.event.Event.Status.PASSED);

            body.forEach(bodyText -> result.bodyData(com.exactpro.th2.common.event.EventUtils.createMessageBean(bodyText)));

            return result.toProtoEvent(rootEventId);
        } catch (JsonProcessingException e) {
            logger.error("Can not create event for router with name '{}', body '{}' and rootEventId = {}", name, body, rootEventId, e);
        }

        return null;
    }

    public static Event sendEvent(MessageRouter<EventBatch> eventRouter, String name, String body, String rootEventId) {
        logger.info(name);
        return sendEvent(eventRouter, createEvent(name, body, rootEventId));
    }

    public static Event sendEvent(MessageRouter<EventBatch> eventRouter, String name, @NotNull Set<String> body, String rootEventId) {
        logger.info(name);
        return sendEvent(eventRouter, createEvent(name, body, rootEventId));
    }

    public static Event sendEvent(MessageRouter<EventBatch> eventRouter, Event event) {
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
