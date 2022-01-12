/*
 * Copyright 2021-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.sim.rule.action.impl

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.MessageGroupBatch
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.sim.rule.action.IAction
import com.exactpro.th2.sim.rule.action.ICancellable
import com.exactpro.th2.sim.rule.action.IExecutionScope
import mu.KotlinLogging
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.function.Consumer

class ActionRunner private constructor(
    private val executor: ScheduledExecutorService,
    private val sender: MessageSender
) : IExecutionScope {
    private val logger = KotlinLogging.logger {}
    private val cancellables = ConcurrentLinkedDeque<ICancellable>()

    constructor(
        executor: ScheduledExecutorService,
        sender: MessageSender,
        action: IAction
    ) : this(executor, sender) {
        submit { action.run { run() } }
    }

    constructor(
        executor: ScheduledExecutorService,
        sender: MessageSender,
        delay: Long,
        action: IAction
    ) : this(executor, sender) {
        schedule(delay) { action.run { run() } }
    }

    constructor(
        executor: ScheduledExecutorService,
        sender: MessageSender,
        delay: Long,
        period: Long,
        action: IAction
    ) : this(executor, sender) {
        schedule(delay, period) { action.run { run() } }
    }

    override fun send(message: Message): ICancellable = submit { sender.send(message) }
    override fun send(message: Message, delay: Long): ICancellable = schedule(delay) { sender.send(message) }
    override fun send(message: Message, delay: Long, period: Long): ICancellable = schedule(delay, period) { sender.send(message) }

    override fun send(message: RawMessage): ICancellable = submit { sender.send(message) }
    override fun send(message: RawMessage, delay: Long): ICancellable  = schedule(delay) { sender.send(message) }
    override fun send(message: RawMessage, delay: Long, period: Long): ICancellable = schedule(delay, period) { sender.send(message) }

    override fun send(group: MessageGroup): ICancellable = submit { sender.send(group) }
    override fun send(group: MessageGroup, delay: Long): ICancellable = schedule(delay) { sender.send(group) }
    override fun send(group: MessageGroup, delay: Long, period: Long): ICancellable = schedule(delay, period) { sender.send(group) }

    override fun execute(action: IAction): ICancellable = ActionRunner(executor, sender, action).registerCancellable()
    override fun execute(delay: Long, action: IAction): ICancellable = ActionRunner(executor, sender, delay, action).registerCancellable()
    override fun execute(delay: Long, period: Long, action: IAction): ICancellable = ActionRunner(executor, sender, delay, period, action).registerCancellable()

    override fun cancel() = cancellables.descendingIterator().forEach {
        it.runCatching(ICancellable::cancel).onFailure { cause ->
            logger.error(cause) { "Failed to cancel" }
        }
    }

    private fun submit(action: () -> Unit): ICancellable = executor.submit(action).toCancellable()
    private fun schedule(delay: Long, action: () -> Unit) = executor.schedule(action, delay.checkDelay(), MILLISECONDS).toCancellable()
    private fun schedule(delay: Long, period: Long, action: () -> Unit) = executor.scheduleAtFixedRate(action, delay.checkDelay(), period.checkPeriod(), MILLISECONDS).toCancellable()

    private fun ICancellable.registerCancellable(): ICancellable = apply(cancellables::add)
    private fun Future<*>.toCancellable(): ICancellable = ICancellable { cancel(true) }.registerCancellable()

    companion object {
        private fun Long.checkDelay() = apply { check(this >= 0) { "Negative delay: $this" } }
        private fun Long.checkPeriod() = apply { check(this > 0) { "Non-positive period: $this" } }
    }
}

class MessageSender(
    private val messageSender: Consumer<Message>,
    private val rawMessageSender: Consumer<RawMessage>,
    private val groupSender: Consumer<MessageGroup>
) {
    fun send(message: Message) = messageSender.accept(message)
    fun send(message: RawMessage) = rawMessageSender.accept(message)
    fun send(group: MessageGroup) = groupSender.accept(group)
}