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

package com.exactpro.th2.sim.rule.action

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage

/**
 * Represents a scope on which an action is executed on.
 * This action can use it to send messages or execute sub-actions
 */
interface IExecutionScope : ICancellable {
    /**
     * Attempts to send a [message] immediately
     * @return an entity which can be used cancel this operation
     */
    fun send(message: Message): ICancellable

    /**
     * Attempts to send a [message] after a specified [delay]
     * @return an entity which can be used cancel this operation
     */
    fun send(message: Message, delay: Long): ICancellable

    /**
     * Attempts to send a [message] after a specified [delay] and
     * then periodically using a specified [period]
     * @return an entity which can be used cancel this operation
     */
    fun send(message: Message, delay: Long, period: Long): ICancellable

    /**
     * Attempts to send a [message] immediately
     * @return an entity which can be used cancel this operation
     */
    fun send(message: RawMessage): ICancellable

    /**
     * Attempts to send a [message] after a specified [delay]
     * @return an entity which can be used cancel this operation
     */
    fun send(message: RawMessage, delay: Long): ICancellable

    /**
     * Attempts to send a [message] after a specified [delay] and
     * then periodically using a specified [period]
     * @return an entity which can be used cancel this operation
     */
    fun send(message: RawMessage, delay: Long, period: Long): ICancellable

    /**
     * Attempts to send a message [group] immediately
     * @return an entity which can be used cancel this operation
     */
    fun send(group: MessageGroup): ICancellable

    /**
     * Attempts to send a message [group] after a specified [delay]
     * @return an entity which can be used cancel this operation
     */
    fun send(group: MessageGroup, delay: Long): ICancellable

    /**
     * Attempts to send a message [group] after a specified [delay] and
     * then periodically using a specified [period]
     * @return an entity which can be used cancel this operation
     */
    fun send(group: MessageGroup, delay: Long, period: Long): ICancellable

    /**
     * Attempts to execute an [action].
     * This action will be executed on sub-scope of this scope.
     * @return an entity which can be used cancel this action and all of its sub-actions
     */
    fun execute(action: IAction): ICancellable

    /**
     * Attempts to execute an [action] after a specified [delay].
     * This action will be executed on sub-scope of this scope.
     * @return an entity which can be used cancel this action and all of its sub-actions
     */
    fun execute(delay: Long, action: IAction): ICancellable

    /**
     * Attempts to execute an [action] after a specified [delay] and then
     * periodically using a specified [period].
     * This action will be executed on sub-scope of this scope.
     * @return an entity which can be used cancel this action and all of its sub-actions
     */
    fun execute(delay: Long, period: Long, action: IAction): ICancellable
}
