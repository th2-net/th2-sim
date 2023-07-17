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
package com.exactpro.th2.sim

import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
import com.exactpro.th2.sim.configuration.SimulatorConfiguration
import com.exactpro.th2.sim.grpc.RuleID
import com.exactpro.th2.sim.impl.Simulator
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import com.google.protobuf.Empty
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class SimulatorTest {

    @Test
    fun `test remove rule`() {
        val sim = Simulator()
        val eventRouter = mock<MessageRouter<EventBatch>>() {
            on(mock.send(Mockito.any())).then {

            }
        }

        val streamObserver = mock<StreamObserver<Empty>>()
        val messageRouter = mock<MessageRouter<GroupBatch>>()

        sim.init(InitializationContext.builder()
            .withRootEventId(EventID.newBuilder().setId("rootEventID").setBookName("bookName").build())
            .withBatchRouter(messageRouter)
            .withEventRouter(eventRouter)
            .withConfiguration(SimulatorConfiguration())
            .withBookName("test_book")
            .build()
        )

        val rules = mutableListOf<RuleID>()

        Assertions.assertDoesNotThrow {
            repeat(10) {
                rules.add(sim.addRule(TestRule(), "alias"))
            }
        }

        repeat(10) {
            sim.removeRule(rules.removeAt((0 until rules.size).random()), streamObserver)
        }

        verify(streamObserver, times(10)).onNext(Empty.getDefaultInstance())
        verify(streamObserver, times(10)).onCompleted()
        verify(streamObserver, times(0)).onError(Mockito.any())
    }

    class TestRule: IRule {
        override fun checkTriggered(message: ParsedMessage): Boolean = true
        override fun handle(ruleContext: IRuleContext, message: ParsedMessage) = ruleContext.send(message)
        override fun touch(ruleContext: IRuleContext, args: MutableMap<String, String>) = Unit
    }
}