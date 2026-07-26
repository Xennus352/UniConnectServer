package com.unicconnect.rmi.config;

import com.unicconnect.rmi.remote.AcademicRemote;
import com.unicconnect.rmi.remote.AttendanceRemote;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;

@Configuration
public class RmiConfig {

    private static final Logger log = LoggerFactory.getLogger(RmiConfig.class);
    private static final int RMI_PORT = 1099;
    private boolean registryStarted = false;

    @PreDestroy
    public void shutdown() {
        log.info("RMI registry shutdown complete");
    }

    private void ensureRegistry() {
        if (registryStarted) return;
        try {
            LocateRegistry.createRegistry(RMI_PORT);
            log.info("RMI registry started on port {}", RMI_PORT);
        } catch (Exception e) {
            log.info("RMI registry already running");
        }
        registryStarted = true;
    }

    private void bind(String name, Remote obj) {
        ensureRegistry();
        try {
            Naming.rebind("rmi://localhost:" + RMI_PORT + "/" + name, obj);
            log.info("RMI bound: {}", name);
        } catch (Exception e) {
            log.error("Failed to bind RMI service: {}", name, e);
            throw new RuntimeException("RMI bind failed: " + name, e);
        }
    }

    @Bean
    public AcademicRemote academicRmiServer(com.unicconnect.rmi.server.AcademicRemoteServer server) {
        bind("AcademicService", server);
        return server;
    }

    @Bean
    public AttendanceRemote attendanceRmiServer(com.unicconnect.rmi.server.AttendanceRemoteServer server) {
        bind("AttendanceService", server);
        return server;
    }
}
