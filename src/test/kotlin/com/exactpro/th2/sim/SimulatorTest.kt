package com.exactpro.th2.sim

import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.message.sessionAlias
import com.exactpro.th2.common.schema.message.MessageRouter
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
import org.mockito.kotlin.reset

class SimulatorTest {

    @Test
    fun `simple rule test`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val rule = mock<IRule>()
        val simulatorConfiguration = SimulatorConfiguration()
        val alias = "TestAlias"

        Mockito.`when`(rule.checkTriggered(Mockito.any())).thenReturn(true)
        Mockito.`when`(rule.handle(Mockito.any(), Mockito.any())).then {
            (it.arguments[0] as IRuleContext).send(it.arguments[1] as Message)
        }

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(rule, RuleConfiguration().apply { sessionAlias = alias }))

        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        verify(eventRouter, times(1)).send(check(EventBatch::check))

        val handlingMsg = Message.newBuilder().apply {
            sessionAlias = alias
        }.build()

        reset(batchRouter)
        sim.handleMessage(handlingMsg, RuleConfiguration.DEFAULT_RELATION)

        verify(rule).handle(Mockito.any(), check { Assertions.assertEquals(handlingMsg, it) })
        verify(batchRouter, times(1)).sendAll(check {
            val message = it.getGroups(0).getMessages(0).message
            Assertions.assertEquals(message.sessionAlias, alias)
        }, check { Assertions.assertEquals("second", it) }, check { Assertions.assertEquals("default", it) })
    }

    companion object {
        private const val rootEventId = "12321"
    }
}