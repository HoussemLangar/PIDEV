package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.SanteQuotidienne;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SanteQuotidienneService {
    private final DatabaseService databaseService;

    public SanteQuotidienneService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public CrudResult create(int userId, SanteQuotidienne item) {
        LocalDate targetDate = (item.getDate() == null ? LocalDateTime.now() : item.getDate()).toLocalDate();
        if (existsEntryOnDate(userId, targetDate, null)) {
            return CrudResult.failure("Un enregistrement existe deja pour cette date. Utilisez la mise a jour.");
        }

        String sql = "INSERT INTO sante_quotidienne "
                + "(poids, taille, imc, tension_arterielle, sommeil, activite_physique, humeur, alimentation, eau_bue, date, user_id, pas, calories, duree_activite_minutes, source_donnees) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            fillStatement(statement, item, userId, false);
            int changed = statement.executeUpdate();
            if (changed != 1) {
                return CrudResult.failure("Impossible d'enregistrer l'entree.");
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return CrudResult.success("Entree enregistree en base.", keys.getInt(1));
                }
            }
            return CrudResult.success("Entree enregistree en base.", null);
        } catch (SQLException exception) {
            return CrudResult.failure("Erreur SQL (create): " + exception.getMessage());
        }
    }

    public CrudResult update(int userId, SanteQuotidienne item) {
        if (item == null || item.getId() == null || item.getId() <= 0) {
            return CrudResult.failure("ID d'entree invalide pour la mise a jour.");
        }

        LocalDate targetDate = (item.getDate() == null ? LocalDateTime.now() : item.getDate()).toLocalDate();
        if (existsEntryOnDate(userId, targetDate, item.getId())) {
            return CrudResult.failure("Impossible de modifier: une autre entree existe deja pour cette date.");
        }

        String sql = "UPDATE sante_quotidienne SET "
                + "poids=?, taille=?, imc=?, tension_arterielle=?, sommeil=?, activite_physique=?, humeur=?, alimentation=?, eau_bue=?, date=?, pas=?, calories=?, duree_activite_minutes=?, source_donnees=? "
                + "WHERE id=? AND user_id=?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            fillStatement(statement, item, userId, true);
            int changed = statement.executeUpdate();
            if (changed != 1) {
                return CrudResult.failure("Mise a jour impossible (entree introuvable).");
            }
            return CrudResult.success("Entree mise a jour en base.", item.getId());
        } catch (SQLException exception) {
            return CrudResult.failure("Erreur SQL (update): " + exception.getMessage());
        }
    }

    public CrudResult delete(int userId, int entryId) {
        String sql = "DELETE FROM sante_quotidienne WHERE id=? AND user_id=?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, entryId);
            statement.setInt(2, userId);
            int changed = statement.executeUpdate();
            if (changed != 1) {
                return CrudResult.failure("Suppression impossible (entree introuvable).");
            }
            return CrudResult.success("Entree supprimee en base.", entryId);
        } catch (SQLException exception) {
            return CrudResult.failure("Erreur SQL (delete): " + exception.getMessage());
        }
    }

    public List<SanteQuotidienne> findByUserAndDate(int userId, LocalDate date) {
        List<SanteQuotidienne> rows = new ArrayList<>();
        String sql = "SELECT id, poids, taille, imc, tension_arterielle, sommeil, activite_physique, humeur, alimentation, eau_bue, date, pas, calories, duree_activite_minutes, source_donnees "
                + "FROM sante_quotidienne "
                + "WHERE user_id=? AND date >= ? AND date < ? "
                + "ORDER BY date DESC";

        LocalDate safeDate = date == null ? LocalDate.now() : date;
        Timestamp start = Timestamp.valueOf(safeDate.atStartOfDay());
        Timestamp end = Timestamp.valueOf(safeDate.plusDays(1).atStartOfDay());

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setTimestamp(2, start);
            statement.setTimestamp(3, end);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapRow(rs));
                }
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public Stats loadStats(int userId) {
        String sql = "SELECT COUNT(*) AS total, COALESCE(AVG(imc),0) AS avg_imc, COALESCE(SUM(COALESCE(pas,0)),0) AS total_steps "
                + "FROM sante_quotidienne WHERE user_id=?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return new Stats(
                            rs.getInt("total"),
                            rs.getDouble("avg_imc"),
                            rs.getInt("total_steps")
                    );
                }
            }
        } catch (SQLException ignored) {
        }

        return new Stats(0, 0.0, 0);
    }

    private void fillStatement(PreparedStatement statement, SanteQuotidienne item, int userId, boolean includeWhere) throws SQLException {
        statement.setDouble(1, safeDouble(item.getPoids()));
        statement.setDouble(2, safeDouble(item.getTaille()));

        if (item.getImc() == null) {
            statement.setNull(3, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(3, item.getImc());
        }

        if (item.getTensionArterielle() == null) {
            statement.setNull(4, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(4, item.getTensionArterielle());
        }

        if (item.getSommeil() == null) {
            statement.setNull(5, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(5, item.getSommeil());
        }

        statement.setString(6, safeTrim(item.getActivitePhysique(), 20));
        statement.setString(7, serializeMood(item.getHumeur()));
        statement.setString(8, safeTrim(item.getAlimentation(), 20));

        if (item.getEauBue() == null) {
            statement.setNull(9, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(9, item.getEauBue());
        }

        statement.setTimestamp(10, Timestamp.valueOf(item.getDate() == null ? LocalDateTime.now() : item.getDate()));

        if (!includeWhere) {
            statement.setInt(11, userId);
        }

        int offset = includeWhere ? 10 : 11;

        if (item.getPas() == null) {
            statement.setNull(offset + 1, java.sql.Types.INTEGER);
        } else {
            statement.setInt(offset + 1, item.getPas());
        }

        if (item.getCalories() == null) {
            statement.setNull(offset + 2, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(offset + 2, item.getCalories());
        }

        if (item.getDureeActiviteMinutes() == null) {
            statement.setNull(offset + 3, java.sql.Types.INTEGER);
        } else {
            statement.setInt(offset + 3, item.getDureeActiviteMinutes());
        }

        String source = item.getSourceDonnees() == null ? "manuel" : item.getSourceDonnees().getValue();
        statement.setString(offset + 4, source);

        if (includeWhere) {
            statement.setInt(offset + 5, item.getId());
            statement.setInt(offset + 6, userId);
        }
    }

    private SanteQuotidienne mapRow(ResultSet rs) throws SQLException {
        SanteQuotidienne item = new SanteQuotidienne();
        item.setId(rs.getInt("id"));
        item.setPoids(rs.getDouble("poids"));
        item.setTaille(rs.getDouble("taille"));
        item.setImc((Double) rs.getObject("imc"));
        item.setTensionArterielle((Double) rs.getObject("tension_arterielle"));
        item.setSommeil((Double) rs.getObject("sommeil"));
        item.setActivitePhysique(rs.getString("activite_physique"));
        item.setHumeur(deserializeMood(rs.getString("humeur")));
        item.setAlimentation(rs.getString("alimentation"));
        item.setEauBue((Double) rs.getObject("eau_bue"));

        Timestamp ts = rs.getTimestamp("date");
        item.setDate(ts == null ? LocalDateTime.now() : ts.toLocalDateTime());

        Number pasNumber = (Number) rs.getObject("pas");
        item.setPas(pasNumber == null ? null : pasNumber.intValue());

        Number caloriesNumber = (Number) rs.getObject("calories");
        item.setCalories(caloriesNumber == null ? null : (int) Math.round(caloriesNumber.doubleValue()));

        Number dureeNumber = (Number) rs.getObject("duree_activite_minutes");
        item.setDureeActiviteMinutes(dureeNumber == null ? null : dureeNumber.intValue());

        item.setSourceDonnees(com.santea.model.SanteDataSource.fromValue(rs.getString("source_donnees")));
        return item;
    }

    private List<Object> deserializeMood(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }

        String normalized = raw.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.replace("\"", "");

        List<Object> values = new ArrayList<>();
        Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .forEach(values::add);
        return values;
    }

    private String serializeMood(List<Object> moods) {
        if (moods == null || moods.isEmpty()) {
            return "";
        }
        return moods.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(v -> !v.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String safeTrim(String value, int maxLen) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() <= maxLen) {
            return safe;
        }
        return safe.substring(0, maxLen);
    }

    private double safeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private boolean existsEntryOnDate(int userId, LocalDate date, Integer excludeId) {
        String sql = "SELECT id FROM sante_quotidienne "
                + "WHERE user_id = ? AND date >= ? AND date < ? "
                + (excludeId == null ? "" : "AND id <> ? ")
                + "LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp start = Timestamp.valueOf(date.atStartOfDay());
            Timestamp end = Timestamp.valueOf(date.plusDays(1).atStartOfDay());

            statement.setInt(1, userId);
            statement.setTimestamp(2, start);
            statement.setTimestamp(3, end);
            if (excludeId != null) {
                statement.setInt(4, excludeId);
            }

            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    public record Stats(int totalEntries, double averageImc, int totalSteps) {
    }

    public record CrudResult(boolean success, String message, Integer entryId) {
        public static CrudResult success(String message, Integer entryId) {
            return new CrudResult(true, message, entryId);
        }

        public static CrudResult failure(String message) {
            return new CrudResult(false, message, null);
        }
    }
}
