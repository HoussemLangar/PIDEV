package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;
import com.santea.repository.GoogleFitAccountRepository;
import com.santea.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.regex.Pattern;

public class AuthService {
    private static final int DEFAULT_REMEMBER_DAYS = 7;
    private static final int EMAIL_HISTORY_LIMIT = 40;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern STRONG_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    private final DatabaseService databaseService;
    private final UserRepository userRepository;
    private final GoogleFitAccountRepository googleFitAccountRepository;
    private final EmailService emailService;
    private final TwoFactorService twoFactorService;

    public AuthService() {
        DatabaseConfig databaseConfig = DatabaseConfig.fromEnvironment();
        this.databaseService = new DatabaseService(databaseConfig);
        this.userRepository = new UserRepository(databaseService);
        this.googleFitAccountRepository = new GoogleFitAccountRepository(databaseService);
        this.emailService = new EmailService();
        this.twoFactorService = new TwoFactorService();
    }

    public LoginResult login(String identifier, String password) {
        return login(identifier, password, null);
    }

    public LoginResult login(String identifier, String password, String mfaCode) {
        return login(identifier, password, mfaCode, false);
    }

    public LoginResult login(String identifier, String password, String mfaCode, boolean rememberMe) {
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
            recordSuspiciousLogin(user, "Mot de passe invalide", true);
            return LoginResult.failure("Mot de passe incorrect.", LoginFailureReason.INVALID_PASSWORD, user);
        }

        if (isBannedEffective(user)) {
            String reason = isBlank(user.getBanReason()) ? "" : " Motif: " + user.getBanReason().trim();
            return LoginResult.failure("Votre compte est banni." + reason, LoginFailureReason.BANNED, user);
        }

        if (isProfessionalRole(user.getRole()) && !Boolean.TRUE.equals(user.getAdminApproved())) {
            return LoginResult.failure("Compte professionnel en attente de validation administrateur.", LoginFailureReason.ADMIN_APPROVAL_REQUIRED, user);
        }

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            if (isBlank(mfaCode)) {
                return LoginResult.failure("Code MFA requis (Google Authenticator).", LoginFailureReason.MFA_REQUIRED, user);
            }

