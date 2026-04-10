package com.santea.repository;

import com.santea.model.User;
import com.santea.service.DatabaseService;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public class UserRepository {
    private final DatabaseService databaseService;

    public UserRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
        ensureUserColumns();
    }

    public Optional<User> findByEmailOrUsername(String identifier) {
        String sql = "SELECT id, username, email, password, nom, prenom, role, email_verified, admin_approved, "
            + "is_banned, ban_reason, ban_until, subscription_status, subscription_type, subscription_end_at, "
            + "theme_preference, locale, mfa_enabled, google_authenticator_secret, "
            + "telephone, adresse, date_naissance, reminder_enabled, avatar_data, avatar_mime "
            + "FROM users WHERE email = ? OR username = ? LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, identifier);
            statement.setString(2, identifier);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapUser(resultSet));
            }
        } catch (SQLException exception) {
            return Optional.empty();
        }
    }

    public Optional<User> findByEmail(String email) {
        String sql = "SELECT id, username, email, password, nom, prenom, role, email_verified, admin_approved, "
                + "is_banned, ban_reason, ban_until, subscription_status, subscription_type, subscription_end_at, "
                + "theme_preference, locale, mfa_enabled, google_authenticator_secret, "
                + "telephone, adresse, date_naissance, reminder_enabled, avatar_data, avatar_mime "
                + "FROM users WHERE email = ? LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(mapUser(resultSet));
            }
        } catch (SQLException exception) {
            return Optional.empty();
        }
    }

    public boolean existsByEmail(String email) {
        return existsByColumn("email", email);
    }

    public boolean existsByUsername(String username) {
        return existsByColumn("username", username);
    }

    public boolean createUser(User user) {
        String sql = "INSERT INTO users (username, email, password, nom, prenom, date_naissance, adresse, telephone, role, "
                + "email_verified, admin_approved, subscription_status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, user.getUsername());
            statement.setString(2, user.getEmail());
            statement.setString(3, user.getPassword());
            statement.setString(4, user.getNom());
            statement.setString(5, user.getPrenom());

            LocalDate localDate = user.getDateNaissance() == null
                    ? null
                    : user.getDateNaissance().toLocalDate();
            if (localDate == null) {
                statement.setNull(6, java.sql.Types.DATE);
            } else {
                statement.setDate(6, Date.valueOf(localDate));
            }

            statement.setString(7, emptyToNull(user.getAdresse()));
            statement.setString(8, emptyToNull(user.getTelephone()));
            statement.setString(9, emptyToDefault(user.getRole(), "ROLE_PATIENT"));
            statement.setBoolean(10, Boolean.TRUE.equals(user.getEmailVerified()));
            statement.setBoolean(11, Boolean.TRUE.equals(user.getAdminApproved()));
            statement.setString(12, emptyToDefault(user.getSubscriptionStatus(), "PENDING"));

            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            return false;
        }
    }

    public Optional<PasswordResetTokenRecord> createPasswordResetToken(int userId, LocalDateTime expiresAt) {
        String token = java.util.UUID.randomUUID().toString().replace("-", "")
                + java.util.UUID.randomUUID().toString().replace("-", "");
        if (token.length() > 64) {
            token = token.substring(0, 64);
        }

        String sql = "INSERT INTO password_reset_tokens (token, expires_at, created_at, is_used, user_id) "
                + "VALUES (?, ?, NOW(), 0, ?)";

        try (Connection connection = databaseService.getConnection();
               PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            statement.setString(1, token);
            statement.setTimestamp(2, Timestamp.valueOf(expiresAt));
            statement.setInt(3, userId);

            int changed = statement.executeUpdate();
            if (changed != 1) {
                return Optional.empty();
            }

            try (ResultSet keys = statement.getGeneratedKeys()) {
                int id = keys.next() ? keys.getInt(1) : 0;
                return Optional.of(new PasswordResetTokenRecord(id, userId, token, expiresAt, false));
            }
        } catch (SQLException exception) {
            return Optional.empty();
        }
    }

    public Optional<PasswordResetTokenRecord> findValidPasswordResetToken(String token) {
        String sql = "SELECT id, user_id, token, expires_at, is_used FROM password_reset_tokens "
                + "WHERE token = ? AND is_used = 0 AND expires_at > NOW() LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, token);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                Timestamp expiresTs = resultSet.getTimestamp("expires_at");
                LocalDateTime expiresAt = expiresTs == null ? LocalDateTime.now() : expiresTs.toLocalDateTime();

                return Optional.of(new PasswordResetTokenRecord(
                        resultSet.getInt("id"),
                        resultSet.getInt("user_id"),
                        resultSet.getString("token"),
                        expiresAt,
                        resultSet.getBoolean("is_used")
                ));
            }
        } catch (SQLException exception) {
            return Optional.empty();
        }
    }

    public boolean markPasswordResetTokenAsUsed(int tokenId) {
        String sql = "UPDATE password_reset_tokens SET is_used = 1 WHERE id = ?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, tokenId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            return false;
        }
    }

    public boolean updatePassword(int userId, String passwordHash) {
        String sql = "UPDATE users SET password = ?, updated_at = NOW() WHERE id = ?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, passwordHash);
            statement.setInt(2, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            return false;
        }
    }

    public boolean updateProfilePreferences(int userId, String nom, String prenom, String telephone, String adresse, String theme, String locale, Boolean reminderEnabled) {
        String sql = "UPDATE users SET nom = ?, prenom = ?, telephone = ?, adresse = ?, theme_preference = ?, locale = ?, reminder_enabled = ?, updated_at = NOW() WHERE id = ?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, emptyToNull(nom));
            statement.setString(2, emptyToNull(prenom));
            statement.setString(3, emptyToNull(telephone));
            statement.setString(4, emptyToNull(adresse));
            statement.setString(5, emptyToNull(theme));
            statement.setString(6, emptyToNull(locale));
            statement.setBoolean(7, Boolean.TRUE.equals(reminderEnabled));
            statement.setInt(8, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            return false;
        }
    }

    public boolean updateMfaConfiguration(int userId, boolean enabled, String secret) {
        String sql = "UPDATE users SET mfa_enabled = ?, google_authenticator_secret = ?, updated_at = NOW() WHERE id = ?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, enabled);
            statement.setString(2, emptyToNull(secret));
            statement.setInt(3, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            return false;
        }
    }

    public boolean softDeleteUser(int userId) {
        String sql = "UPDATE users SET deleted_at = NOW(), is_banned = 1, ban_reason = 'Compte supprime par utilisateur', updated_at = NOW() WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            return false;
        }
    }

    public Optional<User> findById(int userId) {
        String sql = "SELECT id, username, email, password, nom, prenom, role, email_verified, admin_approved, "
                + "is_banned, ban_reason, ban_until, subscription_status, subscription_type, subscription_end_at, "
                + "theme_preference, locale, mfa_enabled, google_authenticator_secret, "
                + "telephone, adresse, date_naissance, reminder_enabled, avatar_data, avatar_mime "
                + "FROM users WHERE id = ? LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapUser(resultSet));
            }
        } catch (SQLException exception) {
            return Optional.empty();
        }
    }

    public boolean updateEmailVerificationToken(int userId, String verificationToken, LocalDateTime expiresAt) {
        String sql = "UPDATE users SET email_verification_token = ?, email_verification_expires_at = ?, updated_at = NOW() WHERE id = ?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, verificationToken);
            statement.setTimestamp(2, Timestamp.valueOf(expiresAt));
            statement.setInt(3, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            return false;
        }
    }

    public void deleteExpiredOrUsedResetTokens(int userId) {
        String sql = "DELETE FROM password_reset_tokens WHERE user_id = ? AND (is_used = 1 OR expires_at <= NOW())";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setInt(1, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            // Intentionnellement silencieux: le nettoyage n'est pas bloquant.
        }
    }

    private boolean existsByColumn(String column, String value) {
        String sql = "SELECT id FROM users WHERE " + column + " = ? LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        User user = new User();
        user.setId(resultSet.getInt("id"));
        user.setUsername(resultSet.getString("username"));
        user.setEmail(resultSet.getString("email"));
        user.setPassword(resultSet.getString("password"));
        user.setNom(resultSet.getString("nom"));
        user.setPrenom(resultSet.getString("prenom"));
        user.setRole(resultSet.getString("role"));
        user.setEmailVerified(resultSet.getBoolean("email_verified"));
        user.setAdminApproved(resultSet.getBoolean("admin_approved"));
        user.setIsBanned(resultSet.getBoolean("is_banned"));
        user.setBanReason(resultSet.getString("ban_reason"));
        user.setSubscriptionStatus(resultSet.getString("subscription_status"));
        user.setSubscriptionType(resultSet.getString("subscription_type"));
        user.setThemePreference(resultSet.getString("theme_preference"));
        user.setLocale(resultSet.getString("locale"));
        user.setMfaEnabled(resultSet.getBoolean("mfa_enabled"));
        user.setGoogleAuthenticatorSecret(resultSet.getString("google_authenticator_secret"));
        user.setTelephone(resultSet.getString("telephone"));
        user.setAdresse(resultSet.getString("adresse"));
        user.setAvatarData(resultSet.getString("avatar_data"));
        user.setAvatarMime(resultSet.getString("avatar_mime"));
        user.setReminderEnabled(resultSet.getBoolean("reminder_enabled"));

        Timestamp dateNaissance = resultSet.getTimestamp("date_naissance");
        if (dateNaissance != null) {
            user.setDateNaissance(dateNaissance.toLocalDateTime());
        }

        Timestamp banUntil = resultSet.getTimestamp("ban_until");
        if (banUntil != null) {
            user.setBanUntil(banUntil.toLocalDateTime());
        }

        Timestamp subscriptionEndAt = resultSet.getTimestamp("subscription_end_at");
        if (subscriptionEndAt != null) {
            user.setSubscriptionEndAt(subscriptionEndAt.toLocalDateTime());
        }

        return user;
    }

    private String emptyToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String emptyToDefault(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private void ensureUserColumns() {
        String[] alters = new String[] {
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS subscription_type VARCHAR(50) NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS subscription_end_at DATETIME NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS theme_preference VARCHAR(32) NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS locale VARCHAR(8) NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN NOT NULL DEFAULT 0",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS google_authenticator_secret VARCHAR(255) NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS reminder_enabled BOOLEAN NOT NULL DEFAULT 0",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_data LONGTEXT NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_mime VARCHAR(120) NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at DATETIME NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_token VARCHAR(255) NULL",
                "ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verification_expires_at DATETIME NULL"
        };

        for (String sql : alters) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.execute();
            } catch (SQLException ignored) {
            }
        }
    }

    public record PasswordResetTokenRecord(int id, int userId, String token, LocalDateTime expiresAt, boolean isUsed) {
    }
}
