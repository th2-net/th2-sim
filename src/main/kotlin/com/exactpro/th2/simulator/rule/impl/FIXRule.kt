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

import com.exactpro.evolution.api.phase_1.Message
import com.exactpro.evolution.api.phase_1.Metadata
import com.exactpro.evolution.api.phase_1.NullValue.NULL_VALUE
import com.exactpro.evolution.api.phase_1.Value
import com.exactpro.th2.simulator.rule.SimulatorRule
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

@SimulatorRule("fix-rule")
class FIXRule(id: Int, newOrderArguments: MutableMap<String, Value>?) : MessageCompareRule(id, "NewOrderSingle", newOrderArguments) {

    companion object {
        val orderId = AtomicInteger(1)
        val execId = AtomicInteger(1)
    }

    override fun handleTriggered(message: Message): MutableList<Message> {
        return Collections.singletonList(
                Message.newBuilder()
                    .setMetadata(Metadata.newBuilder()
                        //.setMessageId(MessageId.newBuilder().Uuids.timeBased().toString())
                        .setMessageType("ExecutionReport")
                        .setNamespace(message.metadata.namespace)
                        .build())
                    .addField("OrderID", orderId.incrementAndGet().toString())
                    .addField("ExecID", execId.incrementAndGet().toString())
                    .addField("ExecType", "2")
                    .addField("OrdStatus", "0")
                    .copyField("Side", message)
                    .copyField("LeavesQty", message)
                    .addField("CumQty", "0")
                    .copyField("ClOrdID", message)
                    .copyField("SecurityID", message)
                    .copyField("SecurityIDSource", message)
                    .copyField("OrdType", message)
                    .copyField("OrderQty", message)
                    .putFields("TradingParty", Value.newBuilder().setNullValue(NULL_VALUE).build())
                    .addField("TransactTime", LocalDateTime.now().toString())
                    .build()
        )
    }
}