package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginViewController {
    private final AuthService authService = new AuthService();

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
    private void handleLogin() {
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String password = passwordField.isVisible() ? passwordField.getText() : visiblePasswordField.getText();

        AuthService.LoginResult response = authService.login(email, password);
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
}
