package com.santea.repository;

import com.santea.model.UserScoreHistory;
import com.santea.model.UserScoreSnapshotType;
import com.santea.service.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository JDBC pour l'historique des scores IA utilisateur.
 * Miroir du Symfony UserScoreHistoryRepository.
 */
public class UserScoreHistoryRepository {

    private final DatabaseService databaseService;

    public UserScoreHistoryRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
        ensureTable();
    }

    /** Vérifie si un snapshot quotidien existe déjà pour cet utilisateur aujourd'hui. */
    public boolean hasSnapshotForDate(int userId, LocalDateTime day) {
        LocalDateTime start = day.toLocalDate().atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        String sql = "SELECT COUNT(*) FROM user_score_history "
                + "WHERE user_id = ? AND created_at >= ? AND created_at < ?";

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setTimestamp(2, Timestamp.valueOf(start));
            ps.setTimestamp(3, Timestamp.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /** Vérifie si le score a atteint le seuil entre deux dates (pour l'éligibilité mois offert). */
    public boolean hasReachedThresholdBetween(int userId, int threshold,
                                              LocalDateTime start, LocalDateTime end) {
        String sql = "SELECT COUNT(*) FROM user_score_history "
                + "WHERE user_id = ? AND score >= ? AND created_at >= ? AND created_at < ?";

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, threshold);
            ps.setTimestamp(3, Timestamp.valueOf(start));
            ps.setTimestamp(4, Timestamp.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    /** Enregistre un snapshot quotidien du score. Retourne true si insertion effectuée. */
    public boolean insertSnapshot(int userId, int score, int activityScore,
                                  int seniorityScore, int ruleComplianceScore,
                                  int sanctionsHistoryScore, UserScoreSnapshotType type) {

        if (hasSnapshotForDate(userId, LocalDateTime.now())) {
            return false;
        }

        String sql = "INSERT INTO user_score_history "
                + "(user_id, score, activity_score, seniority_score, rule_compliance_score, "
                + "sanctions_history_score, snapshot_type, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, score);
            ps.setInt(3, activityScore);
            ps.setInt(4, seniorityScore);
            ps.setInt(5, ruleComplianceScore);
            ps.setInt(6, sanctionsHistoryScore);
            ps.setString(7, type.getValue());
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            return false;
        }
    }

    /** Retourne les N derniers snapshots pour un utilisateur, ordre DESC. */
    public List<UserScoreHistory> findRecentForUser(int userId, int limit) {
        String sql = "SELECT id, user_id, score, activity_score, seniority_score, "
                + "rule_compliance_score, sanctions_history_score, snapshot_type, created_at "
                + "FROM user_score_history WHERE user_id = ? "
                + "ORDER BY created_at DESC LIMIT ?";

        List<UserScoreHistory> result = new ArrayList<>();

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            // retourne liste vide silencieusement
        }

        return result;
    }

    // ─── mapping ─────────────────────────────────────────────────────────────

    private UserScoreHistory mapRow(ResultSet rs) throws SQLException {
        UserScoreHistory h = new UserScoreHistory();
        h.setId(rs.getInt("id"));
        h.setScore(rs.getInt("score"));
        h.setActivityScore(rs.getInt("activity_score"));
        h.setSeniorityScore(rs.getInt("seniority_score"));
        h.setRuleComplianceScore(rs.getInt("rule_compliance_score"));
        h.setSanctionsHistoryScore(rs.getInt("sanctions_history_score"));

        String typeStr = rs.getString("snapshot_type");
        h.setSnapshotType(UserScoreSnapshotType.fromValue(typeStr));

        Timestamp ts = rs.getTimestamp("created_at");
        if (ts != null) {
            h.setCreatedAt(ts.toLocalDateTime());
        }

        return h;
    }

    // ─── DDL auto ─────────────────────────────────────────────────────────────

    private void ensureTable() {
        String ddl = "CREATE TABLE IF NOT EXISTS user_score_history ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "user_id INT NOT NULL, "
                + "score SMALLINT NOT NULL DEFAULT 0, "
                + "activity_score SMALLINT NOT NULL DEFAULT 0, "
                + "seniority_score SMALLINT NOT NULL DEFAULT 0, "
                + "rule_compliance_score SMALLINT NOT NULL DEFAULT 0, "
                + "sanctions_history_score SMALLINT NOT NULL DEFAULT 0, "
                + "snapshot_type VARCHAR(50) NOT NULL DEFAULT 'daily', "
                + "created_at DATETIME NOT NULL DEFAULT NOW(), "
                + "INDEX idx_user_score_history_user_created (user_id, created_at)"
                + ")";

        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(ddl)) {
            ps.execute();
        } catch (SQLException ignored) {
            // Table peut déjà exister avec un schéma différent (Symfony)
        }
    }
}
