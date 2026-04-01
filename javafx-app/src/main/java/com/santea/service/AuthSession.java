package com.santea.service;

import com.santea.model.User;

public final class AuthSession {
    private static User currentUser;

    private AuthSession() {
    }

    public static void login(User user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static boolean isAuthenticated() {
        return currentUser != null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }
}
