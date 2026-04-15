package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.GoogleOAuthService;
import com.santea.ui.ModalDialogs;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LoginViewController {
    private final AuthService authService = new AuthService();
    private final GoogleOAuthService googleOAuthService = new GoogleOAuthService();
    private final ContextMenu emailSuggestionMenu = new ContextMenu();

    private List<String> emailSuggestions = List.of();

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
    private void initialize() {
        emailSuggestions = authService.suggestLoginEmails(12);

        emailField.textProperty().addListener((obs, oldValue, newValue) -> showEmailSuggestions(newValue));
        emailField.focusedProperty().addListener((obs, oldValue, focused) -> {
            if (!focused) {
                emailSuggestionMenu.hide();
            } else {
                emailSuggestions = authService.suggestLoginEmails(12);
                showEmailSuggestions(emailField.getText());
            }
        });
        emailField.setOnMouseClicked(event -> {
            emailSuggestions = authService.suggestLoginEmails(12);
            showEmailSuggestions(emailField.getText());
        });

        passwordField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (!visiblePasswordField.isVisible()) {
                visiblePasswordField.setText(newValue);
            }
        });
        visiblePasswordField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (visiblePasswordField.isVisible()) {
                passwordField.setText(newValue);
            }
        });
    }

    @FXML
    private void handleLogin() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.isVisible() ? passwordField.getText() : visiblePasswordField.getText();
        boolean rememberMe = rememberMeCheckBox != null && rememberMeCheckBox.isSelected();

        AuthService.LoginResult response = authService.login(email, password, null, rememberMe);

        if (!response.success() && response.failureReason() == AuthService.LoginFailureReason.MFA_REQUIRED) {
            String code = askMfaCodePopup();
            if (code == null) {
                showFeedback("Connexion annulee: code 2FA requis.", false);
                return;
            }
            response = authService.login(email, password, code, rememberMe);
        }

        showFeedback(response.message(), response.success());

        if (response.success()) {
            routeAfterLogin(response);
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

    private void routeAfterLogin(AuthService.LoginResult response) {
        emailSuggestions = authService.suggestLoginEmails(12);
        Window owner = resolveOwnerWindow();

        if (response.user() != null && "ROLE_ADMIN".equalsIgnoreCase(response.user().getRole())) {
            AppNavigator.showAdminFaceVerification();
        } else {
            AppNavigator.showHome();
        }

        ensurePrimaryWindowExpanded(owner);
    }

    private void showEmailSuggestions(String typedValue) {
        String keyword = typedValue == null ? "" : typedValue.trim().toLowerCase();
        if (emailSuggestions.isEmpty() || emailField.getScene() == null) {
            emailSuggestionMenu.hide();
            return;
        }

        List<String> filtered = new ArrayList<>();
        for (String email : emailSuggestions) {
            String candidate = email == null ? "" : email.trim();
            if (candidate.isBlank()) {
                continue;
            }
            if (keyword.isBlank() || candidate.toLowerCase().contains(keyword)) {
                filtered.add(candidate);
            }
            if (filtered.size() >= 6) {
                break;
            }
        }

        if (filtered.isEmpty()) {
            emailSuggestionMenu.hide();
            return;
        }

        List<CustomMenuItem> items = new ArrayList<>();
        for (String suggestion : filtered) {
            Label label = new Label(suggestion);
            label.getStyleClass().add("email-suggestion-item");
            CustomMenuItem item = new CustomMenuItem(label, true);
            item.setOnAction(event -> {
                emailField.setText(suggestion);
                emailField.positionCaret(suggestion.length());
                emailSuggestionMenu.hide();
            });
            items.add(item);
        }

        emailSuggestionMenu.getItems().setAll(items);
        if (!emailSuggestionMenu.isShowing()) {
            emailSuggestionMenu.show(emailField, Side.BOTTOM, 0, 4);
        }
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

    private String askText(String title, String header, String content) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);
        Optional<String> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane");
        return result.map(String::trim).orElse(null);
    }

    private String askMfaCodePopup() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Double authentification");
        dialog.setHeaderText("Code Google Authenticator");

        ButtonType verifyType = new ButtonType("Verifier", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, verifyType);

        Label subtitle = new Label("Entrez le code 2FA a 6 chiffres pour continuer.");
        subtitle.getStyleClass().add("mfa-popup-subtitle");

        TextField d1 = createOtpDigitField();
        TextField d2 = createOtpDigitField();
        TextField d3 = createOtpDigitField();
        TextField d4 = createOtpDigitField();
        TextField d5 = createOtpDigitField();
        TextField d6 = createOtpDigitField();

        HBox digitsRow = new HBox(8, d1, d2, d3, d4, d5, d6);
        digitsRow.getStyleClass().add("mfa-popup-digits");

        Label hint = new Label("Code genere par Google Authenticator");
        hint.getStyleClass().add("mfa-popup-hint");

        VBox content = new VBox(10, subtitle, digitsRow, hint);
        content.getStyleClass().add("mfa-popup-content");
        dialog.getDialogPane().setContent(content);

        Node verifyButton = dialog.getDialogPane().lookupButton(verifyType);
        verifyButton.setDisable(true);

        wireOtpNavigation(d1, d2, null, verifyButton, d1, d2, d3, d4, d5, d6);
        wireOtpNavigation(d2, d3, d1, verifyButton, d1, d2, d3, d4, d5, d6);
        wireOtpNavigation(d3, d4, d2, verifyButton, d1, d2, d3, d4, d5, d6);
        wireOtpNavigation(d4, d5, d3, verifyButton, d1, d2, d3, d4, d5, d6);
        wireOtpNavigation(d5, d6, d4, verifyButton, d1, d2, d3, d4, d5, d6);
        wireOtpNavigation(d6, null, d5, verifyButton, d1, d2, d3, d4, d5, d6);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != verifyType) {
                return null;
            }
            return otpValue(d1, d2, d3, d4, d5, d6);
        });

        Optional<String> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "mfa-popup-pane");
        if (result.isEmpty()) {
            return null;
        }

        String sanitized = result.get().replaceAll("\\s+", "").trim();
        if (!sanitized.matches("\\d{6}")) {
            showFeedback("Code 2FA invalide: 6 chiffres requis.", false);
            return null;
        }
        return sanitized;
    }

    private TextField createOtpDigitField() {
        TextField field = new TextField();
        field.getStyleClass().add("mfa-popup-digit");
        field.setTextFormatter(new TextFormatter<>(change -> {
            if (!change.getControlNewText().matches("\\d{0,1}")) {
                return null;
            }
            return change;
        }));
        return field;
    }

    private void wireOtpNavigation(
            TextField field,
            TextField next,
            TextField previous,
            Node verifyButton,
            TextField d1,
            TextField d2,
            TextField d3,
            TextField d4,
            TextField d5,
            TextField d6
    ) {
        field.textProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != null && !newValue.isBlank() && next != null) {
                next.requestFocus();
                next.selectAll();
            }
            verifyButton.setDisable(otpValue(d1, d2, d3, d4, d5, d6).length() != 6);
        });

        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.BACK_SPACE && (field.getText() == null || field.getText().isBlank()) && previous != null) {
                previous.requestFocus();
                previous.clear();
            }
            if (event.getCode() == KeyCode.LEFT && previous != null) {
                previous.requestFocus();
            }
            if (event.getCode() == KeyCode.RIGHT && next != null) {
                next.requestFocus();
            }
        });
    }

    private String otpValue(TextField d1, TextField d2, TextField d3, TextField d4, TextField d5, TextField d6) {
        return safeDigit(d1) + safeDigit(d2) + safeDigit(d3) + safeDigit(d4) + safeDigit(d5) + safeDigit(d6);
    }

    private String safeDigit(TextField field) {
        if (field == null || field.getText() == null) {
            return "";
        }
        String value = field.getText().trim();
        return value.matches("\\d") ? value : "";
    }

    private Window resolveOwnerWindow() {
        if (emailField != null && emailField.getScene() != null) {
            return emailField.getScene().getWindow();
        }
        if (feedbackLabel != null && feedbackLabel.getScene() != null) {
            return feedbackLabel.getScene().getWindow();
        }
        return null;
    }

    private void ensurePrimaryWindowExpanded(Window owner) {
        if (!(owner instanceof Stage stage)) {
            return;
        }

        Platform.runLater(() -> {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(Math.max(bounds.getWidth(), Math.max(stage.getMinWidth(), stage.getWidth())));
            stage.setHeight(Math.max(bounds.getHeight(), Math.max(stage.getMinHeight(), stage.getHeight())));
            stage.setMaximized(true);
            Platform.runLater(() -> {
                stage.setMaximized(true);
                stage.toFront();
            });
        });
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
