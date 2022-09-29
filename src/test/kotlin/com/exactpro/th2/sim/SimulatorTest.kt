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

import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.message.message
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.plusAssign
import com.exactpro.th2.common.message.sessionAlias
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.sim.configuration.DefaultRulesTurnOffStrategy
import com.exactpro.th2.sim.configuration.RuleConfiguration
import com.exactpro.th2.sim.configuration.SimulatorConfiguration
import com.exactpro.th2.sim.grpc.RuleMessageFlow
import com.exactpro.th2.sim.grpc.RulesInfo
import com.exactpro.th2.sim.impl.Simulator
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.check
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import java.util.concurrent.CountDownLatch

class SimulatorTest {

    @Test
    fun `get related rules`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val streamObserver = mock<StreamObserver<RulesInfo>>()
        val simulatorConfiguration = SimulatorConfiguration()
        val ruleDefault = mock<IRule>()

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        sim.addRule(ruleDefault, RuleConfiguration())
        sim.addRule(ruleDefault, RuleConfiguration().apply { messageFlow = "test" })
        sim.addRule(ruleDefault, RuleConfiguration())

        sim.getRelatedRules(RuleMessageFlow.newBuilder().setMessageFlow("default").build(), streamObserver)
        verify(streamObserver, times(1)).onNext(check {
            Assertions.assertEquals(2, it.infoCount)
            Assertions.assertEquals(1, it.getInfo(0).id.id)
            Assertions.assertEquals(3, it.getInfo(1).id.id)
        })

