package com.santea.controller;

import com.santea.navigation.AppNavigator;
import javafx.fxml.FXML;

public class HomeViewController {

    @FXML
    private void handleOpenLogin() {
        AppNavigator.showLogin();
    }

    @FXML
    private void handleOpenRegister() {
        AppNavigator.showRegister();
    }
}
