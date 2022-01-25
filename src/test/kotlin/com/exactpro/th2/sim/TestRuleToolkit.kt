package com.exactpro.th2.sim

import com.exactpro.th2.common.grpc.Event
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.sim.rule.IRule
import com.exactpro.th2.sim.rule.IRuleContext
import com.exactpro.th2.sim.template.rule.test.api.TestRuleContext.Companion.testRule
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError

class TestRuleToolkit {

    @Test
    fun `test of trigger assertion`() {

        TestRule.triggeredAnswer = false

        Assertions.assertThrows(AssertionFailedError::class.java) {
            testRule {
                TestRule.assertTriggered(Message.getDefaultInstance())
            }
        }

        Assertions.assertThrows(AssertionFailedError::class.java) {
            testRule {
                TestRule.assertHandle(Message.getDefaultInstance())
            }
        }

        Assertions.assertDoesNotThrow {
            testRule {
                TestRule.assertNotTriggered(Message.getDefaultInstance())
            }
        }


        TestRule.triggeredAnswer = true

        Assertions.assertThrows(AssertionFailedError::class.java) {
            testRule {
                TestRule.assertNotTriggered(Message.getDefaultInstance())
            }
        }

        Assertions.assertDoesNotThrow {
            testRule {
                TestRule.assertTriggered(Message.getDefaultInstance())
            }
        }

    }

    @Test
    fun `test of sent assertion`() {

        TestRule.triggeredAnswer = true
        TestRule.sendLogic = {
            it.send(Message.getDefaultInstance())
        }

        testRule {
            TestRule.assertHandle(Message.getDefaultInstance())
            Assertions.assertThrows(AssertionFailedError::class.java) {
                assertSent(MessageGroupBatch::class.java) { }
            }

            Assertions.assertThrows(AssertionFailedError::class.java) {
                assertSent(MessageGroupBatch::class.java) { }
            }

            Assertions.assertThrows(AssertionFailedError::class.java) {
                assertSent(Event::class.java) { }
            }
        }

    }


    private object TestRule : IRule {

        var triggeredAnswer = false
        var sendLogic =  { _: IRuleContext -> }

        override fun checkTriggered(message: Message): Boolean = triggeredAnswer

        override fun handle(ruleContext: IRuleContext, message: Message) = sendLogic(ruleContext)

        override fun touch(ruleContext: IRuleContext, args: MutableMap<String, String>) = sendLogic(ruleContext)
    }
}