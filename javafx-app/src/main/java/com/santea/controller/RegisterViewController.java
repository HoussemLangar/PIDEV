package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.Region;

import java.util.concurrent.ThreadLocalRandom;

public class RegisterViewController {
    private final AuthService authService = new AuthService();

    @FXML
    private Label registerFeedbackLabel;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField nomField;

    @FXML
    private TextField prenomField;

    @FXML
    private DatePicker dateNaissancePicker;

    @FXML
    private TextField adresseField;

    @FXML
    private TextField telephoneField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private TextField visiblePasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private TextField visibleConfirmPasswordField;

    @FXML
    private Label captchaQuestionLabel;

    @FXML
    private TextField captchaField;

    @FXML
    private CheckBox termsAcceptedCheckBox;

    private int captchaAnswer;

    @FXML
    private void initialize() {
        refreshCaptcha();
    }

    @FXML
    private void handleOpenHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleOpenLogin() {
        AppNavigator.showLogin();
    }

    @FXML
    private void handleCreateAccount() {
        if (isBlank(usernameField) || isBlank(emailField) || isBlank(nomField) || isBlank(prenomField)) {
            showFeedback("Veuillez compléter les champs obligatoires (utilisateur, email, nom, prénom).", false);
            return;
        }

        if (dateNaissancePicker.getValue() == null) {
            showFeedback("Veuillez saisir votre date de naissance.", false);
            return;
        }

        String password = readPassword(passwordField, visiblePasswordField);
        String confirmPassword = readPassword(confirmPasswordField, visibleConfirmPasswordField);

        if (password.isBlank() || confirmPassword.isBlank()) {
            showFeedback("Veuillez saisir et confirmer votre mot de passe.", false);
            return;
        }

        if (!password.equals(confirmPassword)) {
            showFeedback("Les mots de passe ne correspondent pas.", false);
            return;
        }

        if (!termsAcceptedCheckBox.isSelected()) {
            showFeedback("Vous devez accepter les conditions d'utilisation.", false);
            return;
        }

        String captchaValue = captchaField.getText() == null ? "" : captchaField.getText().trim();
        if (captchaValue.isBlank()) {
            showFeedback("Veuillez résoudre le captcha.", false);
            return;
        }

        try {
            int providedAnswer = Integer.parseInt(captchaValue);
            if (providedAnswer != captchaAnswer) {
                refreshCaptcha();
                captchaField.clear();
                showFeedback("Captcha incorrect. Veuillez réessayer.", false);
                return;
            }
        } catch (NumberFormatException exception) {
            showFeedback("Le captcha doit être un nombre.", false);
            return;
        }

        AuthService.RegistrationRequest request = new AuthService.RegistrationRequest(
                usernameField.getText(),
                emailField.getText(),
                nomField.getText(),
                prenomField.getText(),
                dateNaissancePicker.getValue(),
                adresseField.getText(),
                telephoneField.getText(),
                password,
                confirmPassword,
                termsAcceptedCheckBox.isSelected()
        );

        AuthService.RegisterResult result = authService.register(request);
        showFeedback(result.message(), result.success());

        if (!result.success()) {
            return;
        }

        refreshCaptcha();
        captchaField.clear();

        if (result.success()) {
            AppNavigator.showLogin();
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
        } else {
            visiblePasswordField.setText(passwordField.getText());
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            visiblePasswordField.setVisible(true);
            visiblePasswordField.setManaged(true);
        }
    }

    @FXML
    private void toggleConfirmPasswordVisibility() {
        if (visibleConfirmPasswordField.isVisible()) {
            confirmPasswordField.setText(visibleConfirmPasswordField.getText());
            visibleConfirmPasswordField.setVisible(false);
            visibleConfirmPasswordField.setManaged(false);
            confirmPasswordField.setVisible(true);
            confirmPasswordField.setManaged(true);
        } else {
            visibleConfirmPasswordField.setText(confirmPasswordField.getText());
            confirmPasswordField.setVisible(false);
            confirmPasswordField.setManaged(false);
            visibleConfirmPasswordField.setVisible(true);
            visibleConfirmPasswordField.setManaged(true);
        }
    }

    @FXML
    private void handleShowTerms() {
        ButtonType acceptButton = new ButtonType("Accepter", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButton = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", acceptButton, closeButton);
        alert.setTitle("Conditions d'utilisation SANTÉA");
        alert.setHeaderText("Conditions d'utilisation et politique de confidentialité");
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setContentText(
                "1) Objet: SANTÉA fournit des services numériques de suivi et d'accompagnement santé.\n\n"
                        + "2) Compte utilisateur: vous êtes responsable de la confidentialité de vos identifiants et des activités réalisées depuis votre compte.\n\n"
                        + "3) Données personnelles: vos données sont traitées uniquement pour fournir les services demandés, améliorer la qualité des soins et respecter les obligations légales.\n\n"
                        + "4) Confidentialité: les données médicales sont protégées et partagées uniquement avec les professionnels autorisés et selon votre consentement.\n\n"
                        + "5) Sécurité: vous devez signaler immédiatement toute utilisation suspecte de votre compte.\n\n"
                        + "6) Usage conforme: il est interdit de publier du contenu frauduleux, illégal ou portant atteinte aux droits d'autrui.\n\n"
                        + "7) Limitation: SANTÉA ne remplace pas une prise en charge médicale d'urgence. En cas d'urgence, contactez les services compétents.\n\n"
                        + "8) Acceptation: en créant un compte, vous acceptez ces conditions et la politique de confidentialité associée."
        );

        alert.showAndWait().ifPresent(result -> {
            if (result == acceptButton) {
                termsAcceptedCheckBox.setSelected(true);
                showFeedback("Conditions acceptées.", true);
            }
        });
    }

    private boolean isBlank(TextField field) {
        return field == null || field.getText() == null || field.getText().trim().isBlank();
    }

    private String readPassword(PasswordField hiddenField, TextField visibleField) {
        String value = visibleField.isVisible() ? visibleField.getText() : hiddenField.getText();
        return value == null ? "" : value.trim();
    }

    private void refreshCaptcha() {
        int left = ThreadLocalRandom.current().nextInt(2, 16);
        int right = ThreadLocalRandom.current().nextInt(1, 11);
        captchaAnswer = left + right;
        captchaQuestionLabel.setText(left + " + " + right + " = ?");
    }

    private void showFeedback(String message, boolean success) {
        registerFeedbackLabel.setText(message);
        registerFeedbackLabel.getStyleClass().removeAll("alert-danger", "alert-success");
        registerFeedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        registerFeedbackLabel.setVisible(true);
        registerFeedbackLabel.setManaged(true);
    }
}
