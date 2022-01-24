package com.exactpro.th2.sim

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import com.exactpro.th2.sim.template.rule.test.api.TestRuleContext.Companion.testRule
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError

class TestRuleContextToolkit {

    @Test
    fun `test of trigger assertion`() {
        Assertions.assertThrows(AssertionFailedError::class.java) {
            testRule {
                TestRule.assertNotTriggered(Message.getDefaultInstance())
            }
        }
    }


    private object TestRule : IRule {
        override fun checkTriggered(message: Message): Boolean = true

        override fun handle(ruleContext: IRuleContext, message: Message) = Unit

        override fun touch(ruleContext: IRuleContext, args: MutableMap<String, String>) = Unit
    }
}