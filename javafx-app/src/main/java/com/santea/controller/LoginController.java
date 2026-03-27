package com.santea.controller;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;
import com.santea.repository.UserRepository;
import com.santea.service.DatabaseService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

public class LoginController {
    private final DatabaseService databaseService;
    private final UserRepository userRepository;
    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    public LoginController() {
        DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();
        this.databaseService = new DatabaseService(databaseConfig);
        this.userRepository = new UserRepository(databaseService);
    }

    public LoginResponse authenticate(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return new LoginResponse(false, "Merci de saisir votre email et votre mot de passe.");
        }

        if (!databaseService.canConnect()) {
            String details = databaseService.getLastConnectionError();
            return new LoginResponse(false,
                    "Connexion à la base échouée. Vérifiez DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD. Détail JDBC: " + details);
        }

        Optional<User> userOptional = userRepository.findByEmailOrUsername(email.trim());
        if (userOptional.isEmpty()) {
            return new LoginResponse(false, "Utilisateur introuvable dans la base pidev.");
        }

        User user = userOptional.get();
        String storedPassword = user.getPassword();

        if (storedPassword == null || storedPassword.isBlank()) {
            return new LoginResponse(false, "Mot de passe utilisateur absent en base.");
        }

        if (storedPassword.equals(password)) {
            return new LoginResponse(true, "Connexion réussie. Utilisateur chargé depuis MySQL.");
        }

        if (isHashedPassword(storedPassword)) {
            if (verifyHashedPassword(password, storedPassword)) {
                return new LoginResponse(true, "Connexion réussie. Mot de passe hashé vérifié.");
            }

            if (storedPassword.startsWith("$argon2")) {
                return new LoginResponse(false,
                        "Utilisateur trouvé, mais hash Argon2 non pris en charge actuellement côté JavaFX.");
            }

            return new LoginResponse(false, "Mot de passe incorrect.");
        }

        return new LoginResponse(false, "Mot de passe incorrect.");
    }

    private boolean isHashedPassword(String value) {
        return value.startsWith("$2a$")
                || value.startsWith("$2b$")
                || value.startsWith("$2y$")
                || value.startsWith("$argon2");
    }

    private boolean verifyHashedPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || rawPassword.isBlank() || storedHash.isBlank()) {
            return false;
        }

        if (storedHash.startsWith("$argon2")) {
            return false;
        }

        if (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$")) {
            String normalizedHash = storedHash.startsWith("$2y$")
                    ? "$2a$" + storedHash.substring(4)
                    : storedHash;

            try {
                return BCRYPT.matches(rawPassword, normalizedHash);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        return false;
    }

    public record LoginResponse(boolean success, String message) {
    }
}
