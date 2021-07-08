package com.exactpro.th2.sim.rule.action

/**
 * Represents an action which can executed from a rule or another action's execution scope
 */
fun interface IAction {
    /**
     * Executes this action on a scope
     */
    fun IExecutionScope.run()
}