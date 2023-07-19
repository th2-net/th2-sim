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

package com.exactpro.th2.sim.template.rule.test.api

import com.exactpro.th2.common.buildPrefix
import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.event.EventUtils.toEventID
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.schema.box.configuration.BoxConfiguration
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage
import com.exactpro.th2.common.utils.event.toTransport
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import com.exactpro.th2.sim.rule.action.IAction
import com.exactpro.th2.sim.rule.action.ICancellable
import com.exactpro.th2.sim.rule.action.impl.ActionRunner
import com.exactpro.th2.sim.rule.action.impl.MessageSender
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
import java.time.Duration
import java.time.Instant
import java.util.Deque
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Test context class for rules
 *
 * This class has private constructor, please use testRule block for testing.
 *
 * @property speedUp the divisor of rule delay execution. If the delay after division is too short, it can lead to sequencing problems.
 * @constructor Creates a rule context for tests.
 */
class TestRuleContext private constructor(private val speedUp: Int, val shutdownTimeout: Long) : IRuleContext, AutoCloseable {
    private val messageSender = MessageSender(this::send, this::send, this::send)

    private val cancellables: Deque<ICancellable> = ConcurrentLinkedDeque()
    private val scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(1)

    private val results: Queue<Any> = LinkedList()

    override fun send(msg: ParsedMessage) {
        results.add(msg)
        logger.debug { "Parsed message sent: $msg" }
    }

    override fun send(msg: ParsedMessage.FromMapBuilder) {
        results.add(msg)
        logger.debug { "Parsed message builder sent: $msg" }
    }

    override fun send(msg: RawMessage) {
        results.add(msg)
        logger.debug { "Raw message sent: $msg" }
    }

    override fun send(msg: RawMessage.Builder) {
        results.add(msg)
        logger.debug { "Raw message builder sent: $msg" }
    }

    override fun send(group: MessageGroup) {
        results.add(group)
        logger.debug { "Group sent: $group" }
    }

    @Deprecated("Deprecated in Java")
    override fun send(batch: GroupBatch) {
        results.add(batch)
        logger.debug { "Batch sent: $batch" }
    }

