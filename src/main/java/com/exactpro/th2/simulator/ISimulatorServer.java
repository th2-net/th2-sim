package com.exactpro.th2.simulator;

import java.io.Closeable;

import org.jetbrains.annotations.NotNull;

import com.exactpro.evolution.configuration.MicroserviceConfiguration;

public interface ISimulatorServer extends Closeable {

    void init(@NotNull MicroserviceConfiguration configuration, @NotNull Class<? extends ISimulator> simulatorServer, @NotNull Class<? extends IAdapter> adapterServer);
    boolean start();
    void blockUntilShutdown() throws InterruptedException;
}
