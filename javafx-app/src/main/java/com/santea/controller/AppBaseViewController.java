package com.santea.controller;

import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.geometry.Pos;

public class AppBaseViewController {
    @FXML
    private StackPane contentContainer;

    public void setContent(Parent content) {
        if (content instanceof Region region) {
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        StackPane.setAlignment(content, Pos.TOP_LEFT);
        contentContainer.getChildren().setAll(content);
    }
}
