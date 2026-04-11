package com.santea.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;

public class AppBaseViewController {
    @FXML
    private StackPane contentContainer;

    public void setContent(Parent content) {
        contentContainer.getChildren().setAll(content);
    }
}
