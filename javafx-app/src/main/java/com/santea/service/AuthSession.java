package com.santea.service;

import com.santea.model.User;

public final class AuthSession {
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
}
