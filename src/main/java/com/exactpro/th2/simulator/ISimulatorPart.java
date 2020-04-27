package com.exactpro.th2.simulator;

import org.jetbrains.annotations.NotNull;

import io.grpc.BindableService;

public interface ISimulatorPart extends BindableService {

    void init(@NotNull ISimulator simulator);

}
