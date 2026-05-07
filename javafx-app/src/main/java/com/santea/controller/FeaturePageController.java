package com.santea.controller;

import com.santea.navigation.AppNavigator;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FeaturePageController {
    @FXML
    private Label pageTitleLabel;

    @FXML
    private Label pageSubtitleLabel;

    @FXML
    private Label lineCountLabel;

    @FXML
    private Label sectionSummaryLabel;

    @FXML
    private FlowPane keyPointsPane;

    @FXML
    private VBox contentListBox;

    public void setData(String title, String subtitle, List<String> lines) {
        pageTitleLabel.setText(title == null || title.isBlank() ? "Module" : title);
        pageSubtitleLabel.setText(subtitle == null || subtitle.isBlank() ? "SANTEA" : subtitle);

        List<String> normalizedLines = new ArrayList<>();
        if (lines != null) {
            for (String line : lines) {
                if (line != null && !line.isBlank()) {
                    normalizedLines.add(line.trim());
                }
            }
        }

        lineCountLabel.setText(normalizedLines.size() + (normalizedLines.size() > 1 ? " points" : " point"));
        sectionSummaryLabel.setText(buildSummary(normalizedLines));

        keyPointsPane.getChildren().clear();
        buildHighlights(normalizedLines);

        contentListBox.getChildren().clear();
        if (normalizedLines.isEmpty()) {
            Label empty = new Label("Aucune donnee disponible.");
            empty.getStyleClass().add("feature-empty-line");
            contentListBox.getChildren().add(empty);
            return;
        }

        for (int i = 0; i < normalizedLines.size(); i++) {
            contentListBox.getChildren().add(buildDetailCard(i + 1, normalizedLines.get(i)));
        }
    }

    private void buildHighlights(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }

        int size = Math.min(3, lines.size());
        for (int i = 0; i < size; i++) {
            VBox card = new VBox(6);
            card.getStyleClass().add("feature-highlight-card");

            Label number = new Label("Point " + (i + 1));
            number.getStyleClass().add("feature-highlight-label");

            Label text = new Label(lines.get(i));
            text.setWrapText(true);
            text.getStyleClass().add("feature-highlight-text");

            card.getChildren().addAll(number, text);
            keyPointsPane.getChildren().add(card);
        }
    }

    private VBox buildDetailCard(int index, String line) {
        VBox card = new VBox(8);
        card.getStyleClass().add("feature-detail-card");

        HBox header = new HBox(8);
        Label indexLabel = new Label(String.valueOf(index));
        indexLabel.getStyleClass().add("feature-step-index");

        Label category = new Label(extractCategory(line));
        category.getStyleClass().add("feature-step-tag");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(indexLabel, category, spacer);

        Label item = new Label(line);
        item.setWrapText(true);
        item.getStyleClass().add("feature-line");

        card.getChildren().addAll(header, item);
        return card;
    }

    private String buildSummary(List<String> lines) {
        if (lines.isEmpty()) {
            return "Aucun contenu detaille pour le moment.";
        }

        if (lines.size() == 1) {
            return "1 action recommandee pour cette fonctionnalite.";
        }

        return "Plan d'action en " + lines.size() + " etapes pour exploiter ce module.";
    }

    private String extractCategory(String line) {
        if (line == null || line.isBlank()) {
            return "Info";
        }

        String trimmed = line.trim();
        int separatorIndex = trimmed.indexOf(':');
        if (separatorIndex > 2 && separatorIndex < 26) {
            return trimmed.substring(0, separatorIndex).trim();
        }

        if (trimmed.toLowerCase(Locale.ROOT).contains("priorit")) {
            return "Priorite";
        }
        if (trimmed.toLowerCase(Locale.ROOT).contains("secur")) {
            return "Securite";
        }
        return "Detail";
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleOpenHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleOpenPharmacy() {
        AppNavigator.showPharmacyPage();
    }
}
