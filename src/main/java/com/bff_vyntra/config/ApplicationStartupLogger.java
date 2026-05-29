package com.bff_vyntra.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@Component
public class ApplicationStartupLogger implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupLogger.class);
    private final Environment environment;
    private DataSource dataSource;

    public ApplicationStartupLogger(Environment environment) {
        this.environment = environment;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        // 1. Fetch Server Properties
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

        // 2. Print Connection Block below the Banner
        System.out.println("==========================================================");
        System.out.println("  APPLICATION ENDPOINTS");
        System.out.println("----------------------------------------------------------");
        System.out.println("  Local:      http://localhost:" + port + contextPath);
        System.out.println("  External:   http://" + hostAddress + ":" + port + contextPath);
        System.out.println("==========================================================");

        // 3. Fetch and Print Database Metadata
        if (dataSource != null) {
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                String jdbcUrl = metaData.getURL();
                String dbProduct = metaData.getDatabaseProductName();
                String dbVersion = metaData.getDatabaseProductVersion();
                String username = metaData.getUserName();

                System.out.println("  DATABASE CONNECTION");
                System.out.println("----------------------------------------------------------");
                System.out.println("  Status:     CONNECTED SUCCESSFULLY");
                System.out.println("  Engine:     " + dbProduct + " (v" + dbVersion + ")");
                System.out.println("  JDBC URL:   " + jdbcUrl);
                System.out.println("  User:       " + username);
                System.out.println("==========================================================\n");

            } catch (SQLException e) {
                System.err.println("  DATABASE CONNECTION");
                System.err.println("----------------------------------------------------------");
                System.err.println("  Status:     CONNECTION FAILED ❌");
                System.err.println("  Error:      " + e.getMessage());
                System.err.println("==========================================================\n");
            }
        }
    }
}