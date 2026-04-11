package com.santea.service;

import com.santea.model.User;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;

public final class AuthSession {
    private static final Path SESSION_DIR = Path.of(System.getProperty("user.home"), ".santea");
    private static final Path SESSION_FILE = SESSION_DIR.resolve("session.properties");
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_EXPIRES_AT = "expiresAt";

    private static User currentUser;
    private static boolean faceVerified;
    private static String sessionToken;
    private static Integer userSessionId;

    private AuthSession() {
    }

    public static void login(User user) {
        currentUser = user;
        faceVerified = false;
        sessionToken = null;
        userSessionId = null;
    }

    public static void logout() {
        currentUser = null;
        faceVerified = false;
        sessionToken = null;
        userSessionId = null;
        clearPersistedLogin();
    }

    public static boolean isAuthenticated() {
        return currentUser != null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isFaceVerified() {
        return faceVerified;
    }

    public static void setFaceVerified(boolean verified) {
        faceVerified = verified;
    }

    public static String getSessionToken() {
        return sessionToken;
    }

    public static void setSessionToken(String token) {
        sessionToken = token;
    }

    public static Integer getUserSessionId() {
        return userSessionId;
    }

    public static void setUserSessionId(Integer sessionId) {
        userSessionId = sessionId;
    }

    public static void persistLogin(int userId, LocalDateTime expiresAt) {
        if (userId <= 0 || expiresAt == null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty(KEY_USER_ID, String.valueOf(userId));
        properties.setProperty(KEY_EXPIRES_AT, expiresAt.toString());

        try {
            Files.createDirectories(SESSION_DIR);
            try (OutputStream outputStream = Files.newOutputStream(SESSION_FILE)) {
                properties.store(outputStream, "SANTEA persisted session");
            }
        } catch (IOException ignored) {
        }
    }

    public static Optional<PersistedLogin> loadPersistedLogin() {
        if (!Files.exists(SESSION_FILE)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(SESSION_FILE)) {
            properties.load(inputStream);
            int userId = Integer.parseInt(properties.getProperty(KEY_USER_ID, "0"));
            LocalDateTime expiresAt = LocalDateTime.parse(properties.getProperty(KEY_EXPIRES_AT, ""));
            if (userId <= 0) {
                return Optional.empty();
            }
            return Optional.of(new PersistedLogin(userId, expiresAt));
        } catch (IOException | NumberFormatException | DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    public static void clearPersistedLogin() {
        try {
            Files.deleteIfExists(SESSION_FILE);
        } catch (IOException ignored) {
        }
    }

    public record PersistedLogin(int userId, LocalDateTime expiresAt) {
    }
}
