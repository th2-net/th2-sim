package com.exactpro.th2.sim

import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.schema.message.MessageRouter
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
        val messageRouter = mock<MessageRouter<MessageGroupBatch>>()

        sim.init(messageRouter, eventRouter, SimulatorConfiguration(), EventID.newBuilder().setId("rootEventID").build())

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
        override fun checkTriggered(message: Message): Boolean = true
        override fun handle(ruleContext: IRuleContext, message: Message) = ruleContext.send(message)
        override fun touch(ruleContext: IRuleContext, args: MutableMap<String, String>) = Unit
    }
}