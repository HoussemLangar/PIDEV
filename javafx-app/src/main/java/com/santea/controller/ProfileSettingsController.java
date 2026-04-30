package com.santea.controller;

import com.santea.model.User;
import com.santea.model.UserScoreHistory;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.ProfileService;
import com.santea.service.UserAiScoreService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProfileSettingsController {

    private static final DateTimeFormatter DATE_FORMAT     = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProfileService profileService       = new ProfileService();
    private final AuthService    authService          = new AuthService();
    private final UserAiScoreService userAiScoreService = new UserAiScoreService();

    // ─── Hero ──────────────────────────────────────────────────────────────────
    @FXML private Label heroNameLabel;
    @FXML private Label heroEmailLabel;
    @FXML private Label heroRoleLabel;

    // ─── Score IA ──────────────────────────────────────────────────────────────
    @FXML private Label      scoreValueLabel;
    @FXML private ProgressBar scoreProgressBar;
    @FXML private Label      scoreSupportLabel;
    @FXML private Label      scoreActivityLabel;
    @FXML private Label      scoreSeniorityLabel;
    @FXML private Label      scoreRuleLabel;
    @FXML private Label      scoreSanctionsLabel;
    @FXML private Label      scoreBenefitLabel;
    @FXML private Label      scoreEligibilityLabel;
    @FXML private VBox       scoreHistoryBox;

    // ─── Formulaire profil ────────────────────────────────────────────────────
    @FXML private TextField  nomField;
    @FXML private TextField  prenomField;
    @FXML private TextField  emailField;
    @FXML private TextField  telephoneField;
    @FXML private TextField  adresseField;
    @FXML private TextField  dateNaissanceField;
    @FXML private ComboBox<String> themeCombo;
    @FXML private ComboBox<String> localeCombo;
    @FXML private CheckBox   reminderCheck;

    // ─── Feedback ─────────────────────────────────────────────────────────────
    @FXML private Label feedbackLabel;

    // ─────────────────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        themeCombo.getItems().addAll("light", "dark");
        localeCombo.getItems().addAll("FR", "EN", "AR");
        loadProfile();
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    @FXML private void handleBackHome()             { AppNavigator.showHome(); }
    @FXML private void handleOpenMfaPage()          { AppNavigator.showProfileMfa(); }
    @FXML private void handleOpenSubscriptionPage() { AppNavigator.showSubscriptionPage(); }
    @FXML private void handleOpenConfidentialite()  {
        showFeedback("Section Confidentialité disponible prochainement.", true);
    }

    // ─── Sauvegarde profil ────────────────────────────────────────────────────

    @FXML
    private void handleSaveProfile() {
        User user = AuthSession.getCurrentUser();
        if (user == null) { showFeedback("Session invalide.", false); return; }

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
        if (result.success()) loadProfile();
    }

    // ─── Changement de mot de passe ───────────────────────────────────────────

    @FXML
    private void handleOpenPasswordModal() {
        User user = AuthSession.getCurrentUser();
        if (user == null) { showFeedback("Session invalide.", false); return; }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Modifier mot de passe");
        dialog.setHeaderText("Renseignez votre mot de passe actuel et le nouveau.");

        ButtonType confirm = new ButtonType("Mettre à jour", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);

        PasswordField current = new PasswordField();
        current.setPromptText("Mot de passe actuel");
        PasswordField next = new PasswordField();
        next.setPromptText("Nouveau mot de passe");
        PasswordField confirmNext = new PasswordField();
        confirmNext.setPromptText("Confirmer nouveau mot de passe");

        dialog.getDialogPane().setContent(new VBox(10, current, next, confirmNext));

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != confirm) return;

        AuthService.ActionResult ar = authService.changePassword(
                user, current.getText(), next.getText(), confirmNext.getText());
        showFeedback(ar.message(), ar.success());
    }

    // ─── Suppression compte ───────────────────────────────────────────────────

    @FXML
    private void handleDeleteAccount() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le compte");
        confirm.setHeaderText("Zone dangereuse");
        confirm.setContentText("Cette action est irréversible. Voulez-vous supprimer votre compte ?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        User user = AuthSession.getCurrentUser();
        ProfileService.ProfileResult deleted = profileService.deleteAccount(user);
        if (!deleted.success()) { showFeedback(deleted.message(), false); return; }

        authService.logout();
        AppNavigator.showHome();
    }

    // ─── Chargement profil complet ────────────────────────────────────────────

    private void loadProfile() {
        User sessionUser = AuthSession.getCurrentUser();
        if (sessionUser == null) { showFeedback("Aucun utilisateur connecté.", false); return; }

        ProfileService.ProfileResult result = profileService.loadCurrentUserProfile(sessionUser);
        if (!result.success() || result.user() == null) {
            showFeedback(result.message(), false);
            return;
        }

        User user = result.user();

        // Formulaire
        nomField.setText(safe(user.getNom()));
        prenomField.setText(safe(user.getPrenom()));
        emailField.setText(safe(user.getEmail()));
        telephoneField.setText(safe(user.getTelephone()));
        adresseField.setText(safe(user.getAdresse()));
        dateNaissanceField.setText(user.getDateNaissance() != null
                ? DATE_FORMAT.format(user.getDateNaissance()) : "");

        String theme = safe(user.getThemePreference()).isBlank() ? "light" : user.getThemePreference();
        themeCombo.setValue(themeCombo.getItems().contains(theme) ? theme : "light");

        String locale = safe(user.getLocale()).isBlank() ? "FR" : user.getLocale().toUpperCase();
        localeCombo.setValue(localeCombo.getItems().contains(locale) ? locale : "FR");

        reminderCheck.setSelected(Boolean.TRUE.equals(user.getReminderEnabled()));

        // Hero card
        heroNameLabel.setText(resolveFullName(user));
        heroEmailLabel.setText(safe(user.getEmail()));
        heroRoleLabel.setText(humanizeRole(safe(user.getRole())));

        // Score IA (asynchrone pour ne pas bloquer l'UI)
        Platform.runLater(() -> refreshAiScore(user));

        showFeedback("Profil chargé.", true);
    }

    // ─── Affichage du Score IA ────────────────────────────────────────────────

    private void refreshAiScore(User user) {
        // Calcul score + breakdown
        UserAiScoreService.ProfileScoreSummary summary = userAiScoreService.getProfileSummary(user);
        Map<String, Integer> bd = summary.breakdown();

        // Snapshot quotidien (fire-and-forget)
        try {
            userAiScoreService.recordDailyHistory(user);
        } catch (Exception ignored) {}

        // Score global
        int score = summary.score();
        if (scoreValueLabel    != null) scoreValueLabel.setText(score + "/100");
        if (scoreProgressBar   != null) {
            scoreProgressBar.setProgress(score / 100.0);
            // Couleur dynamique via style inline
            String barColor = score >= 80 ? "#16a34a" : score >= 55 ? "#f59e0b" : "#ef4444";
            scoreProgressBar.setStyle("-fx-accent: " + barColor + ";");
        }

        // Priorité support
        if (scoreSupportLabel != null) {
            String priority = summary.supportPriorityLabel();
            String chipStyle = switch (summary.supportPriority()) {
                case "high"   -> "-fx-background-color:#dcfce7; -fx-text-fill:#15803d;";
                case "normal" -> "-fx-background-color:#fef3c7; -fx-text-fill:#92400e;";
                default       -> "-fx-background-color:#f1f5f9; -fx-text-fill:#64748b;";
            };
            scoreSupportLabel.setText("Support " + priority);
            scoreSupportLabel.setStyle(chipStyle
                    + "-fx-font-weight:700; -fx-font-size:12;"
                    + "-fx-background-radius:999; -fx-padding:5 10 5 10;");
        }

        // Breakdown
        if (scoreActivityLabel   != null) scoreActivityLabel.setText(bd.getOrDefault("activity", 0) + "/35");
        if (scoreSeniorityLabel  != null) scoreSeniorityLabel.setText(bd.getOrDefault("seniority", 0) + "/25");
        if (scoreRuleLabel       != null) scoreRuleLabel.setText(bd.getOrDefault("ruleCompliance", 0) + "/25");
        if (scoreSanctionsLabel  != null) scoreSanctionsLabel.setText(bd.getOrDefault("sanctionsHistory", 0) + "/15");

        // Message bénéfices
        if (scoreBenefitLabel != null) scoreBenefitLabel.setText(summary.benefitsMessage());

        // Éligibilité
        if (scoreEligibilityLabel != null) {
            if (summary.aiOfferActive() && score >= 80) {
                scoreEligibilityLabel.setText("✔ Offre active : accès premium IA activé");
                scoreEligibilityLabel.setStyle("-fx-text-fill:#15803d; -fx-font-weight:700;");
            } else if (summary.premiumEligible()) {
                scoreEligibilityLabel.setText("✔ Éligible au premium IA ce mois-ci");
                scoreEligibilityLabel.setStyle("-fx-text-fill:#0369a1; -fx-font-weight:700;");
            } else {
                scoreEligibilityLabel.setText("✘ Atteignez 80/100 pour débloquer le premium IA");
                scoreEligibilityLabel.setStyle("-fx-text-fill:#9a3412; -fx-font-weight:700;");
            }
        }

        // Historique scoring
        if (scoreHistoryBox != null) {
            scoreHistoryBox.getChildren().clear();
            List<UserScoreHistory> history = userAiScoreService.getRecentHistory(user, 10);
            if (history.isEmpty()) {
                Label empty = new Label("Aucun historique disponible pour le moment.");
                empty.setStyle("-fx-font-size:12; -fx-text-fill:#64748b;");
                scoreHistoryBox.getChildren().add(empty);
            } else {
                for (UserScoreHistory item : history) {
                    HBox row = buildHistoryRow(item);
                    scoreHistoryBox.getChildren().add(row);
                }
            }
        }
    }

    private HBox buildHistoryRow(UserScoreHistory item) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding: 5 0 5 0;");

        Label dateLabel = new Label(item.getCreatedAt() != null
                ? DATETIME_FORMAT.format(item.getCreatedAt()) : "-");
        dateLabel.setStyle("-fx-font-size:12; -fx-text-fill:#334155;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        int s = item.getScore() != null ? item.getScore() : 0;
        String scoreColor = s >= 80 ? "#15803d" : s >= 55 ? "#92400e" : "#b91c1c";
        Label scoreLabel = new Label(s + "/100");
        scoreLabel.setStyle("-fx-font-size:12; -fx-font-weight:700; -fx-text-fill:" + scoreColor + ";");

        row.getChildren().addAll(dateLabel, spacer, scoreLabel);
        return row;
    }

    // ─── Utilitaires ──────────────────────────────────────────────────────────

    private String resolveFullName(User user) {
        String full = (safe(user.getNom()) + " " + safe(user.getPrenom())).trim();
        return full.isBlank() ? "Utilisateur SANTEA" : full;
    }

    private String humanizeRole(String role) {
        return switch (role) {
            case "ROLE_MEDECIN"       -> "Médecin";
            case "ROLE_PHARMACIEN"    -> "Pharmacien";
            case "ROLE_COACH"         -> "Coach sportif";
            case "ROLE_NUTRITIONNISTE"-> "Nutritionniste";
            case "ROLE_ADMIN"         -> "Admin";
            default                   -> "Patient";
        };
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
