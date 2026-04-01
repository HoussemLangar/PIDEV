package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class HomeViewController {
    private final AuthService authService = new AuthService();

    @FXML
    private ScrollPane homeScroll;

    @FXML
    private Button loginButton;

    @FXML
    private Button registerButton;

    @FXML
    private MenuButton accountMenuButton;

    @FXML
    private MenuItem accountHeaderItem;

    @FXML
    private StackPane heroSection;

    @FXML
    private VBox aboutSection;

    @FXML
    private VBox servicesSection;

    @FXML
    private VBox blogSection;

    @FXML
    private VBox contactSection;

    @FXML
    private void initialize() {
        refreshAuthUi();
    }

    @FXML
    private void handleOpenLogin() {
        AppNavigator.showLogin();
    }

    @FXML
    private void handleOpenRegister() {
        AppNavigator.showRegister();
    }

    @FXML
    private void handleAccountProfile() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleAccountMfa() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleAccountMessages() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleAccountDocuments() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleAccountTeleconsultation() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleAccountPlans() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleAccountJournal() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleAccountSymptoms() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleAccountSubscription() {
        // Placeholder JavaFX local action.
    }

    @FXML
    private void handleLogout() {
        authService.logout();
        refreshAuthUi();
    }

    @FXML
    private void handleScrollToTop() {
        scrollTo(heroSection);
    }

    @FXML
    private void handleScrollToAbout() {
        scrollTo(aboutSection);
    }

    @FXML
    private void handleScrollToServices() {
        scrollTo(servicesSection);
    }

    @FXML
    private void handleScrollToBlog() {
        scrollTo(blogSection);
    }

    @FXML
    private void handleScrollToContact() {
        scrollTo(contactSection);
    }

    private void scrollTo(Node section) {
        if (homeScroll == null || section == null || homeScroll.getContent() == null) {
            return;
        }

        double contentHeight = homeScroll.getContent().getBoundsInLocal().getHeight();
        double viewportHeight = homeScroll.getViewportBounds().getHeight();
        double denominator = contentHeight - viewportHeight;

        if (denominator <= 0) {
            homeScroll.setVvalue(0);
            return;
        }

        double y = section.getBoundsInParent().getMinY();
        double target = Math.max(0, Math.min(1, y / denominator));
        homeScroll.setVvalue(target);
    }

    private void refreshAuthUi() {
        boolean connected = AuthSession.isAuthenticated();

        if (loginButton != null) {
            loginButton.setVisible(!connected);
            loginButton.setManaged(!connected);
        }

        if (registerButton != null) {
            registerButton.setVisible(!connected);
            registerButton.setManaged(!connected);
        }

        if (accountMenuButton != null) {
            accountMenuButton.setVisible(connected);
            accountMenuButton.setManaged(connected);
        }

        if (connected && accountHeaderItem != null && AuthSession.getCurrentUser() != null) {
            String nom = AuthSession.getCurrentUser().getNom() == null ? "" : AuthSession.getCurrentUser().getNom();
            String prenom = AuthSession.getCurrentUser().getPrenom() == null ? "" : AuthSession.getCurrentUser().getPrenom();
            String role = AuthSession.getCurrentUser().getRole() == null ? "" : AuthSession.getCurrentUser().getRole();
            accountHeaderItem.setText((nom + " " + prenom).trim() + " • " + role);
        }
    }
}
