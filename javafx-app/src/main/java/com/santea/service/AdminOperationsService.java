package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class AdminOperationsService {
    private final DatabaseService databaseService;

    public AdminOperationsService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public ActionResult approveProfessionalAccount(int userId, String approvedRole) {
        if (userId <= 0) {
            return ActionResult.failure("Utilisateur invalide.");
        }

        String role = normalizeRole(approvedRole);
        if (role.isBlank()) {
            return ActionResult.failure("Role professionnel invalide.");
        }

        String sql = "UPDATE users SET admin_approved = 1, role = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, role);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(3, userId);
            int changed = statement.executeUpdate();
            if (changed == 1) {
                return ActionResult.success("Compte professionnel approuve.");
            }
            return ActionResult.failure("Utilisateur introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Approbation impossible: " + exception.getMessage());
        }
    }

    public ActionResult banUser(int userId, String reason, LocalDateTime until) {
        if (userId <= 0) {
            return ActionResult.failure("Utilisateur invalide.");
        }

        String sql = "UPDATE users SET is_banned = 1, ban_reason = ?, ban_until = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safe(reason));
            if (until == null) {
                statement.setNull(2, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(2, Timestamp.valueOf(until));
            }
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(4, userId);
            int changed = statement.executeUpdate();
            return changed == 1 ? ActionResult.success("Utilisateur banni.") : ActionResult.failure("Utilisateur introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Bannissement impossible: " + exception.getMessage());
        }
    }

    public ActionResult unbanUser(int userId) {
        String sql = "UPDATE users SET is_banned = 0, ban_reason = NULL, ban_until = NULL, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(2, userId);
            int changed = statement.executeUpdate();
            return changed == 1 ? ActionResult.success("Utilisateur debanni.") : ActionResult.failure("Utilisateur introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Debannissement impossible: " + exception.getMessage());
        }
    }

    public List<ActiveSessionRow> listActiveSessions() {
        ensureSessionTable();
        String sql = "SELECT id, user_id, session_token, device_label, ip_address, created_at, last_seen_at FROM user_sessions WHERE is_active = 1 ORDER BY last_seen_at DESC LIMIT 50";
        List<ActiveSessionRow> rows = new ArrayList<>();

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new ActiveSessionRow(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("session_token"),
                        rs.getString("device_label"),
                        rs.getString("ip_address"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("last_seen_at"))
                ));
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public ActionResult revokeSession(int sessionId) {
        ensureSessionTable();
        String sql = "UPDATE user_sessions SET is_active = 0, revoked_at = ?, last_seen_at = ? WHERE id = ?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setTimestamp(1, now);
            statement.setTimestamp(2, now);
            statement.setInt(3, sessionId);
            int changed = statement.executeUpdate();
            return changed == 1 ? ActionResult.success("Session revoquee.") : ActionResult.failure("Session introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Revocation impossible: " + exception.getMessage());
        }
    }

    public List<SuspiciousLoginRow> listSuspiciousLogins() {
        ensureSuspiciousTable();
        String sql = "SELECT id, user_id, email, ip_address, reason, blocked, created_at FROM suspicious_logins ORDER BY created_at DESC LIMIT 100";
        List<SuspiciousLoginRow> rows = new ArrayList<>();

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new SuspiciousLoginRow(
                        rs.getInt("id"),
                        rs.getInt("user_id"),
                        rs.getString("email"),
                        rs.getString("ip_address"),
                        rs.getString("reason"),
                        rs.getBoolean("blocked"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ));
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public ActionResult moderateContent(int contentId, String moderationStatus, String reason) {
        String sql = "UPDATE contenus SET moderation_status = ?, moderation_reason = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safe(moderationStatus));
            statement.setString(2, safe(reason));
            statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(4, contentId);
            int changed = statement.executeUpdate();
            return changed == 1 ? ActionResult.success("Moderation appliquee.") : ActionResult.failure("Contenu introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Moderation non disponible (table contenus absente ou schema different): " + exception.getMessage());
        }
    }

    public ActionResult exportCsvReports() {
        if (!databaseService.canConnect()) {
            return ActionResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        try {
            Path exportDir = Paths.get("generated-exports");
            Files.createDirectories(exportDir);

            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            Path usersCsv = exportDir.resolve("users_" + stamp + ".csv");
            Path paymentsCsv = exportDir.resolve("payments_" + stamp + ".csv");
            Path statsCsv = exportDir.resolve("stats_" + stamp + ".csv");

            writeUsersCsv(usersCsv);
            writePaymentsCsv(paymentsCsv);
            writeStatsCsv(statsCsv);

            return ActionResult.success("Exports CSV crees: " + usersCsv + " | " + paymentsCsv + " | " + statsCsv);
        } catch (Exception exception) {
            return ActionResult.failure("Export CSV impossible: " + exception.getMessage());
        }
    }

    public AdminVoiceResult handleVoiceCommand(String command) {
        String normalized = safe(command).toLowerCase();
        if (normalized.isBlank()) {
            return AdminVoiceResult.failure("Commande vide.");
        }

        if (normalized.contains("export") && normalized.contains("csv")) {
            ActionResult export = exportCsvReports();
            return export.success() ? AdminVoiceResult.success(export.message()) : AdminVoiceResult.failure(export.message());
        }

        if (normalized.contains("sessions actives")) {
            List<ActiveSessionRow> sessions = listActiveSessions();
            return AdminVoiceResult.success("Sessions actives: " + sessions.size());
        }

        if (normalized.contains("logins suspects") || normalized.contains("suspicious")) {
            List<SuspiciousLoginRow> suspicious = listSuspiciousLogins();
            return AdminVoiceResult.success("Alertes securite: " + suspicious.size());
        }

        return AdminVoiceResult.success("Commande comprise mais aucune action directe executee. Exemples: 'export csv', 'sessions actives', 'logins suspects'.");
    }

    public UserScoreSummary computeUserScore(User user) {
        if (user == null) {
            return new UserScoreSummary(0, 0, 0, 0, 0, 0);
        }

        int activity = boolScore(user.getEmailVerified(), 20);
        int seniority = user.getCreatedAt() != null ? 20 : 8;
        int compliance = boolScore(!Boolean.TRUE.equals(user.getIsBanned()), 30);
        int sanctions = boolScore(!Boolean.TRUE.equals(user.getIsBanned()), 20);
        int profile = (!safe(user.getNom()).isBlank() && !safe(user.getPrenom()).isBlank()) ? 10 : 5;

        int total = Math.min(100, activity + seniority + compliance + sanctions + profile);
        return new UserScoreSummary(total, activity, seniority, compliance, sanctions, profile);
    }

    private void writeUsersCsv(Path target) throws IOException, SQLException {
        String sql = "SELECT id, username, email, role, subscription_status, subscription_type, email_verified, admin_approved, is_banned FROM users ORDER BY id DESC LIMIT 200";
        StringBuilder out = new StringBuilder("id,username,email,role,subscription_status,subscription_type,email_verified,admin_approved,is_banned\n");

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.append(rs.getInt("id")).append(',')
                        .append(csv(rs.getString("username"))).append(',')
                        .append(csv(rs.getString("email"))).append(',')
                        .append(csv(rs.getString("role"))).append(',')
                        .append(csv(rs.getString("subscription_status"))).append(',')
                        .append(csv(rs.getString("subscription_type"))).append(',')
                        .append(rs.getBoolean("email_verified")).append(',')
                        .append(rs.getBoolean("admin_approved")).append(',')
                        .append(rs.getBoolean("is_banned"))
                        .append('\n');
            }
        }

        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
    }

    private void writePaymentsCsv(Path target) throws IOException, SQLException {
        StringBuilder out = new StringBuilder("session_id,user_id,plan_type,amount,currency,status,updated_at\n");

        String sql = "SELECT session_id, user_id, plan_type, amount, currency, status, updated_at FROM stripe_payments ORDER BY updated_at DESC LIMIT 200";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.append(csv(rs.getString("session_id"))).append(',')
                        .append(rs.getInt("user_id")).append(',')
                        .append(csv(rs.getString("plan_type"))).append(',')
                        .append(rs.getBigDecimal("amount")).append(',')
                        .append(csv(rs.getString("currency"))).append(',')
                        .append(csv(rs.getString("status"))).append(',')
                        .append(csv(String.valueOf(rs.getTimestamp("updated_at"))))
                        .append('\n');
            }
        } catch (SQLException exception) {
            out.append("no_data,0,NA,0,EUR,table_missing,")
                    .append(csv(exception.getMessage()))
                    .append('\n');
        }

        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
    }

    private void writeStatsCsv(Path target) throws IOException, SQLException {
        StringBuilder out = new StringBuilder("metric,value\n");
        out.append("users_total,").append(scalar("SELECT COUNT(*) FROM users")).append('\n');
        out.append("users_banned,").append(scalar("SELECT COUNT(*) FROM users WHERE is_banned = 1")).append('\n');
        out.append("users_pending,").append(scalar("SELECT COUNT(*) FROM users WHERE admin_approved = 0 AND email_verified = 1")).append('\n');
        out.append("suspicious_total,").append(scalarWithFallback("SELECT COUNT(*) FROM suspicious_logins", 0)).append('\n');
        out.append("payments_total,").append(scalarWithFallback("SELECT COUNT(*) FROM stripe_payments", 0)).append('\n');
        Files.writeString(target, out.toString(), StandardCharsets.UTF_8);
    }

    private int scalar(String sql) throws SQLException {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int scalarWithFallback(String sql, int fallback) {
        try {
            return scalar(sql);
        } catch (SQLException exception) {
            return fallback;
        }
    }

    private void ensureSessionTable() {
        String sql = "CREATE TABLE IF NOT EXISTS user_sessions ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "user_id INT NOT NULL,"
                + "session_token VARCHAR(128) NOT NULL,"
                + "device_label VARCHAR(160) NULL,"
                + "ip_address VARCHAR(64) NULL,"
                + "is_active BOOLEAN NOT NULL DEFAULT 1,"
                + "created_at DATETIME NOT NULL,"
                + "last_seen_at DATETIME NOT NULL,"
                + "revoked_at DATETIME NULL"
                + ")";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException ignored) {
        }
    }

    private void ensureSuspiciousTable() {
        String sql = "CREATE TABLE IF NOT EXISTS suspicious_logins ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "user_id INT NOT NULL,"
                + "email VARCHAR(190) NULL,"
                + "ip_address VARCHAR(64) NULL,"
                + "reason VARCHAR(255) NULL,"
                + "blocked BOOLEAN NOT NULL DEFAULT 0,"
                + "created_at DATETIME NOT NULL"
                + ")";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException ignored) {
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private String csv(String value) {
        String raw = safe(value).replace("\"", "\"\"");
        return '"' + raw + '"';
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int boolScore(Boolean value, int score) {
        return Boolean.TRUE.equals(value) ? score : 0;
    }

    private String normalizeRole(String role) {
        String normalized = safe(role).toUpperCase();
        return switch (normalized) {
            case "ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE" -> normalized;
            default -> "";
        };
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    public record ActiveSessionRow(
            int id,
            int userId,
            String sessionToken,
            String deviceLabel,
            String ipAddress,
            LocalDateTime createdAt,
            LocalDateTime lastSeenAt
    ) {
    }

    public record SuspiciousLoginRow(
            int id,
            int userId,
            String email,
            String ipAddress,
            String reason,
            boolean blocked,
            LocalDateTime createdAt
    ) {
    }

    public record AdminVoiceResult(boolean success, String message) {
        public static AdminVoiceResult success(String message) {
            return new AdminVoiceResult(true, message);
        }

        public static AdminVoiceResult failure(String message) {
            return new AdminVoiceResult(false, message);
        }
    }

    public record UserScoreSummary(
            int scoreTotal,
            int activityScore,
            int seniorityScore,
            int ruleComplianceScore,
            int sanctionsHistoryScore,
            int profileScore
    ) {
    }
}
