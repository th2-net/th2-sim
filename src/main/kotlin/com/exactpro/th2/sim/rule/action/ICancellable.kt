package com.exactpro.th2.sim.rule.action

/**
 * Represents a cancellable entity. Usually an action or its scope
 */
fun interface ICancellable {
    /**
     * Cancels execution of this entity and all of its cancellable sub-entities
     */
    fun cancel()
}