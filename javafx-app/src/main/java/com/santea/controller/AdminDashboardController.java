package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AdminDashboardService;
import com.santea.service.AuthSession;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService = new AdminDashboardService();
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
    private VBox latestBansBox;

    @FXML
    private void initialize() {
        if (!AuthSession.isFaceVerified()) {
            AppNavigator.showAdminFaceVerification();
            return;
        }
        refresh();
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleRefresh() {
        refresh();
    }

    @FXML
    private void handleLogout() {
        authService.logout();
        AppNavigator.showLogin();
    }

    private void refresh() {
        AdminDashboardService.AdminDashboardData data = adminDashboardService.loadData();

        usersTotalLabel.setText(String.valueOf(data.totalUsers()));
        usersBannedLabel.setText(String.valueOf(data.bannedUsers()));
        usersVerifiedLabel.setText(String.valueOf(data.verifiedUsers()));
        usersPendingLabel.setText(String.valueOf(data.pendingUsers()));
        suspiciousBlockedLabel.setText(String.valueOf(data.blockedSuspicious()));
        appointmentsTodayLabel.setText(String.valueOf(data.appointmentsToday()));

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
}
