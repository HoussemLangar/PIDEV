package com.santea.controller;

import com.santea.navigation.AppNavigator;
import com.santea.repository.SanteQuotidienneAdminRepository;
import com.santea.service.SanteQuotidienneAdminService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class SanteQuotidienneAdminViewController implements Initializable {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label totalEntriesLabel;
    @FXML private Label activeUsersLabel;
    @FXML private Label totalPatientsLabel;
    @FXML private Label patientsWithoutEntriesLabel;
    @FXML private Label usagePercentLabel;
    @FXML private Label entriesPercentLabel;
    @FXML private Label weekPercentLabel;
    @FXML private Label pageInfoLabel;
    @FXML private Label entriesInfoLabel;
    @FXML private Label autoRefreshStatusLabel;
    @FXML private ScrollPane pageScrollPane;

    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private Button refreshButton;
    @FXML private Button exportSummaryButton;
    @FXML private Button exportDetailsButton;

    @FXML private TableView<Object[]> dataTable;
    @FXML private TableColumn<Object[], String> emailColumn;
    @FXML private TableColumn<Object[], Integer> entryCountColumn;

    @FXML private TableView<Object[]> entriesTable;
    @FXML private TableColumn<Object[], String> detailNomColumn;
    @FXML private TableColumn<Object[], String> detailPrenomColumn;
    @FXML private TableColumn<Object[], String> detailEmailColumn;
    @FXML private TableColumn<Object[], String> detailDateColumn;

    @FXML private PieChart usagePieChart;

    private final SanteQuotidienneAdminService service = new SanteQuotidienneAdminService();
    private final ObservableList<Object[]> aggregatedRows = FXCollections.observableArrayList();
    private final ObservableList<Object[]> detailedRows = FXCollections.observableArrayList();
    private Timeline autoRefreshTimeline;

    @Override
    public void initialize(java.net.URL location, ResourceBundle resources) {
        try {
            System.out.println("🔄 Initialisation SanteQuotidienneAdminViewController...");
            setupSummaryTable();
            System.out.println("✅ Summary table configurée");
            setupDetailTable();
            System.out.println("✅ Detail table configurée");
            setupEventHandlers();
            System.out.println("✅ Event handlers configurés");
            refreshData(false);
            System.out.println("✅ Données actualisées");
            startAutoRefresh();
            System.out.println("✅ Auto refresh démarré");
            enablePageScrolling();
        } catch (Exception e) {
            System.err.println("❌ Erreur initialization: " + e.getMessage());
            e.printStackTrace();
        }
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

    private void setupSummaryTable() {
        if (emailColumn != null) {
            emailColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(valueAsString(cd.getValue(), 0)));
        }
        if (entryCountColumn != null) {
            entryCountColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(valueAsInt(cd.getValue(), 3)));
        }
    }

    private void setupDetailTable() {
        if (detailNomColumn != null) {
            detailNomColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(valueAsString(cd.getValue(), 3)));
        }
        if (detailPrenomColumn != null) {
            detailPrenomColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(valueAsString(cd.getValue(), 2)));
        }
        if (detailEmailColumn != null) {
            detailEmailColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(valueAsString(cd.getValue(), 1)));
        }
        if (detailDateColumn != null) {
            detailDateColumn.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(valueAsDate(cd.getValue(), 4)));
        }
    }

    private void setupEventHandlers() {
        if (searchButton != null) {
            searchButton.setOnAction(event -> applyFilter());
        }
        if (refreshButton != null) {
            refreshButton.setOnAction(event -> handleRefresh());
        }
        if (exportSummaryButton != null) {
            exportSummaryButton.setOnAction(event -> handleExportSummary());
        }
        if (exportDetailsButton != null) {
            exportDetailsButton.setOnAction(event -> handleExportDetails());
        }
        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());
        }
    }

    private void refreshData(boolean showFeedback) {
        try {
            loadStatistics();
            loadTables();
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

    private void loadStatistics() {
        SanteQuotidienneAdminRepository.StatisticsData stats = service.getStatistics();

        if (totalEntriesLabel != null) {
            totalEntriesLabel.setText(String.valueOf(stats.totalEntries));
        }
        if (activeUsersLabel != null) {
            activeUsersLabel.setText(String.valueOf(stats.usersWithEntries));
        }
        if (totalPatientsLabel != null) {
            totalPatientsLabel.setText(String.valueOf(stats.totalPatientUsers));
        }
        if (patientsWithoutEntriesLabel != null) {
            patientsWithoutEntriesLabel.setText(String.valueOf(stats.patientsWithoutEntries));
        }

        // Calcul des pourcentages
        double usagePercent = stats.totalPatientUsers > 0 
            ? (stats.usersWithEntries * 100.0 / stats.totalPatientUsers) 
            : 0;
        double entriesPercent = stats.totalEntries > 0 
            ? (stats.todayEntries * 100.0 / stats.totalEntries) 
            : 0;
        double weekPercent = stats.totalEntries > 0 
            ? (stats.weekEntries * 100.0 / stats.totalEntries) 
            : 0;

        if (usagePercentLabel != null) {
            usagePercentLabel.setText(String.format("%.1f%%", usagePercent));
        }
        if (entriesPercentLabel != null) {
            entriesPercentLabel.setText(String.format("%.1f%%", entriesPercent));
        }
        if (weekPercentLabel != null) {
            weekPercentLabel.setText(String.format("%.1f%%", weekPercent));
        }

        if (usagePieChart != null) {
            usagePieChart.getData().setAll(
                new PieChart.Data("Patients utilisant Santé Quotidienne", stats.usersWithEntries),
                new PieChart.Data("Patients sans entrée", stats.patientsWithoutEntries)
            );
            usagePieChart.setLabelsVisible(true);
            usagePieChart.setLegendVisible(true);
            usagePieChart.setClockwise(true);
        }
    }

    private void loadTables() {
        aggregatedRows.setAll(service.getUserStatistics());
        detailedRows.setAll(service.getAllData());
        applyFilter();
    }

    private void applyFilter() {
        String filter = searchField == null ? "" : searchField.getText();
        String normalized = filter == null ? "" : filter.trim().toLowerCase();

        ObservableList<Object[]> filteredAggregatedRows = FXCollections.observableArrayList();
        for (Object[] row : aggregatedRows) {
            String email = valueAsString(row, 0).toLowerCase();
            if (normalized.isBlank() || email.contains(normalized)) {
                filteredAggregatedRows.add(row);
            }
        }
        dataTable.setItems(filteredAggregatedRows);

        ObservableList<Object[]> filteredDetailedRows = FXCollections.observableArrayList();
        for (Object[] row : detailedRows) {
            String email = valueAsString(row, 1).toLowerCase();
            if (normalized.isBlank() || email.contains(normalized)) {
                filteredDetailedRows.add(row);
            }
        }
        entriesTable.setItems(filteredDetailedRows);

        if (pageInfoLabel != null) {
            pageInfoLabel.setText(filteredAggregatedRows.size() + " utilisateur(s) affiché(s)");
        }
        if (entriesInfoLabel != null) {
            entriesInfoLabel.setText(filteredDetailedRows.size() + " entrée(s) détaillée(s)");
        }
    }

    private void startAutoRefresh() {
        autoRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(15), event -> refreshData(false)));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    private String valueAsString(Object[] row, int index) {
        Object value = row != null && row.length > index ? row[index] : null;
        return value == null ? "" : value.toString();
    }

    private Integer valueAsInt(Object[] row, int index) {
        Object value = row != null && row.length > index ? row[index] : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String valueAsDate(Object[] row, int index) {
        Object value = row != null && row.length > index ? row[index] : null;
        if (value instanceof LocalDate localDate) {
            return DATE_FORMATTER.format(localDate);
        }
        return value == null ? "" : value.toString();
    }

    @FXML
    private void handleExportSummary() {
        exportSummaryTable();
    }

    @FXML
    private void handleExportDetails() {
        exportDetailsTable();
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

    private void exportSummaryTable() {
        try {
            File file = chooseCsvFile("Exporter le tableau 1", "sante_quotidienne_tableau_1_" + LocalDate.now() + ".csv");
            if (file == null) {
                return;
            }

            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
                writer.write("Email utilisateur,Nombre de fois enregistre");
                writer.newLine();

                for (Object[] row : dataTable.getItems()) {
                    writer.write(csv(valueAsString(row, 0)) + "," + csv(String.valueOf(valueAsInt(row, 3))));
                    writer.newLine();
                }
            }

            showAlert(Alert.AlertType.INFORMATION, "Succès", "Le tableau 1 a été exporté en CSV avec succès.");
        } catch (Exception exception) {
            exception.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur lors de l'export du tableau 1 : " + exception.getMessage());
        }
    }

    private void exportDetailsTable() {
        try {
            File file = chooseCsvFile("Exporter le tableau 2", "sante_quotidienne_tableau_2_" + LocalDate.now() + ".csv");
            if (file == null) {
                return;
            }

            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
                writer.write("Nom,Prenom,Email,Date enregistrement");
                writer.newLine();

                for (Object[] row : entriesTable.getItems()) {
                    writer.write(
                        csv(valueAsString(row, 3)) + "," +
                        csv(valueAsString(row, 2)) + "," +
                        csv(valueAsString(row, 1)) + "," +
                        csv(valueAsDate(row, 4))
                    );
                    writer.newLine();
                }
            }

            showAlert(Alert.AlertType.INFORMATION, "Succès", "Le tableau 2 a été exporté en CSV avec succès.");
        } catch (Exception exception) {
            exception.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Erreur", "Erreur lors de l'export du tableau 2 : " + exception.getMessage());
        }
    }

    private File chooseCsvFile(String title, String defaultName) {
        Button sourceButton = exportSummaryButton != null ? exportSummaryButton : exportDetailsButton;
        if (sourceButton == null || sourceButton.getScene() == null) {
            throw new IllegalStateException("Impossible d'accéder à la fenêtre.");
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName(defaultName);
        return fileChooser.showSaveDialog(sourceButton.getScene().getWindow());
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
