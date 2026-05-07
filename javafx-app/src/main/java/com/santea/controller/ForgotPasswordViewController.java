package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class ForgotPasswordViewController {
    private final AuthService authService = new AuthService();

    @FXML
    private TextField emailField;

    @FXML
    private TextField tokenField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmNewPasswordField;

    @FXML
    private Label tokenHintLabel;

    @FXML
    private VBox resetPasswordSection;

    @FXML
    private VBox emailRequestSection;

    @FXML
    private Button sendResetLinkButton;

    @FXML
    private Label feedbackLabel;

    @FXML
    private void handleOpenHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleBackToLogin() {
        AppNavigator.showLogin();
    }

    @FXML
    private void handleSendResetLink() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        AuthService.ForgotPasswordResult result = authService.requestPasswordReset(email);

        showFeedback(result.message(), result.success());

        tokenHintLabel.setVisible(false);
        tokenHintLabel.setManaged(false);

        if (result.success() && result.resetToken() != null && !result.resetToken().isBlank()) {
            tokenField.setText(result.resetToken());
            tokenHintLabel.setText("SMTP indisponible: token de secours généré. Utilisez-le ci-dessous pour réinitialiser.");
            tokenHintLabel.setVisible(true);
            tokenHintLabel.setManaged(true);
            if (resetPasswordSection != null) {
                resetPasswordSection.setVisible(false);
                resetPasswordSection.setManaged(false);
            }
            if (emailRequestSection != null) {
                emailRequestSection.setVisible(true);
                emailRequestSection.setManaged(true);
            }
            if (sendResetLinkButton != null) {
                sendResetLinkButton.setVisible(true);
                sendResetLinkButton.setManaged(true);
            }
        } else {
            tokenField.clear();
            if (resetPasswordSection != null) {
                boolean emailSent = result.success() && result.emailSent();
                resetPasswordSection.setVisible(emailSent);
                resetPasswordSection.setManaged(emailSent);
            }
            if (emailRequestSection != null) {
                boolean emailSent = result.success() && result.emailSent();
                emailRequestSection.setVisible(!emailSent);
                emailRequestSection.setManaged(!emailSent);
            }
            if (sendResetLinkButton != null) {
                boolean emailSent = result.success() && result.emailSent();
                sendResetLinkButton.setVisible(!emailSent);
                sendResetLinkButton.setManaged(!emailSent);
            }
        }
    }

    @FXML
    private void handleResetPassword() {
        String token = tokenField.getText() == null ? "" : tokenField.getText().trim();
        String newPassword = newPasswordField.getText() == null ? "" : newPasswordField.getText();
        String confirmPassword = confirmNewPasswordField.getText() == null ? "" : confirmNewPasswordField.getText();

        AuthService.ResetPasswordResult result = authService.resetPassword(token, newPassword, confirmPassword);
        showFeedback(result.message(), result.success());

        if (result.success()) {
            newPasswordField.clear();
            confirmNewPasswordField.clear();
            tokenField.clear();
        }
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }
}
