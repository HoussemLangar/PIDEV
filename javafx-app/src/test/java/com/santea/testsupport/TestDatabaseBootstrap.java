package com.santea.testsupport;

import com.santea.config.DatabaseConfig;
import com.santea.service.DatabaseService;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.StringJoiner;

public final class TestDatabaseBootstrap {

    private static final int MAX_CONNECT_RETRIES = 20;
    private static final long RETRY_SLEEP_MILLIS = 1000L;

    private static final int SEED_USER_ID = 900001;
    private static final int SEED_PATIENT_ID = 900001;
    private static final int SEED_SANTE_ID = 910001;
    private static final int SEED_SYMPTOME_ID = 920001;
    private static final int SEED_SYMPTOME_QUOTIDIEN_ID = 930001;

    private TestDatabaseBootstrap() {
    }

    public static void ensureSchemaAndSeed() {
        SQLException lastException = null;
        for (int attempt = 1; attempt <= MAX_CONNECT_RETRIES; attempt++) {
            try {
                DatabaseService databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
                try (Connection connection = databaseService.getConnection()) {
                    connection.setAutoCommit(false);
                    createSchema(connection);
                    seedData(connection);
                    connection.commit();
                    return;
                }
            } catch (SQLException exception) {
                lastException = exception;
                if (!isRetryable(exception)) {
                    break;
                }
                if (attempt < MAX_CONNECT_RETRIES) {
                    sleepBeforeRetry();
                }
            }
        }

        throw new RuntimeException(
                "Impossible d'initialiser la base de test apres " + MAX_CONNECT_RETRIES + " tentatives. Derniere erreur: "
                        + (lastException == null ? "inconnue" : lastException.getMessage()),
                lastException);
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_SLEEP_MILLIS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interruption pendant l'attente de la base de donnees.", interruptedException);
        }
    }

    private static boolean isRetryable(SQLException exception) {
        String sqlState = exception.getSQLState();
        if (sqlState != null) {
            // SQLSTATE class 08: connection issues.
            if (sqlState.startsWith("08")) {
                return true;
            }
            // Deadlock/serialization transient failures.
            if ("40001".equals(sqlState)) {
                return true;
            }
            // Integrity/constraint errors should fail fast.
            if (sqlState.startsWith("23")) {
                return false;
            }
        }

        int errorCode = exception.getErrorCode();
        return errorCode == 1205 || errorCode == 1213 || errorCode == 2002 || errorCode == 2003;
    }

    private static void createSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INT PRIMARY KEY,
                    email VARCHAR(255) NOT NULL,
                    prenom VARCHAR(255),
                    nom VARCHAR(255),
                    role VARCHAR(64)
                ) ENGINE=InnoDB
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS patient (
                    id INT PRIMARY KEY,
                    user_id INT,
                    CONSTRAINT fk_patient_user
                        FOREIGN KEY (user_id) REFERENCES users(id)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS sante_quotidienne (
                    id INT PRIMARY KEY,
                    user_id INT NOT NULL,
                    date DATE,
                    poids DOUBLE,
                    sommeil DOUBLE,
                    humeur VARCHAR(255),
                    activite_physique VARCHAR(255),
                    alimentation VARCHAR(255),
                    eau_bue DOUBLE,
                    tension_arterielle VARCHAR(64),
                    CONSTRAINT fk_sante_user
                        FOREIGN KEY (user_id) REFERENCES users(id)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS symptomes_liste (
                    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    nom VARCHAR(255) NOT NULL,
                    categorie VARCHAR(255),
                    created_at DATETIME
                ) ENGINE=InnoDB
                """);

            statement.execute("""
                CREATE TABLE IF NOT EXISTS symptomes_quotidiens (
                    id INT PRIMARY KEY,
                    patient_id INT NOT NULL,
                    symptome_id INT NOT NULL,
                    date_symptome DATE,
                    CONSTRAINT fk_symptomes_quotidiens_patient
                        FOREIGN KEY (patient_id) REFERENCES patient(id)
                        ON DELETE CASCADE,
                    CONSTRAINT fk_symptomes_quotidiens_symptome
                        FOREIGN KEY (symptome_id) REFERENCES symptomes_liste(id)
                        ON DELETE CASCADE
                ) ENGINE=InnoDB
                """);

            // Ensure legacy tables created without AUTO_INCREMENT are made writable for inserts.
            statement.execute("ALTER TABLE symptomes_liste MODIFY COLUMN id INT NOT NULL AUTO_INCREMENT");
            statement.execute("ALTER TABLE symptomes_liste AUTO_INCREMENT = 920100");
        }
    }

    private static void seedData(Connection connection) throws SQLException {
        upsertUser(connection);
        upsertPatient(connection);
        upsertSanteQuotidienne(connection);
        upsertSymptomeListe(connection);
        upsertSymptomeQuotidien(connection);
    }

    private static void upsertUser(Connection connection) throws SQLException {
        Set<String> userColumns = loadTableColumns(connection, "users");
        String seedEmail = "patient.test@santea.local";
        LocalDate seedBirthDate = LocalDate.of(1990, 1, 1);
        LocalDateTime now = LocalDateTime.now();

        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("id", SEED_USER_ID);
        putIfPresent(userColumns, values, "username", "patient.test." + SEED_USER_ID);
        putIfPresent(userColumns, values, "email", seedEmail);
        putIfPresent(userColumns, values, "password", "test-password-not-used");
        putIfPresent(userColumns, values, "prenom", "Patient");
        putIfPresent(userColumns, values, "nom", "Test");
        putIfPresent(userColumns, values, "role", "ROLE_PATIENT");
        putIfPresent(userColumns, values, "roles", "[\"ROLE_PATIENT\"]");
        putIfPresent(userColumns, values, "date_naissance", java.sql.Date.valueOf(seedBirthDate));
        putIfPresent(userColumns, values, "created_at", java.sql.Timestamp.valueOf(now));
        putIfPresent(userColumns, values, "updated_at", java.sql.Timestamp.valueOf(now));
        putIfPresent(userColumns, values, "email_verified", 1);
        putIfPresent(userColumns, values, "admin_approved", 1);
        putIfPresent(userColumns, values, "mfa_enabled", 0);
        putIfPresent(userColumns, values, "theme_preference", "light");
        putIfPresent(userColumns, values, "locale", "fr");
        putIfPresent(userColumns, values, "reminder_enabled", 1);
        putIfPresent(userColumns, values, "subscription_status", "PENDING");
        putIfPresent(userColumns, values, "is_banned", 0);

        if (!values.containsKey("email")) {
            throw new SQLException("La colonne obligatoire users.email est introuvable.");
        }

        StringJoiner columnsJoiner = new StringJoiner(", ");
        StringJoiner placeholdersJoiner = new StringJoiner(", ");
        List<String> updateAssignments = new ArrayList<>();

        for (String column : values.keySet()) {
            columnsJoiner.add(column);
            placeholdersJoiner.add("?");
            if (!"id".equals(column)) {
                updateAssignments.add(column + " = VALUES(" + column + ")");
            }
        }

        String sql = "INSERT INTO users (" + columnsJoiner + ") VALUES (" + placeholdersJoiner + ")"
                + " ON DUPLICATE KEY UPDATE " + String.join(", ", updateAssignments);

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            for (Object value : values.values()) {
                statement.setObject(parameterIndex++, value);
            }
            statement.executeUpdate();
        }
    }

    private static Set<String> loadTableColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        DatabaseMetaData metaData = connection.getMetaData();
        String catalog = connection.getCatalog();

        try (ResultSet resultSet = metaData.getColumns(catalog, null, tableName, null)) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME");
                if (columnName != null) {
                    columns.add(columnName.toLowerCase(Locale.ROOT));
                }
            }
        }

        return columns;
    }

    private static void putIfPresent(Set<String> availableColumns,
                                     Map<String, Object> values,
                                     String column,
                                     Object value) {
        if (availableColumns.contains(column.toLowerCase(Locale.ROOT))) {
            values.put(column, value);
        }
    }

    private static void upsertPatient(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO patient (id, user_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id)
            """)) {
            statement.setInt(1, SEED_PATIENT_ID);
            statement.setInt(2, SEED_USER_ID);
            statement.executeUpdate();
        }
    }

    private static void upsertSanteQuotidienne(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO sante_quotidienne (
                id, user_id, date, poids, sommeil, humeur,
                activite_physique, alimentation, eau_bue, tension_arterielle
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id),
                date = VALUES(date),
                poids = VALUES(poids),
                sommeil = VALUES(sommeil),
                humeur = VALUES(humeur),
                activite_physique = VALUES(activite_physique),
                alimentation = VALUES(alimentation),
                eau_bue = VALUES(eau_bue),
                tension_arterielle = VALUES(tension_arterielle)
            """)) {
            statement.setInt(1, SEED_SANTE_ID);
            statement.setInt(2, SEED_USER_ID);
            statement.setDate(3, java.sql.Date.valueOf(LocalDate.now()));
            statement.setDouble(4, 71.5);
            statement.setDouble(5, 7.5);
            statement.setString(6, "Bonne");
            statement.setString(7, "Marche");
            statement.setString(8, "Equilibree");
            statement.setDouble(9, 1.8);
            statement.setString(10, "120/80");
            statement.executeUpdate();
        }
    }

    private static void upsertSymptomeListe(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO symptomes_liste (id, nom, categorie, created_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                nom = VALUES(nom),
                categorie = VALUES(categorie),
                created_at = VALUES(created_at)
            """)) {
            statement.setInt(1, SEED_SYMPTOME_ID);
            statement.setString(2, "Migraine");
            statement.setString(3, "Neurologique");
            statement.setTimestamp(4, java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
            statement.executeUpdate();
        }
    }

    private static void upsertSymptomeQuotidien(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO symptomes_quotidiens (id, patient_id, symptome_id, date_symptome)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                patient_id = VALUES(patient_id),
                symptome_id = VALUES(symptome_id),
                date_symptome = VALUES(date_symptome)
            """)) {
            statement.setInt(1, SEED_SYMPTOME_QUOTIDIEN_ID);
            statement.setInt(2, SEED_PATIENT_ID);
            statement.setInt(3, SEED_SYMPTOME_ID);
            statement.setDate(4, java.sql.Date.valueOf(LocalDate.now()));
            statement.executeUpdate();
        }
    }
}