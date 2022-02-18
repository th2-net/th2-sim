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

import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.message.message
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.sessionAlias
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.sim.configuration.DefaultRulesTurnOffStrategy
import com.exactpro.th2.sim.configuration.RuleConfiguration
import com.exactpro.th2.sim.configuration.SimulatorConfiguration
import com.exactpro.th2.sim.impl.Simulator
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.check
import org.mockito.kotlin.never
import org.mockito.kotlin.reset

class SimulatorTest {

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
        sim.handleMessage(Message.getDefaultInstance(), RuleConfiguration.DEFAULT_RELATION)

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

        val simulatorConfiguration = SimulatorConfiguration()

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
        sim.handleMessage(Message.getDefaultInstance(), RuleConfiguration.DEFAULT_RELATION)

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
        val simulatorConfiguration = SimulatorConfiguration()

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
        sim.handleMessage(Message.getDefaultInstance(), RuleConfiguration.DEFAULT_RELATION)

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
        val simulatorConfiguration = SimulatorConfiguration()
        val testAlias = "TestAlias"
        val wrongAlias = "WrongAlias"

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(rule, RuleConfiguration().apply { sessionAlias = testAlias }))

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
        sim.handleMessage(handlingMsg, RuleConfiguration.DEFAULT_RELATION)

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
        sim.handleMessage(wrongMsg, RuleConfiguration.DEFAULT_RELATION)

        verify(rule, never()).handle(Mockito.any(), Mockito.any())
        verify(batchRouter, never()).sendAll(Mockito.any(), Mockito.any(), Mockito.any())
    }

    @Test
    fun `relation test`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val rule = mock<IRule>() {
            on(mock.checkTriggered(Mockito.any())).thenReturn(true)
            on(mock.handle(Mockito.any(), Mockito.any())).then {
                (it.arguments[0] as IRuleContext).send(it.arguments[1] as Message)
            }
        }
        val simulatorConfiguration = SimulatorConfiguration()
        val testRelation = "TestRelation"
        val messageType = "SomeType"

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(rule, RuleConfiguration().apply { relation = testRelation }))

        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        verify(eventRouter, times(1)).send(check(EventBatch::check))

        val handlingMsg = message(messageType).build()

        reset(batchRouter)
        sim.handleMessage(handlingMsg, testRelation)

        verify(rule, times(1)).checkTriggered(check { Assertions.assertEquals(handlingMsg, it) })
        verify(rule).handle(Mockito.any(), check { Assertions.assertEquals(handlingMsg, it) })
        verify(batchRouter, times(1)).sendAll( check {
            Assertions.assertEquals(messageType, it.getGroups(0).getMessages(0).message.messageType)
        }, check { Assertions.assertEquals("second", it) }, check { Assertions.assertEquals(testRelation, it) })

        val wrongMsg = message("messageType").build()

        reset(batchRouter)
        reset(rule)
        sim.handleMessage(wrongMsg, RuleConfiguration.DEFAULT_RELATION)

        verify(rule, never()).handle(Mockito.any(), Mockito.any())
        verify(batchRouter, never()).sendAll(Mockito.any(), Mockito.any(), Mockito.any())
    }


    companion object {
        private const val rootEventId = "12321"
    }
}