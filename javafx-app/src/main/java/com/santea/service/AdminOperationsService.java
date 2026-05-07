package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;
import com.santea.repository.UserRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class AdminOperationsService {
    private final DatabaseService databaseService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public AdminOperationsService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
        this.userRepository = new UserRepository(this.databaseService);
        this.emailService = new EmailService();
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

    public List<PendingApprovalRow> listPendingProfessionalApprovals() {
        String sql = "SELECT id, nom, prenom, email, role, created_at "
                + "FROM users "
                + "WHERE deleted_at IS NULL AND email_verified = 1 AND admin_approved = 0 "
                + "ORDER BY created_at ASC LIMIT 100";

        List<PendingApprovalRow> rows = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new PendingApprovalRow(
                        rs.getInt("id"),
                        rs.getString("nom"),
                        rs.getString("prenom"),
                        rs.getString("email"),
                        rs.getString("role"),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ));
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public ActionResult approveAllPendingProfessionalAccounts(String approvedRole) {
        List<PendingApprovalRow> pending = listPendingProfessionalApprovals();
        if (pending.isEmpty()) {
            return ActionResult.success("Aucun compte en attente de validation.");
        }

        int approved = 0;
        String role = normalizeRole(approvedRole);
        if (role.isBlank()) {
            role = "ROLE_MEDECIN";
        }

        for (PendingApprovalRow row : pending) {
            ActionResult result = approveProfessionalAccount(row.id(), role);
            if (result.success()) {
                approved++;
            }
        }

        return ActionResult.success("Comptes approuves: " + approved + "/" + pending.size());
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

    public ActionResult updateUserByAdmin(
            int userId,
            String nom,
            String prenom,
            String email,
            String role,
            boolean emailVerified,
            boolean adminApproved,
            boolean banned
    ) {
        if (userId <= 0) {
            return ActionResult.failure("Utilisateur invalide.");
        }

        String normalizedEmail = safe(email).toLowerCase();
        if (normalizedEmail.isBlank()) {
            return ActionResult.failure("Email obligatoire.");
        }

        String normalizedRole = normalizeAdminEditableRole(role);
        if (normalizedRole.isBlank()) {
            return ActionResult.failure("Role invalide.");
        }

        String sql = "UPDATE users SET nom = ?, prenom = ?, email = ?, role = ?, email_verified = ?, admin_approved = ?, is_banned = ?, updated_at = ? "
                + "WHERE id = ? AND deleted_at IS NULL";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safe(nom));
            statement.setString(2, safe(prenom));
            statement.setString(3, normalizedEmail);
            statement.setString(4, normalizedRole);
            statement.setBoolean(5, emailVerified);
            statement.setBoolean(6, adminApproved);
            statement.setBoolean(7, banned);
            statement.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(9, userId);
            int changed = statement.executeUpdate();
            return changed == 1 ? ActionResult.success("Utilisateur mis a jour.") : ActionResult.failure("Utilisateur introuvable ou deja supprime.");
        } catch (SQLException exception) {
            return ActionResult.failure("Mise a jour impossible: " + exception.getMessage());
        }
    }

    public ActionResult softDeleteUserByAdmin(int userId) {
        if (userId <= 0) {
            return ActionResult.failure("Utilisateur invalide.");
        }

        String sql = "UPDATE users SET deleted_at = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL";
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, now);
            statement.setTimestamp(2, now);
            statement.setInt(3, userId);
            int changed = statement.executeUpdate();
            return changed == 1 ? ActionResult.success("Utilisateur supprime (suppression logique).") : ActionResult.failure("Utilisateur introuvable ou deja supprime.");
        } catch (SQLException exception) {
            return ActionResult.failure("Suppression impossible: " + exception.getMessage());
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

    public ActionResult markSuspiciousLoginBlocked(int loginId, boolean blocked) {
        ensureSuspiciousTable();
        String sql = "UPDATE suspicious_logins SET blocked = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, blocked);
            statement.setInt(2, loginId);
            int changed = statement.executeUpdate();
            if (changed == 1) {
                return ActionResult.success(blocked ? "Alerte marquee comme bloquee." : "Alerte marquee comme non bloquee.");
            }
            return ActionResult.failure("Alerte introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Mise a jour alerte impossible: " + exception.getMessage());
        }
    }

    public List<ModerationRow> listModerationQueue() {
        List<ModerationRow> rows = new ArrayList<>();

        String sqlSymfony = "SELECT c.id, c.titre, c.type, c.statut, c.created_at, u.email AS auteur_email "
                + "FROM contenu c "
                + "LEFT JOIN users u ON c.auteur_id = u.id "
                + "WHERE c.statut IN ('en_attente', 'valide', 'publie', 'rejete') "
                + "ORDER BY c.created_at DESC LIMIT 80";

        String sqlLegacy = "SELECT c.id, c.title AS titre, c.type, c.moderation_status AS statut, c.created_at, u.email AS auteur_email "
                + "FROM contenus c "
                + "LEFT JOIN users u ON c.user_id = u.id "
                + "ORDER BY c.created_at DESC LIMIT 80";

        if (fillModerationRows(rows, sqlSymfony) || fillModerationRows(rows, sqlLegacy)) {
            return rows;
        }

        return List.of();
    }

    public ActionResult moderateContent(int contentId, String moderationStatus, String reason) {
        String status = normalizeModerationStatus(moderationStatus);
        if (status.isBlank()) {
            return ActionResult.failure("Statut de moderation invalide.");
        }

        String[] sqlOptions = new String[] {
                "UPDATE contenu SET statut = ?, updated_at = ? WHERE id = ?",
                "UPDATE contenus SET moderation_status = ?, moderation_reason = ?, updated_at = ? WHERE id = ?"
        };

        for (String sql : sqlOptions) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                if (sql.contains("contenu SET statut")) {
                    statement.setString(1, status);
                    statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                    statement.setInt(3, contentId);
                } else {
                    statement.setString(1, status);
                    statement.setString(2, safe(reason));
                    statement.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    statement.setInt(4, contentId);
                }

                int changed = statement.executeUpdate();
                if (changed == 1) {
                    return ActionResult.success("Moderation appliquee.");
                }
            } catch (SQLException ignored) {
            }
        }

        return ActionResult.failure("Moderation non disponible (table contenu absente ou schema different).");
    }

    public CommunityStats loadCommunityStats() {
        int total = scalarWithFallback("SELECT COUNT(*) FROM contenu", -1);
        if (total < 0) {
            total = scalarWithFallback("SELECT COUNT(*) FROM contenus", 0);
        }

        int pending = scalarWithFallback("SELECT COUNT(*) FROM contenu WHERE statut = 'en_attente'", -1);
        if (pending < 0) {
            pending = scalarWithFallback("SELECT COUNT(*) FROM contenus WHERE LOWER(moderation_status) IN ('en_attente', 'pending')", 0);
        }

        int published = scalarWithFallback("SELECT COUNT(*) FROM contenu WHERE statut IN ('publie', 'valide')", -1);
        if (published < 0) {
            published = scalarWithFallback("SELECT COUNT(*) FROM contenus WHERE LOWER(moderation_status) IN ('publie', 'valide', 'approved')", 0);
        }

        int rejected = scalarWithFallback("SELECT COUNT(*) FROM contenu WHERE statut = 'rejete'", -1);
        if (rejected < 0) {
            rejected = scalarWithFallback("SELECT COUNT(*) FROM contenus WHERE LOWER(moderation_status) IN ('rejete', 'rejected')", 0);
        }

        return new CommunityStats(total, pending, published, rejected);
    }

    public List<CommunityRow> listCommunityForAdmin(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        List<CommunityRow> rows = new ArrayList<>();

        String sqlSymfony = "SELECT c.id, c.titre, c.type, c.statut, c.categorie, c.tags, c.created_at, c.updated_at, "
                + "u.nom AS auteur_nom, u.prenom AS auteur_prenom, u.email AS auteur_email, "
                + "(SELECT COUNT(*) FROM likes l WHERE l.contenu_id = c.id) AS likes_count, "
                + "(SELECT COUNT(*) FROM commentaires cm WHERE cm.contenu_id = c.id) AS comments_count, "
                + "COALESCE(s.score_article, 0) AS score_article "
                + "FROM contenu c "
                + "LEFT JOIN users u ON u.id = c.auteur_id "
                + "LEFT JOIN article_scores s ON s.contenu_id = c.id "
                + "ORDER BY c.created_at DESC LIMIT ?";

        String sqlLegacy = "SELECT c.id, c.title AS titre, c.type, c.moderation_status AS statut, c.category AS categorie, c.tags, c.created_at, c.updated_at, "
                + "u.nom AS auteur_nom, u.prenom AS auteur_prenom, u.email AS auteur_email, "
                + "0 AS likes_count, 0 AS comments_count, 0 AS score_article "
                + "FROM contenus c "
                + "LEFT JOIN users u ON u.id = c.user_id "
                + "ORDER BY c.created_at DESC LIMIT ?";

        if (fillCommunityRows(rows, sqlSymfony, safeLimit) || fillCommunityRows(rows, sqlLegacy, safeLimit)) {
            return rows;
        }

        return List.of();
    }

    public ActionResult updateCommunityStatusByAdmin(int contentId, String moderationStatus, String reason) {
        String status = normalizeModerationStatus(moderationStatus);
        if (contentId <= 0) {
            return ActionResult.failure("Contenu invalide.");
        }
        if (status.isBlank()) {
            return ActionResult.failure("Statut community invalide.");
        }

        String sqlSymfony = "UPDATE contenu SET statut = ?, date_publication = ?, updated_at = ? WHERE id = ?";
        String sqlLegacy = "UPDATE contenus SET moderation_status = ?, moderation_reason = ?, updated_at = ? WHERE id = ?";

        for (String sql : new String[] {sqlSymfony, sqlLegacy}) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                if (sql.equals(sqlSymfony)) {
                    statement.setString(1, status);
                    if ("publie".equals(status) || "valide".equals(status)) {
                        statement.setTimestamp(2, now);
                    } else {
                        statement.setTimestamp(2, null);
                    }
                    statement.setTimestamp(3, now);
                    statement.setInt(4, contentId);
                } else {
                    statement.setString(1, status);
                    statement.setString(2, safe(reason));
                    statement.setTimestamp(3, now);
                    statement.setInt(4, contentId);
                }

                int changed = statement.executeUpdate();
                if (changed == 1) {
                    return ActionResult.success("Statut community mis a jour.");
                }
            } catch (SQLException ignored) {
            }
        }

        return ActionResult.failure("Mise a jour community indisponible (schema non compatible).");
    }

    public AppointmentAdminStats loadAppointmentAdminStats() {
        int total = scalarWithFallback("SELECT COUNT(*) FROM rendez_vous", -1);
        if (total < 0) {
            total = scalarWithFallback("SELECT COUNT(*) FROM appointments", 0);
        }

        int pending = scalarWithFallback("SELECT COUNT(*) FROM rendez_vous WHERE LOWER(statut) IN ('en_attente', 'pending', 'requested')", -1);
        if (pending < 0) {
            pending = scalarWithFallback("SELECT COUNT(*) FROM appointments WHERE LOWER(status) IN ('en_attente', 'pending', 'requested')", 0);
        }

        int confirmed = scalarWithFallback("SELECT COUNT(*) FROM rendez_vous WHERE LOWER(statut) IN ('confirme', 'confirmed')", -1);
        if (confirmed < 0) {
            confirmed = scalarWithFallback("SELECT COUNT(*) FROM appointments WHERE LOWER(status) IN ('confirme', 'confirmed')", 0);
        }

        int refused = scalarWithFallback("SELECT COUNT(*) FROM rendez_vous WHERE LOWER(statut) IN ('refuse', 'rejected')", -1);
        if (refused < 0) {
            refused = scalarWithFallback("SELECT COUNT(*) FROM appointments WHERE LOWER(status) IN ('refuse', 'rejected')", 0);
        }

        int cancelled = scalarWithFallback("SELECT COUNT(*) FROM rendez_vous WHERE LOWER(statut) IN ('annule', 'cancelled')", -1);
        if (cancelled < 0) {
            cancelled = scalarWithFallback("SELECT COUNT(*) FROM appointments WHERE LOWER(status) IN ('annule', 'cancelled')", 0);
        }

        return new AppointmentAdminStats(total, pending, confirmed, refused, cancelled);
    }

    public List<AppointmentAdminRow> listAppointmentsForAdmin(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        List<AppointmentAdminRow> rows = new ArrayList<>();

        String sqlSymfony = "SELECT r.id, r.date_rdv, r.heure_rdv, r.statut, r.motif, r.created_at, "
                + "pu.nom AS patient_nom, pu.prenom AS patient_prenom, pu.email AS patient_email, "
                + "mu.nom AS medecin_nom, mu.prenom AS medecin_prenom, mu.email AS medecin_email "
                + "FROM rendez_vous r "
                + "LEFT JOIN patients p ON p.id = r.patient_id "
                + "LEFT JOIN users pu ON pu.id = p.user_id "
                + "LEFT JOIN medecins m ON m.id = r.medecin_id "
                + "LEFT JOIN users mu ON mu.id = m.user_id "
                + "ORDER BY r.date_rdv DESC, r.heure_rdv DESC LIMIT ?";

        String sqlLegacy = "SELECT a.id, DATE(a.appointment_date) AS date_rdv, TIME(a.appointment_time) AS heure_rdv, a.status AS statut, a.reason AS motif, a.created_at, "
                + "pu.nom AS patient_nom, pu.prenom AS patient_prenom, pu.email AS patient_email, "
                + "mu.nom AS medecin_nom, mu.prenom AS medecin_prenom, mu.email AS medecin_email "
                + "FROM appointments a "
                + "LEFT JOIN users pu ON pu.id = a.patient_id "
                + "LEFT JOIN users mu ON mu.id = a.doctor_id "
                + "ORDER BY a.appointment_date DESC, a.appointment_time DESC LIMIT ?";

        if (fillAppointmentRows(rows, sqlSymfony, safeLimit) || fillAppointmentRows(rows, sqlLegacy, safeLimit)) {
            return rows;
        }

        return List.of();
    }

    public ActionResult updateAppointmentStatusByAdmin(int appointmentId, String status) {
        String normalized = normalizeAppointmentStatus(status);
        if (appointmentId <= 0) {
            return ActionResult.failure("Rendez-vous invalide.");
        }
        if (normalized.isBlank()) {
            return ActionResult.failure("Statut rendez-vous invalide.");
        }

        String lockSql = "SELECT disponibilite_id FROM rendez_vous WHERE id = ? FOR UPDATE";
        String updateSql = "UPDATE rendez_vous SET statut = ?, updated_at = ? WHERE id = ?";
        String freeSlotSql = "UPDATE disponibilites SET statut = 'disponible', updated_at = ? WHERE id = ?";

        try (Connection connection = databaseService.getConnection()) {
            connection.setAutoCommit(false);

            Integer disponibiliteId = null;
            try (PreparedStatement lock = connection.prepareStatement(lockSql)) {
                lock.setInt(1, appointmentId);
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        return ActionResult.failure("Rendez-vous introuvable.");
                    }
                    disponibiliteId = nullableInt(rs, "disponibilite_id");
                }
            }

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            try (PreparedStatement update = connection.prepareStatement(updateSql)) {
                update.setString(1, normalized);
                update.setTimestamp(2, now);
                update.setInt(3, appointmentId);
                update.executeUpdate();
            }

            if (("refuse".equals(normalized) || "annule".equals(normalized)) && disponibiliteId != null) {
                try (PreparedStatement free = connection.prepareStatement(freeSlotSql)) {
                    free.setTimestamp(1, now);
                    free.setInt(2, disponibiliteId);
                    free.executeUpdate();
                }
            }

            connection.commit();
            return ActionResult.success("Statut rendez-vous mis a jour.");
        } catch (SQLException ignored) {
        }

        String fallbackSql = "UPDATE appointments SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(fallbackSql)) {
            statement.setString(1, normalized);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(3, appointmentId);
            int changed = statement.executeUpdate();
            if (changed == 1) {
                return ActionResult.success("Statut rendez-vous mis a jour.");
            }
            return ActionResult.failure("Rendez-vous introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Mise a jour rendez-vous impossible: " + exception.getMessage());
        }
    }

    public AccompanimentAdminStats loadAccompanimentAdminStats() {
        int total = scalarWithFallback("SELECT COUNT(*) FROM accompaniment_plans", -1);
        if (total < 0) {
            total = scalarWithFallback("SELECT COUNT(*) FROM accompaniment_plan", -1);
        }
        if (total < 0) {
            total = scalarWithFallback("SELECT COUNT(*) FROM accompagnements", 0);
        }

        int active = scalarWithFallback("SELECT COUNT(*) FROM accompaniment_plans WHERE LOWER(status) IN ('active', 'en_cours', 'ongoing')", -1);
        if (active < 0) {
            active = scalarWithFallback("SELECT COUNT(*) FROM accompaniment_plan WHERE LOWER(status) IN ('active', 'en_cours', 'ongoing')", -1);
        }
        if (active < 0) {
            active = scalarWithFallback("SELECT COUNT(*) FROM accompagnements WHERE LOWER(statut) IN ('active', 'en_cours', 'ongoing')", 0);
        }

        int completed = scalarWithFallback("SELECT COUNT(*) FROM accompaniment_plans WHERE LOWER(status) IN ('completed', 'termine', 'terminee', 'valide')", -1);
        if (completed < 0) {
            completed = scalarWithFallback("SELECT COUNT(*) FROM accompaniment_plan WHERE LOWER(status) IN ('completed', 'termine', 'terminee', 'valide')", -1);
        }
        if (completed < 0) {
            completed = scalarWithFallback("SELECT COUNT(*) FROM accompagnements WHERE LOWER(statut) IN ('completed', 'termine', 'terminee', 'valide')", 0);
        }

        int cancelled = scalarWithFallback("SELECT COUNT(*) FROM accompaniment_plans WHERE LOWER(status) IN ('cancelled', 'annule', 'annulee', 'rejete')", -1);
        if (cancelled < 0) {
            cancelled = scalarWithFallback("SELECT COUNT(*) FROM accompaniment_plan WHERE LOWER(status) IN ('cancelled', 'annule', 'annulee', 'rejete')", -1);
        }
        if (cancelled < 0) {
            cancelled = scalarWithFallback("SELECT COUNT(*) FROM accompagnements WHERE LOWER(statut) IN ('cancelled', 'annule', 'annulee', 'rejete')", 0);
        }

        return new AccompanimentAdminStats(total, active, completed, cancelled);
    }

    public List<AccompanimentAdminRow> listAccompanimentsForAdmin(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        List<AccompanimentAdminRow> rows = new ArrayList<>();

        String sqlSymfonyPlural = "SELECT ap.id, ap.title, ap.status, ap.start_date, ap.end_date, ap.duration_weeks, ap.created_at, ap.updated_at, "
                + "pu.nom AS patient_nom, pu.prenom AS patient_prenom, pu.email AS patient_email, "
                + "cu.nom AS coach_nom, cu.prenom AS coach_prenom, cu.email AS coach_email, "
                + "nu.nom AS nutritionist_nom, nu.prenom AS nutritionist_prenom, nu.email AS nutritionist_email, "
                + "(SELECT COUNT(*) FROM plans_exercices pe WHERE pe.accompaniment_plan_id = ap.id) AS exercise_count, "
                + "(SELECT COUNT(*) FROM plans_regimes pr WHERE pr.accompaniment_plan_id = ap.id) AS diet_count "
                + "FROM accompaniment_plans ap "
                + "LEFT JOIN patients p ON p.id = ap.patient_id "
                + "LEFT JOIN users pu ON pu.id = p.user_id "
                + "LEFT JOIN coach_sportifs c ON c.id = ap.coach_id "
                + "LEFT JOIN users cu ON cu.id = c.user_id "
                + "LEFT JOIN nutritionnistes n ON n.id = ap.nutritionist_id "
                + "LEFT JOIN users nu ON nu.id = n.user_id "
                + "ORDER BY ap.created_at DESC LIMIT ?";

        String sqlSymfonySingular = "SELECT ap.id, ap.title, ap.status, ap.start_date, ap.end_date, ap.duration_weeks, ap.created_at, ap.updated_at, "
                + "pu.nom AS patient_nom, pu.prenom AS patient_prenom, pu.email AS patient_email, "
                + "cu.nom AS coach_nom, cu.prenom AS coach_prenom, cu.email AS coach_email, "
                + "nu.nom AS nutritionist_nom, nu.prenom AS nutritionist_prenom, nu.email AS nutritionist_email, "
                + "(SELECT COUNT(*) FROM plans_exercices pe WHERE pe.accompaniment_plan_id = ap.id) AS exercise_count, "
                + "(SELECT COUNT(*) FROM plans_regimes pr WHERE pr.accompaniment_plan_id = ap.id) AS diet_count "
                + "FROM accompaniment_plan ap "
                + "LEFT JOIN patients p ON p.id = ap.patient_id "
                + "LEFT JOIN users pu ON pu.id = p.user_id "
                + "LEFT JOIN coach_sportifs c ON c.id = ap.coach_id "
                + "LEFT JOIN users cu ON cu.id = c.user_id "
                + "LEFT JOIN nutritionnistes n ON n.id = ap.nutritionist_id "
                + "LEFT JOIN users nu ON nu.id = n.user_id "
                + "ORDER BY ap.created_at DESC LIMIT ?";

        String sqlLegacy = "SELECT a.id, a.nom AS title, a.statut AS status, a.date_debut AS start_date, a.date_fin AS end_date, 0 AS duration_weeks, a.created_at, a.updated_at, "
                + "u.nom AS patient_nom, u.prenom AS patient_prenom, u.email AS patient_email, "
                + "'' AS coach_nom, '' AS coach_prenom, '' AS coach_email, "
                + "'' AS nutritionist_nom, '' AS nutritionist_prenom, '' AS nutritionist_email, "
                + "(SELECT COUNT(*) FROM plans_exercices pe WHERE pe.accompagnement_id = a.id) AS exercise_count, "
                + "(SELECT COUNT(*) FROM plans_regimes pr WHERE pr.accompagnement_id = a.id) AS diet_count "
                + "FROM accompagnements a "
                + "LEFT JOIN abonnements ab ON ab.id = a.abonnement_id "
                + "LEFT JOIN users u ON u.id = ab.user_id "
                + "ORDER BY a.created_at DESC LIMIT ?";

        if (fillAccompanimentRows(rows, sqlSymfonyPlural, safeLimit)
                || fillAccompanimentRows(rows, sqlSymfonySingular, safeLimit)
                || fillAccompanimentRows(rows, sqlLegacy, safeLimit)) {
            return rows;
        }

        return List.of();
    }

    public ActionResult updateAccompanimentStatusByAdmin(int accompanimentId, String status) {
        String normalized = normalizeAccompanimentStatus(status);
        if (accompanimentId <= 0) {
            return ActionResult.failure("Plan d'accompagnement invalide.");
        }
        if (normalized.isBlank()) {
            return ActionResult.failure("Statut accompagnement invalide.");
        }

        String[] modernSql = new String[] {
                "UPDATE accompaniment_plans SET status = ?, updated_at = ? WHERE id = ?",
                "UPDATE accompaniment_plan SET status = ?, updated_at = ? WHERE id = ?"
        };

        for (String sql : modernSql) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, normalized);
                statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                statement.setInt(3, accompanimentId);
                int changed = statement.executeUpdate();
                if (changed == 1) {
                    return ActionResult.success("Statut accompagnement mis a jour.");
                }
            } catch (SQLException ignored) {
            }
        }

        String legacyStatus = normalizeAccompanimentStatusForLegacy(normalized);
        String legacySql = "UPDATE accompagnements SET statut = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(legacySql)) {
            statement.setString(1, legacyStatus);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(3, accompanimentId);
            int changed = statement.executeUpdate();
            if (changed == 1) {
                return ActionResult.success("Statut accompagnement mis a jour.");
            }
            return ActionResult.failure("Plan d'accompagnement introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Mise a jour accompagnement impossible: " + exception.getMessage());
        }
    }

    public List<UserScoreRow> listTopUserScores(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = "SELECT id, nom, prenom, email, role, email_verified, admin_approved, is_banned, created_at "
                + "FROM users "
                + "WHERE deleted_at IS NULL "
                + "ORDER BY created_at DESC LIMIT 500";

        List<UserScoreRow> rows = new ArrayList<>();

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setNom(rs.getString("nom"));
                user.setPrenom(rs.getString("prenom"));
                user.setEmail(rs.getString("email"));
                user.setRole(rs.getString("role"));
                user.setEmailVerified(rs.getBoolean("email_verified"));
                user.setAdminApproved(rs.getBoolean("admin_approved"));
                user.setIsBanned(rs.getBoolean("is_banned"));
                user.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));

                UserScoreSummary score = computeUserScore(user);
                rows.add(new UserScoreRow(
                        user.getId() == null ? 0 : user.getId(),
                        safe(user.getNom()),
                        safe(user.getPrenom()),
                        safe(user.getEmail()),
                        safe(user.getRole()),
                        score.scoreTotal(),
                        supportPriorityLabel(score.scoreTotal())
                ));
            }
        } catch (SQLException ignored) {
        }

        rows.sort(Comparator.comparingInt(UserScoreRow::score).reversed());
        if (rows.size() > safeLimit) {
            return new ArrayList<>(rows.subList(0, safeLimit));
        }
        return rows;
    }

    public int averageUserScore() {
        List<UserScoreRow> all = listTopUserScores(100);
        if (all.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (UserScoreRow row : all) {
            total += row.score();
        }
        return (int) Math.round((double) total / all.size());
    }

    public List<AdminUserRow> listUsersForAdmin(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        String sql = "SELECT id, nom, prenom, email, role, subscription_status, created_at, is_banned, email_verified, admin_approved "
                + "FROM users WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT ?";

        List<AdminUserRow> rows = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setNom(rs.getString("nom"));
                    user.setPrenom(rs.getString("prenom"));
                    user.setEmail(rs.getString("email"));
                    user.setRole(rs.getString("role"));
                    user.setSubscriptionStatus(rs.getString("subscription_status"));
                    user.setIsBanned(rs.getBoolean("is_banned"));
                    user.setEmailVerified(rs.getBoolean("email_verified"));
                    user.setAdminApproved(rs.getBoolean("admin_approved"));
                    user.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));

                    UserScoreSummary score = computeUserScore(user);
                    rows.add(new AdminUserRow(
                            user.getId() == null ? 0 : user.getId(),
                            safe(user.getNom()),
                            safe(user.getPrenom()),
                            safe(user.getEmail()),
                            safe(user.getRole()),
                            safe(user.getSubscriptionStatus()),
                            Boolean.TRUE.equals(user.getIsBanned()),
                            Boolean.TRUE.equals(user.getEmailVerified()),
                            Boolean.TRUE.equals(user.getAdminApproved()),
                            user.getCreatedAt(),
                            score.scoreTotal(),
                            supportPriorityLabel(score.scoreTotal())
                    ));
                }
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public ValidationStats loadValidationStats() {
        int total = scalarWithFallback("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL", 0);
        int emailConfirmed = scalarWithFallback("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND email_verified = 1", 0);
        int emailNotConfirmed = scalarWithFallback("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND email_verified = 0", 0);
        int adminPending = scalarWithFallback("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND email_verified = 1 AND admin_approved = 0", 0);
        return new ValidationStats(total, emailConfirmed, emailNotConfirmed, adminPending);
    }

    public List<ValidationUserRow> listValidationUsers(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        String sql = "SELECT id, nom, prenom, email, email_verified, admin_approved, created_at "
                + "FROM users WHERE deleted_at IS NULL ORDER BY created_at DESC LIMIT ?";

        List<ValidationUserRow> rows = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new ValidationUserRow(
                            rs.getInt("id"),
                            safe(rs.getString("nom")),
                            safe(rs.getString("prenom")),
                            safe(rs.getString("email")),
                            rs.getBoolean("email_verified"),
                            rs.getBoolean("admin_approved"),
                            toLocalDateTime(rs.getTimestamp("created_at"))
                    ));
                }
            }
        } catch (SQLException ignored) {
        }
        return rows;
    }

    public ActionResult approveValidationUser(int userId) {
        if (userId <= 0) {
            return ActionResult.failure("Utilisateur invalide.");
        }

        String sql = "UPDATE users SET admin_approved = 1, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(2, userId);
            int changed = statement.executeUpdate();
            return changed == 1 ? ActionResult.success("Compte approuve.") : ActionResult.failure("Utilisateur introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Approbation impossible: " + exception.getMessage());
        }
    }

    public ActionResult resendValidationEmail(int userId) {
        if (userId <= 0) {
            return ActionResult.failure("Utilisateur invalide.");
        }

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return ActionResult.failure("Utilisateur introuvable.");
        }

        User user = userOptional.get();
        String email = safe(user.getEmail()).toLowerCase();
        if (email.isBlank()) {
            return ActionResult.failure("Adresse email indisponible pour cet utilisateur.");
        }

        String token = generateOpaqueToken(64);
        boolean tokenSaved = userRepository.updateEmailVerificationToken(userId, token, LocalDateTime.now().plusDays(2));
        if (!tokenSaved) {
            return ActionResult.failure("Impossible de regenerer le token de verification.");
        }

        String fullName = (safe(user.getNom()) + " " + safe(user.getPrenom())).trim();
        boolean mailSent = emailService.sendEmailVerification(email, fullName.isBlank() ? "Utilisateur" : fullName, token);
        if (mailSent) {
            return ActionResult.success("Email de verification re-envoye a " + email + ".");
        }

        String reason = safe(emailService.getLastError());
        return ActionResult.failure("Email non envoye. Cause: " + (reason.isBlank() ? "configuration SMTP manquante" : reason));
    }

    public ActionResult resendValidationEmailMock(int userId) {
        return resendValidationEmail(userId);
    }

    public ActionResult triggerPasswordResetByAdmin(int userId) {
        if (userId <= 0) {
            return ActionResult.failure("Utilisateur invalide.");
        }

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return ActionResult.failure("Utilisateur introuvable.");
        }

        User user = userOptional.get();
        String email = safe(user.getEmail()).toLowerCase();
        if (email.isBlank()) {
            return ActionResult.failure("Adresse email indisponible pour cet utilisateur.");
        }

        userRepository.deleteExpiredOrUsedResetTokens(userId);
        Optional<UserRepository.PasswordResetTokenRecord> token =
                userRepository.createPasswordResetToken(userId, LocalDateTime.now().plusHours(1));

        if (token.isEmpty()) {
            return ActionResult.failure("Generation du token de reinitialisation impossible.");
        }

        String fullName = (safe(user.getNom()) + " " + safe(user.getPrenom())).trim();
        boolean mailSent = emailService.sendPasswordReset(email, fullName.isBlank() ? "Utilisateur" : fullName, token.get().token());

        if (mailSent) {
            return ActionResult.success("Email de reinitialisation envoye a " + email + ".");
        }

        String reason = safe(emailService.getLastError());
        return ActionResult.success(
                "Email non envoye (" + (reason.isBlank() ? "SMTP non configure" : reason)
                        + "). Token de secours: " + token.get().token()
        );
    }

    public SubscriptionStats loadSubscriptionStats() {
        int total = scalarWithFallback("SELECT COUNT(*) FROM abonnements", 0);
        int active = scalarWithFallback("SELECT COUNT(*) FROM abonnements WHERE LOWER(statut) = 'actif'", 0);
        int expired = scalarWithFallback("SELECT COUNT(*) FROM abonnements WHERE LOWER(statut) IN ('expire', 'expiré', 'expired')", 0);
        int cancelled = scalarWithFallback("SELECT COUNT(*) FROM abonnements WHERE LOWER(statut) IN ('annule', 'annulé', 'cancelled')", 0);
        return new SubscriptionStats(total, active, expired, cancelled);
    }

    public List<SubscriptionRow> listSubscriptionsForAdmin(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        String sql = "SELECT a.id, a.type_abonnement, a.statut, a.date_debut, a.date_fin, a.prix, "
                + "u.nom, u.prenom, u.email, u.id AS user_id "
                + "FROM abonnements a "
                + "LEFT JOIN users u ON a.user_id = u.id "
                + "ORDER BY a.created_at DESC LIMIT ?";

        List<SubscriptionRow> rows = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    LocalDate start = toLocalDate(rs.getDate("date_debut"));
                    LocalDate end = toLocalDate(rs.getDate("date_fin"));
                    BigDecimal price = rs.getBigDecimal("prix") == null ? BigDecimal.ZERO : rs.getBigDecimal("prix");

                    rows.add(new SubscriptionRow(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            safe(rs.getString("nom")),
                            safe(rs.getString("prenom")),
                            safe(rs.getString("email")),
                            safe(rs.getString("type_abonnement")),
                            safe(rs.getString("statut")),
                            start,
                            end,
                            price
                    ));
                }
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public ActionResult updateSubscription(int subscriptionId, String type, String status, LocalDate dateFin) {
        if (subscriptionId <= 0) {
            return ActionResult.failure("Abonnement invalide.");
        }

        String sql = "UPDATE abonnements SET type_abonnement = ?, statut = ?, date_fin = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, safe(type));
            statement.setString(2, safe(status));
            if (dateFin == null) {
                statement.setDate(3, null);
            } else {
                statement.setDate(3, java.sql.Date.valueOf(dateFin));
            }
            statement.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(5, subscriptionId);
            int changed = statement.executeUpdate();
            if (changed == 1) {
                syncUserFromSubscription(subscriptionId);
                return ActionResult.success("Abonnement mis a jour.");
            }
            return ActionResult.failure("Abonnement introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Mise a jour impossible: " + exception.getMessage());
        }
    }

    public ActionResult cancelSubscription(int subscriptionId) {
        if (subscriptionId <= 0) {
            return ActionResult.failure("Abonnement invalide.");
        }

        String sql = "UPDATE abonnements SET statut = 'annule', date_fin = CURDATE(), updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(2, subscriptionId);
            int changed = statement.executeUpdate();
            if (changed == 1) {
                syncUserFromSubscription(subscriptionId);
                return ActionResult.success("Abonnement annule.");
            }
            return ActionResult.failure("Abonnement introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Annulation impossible: " + exception.getMessage());
        }
    }

    public RevenueStats loadRevenueStats() {
        int totalInvoices = scalarWithFallback("SELECT COUNT(*) FROM factures", 0);
        BigDecimal total = decimalWithFallback("SELECT COALESCE(SUM(montant_ttc), 0) FROM factures", BigDecimal.ZERO);
        BigDecimal month = decimalWithFallback(
                "SELECT COALESCE(SUM(montant_ttc), 0) FROM factures WHERE created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01') AND created_at < DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')",
                BigDecimal.ZERO
        );
        return new RevenueStats(totalInvoices, total, month);
    }

    public List<RevenueRow> listRevenuesForAdmin(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 300));
        String sql = "SELECT f.numero, f.montant_ttc, f.devise, f.created_at, f.pdf_path, "
                + "u.nom, u.prenom, a.type_abonnement "
                + "FROM factures f "
                + "LEFT JOIN users u ON f.user_id = u.id "
                + "LEFT JOIN abonnements a ON f.abonnement_id = a.id "
                + "ORDER BY f.created_at DESC LIMIT ?";

        List<RevenueRow> rows = new ArrayList<>();
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RevenueRow(
                            safe(rs.getString("numero")),
                            safe(rs.getString("nom")),
                            safe(rs.getString("prenom")),
                            safe(rs.getString("type_abonnement")),
                            rs.getBigDecimal("montant_ttc") == null ? BigDecimal.ZERO : rs.getBigDecimal("montant_ttc"),
                            safe(rs.getString("devise")),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            safe(rs.getString("pdf_path"))
                    ));
                }
            }
        } catch (SQLException ignored) {
        }

        return rows;
    }

    public ActionResult exportCsvReports() {
        return exportCsvReports(Paths.get("generated-exports"));
    }

    public ActionResult exportCsvReports(Path exportDir) {
        if (!databaseService.canConnect()) {
            return ActionResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        try {
            Path outputDirectory = exportDir == null ? Paths.get("generated-exports") : exportDir;
            Files.createDirectories(outputDirectory);

            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
            Path usersCsv = outputDirectory.resolve("users_" + stamp + ".csv");
            Path paymentsCsv = outputDirectory.resolve("payments_" + stamp + ".csv");
            Path statsCsv = outputDirectory.resolve("stats_" + stamp + ".csv");

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

        if (normalized.contains("validation") || normalized.contains("comptes en attente")) {
            List<PendingApprovalRow> pending = listPendingProfessionalApprovals();
            return AdminVoiceResult.success("Comptes en attente de validation: " + pending.size());
        }

        if ((normalized.contains("approuve") || normalized.contains("valide")) && normalized.contains("tout")) {
            ActionResult approved = approveAllPendingProfessionalAccounts("ROLE_MEDECIN");
            return approved.success() ? AdminVoiceResult.success(approved.message()) : AdminVoiceResult.failure(approved.message());
        }

        if (normalized.contains("moderation") || normalized.contains("contenu en attente")) {
            List<ModerationRow> moderationRows = listModerationQueue();
            long pending = moderationRows.stream().filter(r -> "en_attente".equalsIgnoreCase(r.status())).count();
            return AdminVoiceResult.success("Contenus en moderation: " + pending);
        }

        if (normalized.contains("score") || normalized.contains("scoring")) {
            int average = averageUserScore();
            return AdminVoiceResult.success("Score moyen utilisateurs: " + average + "/100");
        }

        return AdminVoiceResult.success("Commande comprise mais aucune action directe executee. Exemples: 'export csv', 'sessions actives', 'logins suspects', 'validation', 'moderation', 'score'.");
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
        StringBuilder out = new StringBuilder("reference,user_id,plan_type,amount,currency,status,updated_at\n");

        String stripeSql = "SELECT session_id, user_id, plan_type, amount, currency, status, updated_at FROM stripe_payments ORDER BY updated_at DESC LIMIT 200";
        String factureSql = "SELECT numero, user_id, abonnement_id, montant_ttc, devise, 'paid' AS status, created_at FROM factures ORDER BY created_at DESC LIMIT 200";

        if (!fillPaymentsFromStripe(out, stripeSql) && !fillPaymentsFromFacture(out, factureSql)) {
            out.append("no_data,0,NA,0,TND,table_missing,")
                    .append(csv("Aucune source de paiements disponible"))
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
        out.append("subscriptions_active,").append(scalarWithFallback("SELECT COUNT(*) FROM abonnements WHERE statut = 'actif'", 0)).append('\n');
        out.append("pending_moderation,").append(scalarWithFallback("SELECT COUNT(*) FROM contenu WHERE statut = 'en_attente'", 0)).append('\n');
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

    private BigDecimal decimalWithFallback(String sql, BigDecimal fallback) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                BigDecimal value = rs.getBigDecimal(1);
                return value == null ? fallback : value;
            }
            return fallback;
        } catch (SQLException exception) {
            return fallback;
        }
    }

    private LocalDate toLocalDate(java.sql.Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private LocalTime toLocalTime(java.sql.Time value) {
        return value == null ? null : value.toLocalTime();
    }

    private Integer nullableInt(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private void syncUserFromSubscription(int subscriptionId) {
        String select = "SELECT user_id, type_abonnement, statut, date_fin FROM abonnements WHERE id = ? LIMIT 1";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setInt(1, subscriptionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return;
                }

                int userId = rs.getInt("user_id");
                if (userId <= 0) {
                    return;
                }

                String type = safe(rs.getString("type_abonnement"));
                String statut = safe(rs.getString("statut")).toLowerCase();
                LocalDate end = toLocalDate(rs.getDate("date_fin"));

                String userRole = type;
                String userSubscriptionStatus = "ACTIVE";
                String subscriptionType = type;

                if (statut.contains("annul") || statut.contains("expir") || (end != null && end.isBefore(LocalDate.now()))) {
                    userRole = "ROLE_USER";
                    userSubscriptionStatus = "EXPIRED";
                    subscriptionType = null;
                }

                String update = "UPDATE users SET role = ?, subscription_status = ?, subscription_type = ?, subscription_end_at = ?, updated_at = ? WHERE id = ?";
                try (PreparedStatement up = connection.prepareStatement(update)) {
                    up.setString(1, userRole);
                    up.setString(2, userSubscriptionStatus);
                    up.setString(3, subscriptionType);
                    if (end == null) {
                        up.setTimestamp(4, null);
                    } else {
                        up.setTimestamp(4, Timestamp.valueOf(end.atStartOfDay()));
                    }
                    up.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
                    up.setInt(6, userId);
                    up.executeUpdate();
                }
            }
        } catch (SQLException ignored) {
        }
    }

    private boolean fillModerationRows(List<ModerationRow> rows, String sql) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new ModerationRow(
                        rs.getInt("id"),
                        safe(rs.getString("titre")),
                        safe(rs.getString("type")),
                        safe(rs.getString("statut")),
                        safe(rs.getString("auteur_email")),
                        toLocalDateTime(rs.getTimestamp("created_at"))
                ));
            }
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private boolean fillCommunityRows(List<CommunityRow> rows, String sql, int limit) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String author = displayName(rs.getString("auteur_nom"), rs.getString("auteur_prenom"), rs.getString("auteur_email"));
                    rows.add(new CommunityRow(
                            rs.getInt("id"),
                            safe(rs.getString("titre")),
                            safe(rs.getString("type")),
                            safe(rs.getString("statut")),
                            author,
                            safe(rs.getString("categorie")),
                            safe(rs.getString("tags")),
                            rs.getInt("likes_count"),
                            rs.getInt("comments_count"),
                            rs.getDouble("score_article"),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at"))
                    ));
                }
            }
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private boolean fillAppointmentRows(List<AppointmentAdminRow> rows, String sql, int limit) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String patient = displayName(rs.getString("patient_nom"), rs.getString("patient_prenom"), rs.getString("patient_email"));
                    String doctor = displayName(rs.getString("medecin_nom"), rs.getString("medecin_prenom"), rs.getString("medecin_email"));
                    rows.add(new AppointmentAdminRow(
                            rs.getInt("id"),
                            patient,
                            doctor,
                            toLocalDate(rs.getDate("date_rdv")),
                            toLocalTime(rs.getTime("heure_rdv")),
                            safe(rs.getString("statut")),
                            safe(rs.getString("motif")),
                            toLocalDateTime(rs.getTimestamp("created_at"))
                    ));
                }
            }
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private boolean fillAccompanimentRows(List<AccompanimentAdminRow> rows, String sql, int limit) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String patient = displayName(rs.getString("patient_nom"), rs.getString("patient_prenom"), rs.getString("patient_email"));
                    String coach = displayName(rs.getString("coach_nom"), rs.getString("coach_prenom"), rs.getString("coach_email"));
                    String nutritionist = displayName(rs.getString("nutritionist_nom"), rs.getString("nutritionist_prenom"), rs.getString("nutritionist_email"));

                    String normalizedStatus = normalizeAccompanimentStatus(rs.getString("status"));
                    String finalStatus = normalizedStatus.isBlank() ? safe(rs.getString("status")) : normalizedStatus;

                    rows.add(new AccompanimentAdminRow(
                            rs.getInt("id"),
                            safe(rs.getString("title")),
                            patient,
                            (safe(coach).isBlank() || "Inconnu".equalsIgnoreCase(safe(coach))) ? "-" : coach,
                            (safe(nutritionist).isBlank() || "Inconnu".equalsIgnoreCase(safe(nutritionist))) ? "-" : nutritionist,
                            toLocalDate(rs.getDate("start_date")),
                            toLocalDate(rs.getDate("end_date")),
                            Math.max(0, rs.getInt("duration_weeks")),
                            finalStatus,
                            Math.max(0, rs.getInt("exercise_count")),
                            Math.max(0, rs.getInt("diet_count")),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at"))
                    ));
                }
            }
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private boolean fillPaymentsFromStripe(StringBuilder out, String sql) {
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
            return true;
        } catch (SQLException ignored) {
            return false;
        }
    }

    private boolean fillPaymentsFromFacture(StringBuilder out, String sql) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.append(csv(rs.getString("numero"))).append(',')
                        .append(rs.getInt("user_id")).append(',')
                        .append(csv("ABONNEMENT_" + rs.getString("abonnement_id"))).append(',')
                        .append(rs.getBigDecimal("montant_ttc")).append(',')
                        .append(csv(rs.getString("devise"))).append(',')
                        .append(csv("paid")).append(',')
                        .append(csv(String.valueOf(rs.getTimestamp("created_at"))))
                        .append('\n');
            }
            return true;
        } catch (SQLException ignored) {
            return false;
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

    private String generateOpaqueToken(int maxLength) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        if (token.length() <= maxLength) {
            return token;
        }
        return token.substring(0, Math.max(1, maxLength));
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

    private String normalizeAdminEditableRole(String role) {
        String normalized = safe(role).toUpperCase();
        return switch (normalized) {
            case "ROLE_ADMIN", "ROLE_PATIENT", "ROLE_USER", "ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE" -> normalized;
            default -> "";
        };
    }

    private String normalizeModerationStatus(String status) {
        String normalized = safe(status).toLowerCase();
        return switch (normalized) {
            case "valide", "publie", "rejete", "en_attente" -> normalized;
            case "approved", "approve", "accept" -> "valide";
            case "reject", "rejected" -> "rejete";
            case "pending" -> "en_attente";
            default -> "";
        };
    }

    private String normalizeAppointmentStatus(String status) {
        String normalized = safe(status).toLowerCase();
        return switch (normalized) {
            case "en_attente", "pending", "requested" -> "en_attente";
            case "confirme", "confirmed", "approve", "approved" -> "confirme";
            case "refuse", "rejected", "reject" -> "refuse";
            case "annule", "cancelled", "canceled", "cancel" -> "annule";
            default -> "";
        };
    }

    private String normalizeAccompanimentStatus(String status) {
        String normalized = safe(status).toLowerCase();
        return switch (normalized) {
            case "active", "en_cours", "ongoing", "in_progress" -> "active";
            case "paused", "pause", "suspendu", "suspended" -> "paused";
            case "completed", "termine", "terminee", "valide" -> "completed";
            case "cancelled", "canceled", "annule", "annulee", "rejete", "refuse" -> "cancelled";
            default -> "";
        };
    }

    private String normalizeAccompanimentStatusForLegacy(String normalizedStatus) {
        return switch (safe(normalizedStatus).toLowerCase()) {
            case "active" -> "en_cours";
            case "paused" -> "suspendu";
            case "completed" -> "termine";
            case "cancelled" -> "annule";
            default -> "en_cours";
        };
    }

    private String displayName(String nom, String prenom, String email) {
        String full = (safe(prenom) + " " + safe(nom)).trim();
        if (!full.isBlank()) {
            return full;
        }
        return safe(email).isBlank() ? "Inconnu" : safe(email);
    }

    private String supportPriorityLabel(int score) {
        if (score < 50) {
            return "Haute";
        }
        if (score < 75) {
            return "Moyenne";
        }
        return "Normale";
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

    public record PendingApprovalRow(
            int id,
            String nom,
            String prenom,
            String email,
            String currentRole,
            LocalDateTime createdAt
    ) {
        public String displayName() {
            String fullName = (safePart(prenom) + " " + safePart(nom)).trim();
            return fullName.isBlank() ? email : fullName;
        }

        private static String safePart(String value) {
            return value == null ? "" : value.trim();
        }
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

    public record ModerationRow(
            int id,
            String title,
            String type,
            String status,
            String authorEmail,
            LocalDateTime createdAt
    ) {
    }

    public record UserScoreRow(
            int userId,
            String nom,
            String prenom,
            String email,
            String role,
            int score,
            String supportPriority
    ) {
        public String displayName() {
            String fullName = (safePart(prenom) + " " + safePart(nom)).trim();
            return fullName.isBlank() ? email : fullName;
        }

        private static String safePart(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record AdminUserRow(
            int id,
            String nom,
            String prenom,
            String email,
            String role,
            String subscriptionStatus,
            boolean banned,
            boolean emailVerified,
            boolean adminApproved,
            LocalDateTime createdAt,
            int score,
            String supportPriority
    ) {
        public String displayName() {
            String fullName = (safePart(prenom) + " " + safePart(nom)).trim();
            return fullName.isBlank() ? email : fullName;
        }

        private static String safePart(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record ValidationStats(
            int totalUsers,
            int emailConfirmed,
            int emailNotConfirmed,
            int adminPending
    ) {
    }

    public record ValidationUserRow(
            int id,
            String nom,
            String prenom,
            String email,
            boolean emailVerified,
            boolean adminApproved,
            LocalDateTime createdAt
    ) {
        public String displayName() {
            String fullName = (safePart(prenom) + " " + safePart(nom)).trim();
            return fullName.isBlank() ? email : fullName;
        }

        private static String safePart(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record SubscriptionStats(
            int total,
            int active,
            int expired,
            int cancelled
    ) {
    }

    public record SubscriptionRow(
            int id,
            int userId,
            String nom,
            String prenom,
            String email,
            String type,
            String status,
            LocalDate dateDebut,
            LocalDate dateFin,
            BigDecimal price
    ) {
        public String displayName() {
            String fullName = (safePart(prenom) + " " + safePart(nom)).trim();
            return fullName.isBlank() ? email : fullName;
        }

        private static String safePart(String value) {
            return value == null ? "" : value.trim();
        }
    }

    public record RevenueStats(
            int totalInvoices,
            BigDecimal totalRevenue,
            BigDecimal monthlyRevenue
    ) {
    }

    public record RevenueRow(
            String invoiceNumber,
            String nom,
            String prenom,
            String planType,
            BigDecimal totalTtc,
            String currency,
            LocalDateTime createdAt,
            String pdfPath
    ) {
        public String displayName() {
            String fullName = (safePart(prenom) + " " + safePart(nom)).trim();
            return fullName.isBlank() ? "Client" : fullName;
        }

        private static String safePart(String value) {
            return value == null ? "" : value.trim();
        }
    }

        public record CommunityStats(
            int total,
            int pending,
            int published,
            int rejected
        ) {
        }

        public record CommunityRow(
            int id,
            String title,
            String type,
            String status,
            String authorDisplay,
            String category,
            String tags,
            int likesCount,
            int commentsCount,
            double score,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
        ) {
        }

        public record AppointmentAdminStats(
            int total,
            int pending,
            int confirmed,
            int refused,
            int cancelled
        ) {
        }

        public record AppointmentAdminRow(
            int id,
            String patientDisplay,
            String doctorDisplay,
            LocalDate date,
            LocalTime time,
            String status,
            String motif,
            LocalDateTime createdAt
        ) {
        }

        public record AccompanimentAdminStats(
            int total,
            int active,
            int completed,
            int cancelled
        ) {
        }

        public record AccompanimentAdminRow(
            int id,
            String title,
            String patientDisplay,
            String coachDisplay,
            String nutritionistDisplay,
            LocalDate startDate,
            LocalDate endDate,
            int durationWeeks,
            String status,
            int exerciseCount,
            int dietCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
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
