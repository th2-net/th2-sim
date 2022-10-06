/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim.util

import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.utils.message.toGroup
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MessageBatcher(
    private val maxBatchSize: Int = 1000,
    private val maxFlushTime: Long = 1000,
    private val executor: ScheduledExecutorService,
    private val onBatch: (MessageGroupBatch, String) -> Unit
) : AutoCloseable {
    private val batches = ConcurrentHashMap<String, MessageBatch>()

    fun onMessage(message: RawMessage, messageFlow: String) = batches.getOrPut(messageFlow) { MessageBatch(messageFlow) }.add(message.toGroup())
    fun onMessage(message: Message, messageFlow: String) = batches.getOrPut(messageFlow) { MessageBatch(messageFlow) }.add(message.toGroup())
    fun onMessage(message: AnyMessage, messageFlow: String) = batches.getOrPut(messageFlow) { MessageBatch(messageFlow) }.add(MessageGroup.newBuilder().addMessages(message).build())
    fun onGroup(group: MessageGroup, messageFlow: String) = batches.getOrPut(messageFlow) { MessageBatch(messageFlow) }.add(group)

    override fun close() = batches.values.forEach(MessageBatch::close)

    private inner class MessageBatch(val messageFlow: String) : AutoCloseable {
        private val lock = ReentrantLock()
        private var batch = MessageGroupBatch.newBuilder()
        private var future: Future<*> = CompletableFuture.completedFuture(null)

        fun add(group: MessageGroup) = lock.withLock {
            batch.addGroups(group)

            when (batch.groupsCount) {
                1 -> future = executor.schedule(::send, maxFlushTime, MILLISECONDS)
                maxBatchSize -> send()
            }
        }

        private fun send() = lock.withLock<Unit> {
            if (batch.groupsCount == 0) return
            onBatch(batch.build(), messageFlow)
            batch.clearGroups()
            future.cancel(false)
        }

        override fun close() = send()
    }
}