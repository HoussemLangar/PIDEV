package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;
import com.santea.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class AuthService {
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern STRONG_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final DatabaseService databaseService;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public AuthService() {
        DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();
        this.databaseService = new DatabaseService(databaseConfig);
        this.userRepository = new UserRepository(databaseService);
        this.emailService = new EmailService();
    }

    public LoginResult login(String identifier, String password) {
        if (isBlank(identifier) || isBlank(password)) {
            return LoginResult.failure("Merci de saisir votre email et votre mot de passe.", LoginFailureReason.VALIDATION, null);
        }

        if (!databaseService.canConnect()) {
            return LoginResult.failure("Connexion à la base échouée. " + databaseService.getLastConnectionError(), LoginFailureReason.SYSTEM, null);
        }

        Optional<User> userOptional = userRepository.findByEmailOrUsername(identifier.trim());
        if (userOptional.isEmpty()) {
            return LoginResult.failure("Utilisateur introuvable.", LoginFailureReason.NOT_FOUND, null);
        }

        User user = userOptional.get();
        if (!isPasswordValid(password, user.getPassword())) {
            return LoginResult.failure("Mot de passe incorrect.", LoginFailureReason.INVALID_PASSWORD, null);
        }

        if (isBannedEffective(user)) {
            String reason = isBlank(user.getBanReason()) ? "" : " Motif: " + user.getBanReason().trim();
            return LoginResult.failure("Votre compte est banni." + reason, LoginFailureReason.BANNED, user);
        }

        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            return LoginResult.failure("Email non vérifié. Vérifiez votre boîte mail avant de vous connecter.", LoginFailureReason.NOT_VERIFIED, null);
        }

        AuthSession.login(user);
        return LoginResult.success(user, "Connexion réussie.");
    }

    public RegisterResult register(RegistrationRequest request) {
        if (request == null) {
            return RegisterResult.failure("Requête d'inscription invalide.");
        }

        if (!databaseService.canConnect()) {
            return RegisterResult.failure("Connexion à la base échouée. " + databaseService.getLastConnectionError());
        }

        if (isBlank(request.username()) || isBlank(request.email()) || isBlank(request.nom()) || isBlank(request.prenom())) {
            return RegisterResult.failure("Veuillez compléter les champs obligatoires (utilisateur, email, nom, prénom).");
        }

        if (!EMAIL_PATTERN.matcher(request.email().trim()).matches()) {
            return RegisterResult.failure("Adresse email invalide.");
        }

        if (request.dateNaissance() == null) {
            return RegisterResult.failure("Veuillez saisir votre date de naissance.");
        }

        int age = Period.between(request.dateNaissance(), LocalDate.now()).getYears();
        if (age < 13) {
            return RegisterResult.failure("Vous devez avoir au moins 13 ans pour vous inscrire.");
        }

        if (isBlank(request.password()) || !request.password().equals(request.confirmPassword())) {
            return RegisterResult.failure("Les mots de passe ne correspondent pas.");
        }

        if (!STRONG_PASSWORD_PATTERN.matcher(request.password()).matches()) {
            return RegisterResult.failure("Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule et un chiffre.");
        }

        if (!request.termsAccepted()) {
            return RegisterResult.failure("Vous devez accepter les conditions d'utilisation.");
        }

        String normalizedEmail = request.email().trim().toLowerCase();
        String normalizedUsername = request.username().trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            return RegisterResult.failure("Un compte existe déjà avec cet email.");
        }

        if (userRepository.existsByUsername(normalizedUsername)) {
            return RegisterResult.failure("Ce nom d'utilisateur est déjà utilisé.");
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setNom(request.nom().trim());
        user.setPrenom(request.prenom().trim());
        user.setDateNaissance(request.dateNaissance().atStartOfDay());
        user.setAdresse(trimToNull(request.adresse()));
        user.setTelephone(trimToNull(request.telephone()));
        user.setRole("ROLE_PATIENT");
        user.setEmailVerified(false);
        user.setAdminApproved(false);
        user.setSubscriptionStatus("PENDING");
        user.setPassword(BCRYPT.encode(request.password()));

        boolean created = userRepository.createUser(user);
        if (!created) {
            return RegisterResult.failure("Impossible de créer le compte pour le moment.");
        }

        Optional<User> createdUser = userRepository.findByEmail(normalizedEmail);
        if (createdUser.isPresent() && createdUser.get().getId() != null) {
            String verificationToken = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
            if (verificationToken.length() > 64) {
                verificationToken = verificationToken.substring(0, 64);
            }

            userRepository.updateEmailVerificationToken(
                    createdUser.get().getId(),
                    verificationToken,
                    LocalDateTime.now().plusDays(2)
            );

            String fullName = (request.nom().trim() + " " + request.prenom().trim()).trim();
            boolean mailSent = emailService.sendEmailVerification(normalizedEmail, fullName, verificationToken);
            if (mailSent) {
                return RegisterResult.success("Compte créé avec succès. Email de vérification envoyé.");
            }

            String reason = defaultString(emailService.getLastError());
            return RegisterResult.failure("Compte créé, mais email non envoyé. Cause: " + reason);
        }

        return RegisterResult.failure("Compte créé, mais email non envoyé. Vérifiez la configuration SMTP (MAILER_DSN/MAIL_*). ");
    }

    public ForgotPasswordResult requestPasswordReset(String email) {
        if (isBlank(email)) {
            return ForgotPasswordResult.failure("Veuillez saisir votre adresse email.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return ForgotPasswordResult.failure("Adresse email invalide.");
        }

        if (!databaseService.canConnect()) {
            return ForgotPasswordResult.failure("Connexion à la base échouée. " + databaseService.getLastConnectionError());
        }

        Optional<User> userOptional = userRepository.findByEmail(normalizedEmail);
        if (userOptional.isPresent() && userOptional.get().getId() != null) {
            int userId = userOptional.get().getId();
            userRepository.deleteExpiredOrUsedResetTokens(userId);

            Optional<UserRepository.PasswordResetTokenRecord> token =
                    userRepository.createPasswordResetToken(userId, LocalDateTime.now().plusHours(1));

            if (token.isPresent()) {
                User targetUser = userOptional.get();
                String fullName = (defaultString(targetUser.getNom()) + " " + defaultString(targetUser.getPrenom())).trim();
                boolean mailSent = emailService.sendPasswordReset(normalizedEmail, fullName.isBlank() ? "Utilisateur" : fullName, token.get().token());

                if (mailSent) {
                    return ForgotPasswordResult.success(
                        "Si un compte existe pour cet email, un lien de réinitialisation a été envoyé.",
                        null,
                        true
                    );
                }

                String reason = defaultString(emailService.getLastError());

                return ForgotPasswordResult.success(
                    "Email non envoyé. Cause: " + reason + " Utilisez le token affiché pour réinitialiser.",
                        token.get().token(),
                        false
                );
            }
        }

        return ForgotPasswordResult.success(
                "Si un compte existe pour cet email, un lien de réinitialisation a été envoyé.",
                null,
                false
        );
    }

    public ResetPasswordResult resetPassword(String token, String newPassword, String confirmPassword) {
        if (isBlank(token)) {
            return ResetPasswordResult.failure("Token de réinitialisation manquant.");
        }

        if (isBlank(newPassword) || isBlank(confirmPassword)) {
            return ResetPasswordResult.failure("Veuillez saisir et confirmer le nouveau mot de passe.");
        }

        if (!newPassword.equals(confirmPassword)) {
            return ResetPasswordResult.failure("Les mots de passe ne correspondent pas.");
        }

        if (!STRONG_PASSWORD_PATTERN.matcher(newPassword).matches()) {
            return ResetPasswordResult.failure("Le mot de passe doit contenir au moins 8 caractères, une majuscule, une minuscule et un chiffre.");
        }

        if (!databaseService.canConnect()) {
            return ResetPasswordResult.failure("Connexion à la base échouée. " + databaseService.getLastConnectionError());
        }

        Optional<UserRepository.PasswordResetTokenRecord> resetToken = userRepository.findValidPasswordResetToken(token.trim());
        if (resetToken.isEmpty()) {
            return ResetPasswordResult.failure("Ce lien de réinitialisation est invalide ou expiré.");
        }

        String newHash = BCRYPT.encode(newPassword);
        boolean passwordUpdated = userRepository.updatePassword(resetToken.get().userId(), newHash);
        if (!passwordUpdated) {
            return ResetPasswordResult.failure("Impossible de mettre à jour le mot de passe.");
        }

        userRepository.markPasswordResetTokenAsUsed(resetToken.get().id());
        return ResetPasswordResult.success("Votre mot de passe a été réinitialisé avec succès.");
    }

    public void logout() {
        AuthSession.logout();
    }

    private boolean isPasswordValid(String rawPassword, String storedPassword) {
        if (isBlank(rawPassword) || isBlank(storedPassword)) {
            return false;
        }

        if (storedPassword.equals(rawPassword)) {
            return true;
        }

        if (storedPassword.startsWith("$2a$") || storedPassword.startsWith("$2b$") || storedPassword.startsWith("$2y$")) {
            String normalizedHash = storedPassword.startsWith("$2y$")
                    ? "$2a$" + storedPassword.substring(4)
                    : storedPassword;
            try {
                return BCRYPT.matches(rawPassword, normalizedHash);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        return false;
    }

    private boolean isBannedEffective(User user) {
        if (!Boolean.TRUE.equals(user.getIsBanned())) {
            return false;
        }

        if (user.getBanUntil() == null) {
            return true;
        }

        return user.getBanUntil().isAfter(LocalDateTime.now());
    }

    private boolean isAdmin(User user) {
        return "ROLE_ADMIN".equalsIgnoreCase(defaultString(user.getRole()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    public record LoginResult(boolean success, String message, User user, LoginFailureReason failureReason) {
        public static LoginResult success(User user, String message) {
            return new LoginResult(true, message, user, LoginFailureReason.NONE);
        }

        public static LoginResult failure(String message, LoginFailureReason failureReason, User user) {
            return new LoginResult(false, message, user, failureReason == null ? LoginFailureReason.NONE : failureReason);
        }
    }

    public enum LoginFailureReason {
        NONE,
        VALIDATION,
        SYSTEM,
        NOT_FOUND,
        INVALID_PASSWORD,
        BANNED,
        NOT_VERIFIED
    }

    public record RegisterResult(boolean success, String message) {
        public static RegisterResult success(String message) {
            return new RegisterResult(true, message);
        }

        public static RegisterResult failure(String message) {
            return new RegisterResult(false, message);
        }
    }

    public record ForgotPasswordResult(boolean success, String message, String resetToken, boolean emailSent) {
        public static ForgotPasswordResult success(String message, String resetToken, boolean emailSent) {
            return new ForgotPasswordResult(true, message, resetToken, emailSent);
        }

        public static ForgotPasswordResult failure(String message) {
            return new ForgotPasswordResult(false, message, null, false);
        }
    }

    public record ResetPasswordResult(boolean success, String message) {
        public static ResetPasswordResult success(String message) {
            return new ResetPasswordResult(true, message);
        }

        public static ResetPasswordResult failure(String message) {
            return new ResetPasswordResult(false, message);
        }
    }

    public record RegistrationRequest(
            String username,
            String email,
            String nom,
            String prenom,
            LocalDate dateNaissance,
            String adresse,
            String telephone,
            String password,
            String confirmPassword,
            boolean termsAccepted
    ) {
    }
}