        sim.getRelatedRules(RuleMessageFlow.newBuilder().setMessageFlow("test").build(), streamObserver)
        verify(streamObserver, times(1)).onNext(check {
            Assertions.assertEquals(1, it.infoCount)
            Assertions.assertEquals(2, it.getInfo(0).id.id)
        })

    }

    @Test
    fun `default rule test - triggers none on add strategy`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val ruleDefault = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(true)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send((it.arguments[1] as Message).toBuilder().apply {
                    metadataBuilder.messageType = "defaultType"
                }.build())
            }
        }
        val ruleNonDefault = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(false)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send((it.arguments[1] as Message).toBuilder().apply {
                    metadataBuilder.messageType = "nonDefaultType"
                }.build())
            }
        }

        val simulatorConfiguration = SimulatorConfiguration().apply {
            strategyDefaultRules = DefaultRulesTurnOffStrategy.ON_ADD
        }

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(ruleDefault, RuleConfiguration()).also {
            sim.addDefaultRule(it)
        })
        Assertions.assertNotNull(sim.addRule(ruleNonDefault, RuleConfiguration()))


        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        verify(eventRouter, times(2)).send(check(EventBatch::check))

        reset(batchRouter)
        sim.handleAndWait(Message.getDefaultInstance(), RuleConfiguration.DEFAULT_MESSAGE_FLOW)

        verify(ruleDefault, never()).handle(Mockito.any(), Mockito.any())
        verify(ruleNonDefault, never()).handle(Mockito.any(), Mockito.any())
        verify(batchRouter, times(0)).sendAll(Mockito.any(),Mockito.any(), Mockito.any())

    }

    @Test
    fun `default rule test - triggers only default`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val ruleDefault = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(true)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send((it.arguments[1] as Message).toBuilder().apply {
                    metadataBuilder.messageType = "defaultType"
                }.build())
            }
        }
        val ruleNonDefault = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(false)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send((it.arguments[1] as Message).toBuilder().apply {
                    metadataBuilder.messageType = "nonDefaultType"
                }.build())
            }
        }

        val simulatorConfiguration = SimulatorConfiguration().apply {
            maxFlushTime = 100
        }

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(ruleDefault, RuleConfiguration()).also {
            sim.addDefaultRule(it)
        })
        Assertions.assertNotNull(sim.addRule(ruleNonDefault, RuleConfiguration()))

        Thread.sleep(150)

        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        verify(eventRouter, times(2)).send(check(EventBatch::check))

        reset(batchRouter)
        sim.handleAndWait(Message.getDefaultInstance(), RuleConfiguration.DEFAULT_MESSAGE_FLOW, 500)

        verify(ruleDefault, times(1)).handle(Mockito.any(), check { Assertions.assertEquals(Message.getDefaultInstance(), it) })
        verify(ruleNonDefault, never()).handle(Mockito.any(), Mockito.any())
        verify(batchRouter, times(1)).sendAll(check {
            val message = it.getGroups(0).getMessages(0).message
            Assertions.assertEquals(message.messageType, "defaultType")
        }, check { Assertions.assertEquals("second", it) }, check { Assertions.assertEquals("default", it) })

    }

    @Test
    fun `default rule test - triggers only non default`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val ruleDefault = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(true)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send((it.arguments[1] as Message).toBuilder().apply {
                    metadataBuilder.messageType = "defaultType"
                }.build())
            }
        }
        val ruleNonDefault = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(true)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send((it.arguments[1] as Message).toBuilder().apply {
                    metadataBuilder.messageType = "nonDefaultType"
                }.build())
            }
        }
        val simulatorConfiguration = SimulatorConfiguration().apply {
            maxFlushTime = 100
        }

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(ruleDefault, RuleConfiguration()).also {
            sim.addDefaultRule(it)
        })
        Assertions.assertNotNull(sim.addRule(ruleNonDefault, RuleConfiguration()))


        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        verify(eventRouter, times(2)).send(check(EventBatch::check))

        reset(batchRouter)
        sim.handleAndWait(Message.getDefaultInstance(), RuleConfiguration.DEFAULT_MESSAGE_FLOW)

        verify(ruleNonDefault, times(1)).handle(Mockito.any(), check { Assertions.assertEquals(Message.getDefaultInstance(), it) })
        verify(ruleDefault, never()).handle(Mockito.any(), Mockito.any())
        verify(batchRouter, times(1)).sendAll(check {
            val message = it.getGroups(0).getMessages(0).message
            Assertions.assertEquals(message.messageType, "nonDefaultType")
        }, check { Assertions.assertEquals("second", it) }, check { Assertions.assertEquals("default", it) })

    }

    @Test
    fun `alias test`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val rule = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(true)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send(it.arguments[1] as Message)
            }
        }
        val simulatorConfiguration = SimulatorConfiguration().apply {
            maxFlushTime = 100
        }
        val testAlias = "TestAlias"
        val wrongAlias = "WrongAlias"

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(rule, RuleConfiguration().apply { sessionAlias = testAlias }))

        Thread.sleep(150)

        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        verify(eventRouter, times(1)).send(check(EventBatch::check))

        val handlingMsg = Message.newBuilder().apply {
            sessionAlias = testAlias
        }.build()

        reset(batchRouter)
        sim.handleAndWait(handlingMsg, RuleConfiguration.DEFAULT_MESSAGE_FLOW)

        verify(rule).handle(Mockito.any(), check { Assertions.assertEquals(handlingMsg, it) })
        verify(batchRouter, times(1)).sendAll(check {
            val message = it.getGroups(0).getMessages(0).message
            Assertions.assertEquals(message.sessionAlias, testAlias)
        }, check { Assertions.assertEquals("second", it) }, check { Assertions.assertEquals("default", it) })

        val wrongMsg = handlingMsg.toBuilder().apply {
            sessionAlias = wrongAlias
        }.build()

        reset(batchRouter)
        reset(rule)
        sim.handleAndWait(wrongMsg, RuleConfiguration.DEFAULT_MESSAGE_FLOW)

        verify(rule, never()).handle(Mockito.any(), Mockito.any())
        verify(batchRouter, never()).sendAll(Mockito.any(), Mockito.any(), Mockito.any())
    }

    @Test
    fun `messageFlow test`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val rule = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(true)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send(it.arguments[1] as Message)
            }
        }
        val simulatorConfiguration = SimulatorConfiguration().apply {
            maxFlushTime = 100
        }
        val testMessageFlow = "TestMessageFlow"
        val messageType = "SomeType"

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(rule, RuleConfiguration().apply { messageFlow = testMessageFlow }))

        Thread.sleep(150)

        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        verify(eventRouter, times(1)).send(check(EventBatch::check))

        val handlingMsg = message(messageType).build()

        reset(batchRouter)
        sim.handleAndWait(handlingMsg, testMessageFlow)

        verify(rule, times(1)).checkTriggered(check { Assertions.assertEquals(handlingMsg, it) })
        verify(rule).handle(Mockito.any(), check { Assertions.assertEquals(handlingMsg, it) })
        verify(batchRouter, times(1)).sendAll( check {
            Assertions.assertEquals(messageType, it.getGroups(0).getMessages(0).message.messageType)
        }, check { Assertions.assertEquals("second", it) }, check { Assertions.assertEquals(testMessageFlow, it) })

        val wrongMsg = message("messageType").build()

        reset(batchRouter)
        reset(rule)
        sim.handleAndWait(wrongMsg, RuleConfiguration.DEFAULT_MESSAGE_FLOW)

        verify(rule, never()).handle(Mockito.any(), Mockito.any())
        verify(batchRouter, never()).sendAll(Mockito.any(), Mockito.any(), Mockito.any())
    }

    @Test
    fun `order test`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()

        val firstMessageType = "SomeTypeFirst"
        val secondMessageType = "SomeTypeSecond"

        val rule = mock<IRule>() {
            val requestFlag = CountDownLatch(1)
            on(mock.checkTriggered(Mockito.any())).thenAnswer { onMock ->
                (requestFlag.count == 0L).also {
                    if ((onMock.getArgument(0) as Message).messageType == firstMessageType) {
                        requestFlag.countDown()
                    }
                }
            }
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send(it.arguments[1] as Message)
            }
        }
        val simulatorConfiguration = SimulatorConfiguration().apply {
            maxFlushTime = 150
        }

        val testMessageFlow = "TestMessageFlow"

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(rule, RuleConfiguration().apply { messageFlow = testMessageFlow }))

        sim.handleAndWait(MessageGroup.newBuilder().apply {
            this += message(firstMessageType).build()
            this += message(secondMessageType).build()
        }.build(), testMessageFlow)

        verify(rule, times(2)).checkTriggered(Mockito.any())
        verify(rule,  times(1)).handle(Mockito.any(), check {
            Assertions.assertEquals(secondMessageType, it.messageType)
        })
    }

    @Test
    fun `stress test`() {
        val batcherSize = 1000
        val answers = 5
        val stressCount = 15000

        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val ruleDefault = object : IRule {
            override fun checkTriggered(message: Message): Boolean = true
            override fun touch(ruleContext: IRuleContext, args: MutableMap<String, String>) = Unit
            override fun handle(ruleContext: IRuleContext, message: Message) {
                repeat(answers) {
                    ruleContext.send(Message.newBuilder().addField("test", "test").build())
                }
            }
        }

        val simulatorConfiguration = SimulatorConfiguration().apply {
            strategyDefaultRules = DefaultRulesTurnOffStrategy.ON_ADD
            executionPoolSize = 1
            maxFlushTime = 500
            maxBatchSize = batcherSize
        }

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(ruleDefault, RuleConfiguration()).also {
            sim.addDefaultRule(it)
        })

        repeat(stressCount) {
            try {
                sim.handleBatch(Message.getDefaultInstance().toGroup().toBatch(), RuleConfiguration.DEFAULT_MESSAGE_FLOW)
            } catch (e: Exception) {
                fail("${it}th iteration failed", e)
            }
        }

        Thread.sleep(600)
        verify(batchRouter, times(stressCount * answers / batcherSize)).sendAll(Mockito.any(),Mockito.any(), Mockito.any())
    }

    private fun Simulator.handleAndWait(message: Message, messageFlow: String, sleepTime: Long = 200) = this.handleAndWait(message.toGroup(), messageFlow, sleepTime)

    private fun Simulator.handleAndWait(messageGroup: MessageGroup, messageFlow: String, sleepTime: Long = 100) {
        this.handleBatch(messageGroup.toBatch(), messageFlow)
        Thread.sleep(sleepTime)
    }

    private fun Message.toGroup() = MessageGroup.newBuilder().also {
        it.addMessages(AnyMessage.newBuilder().setMessage(this).build())
    }.build()

    private fun MessageGroup.toBatch() = MessageGroupBatch.newBuilder().addGroups(this).build()

    companion object {
        private const val rootEventId = "12321"
    }
}