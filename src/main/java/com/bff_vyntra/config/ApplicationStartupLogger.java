package com.bff_vyntra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ApplicationStartupLogger implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupLogger.class);
    private final Environment environment;

    public ApplicationStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String port = environment.getProperty("local.server.port");
        if (port == null) {
            port = environment.getProperty("server.port", "8080");
        }
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Could not determine local host address.");
        }

        log.info("Application endpoints: local=http://localhost:{}{}, external=http://{}:{}{}",
                port, contextPath, hostAddress, port, contextPath);
    }
}