            if (!twoFactorService.verifyCode(user.getGoogleAuthenticatorSecret(), mfaCode)) {
                recordSuspiciousLogin(user, "Echec code MFA", true);
                return LoginResult.failure("Code MFA invalide.", LoginFailureReason.MFA_INVALID, user);
            }
        }

        hydrateGoogleFitAccount(user);
        AuthSession.login(user);
        registerUserSession(user);
        rememberSuccessfulLoginEmail(user.getEmail());
        if (rememberMe && user.getId() != null) {
            AuthSession.persistLogin(user.getId(), LocalDateTime.now().plusDays(resolveRememberDays()));
        } else {
            AuthSession.clearPersistedLogin();
        }
        return LoginResult.success(user, "Connexion réussie.");
    }

    public LoginResult loginWithOAuth(String provider, String email, String nom, String prenom) {
        if (isBlank(provider) || isBlank(email)) {
            return LoginResult.failure("OAuth invalide: provider/email manquant.", LoginFailureReason.VALIDATION, null);
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalizedEmail).matches()) {
            return LoginResult.failure("Email OAuth invalide.", LoginFailureReason.VALIDATION, null);
        }

        if (!databaseService.canConnect()) {
            return LoginResult.failure("Connexion à la base échouée. " + databaseService.getLastConnectionError(), LoginFailureReason.SYSTEM, null);
        }

        User user;
        Optional<User> found = userRepository.findByEmail(normalizedEmail);
        if (found.isPresent()) {
            user = found.get();
        } else {
            user = new User();
            user.setUsername((provider + "_" + normalizedEmail.substring(0, normalizedEmail.indexOf('@'))).replaceAll("[^A-Za-z0-9_.-]", "_"));
            user.setEmail(normalizedEmail);
            user.setNom(isBlank(nom) ? "OAuth" : nom.trim());
            user.setPrenom(isBlank(prenom) ? provider.toUpperCase() : prenom.trim());
            user.setDateNaissance(LocalDate.of(1990, 1, 1).atStartOfDay());
            user.setRole("ROLE_PATIENT");
            user.setEmailVerified(true);
            user.setAdminApproved(true);
            user.setSubscriptionStatus("PENDING");
            user.setPassword(BCRYPT.encode(UUID.randomUUID().toString()));

            if (!userRepository.createUser(user)) {
                return LoginResult.failure("Impossible de creer le compte OAuth.", LoginFailureReason.SYSTEM, null);
            }

            Optional<User> created = userRepository.findByEmail(normalizedEmail);
            if (created.isEmpty()) {
                return LoginResult.failure("Compte OAuth cree mais lecture impossible.", LoginFailureReason.SYSTEM, null);
            }
            user = created.get();
        }

        if (isBannedEffective(user)) {
            return LoginResult.failure("Votre compte est banni.", LoginFailureReason.BANNED, user);
        }

        hydrateGoogleFitAccount(user);
        AuthSession.login(user);
        registerUserSession(user);
        rememberSuccessfulLoginEmail(user.getEmail());
        AuthSession.clearPersistedLogin();
        return LoginResult.success(user, "Connexion OAuth réussie via " + provider + ".");
    }

    public boolean tryRestoreRememberedSession() {
        if (!databaseService.canConnect()) {
            return false;
        }

        Optional<AuthSession.PersistedLogin> persisted = AuthSession.loadPersistedLogin();
        if (persisted.isEmpty()) {
            return false;
        }

        AuthSession.PersistedLogin data = persisted.get();
        if (data.expiresAt() == null || LocalDateTime.now().isAfter(data.expiresAt())) {
            AuthSession.clearPersistedLogin();
            return false;
        }

        Optional<User> userOptional = userRepository.findById(data.userId());
        if (userOptional.isEmpty()) {
            AuthSession.clearPersistedLogin();
            return false;
        }

        User user = userOptional.get();
        if (isBannedEffective(user)) {
            AuthSession.clearPersistedLogin();
            return false;
        }

        hydrateGoogleFitAccount(user);
        AuthSession.login(user);
        rememberSuccessfulLoginEmail(user.getEmail());
        return true;
    }

    private void hydrateGoogleFitAccount(User user) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            return;
        }
        googleFitAccountRepository.findByUserId(user.getId()).ifPresent(account -> {
            account.setUser(user);
            user.setGoogleFitAccount(account);
        });
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
        closeCurrentUserSession();
        AuthSession.logout();
    }

    public ProfileMfaSetupResult generateMfaSetup(User user) {
        if (user == null || user.getId() == null) {
            return ProfileMfaSetupResult.failure("Session invalide.");
        }

        String secret = twoFactorService.generateSecret();
        String uri = twoFactorService.provisioningUri("SANTEA", user.getEmail(), secret);
        return ProfileMfaSetupResult.success(secret, uri, twoFactorService.currentCodeForDebug(secret));
    }

    public ActionResult enableMfa(User user, String secret, String code) {
        if (user == null || user.getId() == null) {
            return ActionResult.failure("Session invalide.");
        }
        if (!twoFactorService.verifyCode(secret, code)) {
            return ActionResult.failure("Code Google Authenticator invalide.");
        }

        boolean ok = userRepository.updateMfaConfiguration(user.getId(), true, secret);
        if (!ok) {
            return ActionResult.failure("Impossible d'activer la MFA.");
        }

        user.setMfaEnabled(true);
        user.setGoogleAuthenticatorSecret(secret);
        return ActionResult.success("MFA activee.");
    }

    public ActionResult disableMfa(User user) {
        if (user == null || user.getId() == null) {
            return ActionResult.failure("Session invalide.");
        }

        boolean ok = userRepository.updateMfaConfiguration(user.getId(), false, null);
        if (!ok) {
            return ActionResult.failure("Impossible de desactiver la MFA.");
        }

        user.setMfaEnabled(false);
        user.setGoogleAuthenticatorSecret(null);
        return ActionResult.success("MFA desactivee.");
    }

    public ActionResult verifyCurrentPassword(User user, String password) {
        if (user == null || user.getId() == null) {
            return ActionResult.failure("Session invalide.");
        }
        if (isBlank(password)) {
            return ActionResult.failure("Mot de passe requis.");
        }

        Optional<User> latestUser = userRepository.findById(user.getId());
        if (latestUser.isEmpty()) {
            return ActionResult.failure("Utilisateur introuvable.");
        }

        if (!isPasswordValid(password, latestUser.get().getPassword())) {
            return ActionResult.failure("Mot de passe incorrect.");
        }

        return ActionResult.success("Mot de passe confirme.");
    }

    public ActionResult changePassword(User user, String currentPassword, String newPassword, String confirmPassword) {
        if (user == null || user.getId() == null) {
            return ActionResult.failure("Session invalide.");
        }

        ActionResult passwordCheck = verifyCurrentPassword(user, currentPassword);
        if (!passwordCheck.success()) {
            return passwordCheck;
        }

        if (isBlank(newPassword) || isBlank(confirmPassword)) {
            return ActionResult.failure("Nouveau mot de passe requis.");
        }
        if (!newPassword.equals(confirmPassword)) {
            return ActionResult.failure("Les mots de passe ne correspondent pas.");
        }
        if (!STRONG_PASSWORD_PATTERN.matcher(newPassword).matches()) {
            return ActionResult.failure("Le mot de passe doit contenir au moins 8 caracteres, une majuscule, une minuscule et un chiffre.");
        }

        boolean updated = userRepository.updatePassword(user.getId(), BCRYPT.encode(newPassword));
        if (!updated) {
            return ActionResult.failure("Impossible de mettre a jour le mot de passe.");
        }

        return ActionResult.success("Mot de passe mis a jour.");
    }

    public TwoFactorService getTwoFactorService() {
        return twoFactorService;
    }

    public List<String> suggestLoginEmails(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        LinkedHashSet<String> emails = new LinkedHashSet<>();

        for (String remembered : loadRememberedLoginEmails()) {
            if (!isBlank(remembered)) {
                emails.add(remembered.trim().toLowerCase());
                if (emails.size() >= safeLimit) {
                    return new ArrayList<>(emails);
                }
            }
        }

        if (!databaseService.canConnect()) {
            return new ArrayList<>(emails);
        }

        ensureSecurityTables();

        List<String> queryAttempts = List.of(
                "SELECT u.email "
                        + "FROM user_sessions s "
                        + "INNER JOIN users u ON s.user_id = u.id "
                        + "WHERE u.email IS NOT NULL AND TRIM(u.email) <> '' "
                        + "GROUP BY u.id, u.email "
                        + "ORDER BY MAX(s.last_seen_at) DESC "
                        + "LIMIT ?",
                "SELECT u.email "
                        + "FROM user_sessions s "
                        + "INNER JOIN users u ON s.user_id = u.id "
                        + "WHERE u.email IS NOT NULL AND TRIM(u.email) <> '' "
                        + "GROUP BY u.id, u.email "
                        + "ORDER BY MAX(s.created_at) DESC "
                        + "LIMIT ?",
                "SELECT u.email "
                        + "FROM user_sessions s "
                        + "INNER JOIN users u ON s.user_id = u.id "
                        + "WHERE u.email IS NOT NULL AND TRIM(u.email) <> '' "
                        + "GROUP BY u.id, u.email "
                        + "ORDER BY MAX(s.id) DESC "
                        + "LIMIT ?"
        );

        for (String sql : queryAttempts) {
            if (fillSuggestedEmailsFromSessions(sql, safeLimit, emails)) {
                break;
            }
        }

        return new ArrayList<>(emails);
    }

    private Path loginEmailHistoryPath() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".santea", "login-email-history.txt");
    }

    private List<String> loadRememberedLoginEmails() {
        Path path = loginEmailHistoryPath();
        if (!Files.exists(path)) {
            return List.of();
        }

        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            LinkedHashSet<String> unique = new LinkedHashSet<>();
            for (String line : lines) {
                String email = defaultString(line).trim().toLowerCase();
                if (!isBlank(email)) {
                    unique.add(email);
                }
            }
            return new ArrayList<>(unique);
        } catch (IOException exception) {
            return List.of();
        }
    }

    private void rememberSuccessfulLoginEmail(String email) {
        String normalized = defaultString(email).trim().toLowerCase();
        if (isBlank(normalized) || !EMAIL_PATTERN.matcher(normalized).matches()) {
            return;
        }

        LinkedHashSet<String> history = new LinkedHashSet<>();
        history.add(normalized);
        history.addAll(loadRememberedLoginEmails());

        List<String> limited = new ArrayList<>();
        for (String value : history) {
            if (!isBlank(value)) {
                limited.add(value);
                if (limited.size() >= EMAIL_HISTORY_LIMIT) {
                    break;
                }
            }
        }

        Path path = loginEmailHistoryPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String content = String.join("\n", limited);
            if (!content.isBlank()) {
                content = content + "\n";
            }

            Files.writeString(
                    path,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
        } catch (IOException ignored) {
        }
    }

    private boolean fillSuggestedEmailsFromSessions(String sql, int limit, LinkedHashSet<String> emails) {
        try (java.sql.Connection connection = databaseService.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String email = defaultString(rs.getString("email")).trim();
                    if (!email.isBlank()) {
                        emails.add(email);
                    }
                }
            }
            return !emails.isEmpty();
        } catch (java.sql.SQLException ignored) {
            return false;
        }
    }

    private void registerUserSession(User user) {
        ensureSecurityTables();
        if (user == null || user.getId() == null) {
            return;
        }

        String sessionToken = UUID.randomUUID().toString();
        String device = System.getProperty("os.name", "desktop") + "-" + System.getProperty("user.name", "user");

        String sql = "INSERT INTO user_sessions (user_id, session_token, device_label, ip_address, is_active, created_at, last_seen_at) "
                + "VALUES (?, ?, ?, ?, 1, ?, ?)";

        try (java.sql.Connection connection = databaseService.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            java.sql.Timestamp now = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now());
            statement.setInt(1, user.getId());
            statement.setString(2, sessionToken);
            statement.setString(3, device);
            statement.setString(4, "127.0.0.1");
            statement.setTimestamp(5, now);
            statement.setTimestamp(6, now);
            statement.executeUpdate();

            try (java.sql.ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    AuthSession.setUserSessionId(keys.getInt(1));
                }
            }
            AuthSession.setSessionToken(sessionToken);
        } catch (java.sql.SQLException ignored) {
        }

        maybeRecordSuspiciousByDevice(user, device);
    }

    private void closeCurrentUserSession() {
        ensureSecurityTables();
        Integer userSessionId = AuthSession.getUserSessionId();
        if (userSessionId == null) {
            return;
        }

        String sql = "UPDATE user_sessions SET is_active = 0, revoked_at = ?, last_seen_at = ? WHERE id = ?";
        try (java.sql.Connection connection = databaseService.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            java.sql.Timestamp now = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now());
            statement.setTimestamp(1, now);
            statement.setTimestamp(2, now);
            statement.setInt(3, userSessionId);
            statement.executeUpdate();
        } catch (java.sql.SQLException ignored) {
        }
    }

    private void maybeRecordSuspiciousByDevice(User user, String device) {
        String sql = "SELECT device_label FROM user_sessions WHERE user_id = ? ORDER BY created_at DESC LIMIT 5";
        try (java.sql.Connection connection = databaseService.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            try (java.sql.ResultSet rs = statement.executeQuery()) {
                int seen = 0;
                boolean knownDevice = false;
                while (rs.next()) {
                    seen++;
                    if (device.equalsIgnoreCase(defaultString(rs.getString("device_label")))) {
                        knownDevice = true;
                        break;
                    }
                }
                if (seen > 1 && !knownDevice) {
                    recordSuspiciousLogin(user, "Nouveau device detecte", false);
                }
            }
        } catch (java.sql.SQLException ignored) {
        }
    }

    private void recordSuspiciousLogin(User user, String reason, boolean blocked) {
        if (user == null || user.getId() == null) {
            return;
        }
        ensureSecurityTables();

        String sql = "INSERT INTO suspicious_logins (user_id, email, ip_address, reason, blocked, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (java.sql.Connection connection = databaseService.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, user.getId());
            statement.setString(2, user.getEmail());
            statement.setString(3, "127.0.0.1");
            statement.setString(4, reason);
            statement.setBoolean(5, blocked);
            statement.setTimestamp(6, java.sql.Timestamp.valueOf(java.time.LocalDateTime.now()));
            statement.executeUpdate();
        } catch (java.sql.SQLException ignored) {
        }
    }

    private void ensureSecurityTables() {
        Map<String, String> tables = Map.of(
                "user_sessions",
                "CREATE TABLE IF NOT EXISTS user_sessions ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "user_id INT NOT NULL,"
                        + "session_token VARCHAR(128) NOT NULL,"
                        + "device_label VARCHAR(160) NULL,"
                        + "ip_address VARCHAR(64) NULL,"
                        + "is_active BOOLEAN NOT NULL DEFAULT 1,"
                        + "created_at DATETIME NOT NULL,"
                        + "last_seen_at DATETIME NOT NULL,"
                        + "revoked_at DATETIME NULL"
                        + ")",
                "suspicious_logins",
                "CREATE TABLE IF NOT EXISTS suspicious_logins ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "user_id INT NOT NULL,"
                        + "email VARCHAR(190) NULL,"
                        + "ip_address VARCHAR(64) NULL,"
                        + "reason VARCHAR(255) NULL,"
                        + "blocked BOOLEAN NOT NULL DEFAULT 0,"
                        + "created_at DATETIME NOT NULL"
                        + ")"
        );

        for (String createSql : tables.values()) {
            try (java.sql.Connection connection = databaseService.getConnection();
                 java.sql.PreparedStatement statement = connection.prepareStatement(createSql)) {
                statement.execute();
            } catch (java.sql.SQLException ignored) {
            }
        }
    }

    private boolean isProfessionalRole(String role) {
        String normalized = defaultString(role).toUpperCase();
        return normalized.equals("ROLE_MEDECIN")
                || normalized.equals("ROLE_PHARMACIEN")
                || normalized.equals("ROLE_COACH")
                || normalized.equals("ROLE_NUTRITIONNISTE");
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

    private int resolveRememberDays() {
        String raw = System.getenv("SANTEA_REMEMBER_DAYS");
        if (isBlank(raw)) {
            return DEFAULT_REMEMBER_DAYS;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : DEFAULT_REMEMBER_DAYS;
        } catch (NumberFormatException exception) {
            return DEFAULT_REMEMBER_DAYS;
        }
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
        NOT_VERIFIED,
        MFA_REQUIRED,
        MFA_INVALID,
        ADMIN_APPROVAL_REQUIRED
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    public record ProfileMfaSetupResult(boolean success, String secret, String provisioningUri, String debugCode, String message) {
        public static ProfileMfaSetupResult success(String secret, String provisioningUri, String debugCode) {
            return new ProfileMfaSetupResult(true, secret, provisioningUri, debugCode, "Configuration MFA generee.");
        }

        public static ProfileMfaSetupResult failure(String message) {
            return new ProfileMfaSetupResult(false, "", "", "", message);
        }
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
