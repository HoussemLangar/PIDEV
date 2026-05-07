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
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public class AiNutritionPlannerController extends AiToolBaseController {
    private final AiToolsService aiToolsService = new AiToolsService();

    @FXML
    private TextField goalField;

    @FXML
    private ComboBox<String> dietStyleCombo;

    @FXML
    private Spinner<Integer> daysSpinner;

    @FXML
    private TextField allergiesField;

    @FXML
    private VBox resultsBox;

    @FXML
    private Label overviewLabel;

    @FXML
    private TilePane daysGrid;

    @FXML
    private VBox tipsBox;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        if (!guardAiToolsAccess()) {
            return;
        }
        dietStyleCombo.setItems(FXCollections.observableArrayList(
                "standard",
                "vegetarien",
                "vegetalien",
                "mediterraneen",
                "sans-gluten"
        ));
        dietStyleCombo.getSelectionModel().select("standard");
        daysSpinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(1, 14, 7));
        setVisibleManaged(resultsBox, false);
    }

    @FXML
    private void handleBack() {
        AppNavigator.showAiToolsPage();
    }

    @FXML
    private void handleGenerate() {
        AiToolsService.NutritionPlanResponse response = aiToolsService.generateNutritionPlan(
                goalField.getText(),
                dietStyleCombo.getValue(),
                allergiesField.getText(),
                daysSpinner.getValue()
        );
        render(response);
    }

    private void render(AiToolsService.NutritionPlanResponse response) {
        overviewLabel.setText(response.overview());

        daysGrid.getChildren().clear();
        for (AiToolsService.NutritionDay day : response.days()) {
            VBox dayCard = new VBox(8);
            dayCard.getStyleClass().add("ait-day-card");

            Label dayTitle = new Label("Jour " + day.day());
            dayTitle.getStyleClass().add("ait-day-title");

            VBox meals = new VBox(6);
            meals.getChildren().add(buildMeal("Petit-dejeuner", day.breakfast()));
            meals.getChildren().add(buildMeal("Dejeuner", day.lunch()));
            meals.getChildren().add(buildMeal("Diner", day.dinner()));
            meals.getChildren().add(buildMeal("Collation", day.snack()));

            dayCard.getChildren().addAll(dayTitle, meals);
            daysGrid.getChildren().add(dayCard);
        }

        tipsBox.getChildren().clear();
        for (String tip : response.tips()) {
            HBox row = new HBox(8);
            Label bullet = new Label("•");
            bullet.getStyleClass().add("ait-bullet-dot");
            Label text = new Label(tip);
            text.getStyleClass().add("ait-item-text");
            text.setWrapText(true);
            row.getChildren().addAll(bullet, text);
            tipsBox.getChildren().add(row);
        }

        setVisibleManaged(resultsBox, true);
    }

    private VBox buildMeal(String label, String value) {
        VBox box = new VBox(2);
        box.getStyleClass().add("ait-meal-box");
        Label title = new Label(label);
        title.getStyleClass().add("ait-meal-label");
        Label body = new Label(value);
        body.getStyleClass().add("ait-meal-value");
        body.setWrapText(true);
        box.getChildren().addAll(title, body);
        return box;
    }

    private void setVisibleManaged(javafx.scene.Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
