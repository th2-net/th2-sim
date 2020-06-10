/*******************************************************************************
 *  Copyright 2020 Exactpro (Exactpro Systems Limited)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/
package com.exactpro.th2.simulator.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.exactpro.th2.infra.grpc.ListValue;
import com.exactpro.th2.infra.grpc.ListValue.Builder;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.infra.grpc.NullValue;
import com.exactpro.th2.infra.grpc.Value;
import com.exactpro.th2.infra.grpc.Value.KindCase;

/**
 * Class utils for work with {@link Value}
 */
public class ValueUtils {

    /**
     * @return null in {@link Value} format
     * @see Value.KindCase#NULL_VALUE
     */
    public static Value nullValue() {
        return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    }

    /**
     * Convert object to {@link Value}
     * <p>
     * If value is null return {@link Value} with kind case equals {@link Value.KindCase#NULL_VALUE}.
     * <p>
     * If value isn't {@link String}, {@link Message} or {@link Iterable}, use {@link Object#toString()} and create simple {@link Value}
     * @param value Object for transform to {@link Value}
     * @return {@link Value} from object's value
     * @see Value.KindCase#SIMPLE_VALUE
     * @see Value.KindCase#MESSAGE_VALUE
     * @see Value.KindCase#LIST_VALUE
     * @see Value.KindCase#NULL_VALUE
     */
    public static Value getValue(Object value) {
        if (value == null) {
            return nullValue();
        }

        if (value instanceof String) {
            return Value.newBuilder().setSimpleValue((String)value).build();
        }

        if (value instanceof Message) {
            return Value.newBuilder().setMessageValue((Message)value).build();
        }

        if (value instanceof Iterable<?>) {
            return Value.newBuilder().setListValue(getListValue((Iterable<?>)value)).build();
        }

        return Value.newBuilder().setSimpleValue(value.toString()).build();
    }

    /**
     * Create {@link ListValue} from value
     * @param value
     * @return {@link ListValue} created from {@link Iterable}
     */
    public static ListValue getListValue(@NotNull Iterable<?> value) {
        Builder result = ListValue.newBuilder();
        for (Object obj : value) {
            result.addValues(getValue(obj));
        }
        return result.build();
    }

    /**
     * Get {@link String} value from {@link Value}
     * @param value field value
     * @return If value is {@link Value.KindCase#SIMPLE_VALUE}, returns {@link String} else return null
     */
    @Nullable
    public static String toSimpleValue(@NotNull Value value) {
        return value.getKindCase() == KindCase.SIMPLE_VALUE ? value.getSimpleValue() : null;
    }

    /**
     * Get {@link Integer} value from {@link Value}
     * @param value field value
     * @return If value is {@link Value.KindCase#SIMPLE_VALUE} and can parse to {@link Integer}, returns {@link Integer} else return null
     */
    @Nullable
    public static Integer toIntegerValue(@NotNull Value value) {
        try {
            String str = toSimpleValue(value);
            return str == null ? null : Integer.parseInt(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get {@link Long} value from {@link Value}
     * @param value field value
     * @return If value is {@link Value.KindCase#SIMPLE_VALUE} and can parse to {@link Long}, returns {@link Long} else return null
     */
    @Nullable
    public static Long toLongValue(@NotNull Value value) {
        try {
            String str = toSimpleValue(value);
            return str == null ? null : Long.parseLong(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get {@link BigDecimal} value from {@link Value}
     * @param value field value
     * @return If value is {@link Value.KindCase#SIMPLE_VALUE} and can parse to {@link BigDecimal}, returns {@link BigDecimal} else return null
     */
    @Nullable
    public static BigDecimal toBigDecimal(@NotNull Value value) {
        try {
            String str = toSimpleValue(value);
            return str == null ? null : new BigDecimal(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get {@link Message} value from {@link Value}
     * @param value field value
     * @return If value is {@link Value.KindCase#MESSAGE_VALUE}, returns {@link Message} else return null
     */
    @Nullable
    public static Message toMessageValue(@NotNull Value value) {
        return value.getKindCase() == KindCase.MESSAGE_VALUE ? value.getMessageValue() : null;
    }

    /**
     * Get {@link ListValue} value from {@link Value}
     * @param value field value
     * @return If value is {@link Value.KindCase#LIST_VALUE}, returns {@link ListValue} else return null
     */
    @Nullable
    public static ListValue toListValue(@NotNull Value value) {
        return value.getKindCase() == KindCase.LIST_VALUE ? value.getListValue() : null;
    }

    /**
     * Transform {@link ListValue} to {@link List} with help transform function
     * @param value {@link ListValue}
     * @param transformFunction function for transform {@link Value} from {@link ListValue}
     * @param <T> type after transformation
     * @return {@link List} with objects, which have type {@code T}
     */
    public static <T> List<T> transformListValue(@NotNull ListValue value, Function<Value, T> transformFunction) {
        return value.getValuesList().stream().map(transformFunction).collect(Collectors.toList());
    }
}
