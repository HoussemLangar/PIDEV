package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.SymptomesQuotidiensService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

public class SymptomesQuotidiensViewController implements Initializable {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private ListView<SymptomeOptionView> symptomsListView;
    @FXML
    private DatePicker symptomDatePicker;
    @FXML
    private ComboBox<Integer> intensityCombo;
    @FXML
    private TextField durationField;
    @FXML
    private TextArea notesArea;
    @FXML
    private ComboBox<PeriodOption> periodFilterCombo;
    @FXML
    private Label selectedCountLabel;
    @FXML
    private Label selectedHistoryLabel;
    @FXML
    private Label historyInfoLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private ListView<String> historyListView;

    private final SymptomesQuotidiensService service = new SymptomesQuotidiensService();
    private final ObservableList<SymptomeOptionView> availableSymptoms = FXCollections.observableArrayList();
    private final ObservableList<SymptomesQuotidiensService.SymptomeDailyRow> historyRows = FXCollections.observableArrayList();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();

    private Integer selectedHistoryEntryId;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        symptomDatePicker.setValue(LocalDate.now());

        symptomsListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        symptomsListView.setItems(availableSymptoms);
        symptomsListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(SymptomeOptionView item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String category = item.category().isBlank() ? "Non classe" : item.category();
                    setText(item.name() + "  |  " + category);
                }
            }
        });

        intensityCombo.setItems(FXCollections.observableArrayList(IntStream.rangeClosed(1, 10).boxed().toList()));
        intensityCombo.setValue(5);

        periodFilterCombo.setItems(FXCollections.observableArrayList(
                new PeriodOption("Jour", SymptomesQuotidiensService.PeriodFilter.DAY),
                new PeriodOption("Semaine", SymptomesQuotidiensService.PeriodFilter.WEEK),
                new PeriodOption("Mois", SymptomesQuotidiensService.PeriodFilter.MONTH)
        ));
        periodFilterCombo.getSelectionModel().selectFirst();

        historyListView.setItems(historyItems);

        symptomsListView.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<SymptomeOptionView>) change -> updateSelectedCount());
        symptomDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> refreshHistory());
        periodFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshHistory());
        historyListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> handleHistorySelection(newIndex == null ? -1 : newIndex.intValue()));

        loadSymptoms();
        refreshHistory();
        updateSelectedCount();
        selectedHistoryLabel.setText("Aucune ligne selectionnee.");
    }

    @FXML
    private void handleSaveSymptoms() {
        Integer userId = currentUserId();
        if (userId == null) {
            showFeedback("Utilisateur non connecte.", false);
            return;
        }
        if (!validateRequiredFields(false)) {
            return;
        }
        if (!validateNotesLength()) {
            return;
        }

        List<Integer> selectedSymptomIds = symptomsListView.getSelectionModel()
                .getSelectedItems()
                .stream()
                .map(SymptomeOptionView::id)
                .distinct()
                .toList();

        SymptomesQuotidiensService.SaveResult result = service.saveDailySymptoms(
                userId,
                selectedSymptomIds,
                symptomDatePicker.getValue(),
                intensityCombo.getValue(),
                durationField.getText(),
                notesArea.getText()
        );

        showFeedback(result.message(), result.success());
        if (result.success()) {
            clearFormAfterSave();
            refreshHistory();
        }
    }

    @FXML
    private void handleUpdateSelected() {
        Integer userId = currentUserId();
        if (userId == null) {
            showFeedback("Utilisateur non connecte.", false);
            return;
        }
        if (selectedHistoryEntryId == null) {
            showFeedback("Selectionne d'abord une ligne d'historique a modifier.", false);
            return;
        }
        if (!validateRequiredFields(true)) {
            return;
        }
        if (!validateNotesLength()) {
            return;
        }

        List<Integer> selectedSymptomIds = symptomsListView.getSelectionModel()
                .getSelectedItems()
                .stream()
                .map(SymptomeOptionView::id)
                .distinct()
                .toList();

        if (selectedSymptomIds.size() != 1) {
            showFeedback("Pour modifier une ligne, selectionne exactement 1 symptome.", false);
            return;
        }

        SymptomesQuotidiensService.SaveResult result = service.updateSymptomEntry(
                userId,
                selectedHistoryEntryId,
                selectedSymptomIds.get(0),
                symptomDatePicker.getValue(),
                intensityCombo.getValue(),
                durationField.getText(),
                notesArea.getText()
        );

        showFeedback(result.message(), result.success());
        if (result.success()) {
            Integer entryId = selectedHistoryEntryId;
            refreshHistory();
            selectHistoryEntry(entryId);
        }
    }

    @FXML
    private void handleDeleteSelected() {
        Integer userId = currentUserId();
        if (userId == null) {
            showFeedback("Utilisateur non connecte.", false);
            return;
        }
        if (selectedHistoryEntryId == null) {
            showFeedback("Selectionne d'abord une ligne d'historique a supprimer.", false);
            return;
        }

        SymptomesQuotidiensService.SaveResult result = service.deleteSymptomEntry(userId, selectedHistoryEntryId);
        showFeedback(result.message(), result.success());

        if (result.success()) {
            clearSelectionState();
            refreshHistory();
        }
    }

    @FXML
    private void handleExportHistoryCsv() {
        if (historyRows.isEmpty()) {
            showFeedback("Aucune ligne a exporter pour ce filtre.", false);
            return;
        }

        File file = chooseCsvFile();
        if (file == null) {
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("id,date,symptome,categorie,intensite,duree,notes");
            writer.newLine();

            for (SymptomesQuotidiensService.SymptomeDailyRow row : historyRows) {
                writer.write(row.id() + ","
                        + csv(row.date().toString()) + ","
                        + csv(row.symptomName()) + ","
                        + csv(row.category()) + ","
                        + row.intensity() + ","
                        + csv(row.duration()) + ","
                        + csv(row.notes()));
                writer.newLine();
            }

            showFeedback("Export CSV reussi: " + file.getName(), true);
        } catch (IOException exception) {
            showFeedback("Export CSV impossible: " + exception.getMessage(), false);
        }
    }

    @FXML
    private void handleRefreshSymptoms() {
        loadSymptoms();
        refreshHistory();
        showFeedback("Liste des symptomes actualisee.", true);
    }

    @FXML
    private void handleClearForm() {
        symptomsListView.getSelectionModel().clearSelection();
        intensityCombo.setValue(5);
        durationField.clear();
        notesArea.clear();
        symptomDatePicker.setValue(LocalDate.now());
        historyListView.getSelectionModel().clearSelection();
        clearSelectionState();
        updateSelectedCount();
        showFeedback("Formulaire reinitialise.", true);
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    private void loadSymptoms() {
        List<SymptomesQuotidiensService.SymptomeChoice> rows = service.loadAvailableSymptoms();
        availableSymptoms.setAll(rows.stream()
                .map(row -> new SymptomeOptionView(row.id(), row.nom(), row.categorie()))
                .toList());
    }

    private void refreshHistory() {
        Integer userId = currentUserId();
        if (userId == null) {
            historyRows.clear();
            historyItems.clear();
            historyInfoLabel.setText("Utilisateur non connecte.");
            return;
        }

        LocalDate selectedDate = symptomDatePicker.getValue() == null ? LocalDate.now() : symptomDatePicker.getValue();
        PeriodOption selectedPeriod = periodFilterCombo.getValue();
        SymptomesQuotidiensService.PeriodFilter filter = selectedPeriod == null
                ? SymptomesQuotidiensService.PeriodFilter.DAY
                : selectedPeriod.filter();

        List<SymptomesQuotidiensService.SymptomeDailyRow> rows = service.loadRowsForUser(userId, selectedDate, filter);

        historyRows.setAll(rows);
        historyItems.setAll(rows.stream().map(this::toHistoryLine).toList());

        if (rows.isEmpty()) {
            historyInfoLabel.setText("Aucun symptome pour le filtre " + selectedPeriodLabel() + ".");
        } else {
            historyInfoLabel.setText(rows.size() + " symptome(s) trouves pour " + selectedPeriodLabel() + ".");
        }
    }

    private void handleHistorySelection(int index) {
        if (index < 0 || index >= historyRows.size()) {
            clearSelectionState();
            return;
        }

        SymptomesQuotidiensService.SymptomeDailyRow row = historyRows.get(index);
        selectedHistoryEntryId = row.id();
        selectedHistoryLabel.setText("Selection ID " + row.id() + " | " + row.symptomName() + " | " + DATE_FORMATTER.format(row.date()));

        symptomDatePicker.setValue(row.date());
        intensityCombo.setValue(row.intensity());
        durationField.setText(row.duration());
        notesArea.setText(row.notes());

        selectSymptomInList(row.symptomId());
    }

    private void selectSymptomInList(int symptomId) {
        symptomsListView.getSelectionModel().clearSelection();
        for (int i = 0; i < availableSymptoms.size(); i++) {
            if (availableSymptoms.get(i).id() == symptomId) {
                symptomsListView.getSelectionModel().select(i);
                break;
            }
        }
        updateSelectedCount();
    }

    private void selectHistoryEntry(Integer entryId) {
        if (entryId == null) {
            return;
        }
        for (int i = 0; i < historyRows.size(); i++) {
            if (historyRows.get(i).id() == entryId) {
                historyListView.getSelectionModel().select(i);
                return;
            }
        }
    }

    private String toHistoryLine(SymptomesQuotidiensService.SymptomeDailyRow row) {
        String category = row.category().isBlank() ? "Non classe" : row.category();
        String duration = row.duration().isBlank() ? "-" : row.duration();
        String notes = row.notes().isBlank() ? "-" : row.notes();

        return DATE_FORMATTER.format(row.date())
                + " | " + row.symptomName()
                + " | Cat: " + category
                + " | Intensite: " + row.intensity() + "/10"
                + " | Duree: " + duration
                + " | Notes: " + notes;
    }

    private String selectedPeriodLabel() {
        PeriodOption selected = periodFilterCombo.getValue();
        if (selected == null) {
            return "jour";
        }
        return selected.label().toLowerCase();
    }

    private void clearSelectionState() {
        selectedHistoryEntryId = null;
        selectedHistoryLabel.setText("Aucune ligne selectionnee.");
    }

    private void clearFormAfterSave() {
        symptomsListView.getSelectionModel().clearSelection();
        intensityCombo.setValue(5);
        durationField.clear();
        notesArea.clear();
        symptomDatePicker.setValue(LocalDate.now());
        historyListView.getSelectionModel().clearSelection();
        clearSelectionState();
        updateSelectedCount();
    }

    private boolean validateNotesLength() {
        String notes = notesArea.getText();
        if (notes != null && notes.length() > 250) {
            showFeedback("Le champ notes est optionnel, mais limite a 250 caracteres.", false);
            return false;
        }
        return true;
    }

    private boolean validateRequiredFields(boolean forUpdate) {
        List<SymptomeOptionView> selectedSymptoms = symptomsListView.getSelectionModel().getSelectedItems();
        if (forUpdate) {
            if (selectedSymptoms == null || selectedSymptoms.size() != 1) {
                showFeedback("Pour modifier, selectionne exactement 1 symptome.", false);
                return false;
            }
        } else {
            if (selectedSymptoms == null || selectedSymptoms.isEmpty()) {
                showFeedback("Le champ symptomes est obligatoire.", false);
                return false;
            }
        }

        if (symptomDatePicker.getValue() == null) {
            showFeedback("Le champ date est obligatoire.", false);
            return false;
        }

        if (intensityCombo.getValue() == null) {
            showFeedback("Le champ intensite est obligatoire.", false);
            return false;
        }

        String duration = durationField.getText();
        if (duration == null || duration.trim().isEmpty()) {
            showFeedback("Le champ duree est obligatoire.", false);
            return false;
        }

        return true;
    }

    private void updateSelectedCount() {
        int count = symptomsListView.getSelectionModel().getSelectedItems().size();
        selectedCountLabel.setText(count + " symptome(s) selectionne(s)");
    }

    private Integer currentUserId() {
        User user = AuthSession.getCurrentUser();
        return user == null ? null : user.getId();
    }

    private File chooseCsvFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter historique symptomes");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName("symptomes_historique_" + LocalDate.now() + ".csv");

        Stage stage = (Stage) feedbackLabel.getScene().getWindow();
        return chooser.showSaveDialog(stage);
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        String escaped = safe.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message == null ? "" : message);
        boolean visible = message != null && !message.isBlank();
        feedbackLabel.setVisible(visible);
        feedbackLabel.setManaged(visible);
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
    }

    private record SymptomeOptionView(int id, String name, String category) {
    }

    private record PeriodOption(String label, SymptomesQuotidiensService.PeriodFilter filter) {
        @Override
        public String toString() {
            return label;
        }
    }
}
