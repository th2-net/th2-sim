package com.exactpro.th2.simulator;

import java.io.Closeable;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.exactpro.evolution.api.phase_1.ConnectivityId;
import com.exactpro.evolution.api.phase_1.Message;
import com.exactpro.evolution.configuration.MicroserviceConfiguration;
import com.exactpro.th2.simulator.rule.IRule;

import io.grpc.BindableService;

public interface ISimulator extends BindableService, Closeable {

    void init(@NotNull MicroserviceConfiguration configuration, @NotNull Class<? extends IAdapter> adapterClass) throws Exception;
    RuleID addRule(@NotNull IRule rule);
    List<Message> handle(@NotNull ConnectivityId connectivityId, @NotNull Message message);

}
