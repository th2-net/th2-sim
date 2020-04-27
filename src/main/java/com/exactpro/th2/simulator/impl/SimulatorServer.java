package com.exactpro.th2.simulator.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.exactpro.evolution.configuration.MicroserviceConfiguration;
import com.exactpro.th2.simulator.IAdapter;
import com.exactpro.th2.simulator.ISimulator;
import com.exactpro.th2.simulator.ISimulatorPart;
import com.exactpro.th2.simulator.ISimulatorServer;
import com.exactpro.th2.simulator.SimulatorPart;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;

public class SimulatorServer implements ISimulatorServer {

    private final Logger logger = LoggerFactory.getLogger(this.getClass() + "@" + this.hashCode());

    private final Set<Class<? extends ISimulatorPart>> simulatorParts;
    private MicroserviceConfiguration configuration;
    private ISimulator simulator;
    private Server server;

    public SimulatorServer() {
        simulatorParts = new HashSet<>();
        loadTypes();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                System.err.println("Stopping GRPC server in simulator");
                server.shutdownNow();
                System.err.println("GRPC server was stopped");
            }
        }));
    }

    @Override
    public void init(@NotNull MicroserviceConfiguration configuration, @NotNull Class<? extends ISimulator> simulatorClass, @NotNull Class<? extends IAdapter> adapterClass) {
        this.configuration = configuration;
        try {
            simulator = simulatorClass.newInstance();
            simulator.init(configuration, adapterClass);
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException("Can not create simulator with default constructor", e);
        } catch (Exception e) {
            throw new IllegalStateException("Can not init simulator", e);
        }
    }

    @Override
    public boolean start() {
        if (server != null) {
            return false;
        }

        NettyServerBuilder builder = NettyServerBuilder.forPort(configuration.getPort()).addService(simulator);
        for (Class<? extends ISimulatorPart> partClass : simulatorParts) {
            try {
                ISimulatorPart part = partClass.newInstance();
                part.init(simulator);
                builder.addService(part);
            } catch (InstantiationException | IllegalAccessException e) {
                logger.warn("Can not create simulator part with class name: " + partClass.getName(), e);
            }
        }
        server = builder.build();
        try {
            server.start();
            return true;
        } catch (IOException e) {
            logger.error("Can not start server", e);
            return false;
        }
    }

    @Override
    public void close() {
        if (server != null) {
            try {
                server.shutdown().awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.debug("Can not wait to terminate server", e);
            }
        }

        if (simulator != null) {
            try {
                simulator.close();
            } catch (IOException e) {
                logger.error("Can not close simulator" ,e);
            }
        }
    }

    @Override
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private void loadTypes() {
        try {
            File fileOrDirectory = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            List<String> classesName = new ArrayList<>();
            if (fileOrDirectory.isDirectory()) {
                logger.info("Load from directory");
                classesName.addAll(loadClassesNameFromDirectory(fileOrDirectory));
            } else {
                logger.info("Load rule types from jar");
                classesName.addAll(loadClassesNameFromJar(fileOrDirectory));
            }

            ClassLoader loader = this.getClass().getClassLoader();

            for (String className : classesName) {
                try {
                    Class<?> _class = loader.loadClass(className);
                    checkClass(_class);
                } catch (ClassNotFoundException e) {
                    logger.error("Can not check class with name: " + className);
                }
            }
        } catch (URISyntaxException e) {
            logger.error("Can not load types", e);
        }
    }

    private Collection<? extends String> loadClassesNameFromJar(File fileOrDirectory) {
        List<String> result = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(fileOrDirectory))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.');
                    result.add(className.substring(0, className.length() - ".class".length()));
                }
            }

            return result;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    private Collection<? extends String> loadClassesNameFromDirectory(File dir) {
        try {
            Enumeration<URL> resources = this.getClass().getClassLoader().getResources("");

            List<String> classesName = new ArrayList<>();
            while (resources.hasMoreElements()) {
                File tmp = new File(resources.nextElement().getFile());
                classesName.addAll(loadClasses(tmp, tmp));
            }

            return classesName;
        } catch (IOException e) {
            logger.error("Can not get classpath directory from class loader", e);
            return loadClasses(dir, dir);
        }
    }

    private Collection<? extends String> loadClasses(File mainDirectory, File directory) {
        List<String> list = new ArrayList<>();

        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                list.addAll(loadClasses(mainDirectory, file));
            } else if (file.getName().endsWith(".class") || file.getName().endsWith(".kt")) {
                String className = mainDirectory
                        .toPath()
                        .relativize(file.toPath())
                        .toString()
                        .replace('/', '.')
                        .replace('\\', '.')
                        .replace(".class", "");
                list.add(className);
            }
        }
        return list;
    }

    private void checkClass(Class<?> partClass) {
        if (partClass == null) {
            return;
        }

        SimulatorPart annotation = partClass.getAnnotation(SimulatorPart.class);

        if (annotation == null) {
            return;
        }

        if (ISimulatorPart.class.isAssignableFrom(partClass)) {
            simulatorParts.add((Class<? extends ISimulatorPart>) partClass);
        } else {
            logger.error("Can not add simulator part with class name: " + partClass.getName());
        }
    }
}
