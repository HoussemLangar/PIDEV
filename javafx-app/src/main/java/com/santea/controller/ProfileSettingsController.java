package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.ProfileService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class ProfileSettingsController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProfileService profileService = new ProfileService();
    private final AuthService authService = new AuthService();

    @FXML
    private TextField nomField;

    @FXML
    private TextField prenomField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField telephoneField;

    @FXML
    private TextField adresseField;

    @FXML
    private TextField dateNaissanceField;

    @FXML
    private ComboBox<String> themeCombo;

    @FXML
    private ComboBox<String> localeCombo;

    @FXML
    private CheckBox reminderCheck;

    @FXML
    private Label heroNameLabel;

    @FXML
    private Label heroEmailLabel;

    @FXML
    private Label heroRoleLabel;

    @FXML
    private Label scoreValueLabel;

    @FXML
    private Label scoreBreakdownLeft;

    @FXML
    private Label scoreBreakdownRight;

    @FXML
    private Label scoreBenefitLabel;

    @FXML
    private Label feedbackLabel;

    @FXML
    private void initialize() {
        themeCombo.getItems().addAll("light", "dark");
        localeCombo.getItems().addAll("FR", "EN", "AR");
        loadProfile();
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleNavHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleNavServices() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleOpenProfilePage() {
        AppNavigator.showProfileSettings();
    }

    @FXML
    private void handleOpenMfaPage() {
        AppNavigator.showProfileMfa();
    }

    @FXML
    private void handleOpenSubscriptionPage() {
        AppNavigator.showSubscriptionPage();
    }

    @FXML
    private void handleOpenConfidentialite() {
        showFeedback("Section Confidentialite disponible prochainement.", true);
    }

    @FXML
    private void handleSaveProfile() {
        User user = AuthSession.getCurrentUser();
        if (user == null) {
            showFeedback("Session invalide.", false);
            return;
        }

        ProfileService.ProfileResult result = profileService.updateProfile(
                user,
                nomField.getText(),
                prenomField.getText(),
                telephoneField.getText(),
                adresseField.getText(),
                themeCombo.getValue(),
                localeCombo.getValue(),
                reminderCheck.isSelected()
        );

        showFeedback(result.message(), result.success());
        if (result.success()) {
            loadProfile();
        }
    }

    @FXML
    private void handleOpenPasswordModal() {
        User user = AuthSession.getCurrentUser();
        if (user == null) {
            showFeedback("Session invalide.", false);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modifier mot de passe");
        dialog.setHeaderText("Renseignez votre mot de passe actuel et le nouveau mot de passe.");

        ButtonType confirm = new ButtonType("Mettre a jour", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);

        PasswordField current = new PasswordField();
        current.setPromptText("Mot de passe actuel");

        PasswordField next = new PasswordField();
        next.setPromptText("Nouveau mot de passe");

        PasswordField confirmNext = new PasswordField();
        confirmNext.setPromptText("Confirmer nouveau mot de passe");

        VBox content = new VBox(10, current, next, confirmNext);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != confirm) {
            return;
        }

        AuthService.ActionResult actionResult = authService.changePassword(
                user,
                current.getText(),
                next.getText(),
                confirmNext.getText()
        );
        showFeedback(actionResult.message(), actionResult.success());
    }

    @FXML
    private void handleDeleteAccount() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le compte");
        confirm.setHeaderText("Zone dangereuse");
        confirm.setContentText("Cette action est irreversible. Voulez-vous supprimer votre compte ?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        User user = AuthSession.getCurrentUser();
        ProfileService.ProfileResult deleted = profileService.deleteAccount(user);
        if (!deleted.success()) {
            showFeedback(deleted.message(), false);
            return;
        }

        authService.logout();
        AppNavigator.showHome();
    }

    private void loadProfile() {
        User sessionUser = AuthSession.getCurrentUser();
        if (sessionUser == null) {
            showFeedback("Aucun utilisateur connecte.", false);
            return;
        }

        ProfileService.ProfileResult result = profileService.loadCurrentUserProfile(sessionUser);
        if (!result.success() || result.user() == null) {
            showFeedback(result.message(), false);
            return;
        }

        User user = result.user();

        nomField.setText(nullSafe(user.getNom()));
        prenomField.setText(nullSafe(user.getPrenom()));
        emailField.setText(nullSafe(user.getEmail()));
        telephoneField.setText(nullSafe(user.getTelephone()));
        adresseField.setText(nullSafe(user.getAdresse()));

        if (user.getDateNaissance() != null) {
            dateNaissanceField.setText(DATE_FORMAT.format(user.getDateNaissance()));
        } else {
            dateNaissanceField.setText("");
        }

        String theme = nullSafe(user.getThemePreference()).isBlank() ? "light" : user.getThemePreference();
        themeCombo.setValue(themeCombo.getItems().contains(theme) ? theme : "light");

        String locale = nullSafe(user.getLocale()).isBlank() ? "FR" : user.getLocale().toUpperCase();
        localeCombo.setValue(localeCombo.getItems().contains(locale) ? locale : "FR");

        reminderCheck.setSelected(Boolean.TRUE.equals(user.getReminderEnabled()));

        heroNameLabel.setText(resolveFullName(user));
        heroEmailLabel.setText(nullSafe(user.getEmail()));
        heroRoleLabel.setText(humanizeRole(nullSafe(user.getRole())));

        int score = computeProfileScore(user);
        scoreValueLabel.setText(score + "/100");
        scoreBreakdownLeft.setText("Activite " + Math.min(35, score / 3) + "/35    Respect des regles " + Math.min(25, score / 4) + "/25");
        scoreBreakdownRight.setText("Anciennete " + Math.min(25, score / 4) + "/25    Historique sanctions 15/15");
        scoreBenefitLabel.setText(score >= 80
                ? "Offre active: vous etes eligible premium IA."
                : "Offre non activee: atteignez 80/100 pour debloquer le premium IA.");

        showFeedback("Profil charge.", true);
    }

    private int computeProfileScore(User user) {
        int score = 35;
        if (!nullSafe(user.getNom()).isBlank()) {
            score += 15;
        }
        if (!nullSafe(user.getPrenom()).isBlank()) {
            score += 15;
        }
        if (!nullSafe(user.getEmail()).isBlank()) {
            score += 20;
        }
        if (!nullSafe(user.getTelephone()).isBlank()) {
            score += 10;
        }
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            score += 5;
        }
        return Math.min(score, 100);
    }

    private String resolveFullName(User user) {
        String full = (nullSafe(user.getNom()) + " " + nullSafe(user.getPrenom())).trim();
        return full.isBlank() ? "Utilisateur SANTEA" : full;
    }

    private String humanizeRole(String role) {
        return switch (role) {
            case "ROLE_MEDECIN" -> "Medecin";
            case "ROLE_PHARMACIEN" -> "Pharmacien";
            case "ROLE_COACH" -> "Coach sportif";
            case "ROLE_NUTRITIONNISTE" -> "Nutritionniste";
            case "ROLE_ADMIN" -> "Admin";
            default -> "Patient";
        };
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
