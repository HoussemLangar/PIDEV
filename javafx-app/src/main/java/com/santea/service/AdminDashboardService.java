package com.santea.service;

import com.santea.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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
        int pendingUsers = scalarCount("SELECT COUNT(*) FROM users WHERE email_verified = 1 AND admin_approved = 0");
        int unreadSuspicious = scalarCount("SELECT COUNT(*) FROM suspicious_logins WHERE blocked = 1");

        int appointmentsToday = scalarCountWithDate(
                "SELECT COUNT(*) FROM appointments WHERE DATE(appointment_date) = ?",
                LocalDate.now().toString()
        );

        List<String> latestBans = latestBanSamples();

        return new AdminDashboardData(totalUsers, bannedUsers, verifiedUsers, pendingUsers, unreadSuspicious, appointmentsToday, latestBans);
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

    private int scalarCountWithDate(String sql, String date) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, date);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        } catch (SQLException ignored) {
            return 0;
        }

        return 0;
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
            List<String> latestBans
    ) {
        public static AdminDashboardData empty() {
            return new AdminDashboardData(0, 0, 0, 0, 0, 0, List.of());
        }
    }
}
