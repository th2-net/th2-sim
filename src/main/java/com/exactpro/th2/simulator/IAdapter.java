package com.exactpro.th2.simulator;

import java.io.Closeable;

import org.jetbrains.annotations.NotNull;

import com.exactpro.evolution.api.phase_1.ConnectivityId;
import com.exactpro.evolution.configuration.MicroserviceConfiguration;

public interface IAdapter extends Closeable {

    void init(@NotNull MicroserviceConfiguration configuration, @NotNull ConnectivityId connectivityId, @NotNull ISimulator simulator);

}
