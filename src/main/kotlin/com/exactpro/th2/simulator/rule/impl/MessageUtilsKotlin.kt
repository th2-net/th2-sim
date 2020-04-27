/******************************************************************************
 * Copyright 2009-2020 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.simulator.rule.impl

import com.exactpro.evolution.api.phase_1.ListValue
import com.exactpro.evolution.api.phase_1.Message
import com.exactpro.evolution.api.phase_1.NullValue.NULL_VALUE
import com.exactpro.evolution.api.phase_1.Value
import java.util.Objects

fun String.toValue() : Value {
    return Value.newBuilder().setSimpleValue(this).build()
}

fun Message.toValue() : Value {
    return Value.newBuilder().setMessageValue(this).build()
}

fun Message.getString(key: String) : String? {
    return try {
        this.getFieldsOrThrow(key).simpleValue
    } catch (e: Exception) {
        null
    }
}

fun Message.Builder.addField(key: String, value: String?): Message.Builder {
    return if (!value.isNullOrEmpty()) {
        this.putFields(key, Value.newBuilder().setSimpleValue(value).build())
    } else {
        this
    }
}

fun Message.Builder.copyField(key: String, message: Message): Message.Builder {
    return try {
        this.putFields(key, message.getFieldsOrThrow(key))
    } catch (e: Exception) {
        this
    }
}

fun Message.Builder.addField(key: String, value: Message): Message.Builder  {
    return this.putFields(key, Value.newBuilder().setMessageValue(value).build())
}

fun MutableList<*>.toValueList() : ListValue {

    return ListValue.newBuilder().addAllValues(this.map {
        if (it is String) {
            Value.newBuilder().setSimpleValue(it).build()
        }
        if (it is MutableList<*>) {
            Value.newBuilder().setListValue(it.toValueList()).build()
        }
        if (it is Message) {
            Value.newBuilder().setMessageValue(it).build()
        }
        Value.newBuilder().setNullValue(NULL_VALUE).build()
    }.filter { Objects.nonNull(it) }).build()
}

fun Message.Builder.addField(key: String, value: MutableList<*>) {
    this.putFields(key, Value.newBuilder().setListValue(value.toValueList()).build())
}
