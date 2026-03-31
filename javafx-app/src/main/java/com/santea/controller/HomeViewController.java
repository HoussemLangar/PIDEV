package com.santea.controller;

import com.santea.navigation.AppNavigator;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class HomeViewController {

    @FXML
    private ScrollPane homeScroll;

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
    private void handleOpenLogin() {
        AppNavigator.showLogin();
    }

    @FXML
    private void handleOpenRegister() {
        AppNavigator.showRegister();
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
}
