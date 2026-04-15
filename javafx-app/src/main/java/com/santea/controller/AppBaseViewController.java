package com.santea.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.layout.StackPane;
import java.net.URL;
import java.util.ResourceBundle;

public class AppBaseViewController implements Initializable {
    @FXML
    private StackPane contentContainer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Base initialization - override in subclasses
    }

    public void setContent(Parent content) {
        if (contentContainer != null) {
            contentContainer.getChildren().setAll(content);
        }
    }
}
