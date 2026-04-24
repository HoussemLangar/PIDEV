package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.SymptomesMedecinDashboardService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class SymptomesMedecinDashboardViewController implements Initializable {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    @FXML
    private ComboBox<SymptomesMedecinDashboardService.PatientOption> patientFilterCombo;

    @FXML
    private LineChart<String, Number> evolutionChart;

    @FXML
    private CategoryAxis xAxis;

    @FXML
    private NumberAxis yAxis;

    @FXML
    private GridPane heatmapGrid;

    @FXML
    private ListView<String> alertsListView;

    @FXML
    private Label dashboardInfoLabel;

    private final SymptomesMedecinDashboardService service = new SymptomesMedecinDashboardService();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        patientFilterCombo.setItems(FXCollections.observableArrayList(service.loadPatients()));
        patientFilterCombo.getSelectionModel().selectFirst();
        patientFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshDashboard());

        xAxis.setLabel("Jour");
        yAxis.setLabel("Intensite (0-10)");
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(10);
        yAxis.setTickUnit(1);

        refreshDashboard();
    }

    @FXML
    private void handleRefresh() {
        refreshDashboard();
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    private void refreshDashboard() {
        SymptomesMedecinDashboardService.PatientOption patient = patientFilterCombo.getValue();
        Integer patientId = patient == null ? null : patient.patientId();

        SymptomesMedecinDashboardService.DashboardData data = service.loadDashboard(patientId);
        renderChart(data);
        renderHeatmap(data);
        renderAlerts(data);

        dashboardInfoLabel.setText(
                data.daily().size() + " jour(s) de donnees | " + data.alerts().size() + " alerte(s)"
        );
    }

    private void renderChart(SymptomesMedecinDashboardService.DashboardData data) {
        evolutionChart.getData().clear();

        XYChart.Series<String, Number> intensitySeries = new XYChart.Series<>();
        intensitySeries.setName("Intensite moyenne");

        XYChart.Series<String, Number> countSeries = new XYChart.Series<>();
        countSeries.setName("Nombre de symptomes");

        for (SymptomesMedecinDashboardService.DailyPoint point : data.daily()) {
            String label = DATE_FORMATTER.format(point.day());
            intensitySeries.getData().add(new XYChart.Data<>(label, point.avgIntensity()));
            countSeries.getData().add(new XYChart.Data<>(label, point.count()));
        }

        evolutionChart.getData().add(intensitySeries);
        evolutionChart.getData().add(countSeries);
    }

    private void renderHeatmap(SymptomesMedecinDashboardService.DashboardData data) {
        heatmapGrid.getChildren().clear();

        int col = 0;
        int row = 0;
        for (SymptomesMedecinDashboardService.HeatmapCell cell : data.heatmap()) {
            Label tile = new Label(
                    DATE_FORMATTER.format(cell.date()) + "\n" + (cell.count() == 0 ? "-" : String.format("%.1f/10", cell.avgIntensity()))
            );
            tile.getStyleClass().add("med-heatmap-cell");
            tile.setStyle("-fx-background-color: " + colorFor(cell.avgIntensity(), cell.count()) + ";");
            tile.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            GridPane.setHgrow(tile, Priority.ALWAYS);
            GridPane.setVgrow(tile, Priority.ALWAYS);

            heatmapGrid.add(tile, col, row);
            col++;
            if (col == 8) {
                col = 0;
                row++;
            }
        }

        Region spacer = new Region();
        spacer.setMinHeight(2);
        heatmapGrid.add(spacer, 0, row + 1);
    }

    private void renderAlerts(SymptomesMedecinDashboardService.DashboardData data) {
        if (data.alerts().isEmpty()) {
            alertsListView.setItems(FXCollections.observableArrayList("Aucun pic anormal detecte sur la periode."));
            return;
        }

        alertsListView.setItems(FXCollections.observableArrayList(
                data.alerts().stream()
                        .map(alert -> String.format(
                                "%s | %d symptomes | intensite %.1f/10 (seuil %.1f)",
                                DATE_FORMATTER.format(alert.date()),
                                alert.count(),
                                alert.avgIntensity(),
                                alert.threshold()
                        ))
                        .toList()
        ));
    }

    private String colorFor(double avgIntensity, int count) {
        if (count == 0 || avgIntensity <= 0.0) {
            return "#f4f7fb";
        }
        if (avgIntensity < 3.0) {
            return "#d9f1dd";
        }
        if (avgIntensity < 5.0) {
            return "#bfe6c6";
        }
        if (avgIntensity < 7.0) {
            return "#f9d18f";
        }
        return "#f2a47f";
    }
}
