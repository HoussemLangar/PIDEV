package com.santea.repository;

import com.santea.model.GoogleFitAccount;
import com.santea.service.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Optional;

public class GoogleFitAccountRepository {
    private static final String TABLE_PRIMARY = "google_fit_accounts";
    private static final String TABLE_FALLBACK = "google_fit_account";

    private final DatabaseService databaseService;

    public GoogleFitAccountRepository(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public Optional<GoogleFitAccount> findByUserId(int userId) {
        return findByUserIdOnTable(TABLE_PRIMARY, userId)
                .or(() -> findByUserIdOnTable(TABLE_FALLBACK, userId));
    }

    public boolean saveOrUpdateForUser(int userId, GoogleFitAccount account) {
        if (userId <= 0 || account == null) {
            return false;
        }

        return saveOrUpdateOnTable(TABLE_PRIMARY, userId, account)
                || saveOrUpdateOnTable(TABLE_FALLBACK, userId, account);
    }

    private Optional<GoogleFitAccount> findByUserIdOnTable(String tableName, int userId) {
        String sql = "SELECT id, google_account_id, access_token, refresh_token, token_expiration, last_sync_at, scopes "
                + "FROM " + tableName + " WHERE user_id = ? LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                GoogleFitAccount account = new GoogleFitAccount();
                account.setId(rs.getInt("id"));
                account.setGoogleAccountId(rs.getString("google_account_id"));
                account.setAccessToken(rs.getString("access_token"));
                account.setRefreshToken(rs.getString("refresh_token"));

                Timestamp tokenExp = rs.getTimestamp("token_expiration");
                account.setTokenExpiration(tokenExp == null ? null : tokenExp.toLocalDateTime());

                Timestamp lastSync = rs.getTimestamp("last_sync_at");
                account.setLastSyncAt(lastSync == null ? null : lastSync.toLocalDateTime());
                account.setScopes(rs.getString("scopes"));
                return Optional.of(account);
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean saveOrUpdateOnTable(String tableName, int userId, GoogleFitAccount account) {
        String sql = "INSERT INTO " + tableName + " "
                + "(user_id, google_account_id, access_token, refresh_token, token_expiration, last_sync_at, scopes) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "google_account_id = VALUES(google_account_id), "
                + "access_token = VALUES(access_token), "
                + "refresh_token = VALUES(refresh_token), "
                + "token_expiration = VALUES(token_expiration), "
                + "last_sync_at = VALUES(last_sync_at), "
                + "scopes = VALUES(scopes)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, account.getGoogleAccountId());
            statement.setString(3, account.getAccessToken());
            statement.setString(4, account.getRefreshToken());
            if (account.getTokenExpiration() == null) {
                statement.setNull(5, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(5, Timestamp.valueOf(account.getTokenExpiration()));
            }
            if (account.getLastSyncAt() == null) {
                statement.setNull(6, java.sql.Types.TIMESTAMP);
            } else {
                statement.setTimestamp(6, Timestamp.valueOf(account.getLastSyncAt()));
            }
            statement.setString(7, account.getScopes());
            return statement.executeUpdate() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }
}

