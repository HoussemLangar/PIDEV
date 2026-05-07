package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.AiToolsService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class AiWorkoutPlannerController extends AiToolBaseController {
    private final AiToolsService aiToolsService = new AiToolsService();

    @FXML
    private TextField goalField;

    @FXML
    private ComboBox<String> levelCombo;

    @FXML
    private Spinner<Integer> daysSpinner;

    @FXML
    private Spinner<Integer> minutesSpinner;

    @FXML
    private TextField constraintsField;

    @FXML
    private VBox resultsBox;

    @FXML
    private Label overviewLabel;

    @FXML
    private VBox sessionsBox;

    @FXML
    private VBox safetyBox;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        if (!guardAiToolsAccess()) {
            return;
        }
        levelCombo.setItems(FXCollections.observableArrayList("debutant", "intermediaire", "avance"));
        levelCombo.getSelectionModel().select("debutant");
        daysSpinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(1, 7, 3));
        minutesSpinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(10, 120, 30));
        setVisibleManaged(resultsBox, false);
    }

    @FXML
    private void handleBack() {
        AppNavigator.showAiToolsPage();
    }

    @FXML
    private void handleGenerate() {
        AiToolsService.WorkoutPlanResponse response = aiToolsService.generateWorkoutPlan(
                goalField.getText(),
                levelCombo.getValue(),
                daysSpinner.getValue(),
                minutesSpinner.getValue(),
                constraintsField.getText()
        );
        render(response);
    }

    private void render(AiToolsService.WorkoutPlanResponse response) {
        overviewLabel.setText(response.overview());

        sessionsBox.getChildren().clear();
        int index = 1;
        for (AiToolsService.WorkoutSession session : response.sessions()) {
            VBox card = new VBox(7);
            card.getStyleClass().add("ait-session-card");

            HBox head = new HBox(10);
            Label num = new Label(String.valueOf(index++));
            num.getStyleClass().add("ait-session-num");
            Label day = new Label(session.day());
            day.getStyleClass().add("ait-session-day");
            Label meta = new Label(session.focus() + "  |  " + session.durationMin() + " min");
            meta.getStyleClass().add("ait-session-meta");
            head.getChildren().addAll(num, day, meta);

            VBox stepsBox = new VBox(4);
            for (String step : session.plan()) {
                HBox row = new HBox(8);
                Label bullet = new Label("▸");
                bullet.getStyleClass().add("ait-bullet-dot");
                Label text = new Label(step);
                text.getStyleClass().add("ait-item-text");
                row.getChildren().addAll(bullet, text);
                stepsBox.getChildren().add(row);
            }

            card.getChildren().addAll(head, stepsBox);
            sessionsBox.getChildren().add(card);
        }

        safetyBox.getChildren().clear();
        for (String safety : response.safety()) {
            HBox row = new HBox(8);
            row.getStyleClass().add("ait-safety-row");
            Label icon = new Label("!");
            icon.getStyleClass().add("ait-order-dot");
            Label text = new Label(safety);
            text.getStyleClass().add("ait-item-text");
            text.setWrapText(true);
            row.getChildren().addAll(icon, text);
            safetyBox.getChildren().add(row);
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
