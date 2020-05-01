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

import com.exactpro.evolution.api.phase_1.ListValue;
import com.exactpro.evolution.api.phase_1.ListValue.Builder;
import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.api.phase_1.NullValue;
import com.exactpro.evolution.api.phase_1.Value;

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

}
