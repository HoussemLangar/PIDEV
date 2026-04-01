package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class SubscriptionService {
    private final DatabaseService databaseService;

    public SubscriptionService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public List<PlanCard> getPlans() {
        return List.of(
                new PlanCard("Medecin", "ROLE_MEDECIN", "10.00", "Consultations video, gestion rendez-vous, dossiers patients"),
                new PlanCard("Pharmacien", "ROLE_PHARMACIEN", "10.00", "Gestion stock, ordonnances, pharmacie en ligne"),
                new PlanCard("Coach sportif", "ROLE_COACH", "10.00", "Plans d'entrainement, suivi clients, coaching"),
                new PlanCard("Nutritionniste", "ROLE_NUTRITIONNISTE", "10.00", "Plans nutritionnels, suivi alimentaire, recettes"),
                new PlanCard("Patient", "ROLE_PATIENT", "10.00", "Journal sante, teleconsultation, suivi symptomes")
        );
    }

    public SubscriptionOverview loadOverview(User user) {
        if (user == null || user.getId() == null || !databaseService.canConnect()) {
            return SubscriptionOverview.empty();
        }

        ensureAbonnementTable();
        expireIfNeeded(user);

        AbonnementRow latest = findLatestAbonnement(user.getId());
        boolean active = latest != null && isAbonnementActive(latest);

        if (!active) {
            return SubscriptionOverview.inactive(safe(user.getSubscriptionStatus()), safe(user.getSubscriptionType()));
        }

        return SubscriptionOverview.active(
                latest.nom,
                latest.type,
                latest.prix,
                latest.dateDebut,
                latest.dateFin,
                latest.statut
        );
    }

    public ActionResult activatePaidSubscription(User user, String type) {
        if (user == null || user.getId() == null) {
            return ActionResult.failure("Utilisateur invalide.");
        }
        if (!databaseService.canConnect()) {
            return ActionResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        ensureAbonnementTable();

        if (!isValidType(type)) {
            return ActionResult.failure("Type d'abonnement invalide.");
        }

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusMonths(1);

        String insert = "INSERT INTO abonnements (nom, type_abonnement, prix, duree_mois, avantages, description, date_debut, date_fin, statut, created_at, updated_at, user_id) "
                + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, 'actif', ?, ?, ?)";

        String updateUser = "UPDATE users SET role = ?, subscription_status = 'ACTIVE', subscription_type = ?, subscription_end_at = ?, updated_at = ? WHERE id = ?";

        try (Connection connection = databaseService.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement insertStmt = connection.prepareStatement(insert)) {
                insertStmt.setString(1, labelFromRole(type));
                insertStmt.setString(2, type);
                insertStmt.setBigDecimal(3, new BigDecimal("10.00"));
                insertStmt.setString(4, defaultAdvantages(type));
                insertStmt.setString(5, "Abonnement premium SANTEA");
                insertStmt.setDate(6, Date.valueOf(start));
                insertStmt.setDate(7, Date.valueOf(end));
                insertStmt.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                insertStmt.setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()));
                insertStmt.setInt(10, user.getId());
                insertStmt.executeUpdate();
            }

            try (PreparedStatement updateStmt = connection.prepareStatement(updateUser)) {
                updateStmt.setString(1, type);
                updateStmt.setString(2, type);
                updateStmt.setTimestamp(3, Timestamp.valueOf(end.atStartOfDay()));
                updateStmt.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                updateStmt.setInt(5, user.getId());
                updateStmt.executeUpdate();
            }

            connection.commit();

            user.setRole(type);
            user.setSubscriptionStatus("ACTIVE");
            user.setSubscriptionType(type);
            user.setSubscriptionEndAt(end.atStartOfDay());
            return ActionResult.success("Paiement reussi. Abonnement active.");
        } catch (SQLException exception) {
            return ActionResult.failure("Activation impossible: " + exception.getMessage());
        }
    }

    public ActionResult skip(User user) {
        if (user == null || user.getId() == null || !databaseService.canConnect()) {
            return ActionResult.failure("Action impossible sans session valide.");
        }

        String sql = "UPDATE users SET subscription_status = 'SKIPPED', subscription_type = NULL, subscription_end_at = NULL, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(2, user.getId());
            statement.executeUpdate();

            user.setSubscriptionStatus("SKIPPED");
            user.setSubscriptionType(null);
            user.setSubscriptionEndAt(null);
            return ActionResult.success("Mode decouverte active.");
        } catch (SQLException exception) {
            return ActionResult.failure("Impossible d'activer le mode decouverte.");
        }
    }

    public ActionResult cancel(User user) {
        if (user == null || user.getId() == null || !databaseService.canConnect()) {
            return ActionResult.failure("Action impossible sans session valide.");
        }

        ensureAbonnementTable();

        String deleteAbonnements = "DELETE FROM abonnements WHERE user_id = ?";
        String updateUser = "UPDATE users SET role = 'ROLE_USER', subscription_status = 'EXPIRED', subscription_type = NULL, subscription_end_at = NULL, updated_at = ? WHERE id = ?";

        try (Connection connection = databaseService.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement deleteStmt = connection.prepareStatement(deleteAbonnements)) {
                deleteStmt.setInt(1, user.getId());
                deleteStmt.executeUpdate();
            }

            try (PreparedStatement updateStmt = connection.prepareStatement(updateUser)) {
                updateStmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                updateStmt.setInt(2, user.getId());
                updateStmt.executeUpdate();
            }

            connection.commit();
            user.setRole("ROLE_USER");
            user.setSubscriptionStatus("EXPIRED");
            user.setSubscriptionType(null);
            user.setSubscriptionEndAt(null);
            return ActionResult.success("Abonnement annule.");
        } catch (SQLException exception) {
            return ActionResult.failure("Erreur annulation abonnement.");
        }
    }

    private void expireIfNeeded(User user) {
        AbonnementRow latest = findLatestAbonnement(user.getId());
        if (latest == null) {
            return;
        }

        if (latest.dateFin != null && latest.dateFin.isBefore(LocalDate.now()) && "actif".equalsIgnoreCase(latest.statut)) {
            String sql = "UPDATE abonnements SET statut='expire', updated_at=? WHERE id=?";
            String updateUser = "UPDATE users SET role='ROLE_USER', subscription_status='EXPIRED', subscription_type=NULL, subscription_end_at=NULL, updated_at=? WHERE id=?";

            try (Connection connection = databaseService.getConnection();
                 PreparedStatement stmt1 = connection.prepareStatement(sql);
                 PreparedStatement stmt2 = connection.prepareStatement(updateUser)) {
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                stmt1.setTimestamp(1, now);
                stmt1.setInt(2, latest.id);
                stmt1.executeUpdate();

                stmt2.setTimestamp(1, now);
                stmt2.setInt(2, user.getId());
                stmt2.executeUpdate();

                user.setRole("ROLE_USER");
                user.setSubscriptionStatus("EXPIRED");
                user.setSubscriptionType(null);
                user.setSubscriptionEndAt(null);
            } catch (SQLException ignored) {
            }
        }
    }

    private AbonnementRow findLatestAbonnement(int userId) {
        String sql = "SELECT id, nom, type_abonnement, prix, date_debut, date_fin, statut FROM abonnements WHERE user_id=? ORDER BY date_fin DESC LIMIT 1";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    AbonnementRow row = new AbonnementRow();
                    row.id = rs.getInt("id");
                    row.nom = safe(rs.getString("nom"));
                    row.type = safe(rs.getString("type_abonnement"));
                    row.prix = rs.getBigDecimal("prix") == null ? "10.00" : rs.getBigDecimal("prix").toPlainString();
                    Date debut = rs.getDate("date_debut");
                    Date fin = rs.getDate("date_fin");
                    row.dateDebut = debut == null ? null : debut.toLocalDate();
                    row.dateFin = fin == null ? null : fin.toLocalDate();
                    row.statut = safe(rs.getString("statut"));
                    return row;
                }
            }
        } catch (SQLException ignored) {
        }
        return null;
    }

    private boolean isAbonnementActive(AbonnementRow row) {
        return row != null
                && row.dateFin != null
                && !row.dateFin.isBefore(LocalDate.now())
                && "actif".equalsIgnoreCase(row.statut);
    }

    private void ensureAbonnementTable() {
        String sql = "CREATE TABLE IF NOT EXISTS abonnements ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "nom VARCHAR(100) NOT NULL,"
                + "type_abonnement VARCHAR(50) NOT NULL,"
                + "prix NUMERIC(10,2) NOT NULL,"
                + "duree_mois INT NOT NULL DEFAULT 1,"
                + "avantages LONGTEXT NULL,"
                + "description LONGTEXT NULL,"
                + "date_debut DATE NOT NULL,"
                + "date_fin DATE NOT NULL,"
                + "statut VARCHAR(20) NOT NULL DEFAULT 'actif',"
                + "created_at DATETIME NOT NULL,"
                + "updated_at DATETIME NOT NULL,"
                + "user_id INT NULL"
                + ")";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException ignored) {
        }
    }

    private boolean isValidType(String type) {
        return List.of("ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE", "ROLE_PATIENT").contains(type);
    }

    private String labelFromRole(String role) {
        return switch (role) {
            case "ROLE_MEDECIN" -> "Medecin";
            case "ROLE_PHARMACIEN" -> "Pharmacien";
            case "ROLE_COACH" -> "Coach sportif";
            case "ROLE_NUTRITIONNISTE" -> "Nutritionniste";
            case "ROLE_PATIENT" -> "Patient";
            default -> "Abonnement";
        };
    }

    private String defaultAdvantages(String role) {
        return switch (role) {
            case "ROLE_MEDECIN" -> "Consultations video, gestion rendez-vous, dossiers patients";
            case "ROLE_PHARMACIEN" -> "Gestion stock, ordonnances, pharmacie en ligne";
            case "ROLE_COACH" -> "Plans d'entrainement, suivi clients, coaching";
            case "ROLE_NUTRITIONNISTE" -> "Plans nutritionnels, suivi alimentaire, recettes";
            case "ROLE_PATIENT" -> "Journal sante, teleconsultation, suivi symptomes";
            default -> "Acces premium";
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record PlanCard(String label, String role, String price, String features) {
    }

    public record SubscriptionOverview(
            boolean active,
            String planName,
            String type,
            String price,
            LocalDate dateDebut,
            LocalDate dateFin,
            String status,
            String currentStatus,
            String currentType
    ) {
        public static SubscriptionOverview inactive(String currentStatus, String currentType) {
            return new SubscriptionOverview(false, "", "", "", null, null, "", currentStatus, currentType);
        }

        public static SubscriptionOverview active(
                String planName,
                String type,
                String price,
                LocalDate dateDebut,
                LocalDate dateFin,
                String status
        ) {
            return new SubscriptionOverview(true, planName, type, price, dateDebut, dateFin, status, "ACTIVE", type);
        }

        public static SubscriptionOverview empty() {
            return inactive("", "");
        }
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    private static class AbonnementRow {
        private int id;
        private String nom;
        private String type;
        private String prix;
        private LocalDate dateDebut;
        private LocalDate dateFin;
        private String statut;
    }
}
