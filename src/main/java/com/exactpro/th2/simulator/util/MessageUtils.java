/******************************************************************************
 * Copyright 2020 Exactpro (Exactpro Systems Limited)
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
 ******************************************************************************/
package com.exactpro.th2.simulator.util;

import java.util.Map;
import java.util.Map.Entry;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.exactpro.th2.infra.grpc.ListValue;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.infra.grpc.Message.Builder;
import com.exactpro.th2.infra.grpc.Value;
import com.exactpro.th2.infra.grpc.Value.KindCase;

/**
 * Util class for work with {@link Message} and {@link Message.Builder}
 */
public class MessageUtils {

    /**
     * Create {@link Value} with simple value and put to message's builder's field with key.
     * @param builder
     * @param key filed's key
     * @param value field's value
     * @return builder
     * @see ValueUtils#getValue(Object)
     */
    @NotNull
    public static Message.Builder putField(@NotNull Message.Builder builder, @NotNull String key, Object value) {
        return builder.putFields(key, ValueUtils.getValue(value));
    }

    /**
     * Put fields to message's builder
     * @param builder
     * @param fields map with field's key and value
     * @return builder
     * @see MessageUtils#putField
     */
    @NotNull
    public static Message.Builder putFields(@NotNull Message.Builder builder, @NotNull Map<String, Object> fields) {
        for (Entry<String, Object> entry :  fields.entrySet()) {
            putField(builder, entry.getKey(), entry.getValue());
        }
        return builder;
    }

    /**
     * Put fields to message's builder
     * @param builder
     * @param fields array with field's pair key and value
     * @return builder
     * @see MessageUtils#putField
     */
    @NotNull
    public static Message.Builder putFields(@NotNull Message.Builder builder, Object... fields) {
        for (int i = 0; i < fields.length - 1; i += 2) {
            if (fields[i] instanceof String) {
                putField(builder, (String) fields[i], fields[i + 1]);
            }
        }
        return builder;
    }

    /**
     * Copy field from message with key to message's builder
     * @param builder
     * @param key field's key
     * @param message
     * @return builder
     */
    @NotNull
    public static Message.Builder copyField(@NotNull Message.Builder builder, @NotNull String key, @NotNull Message message) {
        Value value = message.getFieldsOrDefault(key, null);
        if (value != null) {
            builder.putFields(key, value);
        }
        return builder;
    }

    /**
     * Copy field from message's builder with key to  another message's builder
     * @param builder
     * @param key field's key
     * @param message builder
     * @return builder
     */
    @NotNull
    public static Message.Builder copyField(@NotNull Message.Builder builder, @NotNull String key, @NotNull Message.Builder message) {
        Value value = message.getFieldsOrDefault(key, null);
        if (value != null) {
            builder.putFields(key, value);
        }
        return builder;
    }

    /**
     * Copy field's from message with key to message's builder
     * @param builder
     * @param keys field's keys
     * @param message
     * @return builder
     * @see MessageUtils#copyField(Builder, String, Message)
     */
    @NotNull
    public static Message.Builder copyFields(@NotNull Message.Builder builder, @NotNull Iterable<String> keys, @NotNull Message message) {
        for (String key : keys) {
            copyField(builder, key, message);
        }
        return builder;
    }

    /**
     * Copy field's from message with key to message's builder
     * @param builder
     * @param keys field's keys
     * @param message
     * @return builder
     * @see MessageUtils#copyField(Builder, String, Message)
     */
    @NotNull
    public static Message.Builder copyFields(@NotNull Message.Builder builder, @NotNull Message message, String... keys) {
        for (String key : keys) {
            copyField(builder, key, message);
        }
        return builder;
    }

    /**
     * Copy field's from message's builder with key to message's builder
     * @param builder
     * @param keys field's keys
     * @param message builder
     * @return builder
     * @see MessageUtils#copyField(Builder, String, Builder)
     */
    @NotNull
    public static Message.Builder copyFields(@NotNull Message.Builder builder, @NotNull Iterable<String> keys, @NotNull Message.Builder message) {
        for (String key : keys) {
            copyField(builder, key, message);
        }
        return builder;
    }

    /**
     * Copy field's from message's builder with key to message's builder
     * @param builder
     * @param keys field's keys
     * @param message builder
     * @return builder
     * @see MessageUtils#copyField(Builder, String, Builder)
     */
    @NotNull
    public static Message.Builder copyFields(@NotNull Message.Builder builder, @NotNull Message.Builder message, String... keys) {
        for (String key : keys) {
            copyField(builder, key, message);
        }
        return builder;
    }

    /**
     * Get field's value from message with key with simple type.
     * @param message
     * @param key field's key
     * @return Null, if message haven't field or field haven't simple type.
     */
    @Nullable
    public static String getSimpleField(@NotNull Message message, @NotNull String key) {
        Value field = message.getFieldsOrDefault(key, null);
        if (field == null || field.getKindCase() != KindCase.SIMPLE_VALUE)  {
            return null;
        }

        return field.getSimpleValue();
    }

    /**
     * Get field's value from message with key with message type.
     * @param message
     * @param key field's key
     * @return Null, if message haven't field or field haven't message type.
     */
    @Nullable
    public static Message getMessageField(@NotNull Message message, @NotNull String key) {
        Value field = message.getFieldsOrDefault(key, null);
        if (field == null || field.getKindCase() != KindCase.MESSAGE_VALUE)  {
            return null;
        }

        return field.getMessageValue();
    }

    /**
     * Get field's value from message with key with list type.
     * @param message
     * @param key field's key
     * @return Null, if message haven't field or field haven't list type.
     */
    @Nullable
    public static ListValue getListField(@NotNull Message message, @NotNull String key) {
        Value field = message.getFieldsOrDefault(key, null);
        if (field == null || field.getKindCase() != KindCase.LIST_VALUE)  {
            return null;
        }

        return field.getListValue();
    }

}
