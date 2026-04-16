package com.santea.controller;

import com.santea.model.SanteDataSource;
import com.santea.model.SanteQuotidienne;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.SanteQuotidienneService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

public class SanteQuotidienneViewController implements Initializable {
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^\\d+(?:[\\.,]\\d+)?$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern LETTERS_PATTERN = Pattern.compile("^[\\p{L}\\s'-]+$");

    @FXML
    private TextField poidsField;
    @FXML
    private TextField tailleField;
    @FXML
    private TextField tensionField;
    @FXML
    private TextField sommeilField;
    @FXML
    private TextField activiteField;
    @FXML
    private TextField humeurField;
    @FXML
    private TextField alimentationField;
    @FXML
    private TextField eauBueField;
    @FXML
    private TextField pasField;
    @FXML
    private TextField caloriesField;
    @FXML
    private TextField dureeActiviteField;
    @FXML
    private DatePicker datePicker;
    @FXML
    private DatePicker historiqueDatePicker;
    @FXML
    private ListView<String> historiqueListView;
    @FXML
    private Label selectedDetailsLabel;
    @FXML
    private Label imcPreviewLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private Label statsLabel;

    private final SanteQuotidienneService santeService = new SanteQuotidienneService();
    private final List<SanteQuotidienne> filteredEntries = new ArrayList<>();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private SanteQuotidienne selectedEntry;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LocalDate today = LocalDate.now();
        datePicker.setValue(today);
        historiqueDatePicker.setValue(today);

