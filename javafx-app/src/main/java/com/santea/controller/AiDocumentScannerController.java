package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AiToolsService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;

public class AiDocumentScannerController extends AiToolBaseController {
    private final AiToolsService aiToolsService = new AiToolsService();

    @FXML
    private TextArea contentArea;

    @FXML
    private Label fileLabel;

    @FXML
    private VBox resultsBox;

    @FXML
    private Label summaryLabel;

    @FXML
    private Label predictionLabel;

    @FXML
    private VBox abnormalBox;

    @FXML
    private VBox keyPointsBox;

    @FXML
    private VBox valuesRowsBox;

    @FXML
    private VBox actionsBox;

    @FXML
    private Label warningLabel;

    @FXML
    private HBox warningBox;

    @FXML
    private Button analyzeButton;

    private File selectedFile;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        if (!guardAiToolsAccess()) {
            return;
        }
        setVisibleManaged(resultsBox, false);
        setVisibleManaged(warningBox, false);
    }

    @FXML
    private void handleBack() {
        AppNavigator.showAiToolsPage();
    }

    @FXML
    private void handleChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.txt", "*.csv", "*.log", "*.md")
        );
        selectedFile = chooser.showOpenDialog(fileLabel.getScene().getWindow());
        fileLabel.setText(selectedFile == null ? "Aucun fichier choisi" : selectedFile.getName());
    }

    @FXML
    private void handleAnalyze() {
        analyzeButton.setDisable(true);
        try {
            AiToolsService.DocumentScanResponse response = aiToolsService.scanDocument(contentArea.getText(), selectedFile);
            render(response);
        } catch (Exception exception) {
            showError("Analyse impossible", exception.getMessage());
        } finally {
            analyzeButton.setDisable(false);
        }
    }

    private void render(AiToolsService.DocumentScanResponse response) {
        summaryLabel.setText(response.summary());
        predictionLabel.setText(response.analysisPrediction());

        abnormalBox.getChildren().clear();
        for (String row : response.abnormalFindings()) {
            abnormalBox.getChildren().add(buildBullet(row));
        }

        keyPointsBox.getChildren().clear();
        for (String row : response.keyPoints()) {
            keyPointsBox.getChildren().add(buildBullet(row));
        }

        valuesRowsBox.getChildren().clear();
        for (AiToolsService.DocumentValue value : response.values()) {
            HBox line = new HBox(10);
            line.getStyleClass().add("ait-value-row");
            Label left = new Label(value.label());
            left.getStyleClass().add("ait-value-label");
            Label center = new Label(value.value());
            center.getStyleClass().add("ait-value-main");
            Label right = new Label(value.unit().isBlank() ? "-" : value.unit());
            right.getStyleClass().add("ait-value-chip");
            line.getChildren().addAll(left, center, right);
            valuesRowsBox.getChildren().add(line);
        }

        actionsBox.getChildren().clear();
        int index = 1;
        for (String action : response.suggestedActions()) {
            HBox line = new HBox(8);
            Label order = new Label(String.valueOf(index++));
            order.getStyleClass().add("ait-order-dot");
            Label text = new Label(action);
            text.getStyleClass().add("ait-item-text");
            text.setWrapText(true);
            line.getChildren().addAll(order, text);
            actionsBox.getChildren().add(line);
        }

        if (!response.warning().isBlank()) {
            warningLabel.setText(response.warning());
            setVisibleManaged(warningBox, true);
        } else {
            setVisibleManaged(warningBox, false);
        }

        setVisibleManaged(resultsBox, true);
        setVisibleManaged(abnormalBox, !response.abnormalFindings().isEmpty());
        setVisibleManaged(keyPointsBox, !response.keyPoints().isEmpty());
        setVisibleManaged(valuesRowsBox, !response.values().isEmpty());
        setVisibleManaged(actionsBox, !response.suggestedActions().isEmpty());
    }

    private HBox buildBullet(String text) {
        HBox line = new HBox(8);
        Label icon = new Label("•");
        icon.getStyleClass().add("ait-bullet-dot");
        Label label = new Label(text);
        label.getStyleClass().add("ait-item-text");
        label.setWrapText(true);
        line.getChildren().addAll(icon, label);
        return line;
    }

    private void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "Erreur inconnue" : message);
        alert.showAndWait();
    }
}
