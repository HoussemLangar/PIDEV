package com.santea.controller;

import com.santea.navigation.AppNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public class FeaturePageController {
    @FXML
    private Label pageTitleLabel;

    @FXML
    private Label pageSubtitleLabel;

    @FXML
    private VBox contentListBox;

    public void setData(String title, String subtitle, List<String> lines) {
        pageTitleLabel.setText(title == null || title.isBlank() ? "Module" : title);
        pageSubtitleLabel.setText(subtitle == null || subtitle.isBlank() ? "SANTEA" : subtitle);

        contentListBox.getChildren().clear();
        if (lines == null || lines.isEmpty()) {
            Label empty = new Label("Aucune donnee disponible.");
            empty.getStyleClass().add("feature-line");
            contentListBox.getChildren().add(empty);
            return;
        }

        for (String line : lines) {
            Label item = new Label(line == null ? "" : line);
            item.setWrapText(true);
            item.getStyleClass().add("feature-line");
            contentListBox.getChildren().add(item);
        }
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }
}
