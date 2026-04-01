package com.santea.service;

import com.santea.model.User;

public final class AuthSession {
    private static User currentUser;
    private static boolean faceVerified;

    private AuthSession() {
    }

    public static void login(User user) {
        currentUser = user;
        faceVerified = false;
    }

    public static void logout() {
        currentUser = null;
        faceVerified = false;
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
}
