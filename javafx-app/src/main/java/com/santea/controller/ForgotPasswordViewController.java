package com.santea.controller;

import com.santea.navigation.AppNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.regex.Pattern;

public class ForgotPasswordViewController {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    @FXML
    private TextField emailField;

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

        if (email.isBlank()) {
            showFeedback("Veuillez saisir votre adresse email.", false);
            return;
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showFeedback("Adresse email invalide.", false);
            return;
        }

        showFeedback("Si un compte existe pour cet email, un lien de réinitialisation a été envoyé.", true);
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }
}
