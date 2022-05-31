/*
 * Copyright 2020-2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim

import com.exactpro.th2.common.event.Event
import com.exactpro.th2.common.event.EventUtils.createMessageBean
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.message.getString
import com.exactpro.th2.common.message.message
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.plusAssign
import com.exactpro.th2.common.message.sessionAlias
import com.exactpro.th2.common.utils.event.EventBatcher
import com.exactpro.th2.sim.configuration.RuleConfiguration
import com.exactpro.th2.sim.impl.SimulatorRuleInfo
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import com.exactpro.th2.sim.util.MessageBatcher
import com.google.protobuf.ByteString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
        val messageBatcher = mock<MessageBatcher>()
        val eventBatcher = mock<EventBatcher>()

        val testParsedMessage = message("SomeMessage").apply {
            metadataBuilder.protocol = "TestProtocol"
            addField("SomeField", "SomeValue")
        }.build()

        fun Message.check() {
            Assertions.assertEquals("SomeMessage", messageType)
            Assertions.assertEquals(testAlias, sessionAlias)
            Assertions.assertEquals("TestProtocol", metadata.protocol)
            Assertions.assertEquals("SomeValue", getString("SomeField"))
        }

        SimulatorRuleInfo(0, EmptyTestRule, ruleConfiguration, messageBatcher, eventBatcher, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->

            simulatorRuleInfo.send(testParsedMessage)
            verify(messageBatcher, times(1)).onMessage(check(Message::check), eq(testRelation))

            reset(messageBatcher)

            simulatorRuleInfo.send(testParsedMessage)
            verify(messageBatcher, times(1)).onMessage(check(Message::check), eq(testRelation))
        }

        verify(eventBatcher, never()).onEvent(any())
    }

    @Test
    fun `send rawMessage`() {
        val messageBatcher = mock<MessageBatcher>()
        val eventBatcher = mock<EventBatcher>()

        val testRawMessage = RawMessage.newBuilder().apply {
            metadataBuilder.protocol = "TestProtocol"
            body = ByteString.copyFrom("test data".toByteArray())
        }.build()

        fun RawMessage.check() {
            Assertions.assertEquals(testAlias, sessionAlias)
            Assertions.assertEquals("TestProtocol", metadata.protocol)
            Assertions.assertEquals(testRawMessage.body, body)
        }

        SimulatorRuleInfo(0, EmptyTestRule, ruleConfiguration, messageBatcher, eventBatcher, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->

            simulatorRuleInfo.send(testRawMessage)
            verify(messageBatcher, times(1)).onMessage(check(RawMessage::check), eq(testRelation))

            reset(messageBatcher)

            simulatorRuleInfo.send(testRawMessage)
            verify(messageBatcher, times(1)).onMessage(check(RawMessage::check), eq(testRelation))
        }

        verify(eventBatcher, never()).onEvent(any())
    }

    @Test
    fun `send group`() {
        val messageBatcher = mock<MessageBatcher>()
        val eventBatcher = mock<EventBatcher>()

        val testParsedMessage = message("SomeMessage").apply {
            metadataBuilder.protocol = "TestProtocol"
            addField("SomeField", "SomeValue")
        }.build()

        val testRawMessage = RawMessage.newBuilder().apply {
            metadataBuilder.protocol = "TestProtocol"
            body = ByteString.copyFrom("test data".toByteArray())
        }.build()


        fun MessageGroup.check() {
            val parsed = this.getMessages(0).message
            Assertions.assertEquals("SomeMessage", parsed.messageType)
            Assertions.assertEquals(testAlias, parsed.sessionAlias)
            Assertions.assertEquals("TestProtocol", parsed.metadata.protocol)
            Assertions.assertEquals("SomeValue", parsed.getString("SomeField"))

            val raw = this.getMessages(1).rawMessage
            Assertions.assertEquals(testAlias, raw.sessionAlias)
            Assertions.assertEquals("TestProtocol", raw.metadata.protocol)
            Assertions.assertEquals(testRawMessage.body, raw.body)
        }

        fun MessageGroup.checkEmptyAlias() {
            val parsed = this.getMessages(0).message
            Assertions.assertEquals("", parsed.sessionAlias)
            val raw = this.getMessages(1).rawMessage
            Assertions.assertEquals("", raw.sessionAlias)
        }

        SimulatorRuleInfo(0, EmptyTestRule, ruleConfiguration, messageBatcher, eventBatcher, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->
            val group = MessageGroup.newBuilder()
            group += testParsedMessage
            group += testRawMessage
            simulatorRuleInfo.send(group.build())
            Thread.sleep(150)
            verify(messageBatcher, times(1)).onGroup(check(MessageGroup::check), eq(testRelation))
        }

        reset(messageBatcher)

        SimulatorRuleInfo(0, EmptyTestRule, RuleConfiguration(), messageBatcher, eventBatcher, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->
            val group = MessageGroup.newBuilder()
            group += testParsedMessage
            group += testRawMessage
            simulatorRuleInfo.send(group.build())
            Thread.sleep(150)
            verify(messageBatcher, times(1)).onGroup(check(MessageGroup::checkEmptyAlias), eq("default"))
        }

        verify(eventBatcher, never()).onEvent(any())
    }

    @Test
    fun `send event`() {
        val messageBatcher = mock<MessageBatcher>()
        val eventBatcher = mock<EventBatcher>()

        val resultEvent = Event.start()
            .endTimestamp()
            .name("SomeName")
            .description(Instant.now().toString())
            .type("event")
            .status(Event.Status.PASSED)

        resultEvent.bodyData(createMessageBean("SomeBody"))

        fun com.exactpro.th2.common.grpc.Event.check() {
            Assertions.assertTrue(name.startsWith("SomeName"))
            Assertions.assertTrue(body.toStringUtf8().endsWith("""{"data":"SomeBody","type":"message"}]"""))
            Assertions.assertEquals(EventStatus.SUCCESS, status)
            Assertions.assertEquals(rootEventId, parentId.id)
        }

        SimulatorRuleInfo(0, EmptyTestRule, ruleConfiguration, messageBatcher, eventBatcher, rootEventId, scheduler) {
            LOGGER.debug("Rule removed")
        }.let { simulatorRuleInfo ->
            simulatorRuleInfo.sendEvent(resultEvent)
            Thread.sleep(150)
            verify(eventBatcher, times(1)).onEvent(check(com.exactpro.th2.common.grpc.Event::check))
        }

        verify(messageBatcher, never()).onMessage(any<Message>(), any())
        verify(messageBatcher, never()).onMessage(any<RawMessage>(), any())
    }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(this::class.java)

        private const val rootEventId = "12345"
        private const val testAlias = "TestAlias"
        private const val testRelation = "testRelation"
        private val ruleConfiguration =  RuleConfiguration().apply {
            this.sessionAlias = testAlias
            this.relation = testRelation
        }

        private var scheduler: ScheduledExecutorService = ScheduledThreadPoolExecutor(1)
    }

    private object EmptyTestRule : IRule {
        override fun checkTriggered(message: Message) = true
        override fun handle(ruleContext: IRuleContext, message: Message) = Unit
        override fun touch(ruleContext: IRuleContext, args: MutableMap<String, String>) = Unit
    }
}

