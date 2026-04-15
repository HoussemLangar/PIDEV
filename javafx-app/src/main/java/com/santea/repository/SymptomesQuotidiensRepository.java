package com.santea.repository;

import com.santea.service.DatabaseService;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SymptomesQuotidiensRepository {
    private final DatabaseService databaseService;

    public SymptomesQuotidiensRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public List<SymptomeChoiceRow> findAvailableSymptoms() {
        List<SymptomeChoiceRow> rows = new ArrayList<>();
        String sql = "SELECT id, nom, categorie FROM symptomes_liste ORDER BY nom ASC";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                rows.add(new SymptomeChoiceRow(
                        resultSet.getInt("id"),
                        safe(resultSet.getString("nom")),
                        safe(resultSet.getString("categorie"))
                ));
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public SaveDailyOutcome saveDailySymptoms(int userId,
                                              List<Integer> symptomIds,
                                              LocalDate symptomDate,
                                              Integer intensity,
                                              String duration,
                                              String notes) {
        String existsSql = "SELECT 1 FROM symptomes_quotidiens WHERE patient_id=? AND symptome_id=? AND date_symptome=? LIMIT 1";
        String insertSql = "INSERT INTO symptomes_quotidiens (patient_id, symptome_id, date_symptome, intensite, duree, notes, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, NOW())";

        try (Connection connection = databaseService.getConnection()) {
            connection.setAutoCommit(false);

            try {
                int patientId = resolveOrCreatePatientId(connection, userId);
                Set<Integer> distinctIds = new LinkedHashSet<>(symptomIds);

                int inserted = 0;
                int skipped = 0;

                try (PreparedStatement existsStatement = connection.prepareStatement(existsSql);
                     PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {

                    for (Integer symptomId : distinctIds) {
                        if (symptomId == null || symptomId <= 0) {
                            continue;
                        }

                        existsStatement.setInt(1, patientId);
                        existsStatement.setInt(2, symptomId);
                        existsStatement.setDate(3, Date.valueOf(symptomDate));

                        boolean alreadyExists;
                        try (ResultSet resultSet = existsStatement.executeQuery()) {
                            alreadyExists = resultSet.next();
                        }

                        if (alreadyExists) {
                            skipped++;
                            continue;
                        }

                        insertStatement.setInt(1, patientId);
                        insertStatement.setInt(2, symptomId);
                        insertStatement.setDate(3, Date.valueOf(symptomDate));
                        insertStatement.setInt(4, intensity);
                        insertStatement.setString(5, trimToNull(duration));
                        insertStatement.setString(6, trimToNull(notes));
                        insertStatement.addBatch();
                        inserted++;
                    }

                    if (inserted > 0) {
                        insertStatement.executeBatch();
                    }
                }

                connection.commit();
                return SaveDailyOutcome.success(inserted, skipped);
            } catch (SQLException exception) {
                connection.rollback();
                return SaveDailyOutcome.failure(exception.getMessage());
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            return SaveDailyOutcome.failure(exception.getMessage());
        }
    }

    public List<SymptomeDailyRowData> findRowsForUserBetweenDates(int userId, LocalDate start, LocalDate end) {
        List<SymptomeDailyRowData> rows = new ArrayList<>();

        String sql = "SELECT sq.id, sq.symptome_id, sq.date_symptome, sq.intensite, sq.duree, sq.notes, sl.nom, sl.categorie "
                + "FROM symptomes_quotidiens sq "
                + "JOIN symptomes_liste sl ON sl.id = sq.symptome_id "
                + "JOIN patients p ON p.id = sq.patient_id "
                + "WHERE p.user_id=? AND sq.date_symptome BETWEEN ? AND ? "
                + "ORDER BY sq.date_symptome DESC, sq.created_at DESC, sl.nom ASC";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setDate(2, Date.valueOf(start));
            statement.setDate(3, Date.valueOf(end));

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(new SymptomeDailyRowData(
                            resultSet.getInt("id"),
                            resultSet.getInt("symptome_id"),
                            safe(resultSet.getString("nom")),
                            safe(resultSet.getString("categorie")),
                            resultSet.getInt("intensite"),
                            safe(resultSet.getString("duree")),
                            safe(resultSet.getString("notes")),
                            resultSet.getDate("date_symptome").toLocalDate()
                    ));
                }
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public MutationOutcome updateSymptomEntry(int userId,
                                              int entryId,
                                              int symptomId,
                                              LocalDate symptomDate,
                                              Integer intensity,
                                              String duration,
                                              String notes) {
        String sql = "UPDATE symptomes_quotidiens sq "
                + "JOIN patients p ON p.id = sq.patient_id "
                + "SET sq.symptome_id=?, sq.date_symptome=?, sq.intensite=?, sq.duree=?, sq.notes=? "
                + "WHERE sq.id=? AND p.user_id=?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, symptomId);
            statement.setDate(2, Date.valueOf(symptomDate));
            statement.setInt(3, intensity);
            statement.setString(4, trimToNull(duration));
            statement.setString(5, trimToNull(notes));
            statement.setInt(6, entryId);
            statement.setInt(7, userId);

            int changed = statement.executeUpdate();
            if (changed != 1) {
                return MutationOutcome.failure("entree introuvable");
            }
            return MutationOutcome.ok();
        } catch (SQLException exception) {
            return MutationOutcome.failure(exception.getMessage());
        }
    }

    public MutationOutcome deleteSymptomEntry(int userId, int entryId) {
        String sql = "DELETE sq FROM symptomes_quotidiens sq "
                + "JOIN patients p ON p.id = sq.patient_id "
                + "WHERE sq.id=? AND p.user_id=?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, entryId);
            statement.setInt(2, userId);

            int changed = statement.executeUpdate();
            if (changed != 1) {
                return MutationOutcome.failure("entree introuvable");
            }
            return MutationOutcome.ok();
        } catch (SQLException exception) {
            return MutationOutcome.failure(exception.getMessage());
        }
    }

    private int resolveOrCreatePatientId(Connection connection, int userId) throws SQLException {
        String selectSql = "SELECT id FROM patients WHERE user_id=? LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }

        String insertSql = "INSERT INTO patients (user_id, created_at, updated_at) VALUES (?, NOW(), NOW())";
        try (PreparedStatement statement = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, userId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        try (PreparedStatement retry = connection.prepareStatement(selectSql)) {
            retry.setInt(1, userId);
            try (ResultSet resultSet = retry.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }

        throw new SQLException("Impossible de resoudre patient_id pour user_id=" + userId);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record SymptomeChoiceRow(int id, String nom, String categorie) {
    }

    public record SymptomeDailyRowData(int id,
                                       int symptomId,
                                       String symptomName,
                                       String category,
                                       int intensity,
                                       String duration,
                                       String notes,
                                       LocalDate date) {
    }

    public record SaveDailyOutcome(boolean success, int insertedCount, int skippedCount, String errorMessage) {
        public static SaveDailyOutcome success(int insertedCount, int skippedCount) {
            return new SaveDailyOutcome(true, insertedCount, skippedCount, null);
        }

        public static SaveDailyOutcome failure(String errorMessage) {
            return new SaveDailyOutcome(false, 0, 0, errorMessage);
        }
    }

    public record MutationOutcome(boolean success, String errorMessage) {
        public static MutationOutcome ok() {
            return new MutationOutcome(true, null);
        }

        public static MutationOutcome failure(String errorMessage) {
            return new MutationOutcome(false, errorMessage);
        }
    }
}
