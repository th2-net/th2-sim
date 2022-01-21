package com.exactpro.th2.sim

import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.event.EventUtils.createMessageBean
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.message.getString
import com.exactpro.th2.common.message.message
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.plusAssign
import com.exactpro.th2.common.message.sessionAlias
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.sim.configuration.RuleConfiguration
import com.exactpro.th2.sim.impl.SimulatorRuleInfo
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import com.google.protobuf.ByteString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor

class SimRuleInfoTest {

    @Test
    fun `send parsed`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()

        val testParsedMessage = message("SomeMessage").apply {
            metadataBuilder.protocol = "TestProtocol"
            addField("SomeField", "SomeValue")
        }.build()

        fun MessageGroupBatch.check() {
            val message = this.getGroups(0).getMessages(0).message
            Assertions.assertEquals("SomeMessage", message.messageType)
            Assertions.assertEquals(testAlias, message.sessionAlias)
            Assertions.assertEquals("TestProtocol", message.metadata.protocol)
            Assertions.assertEquals("SomeValue", message.getString("SomeField"))
        }

        SimulatorRuleInfo(0, EmptyTestRule, false, ruleConfiguration, batchRouter, eventRouter, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->

            simulatorRuleInfo.send(testParsedMessage)
            verify(batchRouter, times(1)).sendAll(check(MessageGroupBatch::check), check { Assertions.assertEquals("second", it) })

            reset(batchRouter)

            simulatorRuleInfo.send(MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setMessage(testParsedMessage).build()).build())
            verify(batchRouter, times(1)).sendAll(check(MessageGroupBatch::check), check { Assertions.assertEquals("second", it) })
        }
    }

    @Test
    fun `send rawMessage`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()

        val testRawMessage = RawMessage.newBuilder().apply {
            metadataBuilder.protocol = "TestProtocol"
            body = ByteString.copyFrom("test data".toByteArray())
        }.build()

        fun MessageGroupBatch.check() {
            val message = this.getGroups(0).getMessages(0).rawMessage
            Assertions.assertEquals(testAlias, message.sessionAlias)
            Assertions.assertEquals("TestProtocol", message.metadata.protocol)
            Assertions.assertEquals(testRawMessage.body, message.body)
        }

        SimulatorRuleInfo(0, EmptyTestRule, false, ruleConfiguration, batchRouter, eventRouter, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->

            simulatorRuleInfo.send(testRawMessage)
            verify(batchRouter, times(1)).sendAll(check(MessageGroupBatch::check), check { Assertions.assertEquals("second", it) })

            reset(batchRouter)

            simulatorRuleInfo.send(MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setRawMessage(testRawMessage).build()).build())
            verify(batchRouter, times(1)).sendAll(check(MessageGroupBatch::check), check { Assertions.assertEquals("second", it) })
        }
    }

    @Test
    fun `send group`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()

        val testParsedMessage = message("SomeMessage").apply {
            metadataBuilder.protocol = "TestProtocol"
            addField("SomeField", "SomeValue")
        }.build()

        val testRawMessage = RawMessage.newBuilder().apply {
            metadataBuilder.protocol = "TestProtocol"
            body = ByteString.copyFrom("test data".toByteArray())
        }.build()


        fun MessageGroupBatch.check() {
            val parsed = this.getGroups(0).getMessages(0).message
            Assertions.assertEquals("SomeMessage", parsed.messageType)
            Assertions.assertEquals(testAlias, parsed.sessionAlias)
            Assertions.assertEquals("TestProtocol", parsed.metadata.protocol)
            Assertions.assertEquals("SomeValue", parsed.getString("SomeField"))

            val raw = this.getGroups(0).getMessages(1).rawMessage
            Assertions.assertEquals(testAlias, raw.sessionAlias)
            Assertions.assertEquals("TestProtocol", raw.metadata.protocol)
            Assertions.assertEquals(testRawMessage.body, raw.body)
        }

        SimulatorRuleInfo(0, EmptyTestRule, false, ruleConfiguration, batchRouter, eventRouter, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->
            val group = MessageGroup.newBuilder()
            group += testParsedMessage
            group += testRawMessage
            simulatorRuleInfo.send(group.build())
            verify(batchRouter, times(1)).sendAll(check(MessageGroupBatch::check), check { Assertions.assertEquals("second", it) })
        }
    }

    @Test
    fun `send event`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()

        val resultEvent = Event.start()
            .endTimestamp()
            .name("SomeName")
            .description(Instant.now().toString())
            .type("event")
            .status(Event.Status.PASSED)

        resultEvent.bodyData(createMessageBean("SomeBody"))

        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertTrue(event.name.startsWith("SomeName"))
            Assertions.assertTrue(event.body.toStringUtf8().endsWith("""{"data":"SomeBody","type":"message"}]"""))
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        SimulatorRuleInfo(0, EmptyTestRule, false, ruleConfiguration, batchRouter, eventRouter, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->
            simulatorRuleInfo.sendEvent(resultEvent)
            verify(eventRouter, times(1)).send(check(EventBatch::check))
        }
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(this::class.java)

        private const val rootEventId = "12345"
        private const val testAlias = "TestAlias"
        private val ruleConfiguration =  RuleConfiguration().apply { this.sessionAlias = testAlias }

        private var scheduler: ScheduledExecutorService = ScheduledThreadPoolExecutor(1)
    }

    private object EmptyTestRule : IRule {
        override fun checkTriggered(message: Message) = true
        override fun handle(ruleContext: IRuleContext, message: Message) = Unit
        override fun touch(ruleContext: IRuleContext, args: MutableMap<String, String>) = Unit
    }
}

