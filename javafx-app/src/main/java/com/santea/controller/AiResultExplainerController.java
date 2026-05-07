package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AiToolsService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AiResultExplainerController extends AiToolBaseController {
    private final AiToolsService aiToolsService = new AiToolsService();

    @FXML
    private TextField testNameField;

    @FXML
    private TextField valueField;

    @FXML
    private TextField unitField;

    @FXML
    private TextField referenceRangeField;

    @FXML
    private VBox resultsBox;

    @FXML
    private Label plainExplanationLabel;

    @FXML
    private VBox meaningBox;

    @FXML
    private VBox nextStepsBox;

    @FXML
    private Label warningLabel;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        if (!guardAiToolsAccess()) {
            return;
        }
        setVisibleManaged(resultsBox, false);
    }

    @FXML
    private void handleBack() {
        AppNavigator.showAiToolsPage();
    }

    @FXML
    private void handleExplain() {
        AiToolsService.ResultExplanationResponse response = aiToolsService.explainResult(
                testNameField.getText(),
                valueField.getText(),
                unitField.getText(),
                referenceRangeField.getText()
        );
        render(response);
    }

    private void render(AiToolsService.ResultExplanationResponse response) {
        plainExplanationLabel.setText(response.plainExplanation());
        warningLabel.setText(response.warning());

        meaningBox.getChildren().clear();
        for (String row : response.possibleMeaning()) {
            HBox line = new HBox(8);
            line.getStyleClass().add("ait-meaning-row");
            Label bullet = new Label("◦");
            bullet.getStyleClass().add("ait-bullet-dot");
            Label text = new Label(row);
            text.getStyleClass().add("ait-item-text");
            text.setWrapText(true);
            line.getChildren().addAll(bullet, text);
            meaningBox.getChildren().add(line);
        }

        nextStepsBox.getChildren().clear();
        int step = 1;
        for (String row : response.nextSteps()) {
            HBox line = new HBox(8);
            line.getStyleClass().add("ait-next-row");
            Label order = new Label(String.valueOf(step++));
            order.getStyleClass().add("ait-order-dot");
            Label text = new Label(row);
            text.getStyleClass().add("ait-item-text");
            text.setWrapText(true);
            line.getChildren().addAll(order, text);
            nextStepsBox.getChildren().add(line);
        }

        setVisibleManaged(resultsBox, true);
    }

    private void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
