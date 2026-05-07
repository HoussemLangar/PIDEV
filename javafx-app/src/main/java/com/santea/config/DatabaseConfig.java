package com.santea.config;

import java.io.File;

public class DatabaseConfig {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    public DatabaseConfig(String host, int port, String database, String username, String password) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    public static DatabaseConfig fromEnvironment() {
        String host = getOrDefault("DB_HOST", defaultHost());
        int port = parsePort(getOrDefault("DB_PORT", "3306"));
        String database = getOrDefault("DB_NAME", "pidev");
        String username = getOrDefault("DB_USER", "symfony");
        String password = getOrDefault("DB_PASSWORD", "symfony");

        return new DatabaseConfig(host, port, database, username, password);
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    private static String getOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int parsePort(String rawPort) {
        try {
            return Integer.parseInt(rawPort == null ? "3306" : rawPort.trim());
        } catch (NumberFormatException exception) {
            return 3306;
        }
    }

    private static String defaultHost() {
        // In Docker Compose network, the DB service is reachable as "db".
        if (new File("/.dockerenv").exists()) {
            return "db";
        }
        return "127.0.0.1";
    }
}