    override fun send(msg: ParsedMessage, delay: Long, timeUnit: TimeUnit) {
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, timeUnit.toMillis(delay) / speedUp) {
            send(msg)
        })
    }

    override fun send(msg: RawMessage, delay: Long, timeUnit: TimeUnit) {
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, timeUnit.toMillis(delay) / speedUp) {
            send(msg)
        })
    }

    override fun send(group: MessageGroup, delay: Long, timeUnit: TimeUnit) {
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, timeUnit.toMillis(delay) / speedUp) {
            send(group)
        })
    }

    @Deprecated("Deprecated in Java")
    override fun send(batch: GroupBatch, delay: Long, timeUnit: TimeUnit) {
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, timeUnit.toMillis(delay) / speedUp) {
            send(batch)
        })
    }

    override fun execute(action: IAction): ICancellable =
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, action))

    override fun execute(delay: Long, action: IAction): ICancellable =
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, delay / speedUp, action))

    override fun execute(delay: Long, period: Long, action: IAction): ICancellable =
        registerCancellable(
            ActionRunner(
                scheduledExecutorService,
                messageSender,
                delay / speedUp,
                period / speedUp,
                action
            )
        )

    override fun getRootEventIdProto() = toEventID(
        Instant.now(),
        BoxConfiguration.DEFAULT_BOOK_NAME,
        "testEventID",
    )

    override fun getRootEventId() = rootEventIdProto.toTransport()

    override fun sendEvent(event: Event) {
        results.add(event)
        logger.debug { "Event sent: $event" }
    }

    override fun sendEvent(event: Event, parentId: EventID) {
        results.add(event.toProto(parentId))
        logger.debug { "Event with $parentId parent ID sent: $event" }
    }

    override fun removeRule() {
        cancellables.forEach { cancellable ->
            runCatching(cancellable::cancel).onFailure {
                logger.error(it) { "Failed to cancel sub-task of rule" }
            }
        }
        logger.debug { "Rule removed" }
    }

    private fun registerCancellable(cancellable: ICancellable): ICancellable = cancellable.apply(cancellables::add)

    /**
     * checks if rule wasn't triggered
     * @param testMessage incoming Message.
     * @param failureMessage log message on fail.
     * @return fail if rule was triggered
     */
    fun IRule.assertNotTriggered(testMessage: ParsedMessage, failureMessage: String? = null) {
        if (checkTriggered(testMessage)) {
            fail { "${buildPrefix(failureMessage)}Rule ${this::class.simpleName} expected: <not triggered> but was: <triggered>" }
        }
        logger.debug { "Rule ${this::class.simpleName} was not triggered" }
    }

    /**
     * checks if rule was triggered
     * @param testMessage incoming Message.
     * @param failureMessage log message on fail.
     * @return fail if rule was not triggered
     */
    fun IRule.assertTriggered(testMessage: ParsedMessage, failureMessage: String? = null) {
        if (!checkTriggered(testMessage)) {
            fail { "${buildPrefix(failureMessage)}Rule ${this::class.simpleName} expected: <triggered> but was: <not triggered>" }
        }
        logger.debug { "Rule ${this::class.simpleName} was triggered" }
    }

    /**
     * method to test rule message handling after checkTrigger assertion
     * @param testMessage incoming Message.
     * @param duration pause to wait result of rule handler.
     * @param failureMessage log message on fail.
     * @return fail if rule was not triggered
     */
    fun IRule.assertHandle(
        testMessage: ParsedMessage,
        duration: Duration = Duration.ZERO,
        failureMessage: String? = null
    ) {
        assertTriggered(testMessage, failureMessage)
        handle(testMessage, duration)
    }

    /**
     * method to call implementation of handle inside rule and wait results after [duration]
     * @param testMessage incoming Message.
     * @param duration max expected message handling time
     */
    private fun IRule.handle(testMessage: ParsedMessage, duration: Duration = Duration.ZERO) {
        handle(this@TestRuleContext, testMessage)
        Thread.sleep(duration.toMillis())
        removeRule()
        logger.debug { "Rule ${this::class.simpleName} was successfully handled after $duration delay" }
    }

    /**
     * method to execute rule's touch method
     * @param args incoming arguments
     * @param duration pause to wait result of touch, after delay all execution tasks will be stopped.
     */
    fun IRule.touch(args: Map<String, String>, duration: Duration = Duration.ZERO) {
        this.touch(this@TestRuleContext, args)
        Thread.sleep(duration.toMillis())
        removeRule()
        logger.debug { "Rule ${this::class.simpleName} was successfully touched after $duration delay" }
    }

    /**
     * asserts that's nothing was sent by the rule
     * @return fail if rule had results
     */
    fun assertNothingSent(failureMessage: String? = null) {
        results.peek()?.let { actual ->
            fail { "${buildPrefix(failureMessage)}Rule ${this::class.simpleName} expected: <Nothing> but was: <${actual::class.simpleName}>" }
        }
        logger.debug { "Rule ${this::class.simpleName}: nothing was sent" }
    }

    /**
     * asserts message, batch or event that was sent by the rule
     * @param expected value to assertEquals with result of rule handler
     * @param failureMessage error message on fail
     * @return fail if rule handle had different result
     */
    fun assertSent(expected: Any, failureMessage: String? = null) {
        assertSent(expected::class.java) { actual: Any ->
            when (expected) {
                is ParsedMessage -> assertEquals(expected, actual as ParsedMessage) { failureMessage }
                is ParsedMessage.FromMapBuilder -> assertEquals(
                    expected.build(),
                    (actual as ParsedMessage.FromMapBuilder).build()
                ) { failureMessage }

                is RawMessage -> assertEquals(expected, actual) { failureMessage }
                is MessageGroup -> assertEquals(expected, actual) { failureMessage }
                is Event -> assertEquals(expected, actual) { failureMessage }
                else -> fail { "Unsupported format of expecting sent data: $expected" }
            }
        }
    }

    /**
     * method to test handle results
     * @param expectedType class of expected result
     * @param testCase block to execute after type assert
     * @return fail if rule handle result had different type
     */
    fun <T> assertSent(expectedType: Class<T>, testCase: (T) -> Unit) {
        val actual = results.peek()
        Assertions.assertNotNull(actual) { "Nothing was sent from rule" }

        if (!expectedType.isInstance(actual)) {
            fail { "Rule ${this::class.simpleName} expected: <${expectedType.simpleName}> but was: <${actual::class.simpleName}>" }
        }

        testCase(expectedType.cast(actual))

        logger.debug { "Rule ${this::class.simpleName}: Message was successfully handled" }
        results.poll()
    }

    override fun close() {
        scheduledExecutorService.shutdown()
        if (!scheduledExecutorService.awaitTermination(shutdownTimeout, TimeUnit.MILLISECONDS)) {
            scheduledExecutorService.shutdownNow()
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /**
         * method to test rule inside block: testRule {}
         * all results of rule execution are persisted until end of block
         * @param speedUp param to speed up delay and period of execution
         * @param shutdownTimeout timeout of shutdown hook
         * @param block test case
         */
        fun testRule(speedUp: Int = 1, shutdownTimeout: Long = 3000, block: TestRuleContext.() -> Unit) {
            TestRuleContext(speedUp, shutdownTimeout).use {
                it.block()
            }
        }

    }


}

