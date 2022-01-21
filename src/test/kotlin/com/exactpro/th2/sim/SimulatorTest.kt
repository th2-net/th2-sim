package com.exactpro.th2.sim

import com.exactpro.th2.common.grpc.EventBatch
import com.exactpro.th2.common.grpc.EventStatus
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.schema.message.MessageRouter
import com.exactpro.th2.sim.configuration.RuleConfiguration
import com.exactpro.th2.sim.configuration.SimulatorConfiguration
import com.exactpro.th2.sim.impl.Simulator
import com.exactpro.th2.sim.rule.IRule
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.check

class SimulatorTest {

    @Test
    fun `add rule`() {
        val batchRouter = mock<MessageRouter<MessageGroupBatch>>()
        val eventRouter = mock<MessageRouter<EventBatch>>()
        val rule = mock<IRule>()
        val simulatorConfiguration = SimulatorConfiguration()

        val sim = Simulator().apply {
            init(batchRouter, eventRouter, simulatorConfiguration, rootEventId)
        }

        Assertions.assertNotNull(sim.addRule(rule, RuleConfiguration().apply { sessionAlias = "TestAlias" }))

        fun EventBatch.check() {
            val event = this.getEvents(0)
            Assertions.assertEquals(EventStatus.SUCCESS, event.status)
            Assertions.assertEquals(rootEventId, event.parentId.id)
        }

        verify(eventRouter, times(1)).send(check(EventBatch::check))
    }

    companion object {
        private const val rootEventId = "12321"
    }
}