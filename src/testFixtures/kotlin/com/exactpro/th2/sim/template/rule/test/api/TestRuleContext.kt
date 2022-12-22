/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.common.assertEqualGroups
import com.exactpro.th2.common.assertEqualMessages
import com.exactpro.th2.common.buildPrefix
import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.event.EventUtils.toEventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import com.exactpro.th2.sim.rule.action.IAction
import com.exactpro.th2.sim.rule.action.ICancellable
import com.exactpro.th2.sim.rule.action.impl.ActionRunner
import com.exactpro.th2.sim.rule.action.impl.MessageSender
import com.google.protobuf.TextFormat
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.fail
import java.time.Duration
import java.util.*
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
    private val scheduledExecutorService: ScheduledExecutorService =  Executors.newScheduledThreadPool(1)

    private val results: Queue<Any> = LinkedList()

    override fun send(msg: Message) {
        results.add(msg)
        logger.debug { "Parsed message sent: ${TextFormat.shortDebugString(msg)}" }
    }

    override fun send(msg: RawMessage) {
        results.add(msg)
        logger.debug { "Raw message sent: ${TextFormat.shortDebugString(msg)}" }
    }

    override fun send(group: MessageGroup) {
        results.add(group)
        logger.debug { "Group sent: ${TextFormat.shortDebugString(group)}" }
    }

    override fun send(batch: MessageBatch) {
        results.add(batch)
        logger.debug { "Batch sent: ${TextFormat.shortDebugString(batch)}" }
    }

    override fun send(msg: Message, delay: Long, timeUnit: TimeUnit) {
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

    override fun send(batch: MessageBatch, delay: Long, timeUnit: TimeUnit) {
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, timeUnit.toMillis(delay) / speedUp) {
            send(batch)
        })
    }

    override fun execute(action: IAction): ICancellable =
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, action))

    override fun execute(delay: Long, action: IAction): ICancellable =
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, delay / speedUp, action))

    override fun execute(delay: Long, period: Long, action: IAction): ICancellable =
        registerCancellable(ActionRunner(scheduledExecutorService, messageSender, delay / speedUp, period / speedUp, action))

    override fun getRootEventId() = toEventID("testEventID")

    override fun sendEvent(event: Event) {
        results.add(event)
        logger.debug { "Event sent: $event" }
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
    fun IRule.assertNotTriggered(testMessage: Message, failureMessage: String? = null) {
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
    fun IRule.assertTriggered(testMessage: Message, failureMessage: String? = null) {
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
    fun IRule.assertHandle(testMessage: Message, duration: Duration = Duration.ZERO, failureMessage: String? = null) {
        assertTriggered(testMessage, failureMessage)
        handle(testMessage, duration)
    }

    /**
     * method to call implementation of handle inside rule and wait results after [duration]
     * @param testMessage incoming Message.
     * @param duration max expected message handling time
     */
    private fun IRule.handle(testMessage: Message, duration: Duration = Duration.ZERO) {
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
                is Message -> assertEqualMessages(expected, actual as Message) { failureMessage }
                is RawMessage -> assertEqualMessages(expected, actual as RawMessage) { failureMessage }
                is MessageGroup -> assertEqualGroups(expected, actual as MessageGroup) { failureMessage }
                is Event -> Assertions.assertEquals(expected, actual as Event) { failureMessage }
                else -> fail {"Unsupported format of expecting sent data: $expected"}
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

        testCase(actual as T)

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

