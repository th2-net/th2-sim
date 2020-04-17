/******************************************************************************
 * Copyright 2009-2020 Exactpro (Exactpro Systems Limited)
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
 ******************************************************************************/
package com.exactpro.th2.simulator.rule.impl

import com.exactpro.evolution.IMessageToProtoConverter
import com.exactpro.evolution.api.phase_1.Message
import com.exactpro.sf.common.messages.IMessageFactory
import com.exactpro.th2.simulator.rule.SimulatorRule
import java.util.Collections

@SimulatorRule("fix-rule")
class FIXRule(id: Int, arguments: MutableMap<String, String>?, private var factory: IMessageFactory) : MessageCompareRule(id, arguments) {

    companion object {
        const val SEND_MESSAGE_NAME = "SendMessageName";
    }

    private val converter: IMessageToProtoConverter = IMessageToProtoConverter()

    override fun postInit(arguments: MutableMap<String, String>) {
        arguments.putIfAbsent(MESSAGE_NAME, "NewOrderSingle")
        arguments.putIfAbsent(SEND_MESSAGE_NAME, "ExecutionReport")
    }

    override fun getType(): String {
        return "fix-rule"
    }

    override fun handleTriggered(message: Message?): MutableList<Message> {
         return Collections.singletonList(converter
             .toProtoMessage(factory
                 .createMessage(arguments.get(SEND_MESSAGE_NAME))
             ).build()
         )
    }
}