package com.exactpro.th2.sim.rule.action

import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageBatch

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
     * Attempts to send a message [batch] immediately
     * @return an entity which can be used cancel this operation
     */
    fun send(batch: MessageBatch): ICancellable

    /**
     * Attempts to send a message [batch] after a specified [delay]
     * @return an entity which can be used cancel this operation
     */
    fun send(batch: MessageBatch, delay: Long): ICancellable

    /**
     * Attempts to send a message [batch] after a specified [delay] and
     * then periodically using a specified [period]
     * @return an entity which can be used cancel this operation
     */
    fun send(batch: MessageBatch, delay: Long, period: Long): ICancellable

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
