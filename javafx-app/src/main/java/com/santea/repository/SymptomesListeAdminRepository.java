package com.santea.repository;

import com.santea.service.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SymptomesListeAdminRepository {
    private final DatabaseService databaseService;

    public SymptomesListeAdminRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public List<SymptomRowData> findAllSymptoms() {
        return findFilteredSymptoms(null, null, null);
    }

    public List<SymptomRowData> findFilteredSymptoms(String keyword, LocalDate createdFrom, LocalDate createdTo) {
        List<SymptomRowData> rows = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, nom, categorie, created_at FROM symptomes_liste WHERE 1=1");

        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        if (!normalizedKeyword.isEmpty()) {
            sql.append(" AND (LOWER(nom) LIKE ? OR LOWER(categorie) LIKE ?)");
        }
        if (createdFrom != null) {
            sql.append(" AND DATE(created_at) >= ?");
        }
        if (createdTo != null) {
            sql.append(" AND DATE(created_at) <= ?");
        }
        sql.append(" ORDER BY created_at DESC, id DESC");

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            if (!normalizedKeyword.isEmpty()) {
                String pattern = "%" + normalizedKeyword + "%";
                statement.setString(index++, pattern);
                statement.setString(index++, pattern);
            }
            if (createdFrom != null) {
                statement.setDate(index++, java.sql.Date.valueOf(createdFrom));
            }
            if (createdTo != null) {
                statement.setDate(index++, java.sql.Date.valueOf(createdTo));
            }

            try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                Timestamp createdAt = resultSet.getTimestamp("created_at");
                rows.add(new SymptomRowData(
                        resultSet.getInt("id"),
                        safe(resultSet.getString("nom")),
                        safe(resultSet.getString("categorie")),
                        createdAt == null ? null : createdAt.toLocalDateTime()
                ));
            }
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public MutationResult insertSymptom(String nom, String categorie, LocalDateTime createdAt) {
        String sql = "INSERT INTO symptomes_liste (nom, categorie, created_at) VALUES (?, ?, ?)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, nom);
            statement.setString(2, categorie);
            statement.setTimestamp(3, Timestamp.valueOf(createdAt));

            int changed = statement.executeUpdate();
            if (changed != 1) {
                return MutationResult.failure("Insertion impossible.");
            }

            Integer generatedId = null;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    generatedId = keys.getInt(1);
                }
            }
            return MutationResult.success(generatedId);
        } catch (SQLException exception) {
            return MutationResult.failure(exception.getMessage());
        }
    }

    public MutationResult updateSymptom(int id, String nom, String categorie, LocalDateTime createdAt) {
        String sql = "UPDATE symptomes_liste SET nom=?, categorie=?, created_at=? WHERE id=?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, nom);
            statement.setString(2, categorie);
            statement.setTimestamp(3, Timestamp.valueOf(createdAt));
            statement.setInt(4, id);

            int changed = statement.executeUpdate();
            if (changed != 1) {
                return MutationResult.failure("Symptome introuvable.");
            }
            return MutationResult.success(id);
        } catch (SQLException exception) {
            return MutationResult.failure(exception.getMessage());
        }
    }

    public MutationResult deleteSymptom(int id) {
        String sql = "DELETE FROM symptomes_liste WHERE id=?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, id);
            int changed = statement.executeUpdate();
            if (changed != 1) {
                return MutationResult.failure("Symptome introuvable.");
            }
            return MutationResult.success(id);
        } catch (SQLException exception) {
            return MutationResult.failure(exception.getMessage());
        }
    }

    public List<SymptomPieStatData> findTodayUsageStats() {
        List<SymptomPieStatData> rows = new ArrayList<>();
        String sql = "SELECT sl.nom, COUNT(DISTINCT sq.patient_id) AS patients_count "
                + "FROM symptomes_liste sl "
                + "LEFT JOIN symptomes_quotidiens sq "
                + "ON sq.symptome_id = sl.id AND sq.date_symptome = CURDATE() "
                + "GROUP BY sl.id, sl.nom "
                + "HAVING patients_count > 0 "
                + "ORDER BY patients_count DESC, sl.nom ASC";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                rows.add(new SymptomPieStatData(
                        safe(resultSet.getString("nom")),
                        resultSet.getInt("patients_count")
                ));
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public RegistrationStatsData getRegistrationStats() {
        String sql = "SELECT "
                + "COUNT(DISTINCT p.id) AS total_patients, "
                + "COUNT(DISTINCT sq.patient_id) AS patients_with_symptoms "
                + "FROM patient p "
                + "LEFT JOIN symptomes_quotidiens sq ON sq.patient_id = p.id";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return new RegistrationStatsData(
                        resultSet.getInt("patients_with_symptoms"),
                        resultSet.getInt("total_patients")
                );
            }
        } catch (SQLException ignored) {
        }

        return new RegistrationStatsData(0, 0);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record SymptomRowData(int id, String nom, String categorie, LocalDateTime createdAt) {
    }

    public record SymptomPieStatData(String nom, int patientsCount) {
    }

    public record RegistrationStatsData(int withSymptoms, int totalPatients) {
    }

    public record MutationResult(boolean success, Integer id, String errorMessage) {
        public static MutationResult success(Integer id) {
            return new MutationResult(true, id, null);
        }

        public static MutationResult failure(String errorMessage) {
            return new MutationResult(false, null, errorMessage == null ? "Erreur SQL inconnue." : errorMessage);
        }
    }
}
