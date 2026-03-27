package com.santea.controller;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.StackPane;

public class AuthBaseViewController {

    @FXML
    private ComboBox<String> languageSelect;

    @FXML
    private StackPane contentContainer;

    @FXML
    private void initialize() {
        languageSelect.getItems().setAll("Français", "English/US");
        languageSelect.getSelectionModel().selectFirst();
    }

    public void setContent(Node content) {
        contentContainer.getChildren().setAll(content);
    }
}
