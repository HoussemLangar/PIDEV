package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.repository.SanteQuotidienneAdminRepository;
import com.santea.service.SanteQuotidienneAdminService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class SanteQuotidienneAdminViewController implements Initializable {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label totalEntriesLabel;
    @FXML private Label activeUsersLabel;
    @FXML private Label totalPatientsLabel;
    @FXML private Label patientsWithoutEntriesLabel;
    @FXML private Label pageInfoLabel;
    @FXML private Label autoRefreshStatusLabel;

    @FXML private TextField searchField;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private Button searchButton;
    @FXML private Button exportButton;
    @FXML private Button refreshButton;

    @FXML private TableView<Object[]> dataTable;
    @FXML private TableColumn<Object[], String> emailColumn;
    @FXML private TableColumn<Object[], String> prenomColumn;
    @FXML private TableColumn<Object[], String> nomColumn;
    @FXML private TableColumn<Object[], String> dateColumn;
    @FXML private TableColumn<Object[], Double> poidsColumn;
    @FXML private TableColumn<Object[], Integer> sommeilColumn;
    @FXML private TableColumn<Object[], String> humeurColumn;
    @FXML private TableColumn<Object[], Integer> activiteColumn;
    @FXML private TableColumn<Object[], String> alimentationColumn;
    @FXML private TableColumn<Object[], Double> eauColumn;
    @FXML private TableColumn<Object[], String> tensionColumn;

    @FXML private TableView<Object[]> userStatsTable;
    @FXML private TableColumn<Object[], String> statsEmailColumn;
    @FXML private TableColumn<Object[], String> statsPrenomColumn;
    @FXML private TableColumn<Object[], String> statsNomColumn;
    @FXML private TableColumn<Object[], Integer> statsCountColumn;

    @FXML private PieChart usagePieChart;

    private final SanteQuotidienneAdminService service = new SanteQuotidienneAdminService();
    private Timeline autoRefreshTimeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupMainTable();
        setupUserStatsTable();
        setupEventHandlers();
        refreshData(false);
        startAutoRefresh();
    }

    private void setupMainTable() {
        emailColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 1));
        prenomColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 2));
        nomColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 3));
        dateColumn.setCellValueFactory(cd -> {
            LocalDate date = cd.getValue()[4] instanceof LocalDate ? (LocalDate) cd.getValue()[4] : null;
            return new ReadOnlyObjectWrapper<>(date == null ? "" : DATE_FORMATTER.format(date));
        });
        poidsColumn.setCellValueFactory(cd -> wrapDouble(cd.getValue(), 5));
        sommeilColumn.setCellValueFactory(cd -> wrapInteger(cd.getValue(), 6));
        humeurColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 7));
        activiteColumn.setCellValueFactory(cd -> wrapInteger(cd.getValue(), 8));
        alimentationColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 9));
        eauColumn.setCellValueFactory(cd -> wrapDouble(cd.getValue(), 10));
        tensionColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 11));
    }

    private void setupUserStatsTable() {
        statsEmailColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 0));
        statsPrenomColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 1));
        statsNomColumn.setCellValueFactory(cd -> wrapString(cd.getValue(), 2));
        statsCountColumn.setCellValueFactory(cd -> wrapInteger(cd.getValue(), 3));
    }

    private void setupEventHandlers() {
        if (searchButton != null) {
            searchButton.setOnAction(event -> handleSearch());
        }
        if (refreshButton != null) {
            refreshButton.setOnAction(event -> handleRefresh());
        }
        if (exportButton != null) {
            exportButton.setOnAction(event -> handleExport());
        }
        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> handleSearch());
        }
        if (startDatePicker != null) {
            startDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> handleSearch());
        }
        if (endDatePicker != null) {
            endDatePicker.valueProperty().addListener((observable, oldValue, newValue) -> handleSearch());
        }
    }

    private void refreshData(boolean showFeedback) {
        try {
            loadDetailedEntries();
            loadStatistics();
            loadUserStatistics();
            if (autoRefreshStatusLabel != null) {
                autoRefreshStatusLabel.setText("Actualisation auto active (toutes les 15s)");
            }
            if (showFeedback) {
                showAlert(Alert.AlertType.INFORMATION, "Actualisation", "Les données Santé Quotidienne ont été actualisées.");
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            if (autoRefreshStatusLabel != null) {
                autoRefreshStatusLabel.setText("Erreur lors de l'actualisation");
            }
            showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible de charger les données Santé Quotidienne : " + exception.getMessage());
        }
    }

    private void loadDetailedEntries() {
        String searchTerm = searchField != null ? searchField.getText() : "";
        LocalDate startDate = startDatePicker != null ? startDatePicker.getValue() : null;
        LocalDate endDate = endDatePicker != null ? endDatePicker.getValue() : null;

        dataTable.setItems(service.getFilteredData(searchTerm, startDate, endDate));
        if (pageInfoLabel != null) {
            pageInfoLabel.setText(dataTable.getItems().size() + " entrée(s) affichée(s)");
        }
    }

    private void loadStatistics() {
        SanteQuotidienneAdminRepository.StatisticsData stats = service.getStatistics();

        totalEntriesLabel.setText(String.valueOf(stats.totalEntries));
        activeUsersLabel.setText(String.valueOf(stats.usersWithEntries));
        totalPatientsLabel.setText(String.valueOf(stats.totalPatientUsers));
        patientsWithoutEntriesLabel.setText(String.valueOf(stats.patientsWithoutEntries));

        usagePieChart.getData().setAll(
            new PieChart.Data("Patients utilisant Santé Quotidienne", stats.usersWithEntries),
            new PieChart.Data("Patients sans entrée", stats.patientsWithoutEntries)
        );
        usagePieChart.setLabelsVisible(true);
        usagePieChart.setLegendVisible(true);
        usagePieChart.setClockwise(true);
    }

    private void loadUserStatistics() {
        userStatsTable.setItems(service.getUserStatistics());
    }

    private void startAutoRefresh() {
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(15), event -> refreshData(false)));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    private ReadOnlyObjectWrapper<String> wrapString(Object[] row, int index) {
        Object value = row != null && row.length > index ? row[index] : null;
        return new ReadOnlyObjectWrapper<>(value == null ? "" : value.toString());
    }

    private ReadOnlyObjectWrapper<Double> wrapDouble(Object[] row, int index) {
        Object value = row != null && row.length > index ? row[index] : null;
        if (value instanceof Number number) {
            return new ReadOnlyObjectWrapper<>(number.doubleValue());
        }
        return new ReadOnlyObjectWrapper<>(0.0);
    }

    private ReadOnlyObjectWrapper<Integer> wrapInteger(Object[] row, int index) {
        Object value = row != null && row.length > index ? row[index] : null;
        if (value instanceof Number number) {
            return new ReadOnlyObjectWrapper<>(number.intValue());
        }
        return new ReadOnlyObjectWrapper<>(0);
    }

    @FXML
    private void handleSearch() {
        loadDetailedEntries();
    }

    @FXML
    private void handleExport() {
        try {
            if (exportButton == null || exportButton.getScene() == null) {
                showAlert(Alert.AlertType.ERROR, "Erreur", "Impossible d'accéder à la fenêtre.");
                return;
            }

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Exporter les données Santé Quotidienne");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
            fileChooser.setInitialFileName("sante_quotidienne_admin_" + LocalDate.now() + ".csv");

            File file = fileChooser.showSaveDialog(exportButton.getScene().getWindow());
            if (file == null) {
                return;
            }

            boolean success = service.exportToCSV(file, startDatePicker.getValue(), endDatePicker.getValue());
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Succès", "Export CSV effectué avec succès.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Erreur", "L'export CSV a échoué.");
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur lors de l'export : " + exception.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        refreshData(true);
    }

    @FXML
    private void handleGoBack() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
        }
        AppNavigator.showAdminDashboard();
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
