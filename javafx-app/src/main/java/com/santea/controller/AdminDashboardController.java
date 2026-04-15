package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AdminOperationsService;
import com.santea.service.AdminDashboardService;
import com.santea.service.AuthSession;
import com.santea.ui.ConfirmDialogs;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;

import java.util.Optional;

public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService = new AdminDashboardService();
    private final AdminOperationsService adminOperationsService = new AdminOperationsService();
    private final AuthService authService = new AuthService();

    @FXML
    private Label usersTotalLabel;

    @FXML
    private Label usersBannedLabel;

    @FXML
    private Label usersVerifiedLabel;

    @FXML
    private Label usersPendingLabel;

    @FXML
    private Label suspiciousBlockedLabel;

    @FXML
    private Label appointmentsTodayLabel;

    @FXML
    private Label paymentsSucceededLabel;

    @FXML
    private Label activeSessionsLabel;

    @FXML
    private Label adminFeedbackLabel;

    @FXML
    private VBox latestBansBox;

    @FXML
    private void initialize() {
        // Face verification désactivée pour le développement
        refresh();
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleSanteQuotidienneAdmin() {
        try {
            AppNavigator.showSanteQuotidienneAdmin();
        } catch (Exception exception) {
            exception.printStackTrace();
            showFeedback("Erreur ouverture Santé Quotidienne admin : " + exception.getMessage(), false);
        }
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleLogout() {
        ConfirmDialogs.confirmLogout(usersTotalLabel, () -> {
            authService.logout();
            AppNavigator.showLogin();
        });
    }

    @FXML
    private void handleExportCsv() {
        AdminOperationsService.ActionResult result = adminOperationsService.exportCsvReports();
        showFeedback(result.message(), result.success());
    }

    @FXML
    private void handleRevokeLatestSession() {
        var sessions = adminOperationsService.listActiveSessions();
        if (sessions.isEmpty()) {
            showFeedback("Aucune session active a revoquer.", false);
            return;
        }

        AdminOperationsService.ActionResult result = adminOperationsService.revokeSession(sessions.get(0).id());
        showFeedback(result.message(), result.success());
        refresh();
    }

    @FXML
    private void handleReviewSuspicious() {
        var suspicious = adminOperationsService.listSuspiciousLogins();
        if (suspicious.isEmpty()) {
            showFeedback("Aucune connexion suspecte detectee.", true);
            return;
        }

        AdminOperationsService.SuspiciousLoginRow latest = suspicious.get(0);
        showFeedback("Derniere alerte: " + latest.email() + " - " + latest.reason(), true);
    }

    @FXML
    private void handleVoiceAssistant() {
        TextInputDialog dialog = new TextInputDialog("export csv");
        dialog.setTitle("Assistant Vocal Admin");
        dialog.setHeaderText("Commande admin (API)");
        dialog.setContentText("Exemple: export csv, sessions actives, logins suspects");
        Optional<String> command = dialog.showAndWait();

        if (command.isEmpty() || command.get().isBlank()) {
            showFeedback("Commande annulee.", false);
            return;
        }

        AdminOperationsService.AdminVoiceResult result = adminOperationsService.handleVoiceCommand(command.get());
        showFeedback(result.message(), result.success());
        refresh();
    }

    private void refresh() {
        AdminDashboardService.AdminDashboardData data = adminDashboardService.loadData();

        usersTotalLabel.setText(String.valueOf(data.totalUsers()));
        usersBannedLabel.setText(String.valueOf(data.bannedUsers()));
        usersVerifiedLabel.setText(String.valueOf(data.verifiedUsers()));
        usersPendingLabel.setText(String.valueOf(data.pendingUsers()));
        suspiciousBlockedLabel.setText(String.valueOf(data.blockedSuspicious()));
        appointmentsTodayLabel.setText(String.valueOf(data.appointmentsToday()));
        if (paymentsSucceededLabel != null) {
            paymentsSucceededLabel.setText(String.valueOf(data.succeededPayments()));
        }
        if (activeSessionsLabel != null) {
            activeSessionsLabel.setText(String.valueOf(data.activeSessions()));
        }

        latestBansBox.getChildren().clear();
        if (data.latestBans().isEmpty()) {
            Label empty = new Label("Aucun compte banni recemment.");
            empty.getStyleClass().add("admin-line");
            latestBansBox.getChildren().add(empty);
            return;
        }

        for (String row : data.latestBans()) {
            Label item = new Label(row);
            item.getStyleClass().add("admin-line");
            latestBansBox.getChildren().add(item);
        }
    }

    private void showFeedback(String message, boolean success) {
        if (adminFeedbackLabel == null) {
            return;
        }
        adminFeedbackLabel.setText(message);
        adminFeedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        adminFeedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        adminFeedbackLabel.setVisible(true);
        adminFeedbackLabel.setManaged(true);
    }
}
