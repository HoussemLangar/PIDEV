package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;
import com.santea.repository.UserRepository;

import java.util.Optional;

public class ProfileService {
    private final DatabaseService databaseService;
    private final UserRepository userRepository;

    public ProfileService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
        this.userRepository = new UserRepository(databaseService);
    }

    public ProfileResult loadCurrentUserProfile(User sessionUser) {
        if (sessionUser == null || sessionUser.getId() == null) {
            return ProfileResult.failure("Session utilisateur invalide.");
        }

        if (!databaseService.canConnect()) {
            return ProfileResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        Optional<User> dbUser = userRepository.findById(sessionUser.getId());
        if (dbUser.isEmpty()) {
            return ProfileResult.failure("Utilisateur introuvable.");
        }

        return ProfileResult.success(dbUser.get(), "Profil charge.");
    }

    public ProfileResult updateProfile(User sessionUser, String nom, String prenom, String telephone, String adresse, String theme, String locale, Boolean reminderEnabled) {
        if (sessionUser == null || sessionUser.getId() == null) {
            return ProfileResult.failure("Session utilisateur invalide.");
        }

        if (!databaseService.canConnect()) {
            return ProfileResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        boolean updated = userRepository.updateProfilePreferences(
                sessionUser.getId(),
                nom,
                prenom,
                telephone,
                adresse,
                theme,
                locale,
                reminderEnabled
        );

        if (!updated) {
            return ProfileResult.failure("Impossible de mettre a jour le profil.");
        }

        Optional<User> refreshed = userRepository.findById(sessionUser.getId());
        if (refreshed.isPresent()) {
            User u = refreshed.get();
            sessionUser.setNom(u.getNom());
            sessionUser.setPrenom(u.getPrenom());
            sessionUser.setTelephone(u.getTelephone());
            sessionUser.setAdresse(u.getAdresse());
            sessionUser.setThemePreference(u.getThemePreference());
            sessionUser.setLocale(u.getLocale());
            sessionUser.setReminderEnabled(u.getReminderEnabled());
            sessionUser.setDateNaissance(u.getDateNaissance());
            sessionUser.setMfaEnabled(u.getMfaEnabled());
            sessionUser.setGoogleAuthenticatorSecret(u.getGoogleAuthenticatorSecret());
        }

        return ProfileResult.success(sessionUser, "Profil mis a jour.");
    }

    public ProfileResult deleteAccount(User sessionUser) {
        if (sessionUser == null || sessionUser.getId() == null) {
            return ProfileResult.failure("Session utilisateur invalide.");
        }

        if (!databaseService.canConnect()) {
            return ProfileResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        boolean deleted = userRepository.softDeleteUser(sessionUser.getId());
        if (!deleted) {
            return ProfileResult.failure("Impossible de supprimer le compte.");
        }

        return ProfileResult.success(null, "Votre compte a ete supprime.");
    }

    public record ProfileResult(boolean success, String message, User user) {
        public static ProfileResult success(User user, String message) {
            return new ProfileResult(true, message, user);
        }

        public static ProfileResult failure(String message) {
            return new ProfileResult(false, message, null);
        }
    }
}
