package com.exactpro.th2.sim.rule;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface IBaseRule<T> {
    /**
     * @param message input message
     * @return True, if rule will triggered on this message
     */
    boolean checkTriggered(@NotNull T message);

    /**
     * @param ruleContext context
     * @param message input message
     */
    void handle(@NotNull IRuleContext ruleContext, @NotNull T message);

    /**
     * Called on grpc request touchRule
     * @param ruleContext context
     * @param args custom arguments
     */
    void touch(@NotNull IRuleContext ruleContext, @NotNull Map<String, String> args);
}
