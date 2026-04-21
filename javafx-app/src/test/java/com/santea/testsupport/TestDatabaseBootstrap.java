package com.santea.testsupport;

import com.santea.config.DatabaseConfig;
import com.santea.service.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

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
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO users (id, email, prenom, nom, role)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                email = VALUES(email),
                prenom = VALUES(prenom),
                nom = VALUES(nom),
                role = VALUES(role)
            """)) {
            statement.setInt(1, SEED_USER_ID);
            statement.setString(2, "patient.test@santea.local");
            statement.setString(3, "Patient");
            statement.setString(4, "Test");
            statement.setString(5, "ROLE_PATIENT");
            statement.executeUpdate();
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