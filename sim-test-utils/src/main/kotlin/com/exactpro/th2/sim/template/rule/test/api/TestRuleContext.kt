package com.exactpro.th2.sim.template.rule.test.api

import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import com.exactpro.th2.sim.rule.action.IAction
import com.exactpro.th2.sim.rule.action.ICancellable
import com.exactpro.th2.sim.rule.action.impl.ActionRunner
import com.exactpro.th2.sim.rule.action.impl.MessageSender
import mu.KotlinLogging
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.fail
import java.time.Duration
import java.util.Deque
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class TestRuleContext : IRuleContext {

    private val messageSender = MessageSender(this::send, this::send)

    private val cancellables: Deque<ICancellable> = ConcurrentLinkedDeque()
    private val scheduledExecutorService: ScheduledExecutorService = Executors.newScheduledThreadPool(5)

    private val results: Queue<Any> = LinkedList()

    override fun send(msg: Message) {
        results.add(msg)
        logger.debug { "Message sent: $msg" }
    }

    override fun send(batch: MessageBatch) {
        results.add(batch)
        logger.debug { "Batch sent: $batch" }
    }

    override fun send(msg: Message, delay: Long, timeUnit: TimeUnit) {
        scheduledExecutorService.schedule({
            send(msg)
        }, delay, timeUnit)
    }

    override fun send(batch: MessageBatch, delay: Long, timeUnit: TimeUnit) {
        scheduledExecutorService.schedule({
            send(batch)
        }, delay, timeUnit)
    }

    override fun execute(action: IAction): ICancellable = registerCancellable(ActionRunner(scheduledExecutorService,  messageSender, action))

    override fun execute(delay: Long, action: IAction): ICancellable = registerCancellable(ActionRunner(scheduledExecutorService, messageSender, delay, action))

    override fun execute(delay: Long, period: Long, action: IAction): ICancellable  = registerCancellable(ActionRunner(scheduledExecutorService, messageSender, delay, period, action))

    override fun getRootEventId(): String {
        return "testEventID"
    }

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

    private fun registerCancellable(cancellable: ICancellable): ICancellable  = cancellable.apply(cancellables::add)

    fun IRule.assertNotTriggered(testMessage: Message, failedMessage: String? = null) {
        if (checkTriggered(testMessage)) {
            fail { "${buildPrefix(failedMessage)}Rule ${this.javaClass.simpleName} expected: <not triggered> but was: <triggered>" }
        }
        logger.debug { "Rule ${this.javaClass.name} was not triggered" }
    }

    fun IRule.assertTriggered(testMessage: Message, failedMessage: String? = null, duration: Duration = Duration.ZERO) {
        if (!checkTriggered(testMessage)) {
            fail { "${buildPrefix(failedMessage)}Rule ${this::class.simpleName} expected: <triggered> but was: <not triggered>" }
        }
        handle(this@TestRuleContext, testMessage)
        Thread.sleep(duration.toMillis())
        removeRule()
        logger.debug { "Rule ${this.javaClass.name} was successfully triggered after $duration delay" }
    }

    fun IRule.touch(args: Map<String, String>, duration: Duration = Duration.ZERO) {
        this.touch(this@TestRuleContext, args)
        Thread.sleep(duration.toMillis())
        logger.debug { "Rule ${this.javaClass.name} was successfully touched after $duration delay" }
    }

    fun assertNothingSent(failedMessage: String? = null) {
        results.peek()?.let { actual ->
            fail { "${buildPrefix(failedMessage)}Rule ${this.javaClass.simpleName} expected: <Nothing> but was: <${actual::class.simpleName}>" }
        }
        logger.debug { "Rule ${this.javaClass.name}: nothing was sent" }
    }

    fun assertSent(expected: Any, failedMessage: String? = null) {
        assertSent(expected::class.java) { actual: Any ->
            when (expected) {
                is Message -> assertEqualsMessages(expected, actual as Message) {failedMessage}
                is MessageBatch -> assertEqualsBatches(expected, actual as MessageBatch) {failedMessage}
                is Event -> Assertions.assertEquals(expected, actual as Event) {failedMessage}
            }
        }
    }

    fun <T> assertSent(expectedType: Class<T>, testCase: (T) -> Unit) {
        val actual = results.peek()
        Assertions.assertNotNull(actual) {"Nothing was sent from rule"}

        if (expectedType::class.isInstance(actual)) {
            fail { "Rule ${this.javaClass.simpleName} expected: <${expectedType.simpleName}> but was: <${actual::class.simpleName}>" }
        }

        testCase(actual as T)

        logger.debug { "Rule ${this.javaClass.name}: Message was successfully handled" }
        results.poll()
    }

    fun test(block: TestRuleContext.() -> Unit) = block().apply {
        results.clear()
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

fun testRule(block: TestRuleContext.() -> Unit) = TestRuleContext().apply {
    test(block)
}