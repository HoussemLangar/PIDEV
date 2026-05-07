package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.service.SymptomesListeAdminService;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class SymptomesListeAdminViewController implements Initializable {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private TextField nomField;
    @FXML private TextField categorieField;
    @FXML private DatePicker createdAtPicker;
    @FXML private TextField searchField;
    @FXML private DatePicker filterFromPicker;
    @FXML private DatePicker filterToPicker;
    @FXML private Label feedbackLabel;
    @FXML private Label tableInfoLabel;
    @FXML private ScrollPane pageScrollPane;

    @FXML private Button addButton;
    @FXML private Button updateButton;
    @FXML private Button deleteButton;

    @FXML private TableView<SymptomesListeAdminService.SymptomRow> symptomsTable;
    @FXML private TableColumn<SymptomesListeAdminService.SymptomRow, Integer> idColumn;
    @FXML private TableColumn<SymptomesListeAdminService.SymptomRow, String> nomColumn;
    @FXML private TableColumn<SymptomesListeAdminService.SymptomRow, String> categorieColumn;
    @FXML private TableColumn<SymptomesListeAdminService.SymptomRow, String> createdAtColumn;

    @FXML private PieChart todayUsagePieChart;
    @FXML private Label patientsRegistrationLabel;

    private final SymptomesListeAdminService service = new SymptomesListeAdminService();
    private final ObservableList<SymptomesListeAdminService.SymptomRow> tableRows = FXCollections.observableArrayList();
    private Integer selectedId;
    private String activeKeyword = "";
    private LocalDate activeFromDate;
    private LocalDate activeToDate;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        createdAtPicker.setValue(LocalDate.now());
        refreshAll();
        enablePageScrolling();
    }

    private void enablePageScrolling() {
        if (pageScrollPane == null) {
            return;
        }
        // Enable native scrolling with proper settings
        pageScrollPane.setFitToWidth(true);
        pageScrollPane.setPannable(true);
        pageScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
    }

    private void setupTable() {
        idColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().id()));
        nomColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().nom()));
        categorieColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().categorie()));
        createdAtColumn.setCellValueFactory(data -> {
            LocalDateTime value = data.getValue().createdAt();
            return new ReadOnlyObjectWrapper<>(value == null ? "" : DATE_FORMATTER.format(value));
        });

        symptomsTable.setItems(tableRows);
        symptomsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                clearSelection();
                return;
            }
            selectedId = newValue.id();
            nomField.setText(newValue.nom());
            categorieField.setText(newValue.categorie());
            createdAtPicker.setValue(newValue.createdAt() == null ? LocalDate.now() : newValue.createdAt().toLocalDate());
            showFeedback("Symptome ID " + selectedId + " selectionne.", true);
        });
    }

    @FXML
    private void handleAddSymptom() {
        SymptomesListeAdminService.ActionResult result = service.createSymptom(
                nomField.getText(),
                categorieField.getText(),
                createdAtPicker.getValue()
        );
        showFeedback(result.message(), result.success());
        if (result.success()) {
            refreshAll();
            clearForm();
        }
    }

    @FXML
    private void handleUpdateSymptom() {
        SymptomesListeAdminService.ActionResult result = service.updateSymptom(
                selectedId,
                nomField.getText(),
                categorieField.getText(),
                createdAtPicker.getValue()
        );
        showFeedback(result.message(), result.success());
        if (result.success()) {
            refreshAll();
            clearForm();
        }
    }

    @FXML
    private void handleDeleteSymptom() {
        SymptomesListeAdminService.ActionResult result = service.deleteSymptom(selectedId);
        showFeedback(result.message(), result.success());
        if (result.success()) {
            refreshAll();
            clearForm();
        }
    }

    @FXML
    private void handleRefresh() {
        refreshAll();
        showFeedback("Donnees actualisees.", true);
    }

    @FXML
    private void handleApplyFilter() {
        activeKeyword = searchField == null ? "" : searchField.getText();
        activeFromDate = filterFromPicker == null ? null : filterFromPicker.getValue();
        activeToDate = filterToPicker == null ? null : filterToPicker.getValue();

        if (activeFromDate != null && activeToDate != null && activeFromDate.isAfter(activeToDate)) {
            showFeedback("Filtre invalide: la date debut doit etre <= date fin.", false);
            return;
        }

        refreshAll();
        showFeedback("Filtre applique.", true);
    }

    @FXML
    private void handleClearFilter() {
        activeKeyword = "";
        activeFromDate = null;
        activeToDate = null;
        if (searchField != null) {
            searchField.clear();
        }
        if (filterFromPicker != null) {
            filterFromPicker.setValue(null);
        }
        if (filterToPicker != null) {
            filterToPicker.setValue(null);
        }
        refreshAll();
        showFeedback("Filtre reinitialise.", true);
    }

    @FXML
    private void handleExportCsv() {
        java.util.List<SymptomesListeAdminService.SymptomRow> exportRows =
                service.findFilteredSymptoms(activeKeyword, activeFromDate, activeToDate);
        if (exportRows.isEmpty()) {
            showFeedback("Aucune ligne a exporter.", false);
            return;
        }

        File target = chooseCsvFile();
        if (target == null) {
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(target.toPath(), StandardCharsets.UTF_8)) {
            writer.write("id,nom,categorie,created_at");
            writer.newLine();
            for (SymptomesListeAdminService.SymptomRow row : exportRows) {
                writer.write(row.id() + ","
                        + csv(row.nom()) + ","
                        + csv(row.categorie()) + ","
                        + csv(row.createdAt() == null ? "" : row.createdAt().toString()));
                writer.newLine();
            }
            showFeedback("Export CSV reussi: " + target.getName(), true);
        } catch (IOException exception) {
            showFeedback("Export CSV impossible: " + exception.getMessage(), false);
        }
    }

    @FXML
    private void handleClearForm() {
        clearForm();
        showFeedback("Formulaire vide.", true);
    }

    @FXML
    private void handleGoBack() {
        AppNavigator.showAdminDashboard();
    }

    private void refreshAll() {
        tableRows.setAll(service.findFilteredSymptoms(activeKeyword, activeFromDate, activeToDate));
        if (tableInfoLabel != null) {
            tableInfoLabel.setText(tableRows.size() + " symptome(s) dans la liste.");
        }
        loadTodayPieChart();
        loadRegistrationStats();
    }

    private void loadRegistrationStats() {
        SymptomesListeAdminService.RegistrationStats stats = service.getRegistrationStats();
        if (patientsRegistrationLabel != null) {
            String label = String.format("%d / %d patients (%.1f%%) ont enregistre des symptomes quotidiens",
                    stats.withSymptoms(), stats.totalPatients(),
                    stats.totalPatients() > 0 ? (stats.withSymptoms() * 100.0 / stats.totalPatients()) : 0);
            patientsRegistrationLabel.setText(label);
        }
    }

    private void loadTodayPieChart() {
        if (todayUsagePieChart == null) {
            return;
        }

        ObservableList<PieChart.Data> chartData = FXCollections.observableArrayList();
        for (SymptomesListeAdminService.PieStat stat : service.loadTodayUsageStats()) {
            chartData.add(new PieChart.Data(stat.label(), stat.count()));
        }

        todayUsagePieChart.setData(chartData);
        todayUsagePieChart.setLegendVisible(true);
        todayUsagePieChart.setLabelsVisible(true);
        todayUsagePieChart.setClockwise(true);
    }

    private void clearForm() {
        nomField.clear();
        categorieField.clear();
        createdAtPicker.setValue(LocalDate.now());
        symptomsTable.getSelectionModel().clearSelection();
        clearSelection();
    }

    private void clearSelection() {
        selectedId = null;
    }

    private void showFeedback(String message, boolean success) {
        if (feedbackLabel == null) {
            return;
        }
        feedbackLabel.setText(message == null ? "" : message);
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        feedbackLabel.setVisible(message != null && !message.isBlank());
        feedbackLabel.setManaged(message != null && !message.isBlank());
    }

    private File chooseCsvFile() {
        if (feedbackLabel == null || feedbackLabel.getScene() == null) {
            return null;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter la liste des symptomes");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("symptomes_liste_" + LocalDate.now() + ".csv");
        Stage stage = (Stage) feedbackLabel.getScene().getWindow();
        return chooser.showSaveDialog(stage);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
