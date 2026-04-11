package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.GoogleOAuthService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.awt.Desktop;
import java.net.URI;
import java.util.Optional;

public class LoginViewController {
    private final AuthService authService = new AuthService();
    private final GoogleOAuthService googleOAuthService = new GoogleOAuthService();

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField visiblePasswordField;

    @FXML
    private Button togglePasswordButton;

    @FXML
    private Label feedbackLabel;

    @FXML
    private CheckBox rememberMeCheckBox;

    @FXML
    private void handleLogin() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.isVisible() ? passwordField.getText() : visiblePasswordField.getText();
        boolean rememberMe = rememberMeCheckBox != null && rememberMeCheckBox.isSelected();

        AuthService.LoginResult response = authService.login(email, password, null, rememberMe);

        if (!response.success() && response.failureReason() == AuthService.LoginFailureReason.MFA_REQUIRED) {
            String code = askMfaCode();
            if (code == null) {
                showFeedback("Connexion annulee: code MFA requis.", false);
                return;
            }
            response = authService.login(email, password, code, rememberMe);
        }

        showFeedback(response.message(), response.success());

        if (response.success()) {
            if (response.user() != null && "ROLE_ADMIN".equalsIgnoreCase(response.user().getRole())) {
                AppNavigator.showAdminFaceVerification();
            } else {
                AppNavigator.showHome();
            }
            return;
        }

        if (response.failureReason() == AuthService.LoginFailureReason.BANNED) {
            AppNavigator.showBanned(response.user());
        }
    }

    @FXML
    private void handleGoogleLogin() {
        showFeedback("Ouverture de Google Auth...", true);

        Task<GoogleOAuthService.AuthResult> task = new Task<>() {
            @Override
            protected GoogleOAuthService.AuthResult call() {
                return googleOAuthService.authenticate();
            }
        };

        task.setOnSucceeded(event -> {
            GoogleOAuthService.AuthResult authResult = task.getValue();
            if (authResult == null || !authResult.success() || authResult.profile() == null) {
                showFeedback(authResult == null ? "Echec Google OAuth." : authResult.message(), false);
                return;
            }

            GoogleOAuthService.GoogleProfile profile = authResult.profile();
            AuthService.LoginResult response = authService.loginWithOAuth(
                    "Google",
                    profile.email(),
                    profile.nom(),
                    profile.prenom()
            );

            showFeedback(response.message(), response.success());
            if (response.success()) {
                if (response.user() != null && "ROLE_ADMIN".equalsIgnoreCase(response.user().getRole())) {
                    AppNavigator.showAdminFaceVerification();
                } else {
                    AppNavigator.showHome();
                }
            }
        });

        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            showFeedback("Erreur Google OAuth: " + (ex == null ? "inconnue" : ex.getMessage()), false);
        });

        Thread thread = new Thread(task, "google-oauth-task");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleFacebookLogin() {
        handleOAuth("Facebook");
    }

    @FXML
    private void togglePasswordVisibility() {
        if (visiblePasswordField.isVisible()) {
            passwordField.setText(visiblePasswordField.getText());
            visiblePasswordField.setVisible(false);
            visiblePasswordField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            togglePasswordButton.setText("👁");
        } else {
            visiblePasswordField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            visiblePasswordField.setVisible(true);
            visiblePasswordField.setManaged(true);
            togglePasswordButton.setText("🙈");
        }
    }

    @FXML
    private void handleOpenHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleOpenRegister() {
        AppNavigator.showRegister();
    }

    @FXML
    private void handleOpenForgotPassword() {
        AppNavigator.showForgotPassword();
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private void handleOAuth(String provider) {
        String providerUrl = "Google".equalsIgnoreCase(provider)
                ? "https://accounts.google.com/"
                : "https://www.facebook.com/login.php";
        boolean opened = openExternalBrowser(providerUrl);
        if (!opened) {
            showFeedback("Impossible d'ouvrir le navigateur automatiquement. Copiez ce lien: " + providerUrl, false);
        }

        String email = askText("OAuth " + provider, "Email", "Saisissez l'email OAuth:");
        if (email == null || email.isBlank()) {
            showFeedback("Connexion OAuth annulee.", false);
            return;
        }

        String nom = askText("OAuth " + provider, "Nom", "Nom:");
        String prenom = askText("OAuth " + provider, "Prenom", "Prenom:");

        AuthService.LoginResult response = authService.loginWithOAuth(provider, email, nom, prenom);
        showFeedback(response.message(), response.success());

        if (response.success()) {
            if (response.user() != null && "ROLE_ADMIN".equalsIgnoreCase(response.user().getRole())) {
                AppNavigator.showAdminFaceVerification();
            } else {
                AppNavigator.showHome();
            }
        }
    }

    private String askMfaCode() {
        return askText("Double authentification", "Code Google Authenticator", "Entrez le code a 6 chiffres:");
    }

    private String askText(String title, String header, String content) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        Optional<String> result = dialog.showAndWait();
        return result.map(String::trim).orElse(null);
    }

    private boolean openExternalBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
            String os = System.getProperty("os.name", "").toLowerCase();
            Process process;
            if (os.contains("linux")) {
                process = new ProcessBuilder("xdg-open", url).start();
            } else if (os.contains("mac")) {
                process = new ProcessBuilder("open", url).start();
            } else if (os.contains("win")) {
                process = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else {
                return false;
            }
            return process.isAlive() || process.exitValue() == 0;
        } catch (Exception exception) {
            return false;
        }
    }
}
