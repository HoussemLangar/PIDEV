package com.santea.service;

import com.santea.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class HomeDashboardService {
    private final DatabaseService databaseService;

    public HomeDashboardService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
    }

    public DashboardData loadForUser(int userId) {
        if (!databaseService.canConnect()) {
            return DashboardData.empty();
        }

        int unreadNotifications = countUnreadNotifications(userId);
        int unreadMessages = countUnreadMessages(userId);

        List<NotificationPreview> notifications = loadRecentNotifications(userId, 6);
        List<ConversationPreview> conversations = loadRecentConversations(userId, 6);

        return new DashboardData(unreadNotifications, unreadMessages, notifications, conversations);
    }

    public boolean markAllNotificationsRead(int userId) {
        String sql = "UPDATE notifications SET lu = 1 WHERE user_id = ? AND lu = 0";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            statement.executeUpdate();
            return true;
        } catch (SQLException exception) {
            return false;
        }
    }

    private int countUnreadNotifications(int userId) {
        String sql = "SELECT COUNT(*) AS total FROM notifications WHERE user_id = ? AND lu = 0";
        return singleCountQuery(sql, userId);
    }

    private int countUnreadMessages(int userId) {
        String sql = "SELECT COUNT(*) AS total FROM messages WHERE recipient_id = ? AND is_read = 0";
        return singleCountQuery(sql, userId);
    }

    private int singleCountQuery(String sql, int userId) {
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("total");
                }
            }
        } catch (SQLException exception) {
            return 0;
        }
        return 0;
    }

    private List<NotificationPreview> loadRecentNotifications(int userId, int limit) {
        String sql = "SELECT id, titre, message, lu, date_envoi FROM notifications "
                + "WHERE user_id = ? ORDER BY date_envoi DESC LIMIT ?";

        List<NotificationPreview> list = new ArrayList<>();

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            statement.setInt(2, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    list.add(new NotificationPreview(
                            resultSet.getInt("id"),
                            resultSet.getString("titre"),
                            resultSet.getString("message"),
                            resultSet.getBoolean("lu"),
                            toLocalDateTime(resultSet.getTimestamp("date_envoi"))
                    ));
                }
            }
        } catch (SQLException exception) {
            return List.of();
        }

        return list;
    }

    private List<ConversationPreview> loadRecentConversations(int userId, int limit) {
        String sql = "SELECT c.id AS conversation_id, "
                + "u.id AS other_user_id, u.nom AS other_nom, u.prenom AS other_prenom, "
                + "m.content AS last_message, m.created_at AS last_message_at, "
                + "(SELECT COUNT(*) FROM messages um WHERE um.conversation_id = c.id AND um.recipient_id = ? AND um.is_read = 0) AS unread_in_conversation "
                + "FROM conversations c "
                + "JOIN users u ON u.id = CASE WHEN c.user_one_id = ? THEN c.user_two_id ELSE c.user_one_id END "
                + "JOIN messages m ON m.id = (SELECT m2.id FROM messages m2 WHERE m2.conversation_id = c.id ORDER BY m2.created_at DESC LIMIT 1) "
                + "WHERE c.user_one_id = ? OR c.user_two_id = ? "
                + "ORDER BY m.created_at DESC LIMIT ?";

        List<ConversationPreview> list = new ArrayList<>();

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            statement.setInt(2, userId);
            statement.setInt(3, userId);
            statement.setInt(4, userId);
            statement.setInt(5, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String nom = resultSet.getString("other_nom") == null ? "" : resultSet.getString("other_nom");
                    String prenom = resultSet.getString("other_prenom") == null ? "" : resultSet.getString("other_prenom");
                    String display = (nom + " " + prenom).trim();
                    if (display.isBlank()) {
                        display = "Utilisateur";
                    }

                    list.add(new ConversationPreview(
                            resultSet.getInt("conversation_id"),
                            resultSet.getInt("other_user_id"),
                            display,
                            resultSet.getString("last_message"),
                            toLocalDateTime(resultSet.getTimestamp("last_message_at")),
                            resultSet.getInt("unread_in_conversation")
                    ));
                }
            }
        } catch (SQLException exception) {
            return List.of();
        }

        return list;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record DashboardData(
            int unreadNotifications,
            int unreadMessages,
            List<NotificationPreview> notifications,
            List<ConversationPreview> conversations
    ) {
        public static DashboardData empty() {
            return new DashboardData(0, 0, List.of(), List.of());
        }
    }

    public record NotificationPreview(int id, String titre, String message, boolean read, LocalDateTime sentAt) {
    }

    public record ConversationPreview(
            int conversationId,
            int otherUserId,
            String otherUserDisplay,
            String lastMessage,
            LocalDateTime lastMessageAt,
            int unreadInConversation
    ) {
    }
}
