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

import com.exactpro.th2.common.grpc.Event
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
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
                TestRule.assertTriggered(ParsedMessage.EMPTY)
            }
        }

        Assertions.assertThrows(AssertionFailedError::class.java) {
            testRule {
                TestRule.assertHandle(ParsedMessage.EMPTY)
            }
        }

        Assertions.assertDoesNotThrow {
            testRule {
                TestRule.assertNotTriggered(ParsedMessage.EMPTY)
            }
        }


        TestRule.triggeredAnswer = true

        Assertions.assertThrows(AssertionFailedError::class.java) {
            testRule {
                TestRule.assertNotTriggered(ParsedMessage.EMPTY)
            }
        }

        Assertions.assertDoesNotThrow {
            testRule {
                TestRule.assertTriggered(ParsedMessage.EMPTY)
            }
        }

    }

    @Test
    fun `test of sent assertion`() {

        TestRule.triggeredAnswer = true
        TestRule.sendLogic = {
            it.send(ParsedMessage.EMPTY)
        }

        testRule {
            TestRule.assertHandle(ParsedMessage.EMPTY)
            Assertions.assertThrows(AssertionFailedError::class.java) {
                assertSent(GroupBatch::class.java) { }
            }

            Assertions.assertThrows(AssertionFailedError::class.java) {
                assertSent(Event::class.java) { }
            }
        }

    }


    private object TestRule : IRule {

        var triggeredAnswer = false
        var sendLogic =  { _: IRuleContext -> }

        override fun checkTriggered(message: ParsedMessage): Boolean = triggeredAnswer

        override fun handle(ruleContext: IRuleContext, message: ParsedMessage) = sendLogic(ruleContext)

        override fun touch(ruleContext: IRuleContext, args: MutableMap<String, String>) = sendLogic(ruleContext)
    }
}