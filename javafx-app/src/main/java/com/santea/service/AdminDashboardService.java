package com.santea.service;

import com.santea.config.DatabaseConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AdminDashboardService {
    private final DatabaseService databaseService;

    public AdminDashboardService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public AdminDashboardData loadData() {
        if (!databaseService.canConnect()) {
            return AdminDashboardData.empty();
        }

        int totalUsers = scalarCount("SELECT COUNT(*) FROM users");
        int bannedUsers = scalarCount("SELECT COUNT(*) FROM users WHERE is_banned = 1");
        int verifiedUsers = scalarCount("SELECT COUNT(*) FROM users WHERE email_verified = 1");
        int pendingUsers = scalarCount("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND email_verified = 1 AND admin_approved = 0");
        int unreadSuspicious = scalarCountSafe("SELECT COUNT(*) FROM suspicious_logins WHERE blocked = 1");
        int paymentsSucceeded = firstWorkingCount(
            "SELECT COUNT(*) FROM stripe_payments WHERE status = 'succeeded'",
            "SELECT COUNT(*) FROM factures"
        );
        int activeSessions = scalarCountSafe("SELECT COUNT(*) FROM user_sessions WHERE is_active = 1");

        int appointmentsToday = firstWorkingCount(
            "SELECT COUNT(*) FROM rendez_vous WHERE DATE(date_rdv) = CURDATE()",
            "SELECT COUNT(*) FROM appointments WHERE DATE(appointment_date) = CURDATE()"
        );

        int activeSubscriptions = firstWorkingCount(
            "SELECT COUNT(*) FROM abonnements WHERE statut = 'actif'",
            "SELECT COUNT(*) FROM users WHERE subscription_status = 'ACTIVE'"
        );

        int pendingPayments = firstWorkingCount(
            "SELECT COUNT(*) FROM users WHERE subscription_status = 'PENDING'",
            "SELECT COUNT(*) FROM stripe_payments WHERE status = 'pending'"
        );

        int pendingModeration = firstWorkingCount(
            "SELECT COUNT(*) FROM contenu WHERE statut = 'en_attente'",
            "SELECT COUNT(*) FROM contenus WHERE moderation_status = 'pending'"
        );

        BigDecimal monthlyRevenue = firstWorkingDecimal(
            "SELECT COALESCE(SUM(montant_ttc), 0) FROM factures WHERE created_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01') AND created_at < DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')",
            "SELECT COALESCE(SUM(amount), 0) FROM stripe_payments WHERE status = 'succeeded' AND updated_at >= DATE_FORMAT(CURDATE(), '%Y-%m-01') AND updated_at < DATE_FORMAT(DATE_ADD(CURDATE(), INTERVAL 1 MONTH), '%Y-%m-01')"
        );

        List<String> latestBans = latestBanSamples();

        return new AdminDashboardData(
            totalUsers,
            bannedUsers,
            verifiedUsers,
            pendingUsers,
            unreadSuspicious,
            appointmentsToday,
            paymentsSucceeded,
            activeSessions,
            activeSubscriptions,
            pendingPayments,
            pendingModeration,
            monthlyRevenue,
            latestBans
        );
    }

    private int scalarCount(String sql) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        } catch (SQLException ignored) {
            return 0;
        }

        return 0;
    }

    private int scalarCountSafe(String sql) {
        try {
            return scalarCount(sql);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int firstWorkingCount(String... sqlOptions) {
        for (String sql : sqlOptions) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            } catch (SQLException ignored) {
            }
        }
        return 0;
    }

    private BigDecimal firstWorkingDecimal(String... sqlOptions) {
        for (String sql : sqlOptions) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    BigDecimal value = resultSet.getBigDecimal(1);
                    return value == null ? BigDecimal.ZERO : value;
                }
            } catch (SQLException ignored) {
            }
        }
        return BigDecimal.ZERO;
    }

    private List<String> latestBanSamples() {
        String sql = "SELECT email, ban_reason FROM users WHERE is_banned = 1 ORDER BY updated_at DESC LIMIT 5";
        List<String> rows = new ArrayList<>();

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                String email = resultSet.getString("email") == null ? "utilisateur" : resultSet.getString("email");
                String reason = resultSet.getString("ban_reason") == null ? "motif non specifie" : resultSet.getString("ban_reason");
                rows.add(email + " - " + reason);
            }
        } catch (SQLException ignored) {
            return List.of();
        }

        return rows;
    }

    public record AdminDashboardData(
            int totalUsers,
            int bannedUsers,
            int verifiedUsers,
            int pendingUsers,
            int blockedSuspicious,
            int appointmentsToday,
            int succeededPayments,
            int activeSessions,
            int activeSubscriptions,
            int pendingPayments,
            int pendingModeration,
            BigDecimal monthlyRevenue,
            List<String> latestBans
    ) {
        public static AdminDashboardData empty() {
            return new AdminDashboardData(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO, List.of());
        }

        public String monthlyRevenueDisplay() {
            return monthlyRevenue == null ? "0.00" : monthlyRevenue.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }
    }
}
