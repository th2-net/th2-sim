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

import org.jetbrains.annotations.Nullable;

import com.exactpro.th2.infra.grpc.ListValue;
import com.exactpro.th2.infra.grpc.ListValue.Builder;
import com.exactpro.th2.infra.grpc.Message;
import com.exactpro.th2.infra.grpc.NullValue;
import com.exactpro.th2.infra.grpc.Value;
import com.exactpro.th2.infra.grpc.Value.KindCase;

/**
 * Class for work with {@link Value}
 */
public class ValueUtils {

    /**
     * @return null in {@link Value} format
     */
    public static Value nullValue() {
        return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
    }

    /**
     * Convert object to {@link Value}
     * If {@link Value.KindCase} haven't object's type, use {@link Object#toString()} and create simple value
     * @param value
     * @return {@link Value} from object's value
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
     * @param value
     * @return {@link ListValue} created from {@link Iterable}
     */
    public static ListValue getListValue(Iterable<?> value) {
        Builder result = ListValue.newBuilder();
        for (Object obj : value) {
            result.addValues(getValue(obj));
        }
        return result.build();
    }

    @Nullable
    public static String toSimpleValue(Value value) {
        return value.getKindCase() == KindCase.SIMPLE_VALUE ? value.getSimpleValue() : null;
    }

    @Nullable
    public static Message toMessageValue(Value value) {
        return value.getKindCase() == KindCase.MESSAGE_VALUE ? value.getMessageValue() : null;
    }

    @Nullable
    public static ListValue toListValue(Value value) {
        return value.getKindCase() == KindCase.LIST_VALUE ? value.getListValue() : null;
    }

}
