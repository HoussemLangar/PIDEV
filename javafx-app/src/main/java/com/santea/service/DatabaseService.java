package com.santea.service;

import com.santea.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseService {
    private final DatabaseConfig config;
    private String lastConnectionError = "";

    public DatabaseService(DatabaseConfig config) {
        this.config = config;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.getUsername(), config.getPassword());
    }

    public boolean canConnect() {
        try (Connection ignored = getConnection()) {
            lastConnectionError = "";
            return true;
        } catch (SQLException exception) {
            lastConnectionError = exception.getMessage() == null ? "Erreur JDBC inconnue." : exception.getMessage();
            return false;
        }
    }

    public String getLastConnectionError() {
        return lastConnectionError;
    }
}
