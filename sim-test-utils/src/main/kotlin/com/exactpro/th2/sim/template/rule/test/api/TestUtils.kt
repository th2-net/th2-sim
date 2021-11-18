package com.exactpro.th2.sim.template.rule.test.api

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.grpc.Value
import com.exactpro.th2.common.message.get
import com.exactpro.th2.common.message.getDouble
import com.exactpro.th2.common.message.getField
import com.exactpro.th2.common.message.getInt
import com.exactpro.th2.common.message.getList
import com.exactpro.th2.common.message.getMessage
import com.exactpro.th2.common.message.getString
import com.exactpro.th2.common.message.messageType

import com.google.protobuf.TextFormat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.fail
import org.junit.platform.commons.util.StringUtils
import org.opentest4j.AssertionFailedError
import java.lang.RuntimeException

fun assertEqualsBatches(expected: MessageBatch, actual: MessageBatch, lazyMessage: () -> String? = {null}) {
    Assertions.assertEquals(expected.messagesCount, actual.messagesCount) {"wrong count of messages in batch: \n${TextFormat.shortDebugString(actual)}"}
    expected.messagesList.forEachIndexed { i, message ->
        try {
            assertEqualsMessages(message, actual.messagesList[i], lazyMessage)
        } catch (e: AssertionFailedError) {
            throw AssertionFailedError(
                "Error in message from batch with index '$i'.\n${e.message}",
                e.expected,
                e.actual,
                e.cause
            )
        }

    }
}

fun assertEqualsMessages(expected: Message, actual: Message, lazyMessage: () -> String? = {null}) {
    val assertExpected = expected.toBuilder().apply {
        metadataBuilder.timestampBuilder.resetTimestamp()
    }.build()
    val assertActual = actual.toBuilder().apply {
        metadataBuilder.timestampBuilder.resetTimestamp()
    }.build()
    try {
        Assertions.assertEquals(assertExpected, assertActual, lazyMessage)
    } catch (e: AssertionFailedError) {
        throw AssertionFailedError(
            "Error in message with type '${actual.messageType}'.\n${e.message}",
            e.expected,
            e.actual,
            e.cause
        )
    }
}

fun buildPrefix(message: String?): String {
    return if (StringUtils.isNotBlank(message)) "$message ==> " else ""
}

fun com.google.protobuf.Timestamp.Builder.resetTimestamp() {
    nanos = 0
    seconds = 0
}

fun Message.assertContain(vararg name: String, errorMessage: String? = null) {
    name.forEach { fieldName ->
        if (!this.containsFields(fieldName)) {
            fail { "${buildPrefix(errorMessage)}${this.messageType} must contain $fieldName" }
        }
    }
}

fun Message.assertNotContain(vararg name: String, errorMessage: String? = null) {
    name.forEach { fieldName ->
        if (this.containsFields(fieldName)) {
            fail { "${buildPrefix(errorMessage)}${this.messageType} must not contain $fieldName" }
        }
    }
}

fun Message.assertField(name: String): Value {
    this.assertContain(name)
    return this.getField(name)!!
}

fun Message.assertMessage(name: String): Message {
    this.assertContain(name)
    return this.getMessage(name)!!
}

fun Message.assertInt(name: String, expected: Int? = null): Int {
    this.assertContain(name)
    val actual = this.getInt(name)!!
    expected?.let {
        Assertions.assertEquals(expected, actual) {"Field value was different"}
    }
    return actual
}

fun Message.assertList(name: String, expected: List<Value> ? = null): List<Value> {
    this.assertContain(name)
    val actual = this.getList(name)!!
    expected?.let {
        Assertions.assertEquals(expected, actual)  {"Field value was different"}
    }
    return actual
}

fun Message.assertString(name: String, expected: String? = null): String {
    this.assertContain(name)
    val actual = this.getString(name)!!
    expected?.let {
        Assertions.assertEquals(expected, actual) {"Field value was different"}
    }
    return actual
}

fun Message.assertDouble(name: String, expected: Double? = null): Double {
    this.assertContain(name)
    val actual = this.getDouble(name)!!
    expected?.let {
        Assertions.assertEquals(expected, actual) {"Field value was different"}
    }
    return actual
}

fun <T> Message.assertValue(name: String, expected: T? = null): T {
    this.assertContain(name)
    val actual = when (expected) {
        is Int -> this.getInt(name)
        is Double -> this.getDouble(name)
        is List<*> -> this.getList(name)
        is String -> this.getString(name)
        null -> this[name]
        else -> throw RuntimeException("This type for assertion of field value is not supported")
    }!!
    expected?.let {
        Assertions.assertEquals(expected, actual) {"Field value was different"}
    } ?: Assertions.assertNull(actual) {"Field value wasn't null"}
    return actual as T
}

