package com.exactpro.th2.simulator.adapter;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import com.exactpro.evolution.api.phase_1.ConnectivityId;
import com.exactpro.evolution.configuration.MicroserviceConfiguration;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;

/**
 * Implementation of {@link IAdapter}. Does nothing.
 */
public class EmptyAdapter implements IAdapter {
    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull ConnectivityId connectivityId, @NotNull ISimulator simulator) {}

    @Override
    public void close() throws IOException {}
}