        historiqueListView.setItems(historyItems);
        historiqueListView.getSelectionModel().selectedIndexProperty()
                .addListener((obs, oldIndex, newIndex) -> handleHistorySelection(newIndex.intValue()));
        historiqueDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> refreshHistoryForSelectedDate());

        updateStats();
        refreshHistoryForSelectedDate();
    }

    @FXML
    private void handleSaveEntry() {
        // Si une entree est selectionnee, on evite de creer un doublon:
        // "Enregistrer" agit comme une mise a jour.
        if (selectedEntry != null && selectedEntry.getId() != null) {
            handleUpdateSelected();
            return;
        }

        Integer userId = currentUserId();
        if (userId == null) {
            showFeedback("Utilisateur non connecte.", false);
            return;
        }

        try {
            SanteQuotidienne item = buildValidatedEntry(SanteDataSource.MANUEL);
            SanteQuotidienneService.CrudResult result = santeService.create(userId, item);
            if (!result.success()) {
                showFeedback(result.message(), false);
                return;
            }

            showFeedback("Donnees ajoutées avec succes.", true);
            refreshHistoryForSelectedDate();
            updateStats();
            selectEntryById(result.entryId());
            clearInputForm();
        } catch (IllegalArgumentException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    @FXML
    private void handleImportGoogleFit() {
        Integer userId = currentUserId();
        User currentUser = AuthSession.getCurrentUser();
        if (userId == null || currentUser == null) {
            showFeedback("Utilisateur non connecte.", false);
            return;
        }

        boolean accountLinked = currentUser.getGoogleFitAccount() != null;
        try {
            LocalDate pickedDate = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
            SanteQuotidienne item = new SanteQuotidienne();
            item.setUser(currentUser);
            item.setDate(LocalDateTime.of(pickedDate, LocalTime.now().withSecond(0).withNano(0)));

            Double poids = parseOptionalDecimal(poidsField.getText(), "Poids");
            Double taille = parseOptionalDecimal(tailleField.getText(), "Taille");
            item.setPoids(poids == null ? 70.0 : poids);
            item.setTaille(taille == null ? 1.75 : taille);

            if (item.getPoids() <= 0 || item.getPoids() >= 350) {
                throw new IllegalArgumentException("Poids invalide: il doit etre > 0 et < 350 kg.");
            }
            if (!isValidTaille(item.getTaille())) {
                throw new IllegalArgumentException("Taille invalide: > 0 et max 2.5 m (ou 250 cm).");
            }

            item.setImc(calculateImc(item.getPoids(), item.getTaille()));
            item.setTensionArterielle(defaultIfNull(parseOptionalDecimal(tensionField.getText(), "Tension arterielle"), 12.0));
            if (item.getTensionArterielle() != null && (item.getTensionArterielle() <= 0 || item.getTensionArterielle() > 25)) {
                throw new IllegalArgumentException("Tension arterielle invalide: > 0 et <= 25.");
            }

            item.setSommeil(defaultIfNull(parseOptionalDecimal(sommeilField.getText(), "Sommeil"), 7.5));
            item.setEauBue(defaultIfNull(parseOptionalDecimal(eauBueField.getText(), "Eau bue"), 2.0));
            item.setPas(defaultIfNull(parseOptionalInteger(pasField.getText(), "Pas"), 8200));
            item.setCalories(defaultIfNull(parseOptionalInteger(caloriesField.getText(), "Calories"), 2200));
            item.setDureeActiviteMinutes(defaultIfNull(parseOptionalInteger(dureeActiviteField.getText(), "Duree activite"), 40));

            if ((item.getSommeil() != null && item.getSommeil() < 0)
                    || (item.getEauBue() != null && item.getEauBue() < 0)
                    || (item.getPas() != null && item.getPas() < 0)
                    || (item.getCalories() != null && item.getCalories() < 0)
                    || (item.getDureeActiviteMinutes() != null && item.getDureeActiviteMinutes() < 0)) {
                throw new IllegalArgumentException("Les valeurs numeriques optionnelles doivent etre >= 0.");
            }

            if (!isLettersOnly(activiteField.getText())) {
                throw new IllegalArgumentException("Activite physique: lettres uniquement.");
            }
            if (!isMoodLettersOnly(humeurField.getText())) {
                throw new IllegalArgumentException("Humeur: lettres uniquement (separees par des virgules).");
            }
            if (!isLettersOnly(alimentationField.getText())) {
                throw new IllegalArgumentException("Alimentation: lettres uniquement.");
            }

            item.setActivitePhysique(clean(activiteField.getText()).isBlank() ? "Marche rapide" : clean(activiteField.getText()));
            item.setAlimentation(clean(alimentationField.getText()).isBlank() ? "Equilibree" : clean(alimentationField.getText()));
            item.setHumeur(parseMood(humeurField.getText()));
            item.setSourceDonnees(SanteDataSource.GOOGLE_FIT);

            SanteQuotidienneService.CrudResult result = santeService.create(userId, item);
            if (!result.success()) {
                showFeedback(result.message(), false);
                return;
            }

            if (accountLinked) {
                showFeedback("Donnees importees depuis Google Fit et enregistrees en base.", true);
            } else {
                showFeedback("Compte Google Fit non lie: donnees de demonstration enregistrees en base.", true);
            }

            refreshHistoryForSelectedDate();
            updateStats();
            selectEntryById(result.entryId());
        } catch (IllegalArgumentException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    @FXML
    private void handleUpdateSelected() {
        Integer userId = currentUserId();
        if (userId == null) {
            showFeedback("Utilisateur non connecte.", false);
            return;
        }
        if (selectedEntry == null || selectedEntry.getId() == null) {
            showFeedback("Selectionne une entree depuis l'historique pour la modifier.", false);
            return;
        }

        try {
            SanteQuotidienne updated = buildValidatedEntry(selectedEntry.getSourceDonnees());
            updated.setId(selectedEntry.getId());
            updated.setUser(selectedEntry.getUser());

            SanteQuotidienneService.CrudResult result = santeService.update(userId, updated);
            if (!result.success()) {
                showFeedback(result.message(), false);
                return;
            }

            showFeedback("Donnees mise ajours avec sucess.", true);
            historiqueDatePicker.setValue(updated.getDate().toLocalDate());
            refreshHistoryForSelectedDate();
            updateStats();
            selectEntryById(updated.getId());
        } catch (IllegalArgumentException exception) {
            showFeedback(exception.getMessage(), false);
        }
    }

    @FXML
    private void handleDeleteSelected() {
        Integer userId = currentUserId();
        if (userId == null) {
            showFeedback("Utilisateur non connecte.", false);
            return;
        }
        if (selectedEntry == null || selectedEntry.getId() == null) {
            showFeedback("Selectionne une entree a supprimer dans l'historique.", false);
            return;
        }

        SanteQuotidienneService.CrudResult result = santeService.delete(userId, selectedEntry.getId());
        if (!result.success()) {
            showFeedback(result.message(), false);
            return;
        }

        selectedEntry = null;
        selectedDetailsLabel.setText("Aucune entree selectionnee.");
        showFeedback(result.message(), true);
        refreshHistoryForSelectedDate();
        updateStats();
        clearInputForm();
    }

    @FXML
    private void handleClearForm() {
        clearInputForm();
        showFeedback("", true);
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    private SanteQuotidienne buildValidatedEntry(SanteDataSource source) {
        Double poids = parseRequiredDecimal(poidsField.getText(), "Poids");
        if (poids <= 0 || poids >= 350) {
            throw new IllegalArgumentException("Poids invalide: il doit etre > 0 et < 350 kg.");
        }

        Double taille = parseRequiredDecimal(tailleField.getText(), "Taille");
        if (!isValidTaille(taille)) {
            throw new IllegalArgumentException("Taille invalide: > 0 et max 2.5 m (ou 250 cm).");
        }

        Double tension = parseOptionalDecimal(tensionField.getText(), "Tension arterielle");
        if (tension != null && (tension <= 0 || tension > 25)) {
            throw new IllegalArgumentException("Tension arterielle invalide: > 0 et <= 25.");
        }

        Double sommeil = parseOptionalDecimal(sommeilField.getText(), "Sommeil");
        if (sommeil != null && sommeil < 0) {
            throw new IllegalArgumentException("Sommeil invalide: la valeur doit etre >= 0.");
        }

        Double eauBue = parseOptionalDecimal(eauBueField.getText(), "Eau bue");
        if (eauBue != null && eauBue < 0) {
            throw new IllegalArgumentException("Eau bue invalide: la valeur doit etre >= 0.");
        }

        Integer pas = parseOptionalInteger(pasField.getText(), "Pas");
        if (pas != null && pas < 0) {
            throw new IllegalArgumentException("Pas invalide: la valeur doit etre >= 0.");
        }

        Integer calories = parseOptionalInteger(caloriesField.getText(), "Calories");
        if (calories != null && calories < 0) {
            throw new IllegalArgumentException("Calories invalides: la valeur doit etre >= 0.");
        }

        Integer duree = parseOptionalInteger(dureeActiviteField.getText(), "Duree activite");
        if (duree != null && duree < 0) {
            throw new IllegalArgumentException("Duree activite invalide: la valeur doit etre >= 0.");
        }

        if (!isLettersOnly(activiteField.getText())) {
            throw new IllegalArgumentException("Activite physique: lettres uniquement.");
        }
        if (!isMoodLettersOnly(humeurField.getText())) {
            throw new IllegalArgumentException("Humeur: lettres uniquement (separees par des virgules).");
        }
        if (!isLettersOnly(alimentationField.getText())) {
            throw new IllegalArgumentException("Alimentation: lettres uniquement.");
        }

        SanteQuotidienne item = new SanteQuotidienne();
        item.setPoids(poids);
        item.setTaille(taille);
        item.setImc(calculateImc(poids, taille));
        item.setTensionArterielle(tension);
        item.setSommeil(sommeil);
        item.setActivitePhysique(clean(activiteField.getText()));
        item.setHumeur(parseMood(humeurField.getText()));
        item.setAlimentation(clean(alimentationField.getText()));
        item.setEauBue(eauBue);
        item.setPas(pas);
        item.setCalories(calories);
        item.setDureeActiviteMinutes(duree);
        item.setSourceDonnees(source);

        LocalDate pickedDate = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
        item.setDate(LocalDateTime.of(pickedDate, LocalTime.now().withSecond(0).withNano(0)));

        User currentUser = AuthSession.getCurrentUser();
        if (currentUser != null) {
            item.setUser(currentUser);
        }
        return item;
    }

    private void refreshHistoryForSelectedDate() {
        Integer userId = currentUserId();
        filteredEntries.clear();
        historyItems.clear();

        if (userId == null) {
            selectedEntry = null;
            selectedDetailsLabel.setText("Utilisateur non connecte.");
            return;
        }

        LocalDate selectedDate = historiqueDatePicker.getValue() == null ? LocalDate.now() : historiqueDatePicker.getValue();
        filteredEntries.addAll(santeService.findByUserAndDate(userId, selectedDate));
        historyItems.setAll(filteredEntries.stream().map(this::toHistoryLine).toList());

        if (filteredEntries.isEmpty()) {
            selectedEntry = null;
            selectedDetailsLabel.setText("Aucune entree pour la date selectionnee.");
            return;
        }
        historiqueListView.getSelectionModel().select(0);
    }

    private void updateStats() {
        Integer userId = currentUserId();
        if (userId == null) {
            statsLabel.setText("Utilisateur non connecte.");
            return;
        }

        SanteQuotidienneService.Stats stats = santeService.loadStats(userId);
        if (stats.totalEntries() <= 0) {
            statsLabel.setText("Aucune entree enregistree pour le moment.");
            return;
        }
        statsLabel.setText("Entrees: " + stats.totalEntries()
                + " | IMC moyen: " + String.format("%.2f", stats.averageImc())
                + " | Pas cumules: " + stats.totalSteps());
    }

    private void handleHistorySelection(int index) {
        if (index < 0 || index >= filteredEntries.size()) {
            selectedEntry = null;
            selectedDetailsLabel.setText("Aucune entree selectionnee.");
            return;
        }

        selectedEntry = filteredEntries.get(index);
        fillFormFromEntry(selectedEntry);
        selectedDetailsLabel.setText(buildDetailsText(selectedEntry));
    }

    private void fillFormFromEntry(SanteQuotidienne item) {
        poidsField.setText(format(item.getPoids()));
        tailleField.setText(format(item.getTaille()));
        tensionField.setText(format(item.getTensionArterielle()));
        sommeilField.setText(format(item.getSommeil()));
        activiteField.setText(clean(item.getActivitePhysique()));
        humeurField.setText(joinMood(item.getHumeur()));
        alimentationField.setText(clean(item.getAlimentation()));
        eauBueField.setText(format(item.getEauBue()));
        pasField.setText(format(item.getPas()));
        caloriesField.setText(format(item.getCalories()));
        dureeActiviteField.setText(format(item.getDureeActiviteMinutes()));
        if (item.getDate() != null) {
            datePicker.setValue(item.getDate().toLocalDate());
        }
        imcPreviewLabel.setText(item.getImc() == null ? "IMC calcule: -" : String.format("IMC calcule: %.2f", item.getImc()));
    }

    private String buildDetailsText(SanteQuotidienne item) {
        return "Date: " + dateFormatter.format(item.getDate())
                + "\nPoids: " + format(item.getPoids()) + " kg"
                + "\nTaille: " + format(item.getTaille())
                + "\nIMC: " + format(item.getImc())
                + "\nSommeil: " + format(item.getSommeil()) + " h"
                + "\nEau: " + format(item.getEauBue()) + " L"
                + "\nPas: " + format(item.getPas())
                + "\nCalories: " + format(item.getCalories())
                + "\nActivite: " + clean(item.getActivitePhysique())
                + "\nHumeur: " + joinMood(item.getHumeur())
                + "\nAlimentation: " + clean(item.getAlimentation())
                + "\nSource: " + item.getSourceDonnees();
    }

    private void selectEntryById(Integer id) {
        if (id == null) {
            return;
        }
        for (int i = 0; i < filteredEntries.size(); i++) {
            SanteQuotidienne row = filteredEntries.get(i);
            if (row.getId() != null && row.getId().equals(id)) {
                historiqueListView.getSelectionModel().select(i);
                return;
            }
        }
    }

    private Integer currentUserId() {
        User user = AuthSession.getCurrentUser();
        return user == null ? null : user.getId();
    }

    private void clearInputForm() {
        selectedEntry = null;
        historiqueListView.getSelectionModel().clearSelection();
        selectedDetailsLabel.setText("Aucune entree selectionnee.");

        poidsField.clear();
        tailleField.clear();
        tensionField.clear();
        sommeilField.clear();
        activiteField.clear();
        humeurField.clear();
        alimentationField.clear();
        eauBueField.clear();
        pasField.clear();
        caloriesField.clear();
        dureeActiviteField.clear();
        datePicker.setValue(LocalDate.now());
        imcPreviewLabel.setText("IMC calcule: -");
    }

    private Double calculateImc(double poids, double tailleRaw) {
        double tailleMetres = tailleRaw > 3 ? tailleRaw / 100.0 : tailleRaw;
        return poids / (tailleMetres * tailleMetres);
    }

    private String toHistoryLine(SanteQuotidienne item) {
        return dateFormatter.format(item.getDate()) + " | IMC " + format(item.getImc())
                + " | " + format(item.getPoids()) + "kg"
                + " | " + format(item.getPas()) + " pas"
                + " | " + item.getSourceDonnees();
    }

    private void showFeedback(String message, boolean success) {
        feedbackLabel.setText(message);
        feedbackLabel.setVisible(!message.isBlank());
        feedbackLabel.setManaged(!message.isBlank());
        feedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        feedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
    }

    private List<Object> parseMood(String rawMood) {
        if (rawMood == null || rawMood.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(rawMood.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String joinMood(List<Object> moods) {
        if (moods == null || moods.isEmpty()) {
            return "";
        }
        return moods.stream().map(String::valueOf).toList().toString().replace("[", "").replace("]", "");
    }

    private Double parseRequiredDecimal(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + " est obligatoire.");
        }
        return parseStrictDecimal(raw, fieldLabel);
    }

    private Double parseOptionalDecimal(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return parseStrictDecimal(raw, fieldLabel);
    }

    private Double parseStrictDecimal(String raw, String fieldLabel) {
        String value = raw.trim();
        if (!DECIMAL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldLabel + " doit contenir des chiffres uniquement.");
        }
        try {
            return Double.parseDouble(value.replace(',', '.'));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldLabel + " invalide.");
        }
    }

    private Integer parseOptionalInteger(String raw, String fieldLabel) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!INTEGER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldLabel + " doit contenir des chiffres uniquement.");
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldLabel + " invalide.");
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private Double defaultIfNull(Double value, Double fallback) {
        return value == null ? fallback : value;
    }

    private Integer defaultIfNull(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    private boolean isValidTaille(Double taille) {
        if (taille == null || taille <= 0) {
            return false;
        }
        if (taille <= 2.5) {
            return true;
        }
        return taille <= 250;
    }

    private boolean isLettersOnly(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return LETTERS_PATTERN.matcher(raw.trim()).matches();
    }

    private boolean isMoodLettersOnly(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .allMatch(token -> LETTERS_PATTERN.matcher(token).matches());
    }

    private String format(Number value) {
        return value == null ? "" : value.toString();
    }
}
