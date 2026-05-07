package com.santea.controller;

import com.santea.model.SanteDataSource;
import com.santea.model.SanteQuotidienne;
import com.santea.model.User;
import com.santea.model.GoogleFitAccount;
import com.santea.navigation.AppNavigator;
import com.santea.repository.GoogleFitAccountRepository;
import com.santea.service.AuthSession;
import com.santea.service.DatabaseService;
import com.santea.service.GoogleFitApiServiceV2;
import com.santea.service.GoogleOAuthService;
import com.santea.service.HydrationRecommendationService;
import com.santea.service.OpenMeteoService;
import com.santea.service.SanteQuotidienneService;
import com.santea.service.WeatherRiskAdviceService;
import com.santea.config.DatabaseConfig;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.Node;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;
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
    private ScrollPane pageScrollPane;
    @FXML
    private Label selectedDetailsLabel;
    @FXML
    private Label imcPreviewLabel;
    @FXML
    private Label feedbackLabel;
    @FXML
    private Label statsLabel;
    @FXML
    private Label hydrationAdviceLabel;

    private final SanteQuotidienneService santeService = new SanteQuotidienneService();
    private final GoogleFitAccountRepository googleFitAccountRepository =
            new GoogleFitAccountRepository(new DatabaseService(DatabaseConfig.fromEnvironment()));
    private final List<SanteQuotidienne> filteredEntries = new ArrayList<>();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private SanteQuotidienne selectedEntry;
    private final AtomicInteger historySeq = new AtomicInteger(0);
    private final AtomicInteger statsSeq = new AtomicInteger(0);

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        LocalDate today = LocalDate.now();
        datePicker.setValue(today);
        historiqueDatePicker.setValue(today);

        historiqueListView.setItems(historyItems);
        historiqueListView.getSelectionModel().selectedIndexProperty()
                .addListener((obs, oldIndex, newIndex) -> handleHistorySelection(newIndex.intValue()));
        historiqueDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> refreshHistoryForSelectedDateAsync());
        // Scrolling is handled by the ScrollPane itself + the global scroll bridge in AppBaseViewController.

        setupHydrationAdviser();
        updateStatsAsync();
        refreshHistoryForSelectedDateAsync();
    }

    private void setupHydrationAdviser() {
        if (hydrationAdviceLabel != null) {
            hydrationAdviceLabel.setText("");
            hydrationAdviceLabel.setVisible(false);
            hydrationAdviceLabel.setManaged(false);
        }

        if (pasField != null) {
            pasField.textProperty().addListener((obs, oldV, newV) -> updateHydrationAdviceAsync());
        }
        if (dureeActiviteField != null) {
            dureeActiviteField.textProperty().addListener((obs, oldV, newV) -> updateHydrationAdviceAsync());
        }
        if (activiteField != null) {
            activiteField.textProperty().addListener((obs, oldV, newV) -> updateHydrationAdviceAsync());
        }

        updateHydrationAdviceAsync();
    }

    private void updateHydrationAdviceAsync() {
        if (hydrationAdviceLabel == null) {
            return;
        }
        Integer steps = safeParseInt(pasField == null ? null : pasField.getText());
        Integer minutes = safeParseInt(dureeActiviteField == null ? null : dureeActiviteField.getText());

        hydrationAdviceLabel.setText("Conseil hydratation: chargement météo...");
        hydrationAdviceLabel.setVisible(true);
        hydrationAdviceLabel.setManaged(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                // Default: Tunis (can be overridden by env vars)
                double lat = parseEnvDouble("OPEN_METEO_LAT", 36.8065);
                double lon = parseEnvDouble("OPEN_METEO_LON", 10.1815);
                OpenMeteoService.WeatherSnapshot snap = OpenMeteoService.getTodaySnapshot(lat, lon);
                Double temp = snap == null ? null : snap.apparentTemperatureC();
                if (temp == null && snap != null) {
                    temp = snap.temperatureC();
                }
                HydrationRecommendationService.Recommendation rec =
                        HydrationRecommendationService.recommend(temp, steps, minutes);
                List<String> risks = WeatherRiskAdviceService.buildRiskTips(temp, snap == null ? null : snap.humidityPercent());
                String reasons = (rec.reasons() == null || rec.reasons().isEmpty())
                        ? ""
                        : " (ajusté: " + String.join(", ", rec.reasons()) + ")";
                String city = System.getenv("OPEN_METEO_CITY");
                String location = (city == null || city.isBlank()) ? "Tunis" : city.trim();
                StringBuilder sb = new StringBuilder();
                sb.append("💧 Objectif eau: ")
                        .append(String.format("%.1f", rec.targetLiters()))
                        .append(" L/jour")
                        .append(reasons)
                        .append(".");
                if (temp != null) {
                    sb.append("\n🌡️ Météo: ").append(String.format("%.0f°C", temp)).append(" (").append(location).append(").");
                }
                if (risks != null && !risks.isEmpty()) {
                    sb.append("\n🧠 Conseils liés à la météo:");
                    for (String line : risks) {
                        if (line == null || line.isBlank()) {
                            continue;
                        }
                        sb.append("\n").append(line.trim());
                    }
                }
                sb.append("\n(Conseils généraux, pas un diagnostic.)");
                return sb.toString();
            }
        };

        task.setOnSucceeded(evt -> hydrationAdviceLabel.setText(task.getValue()));
        task.setOnFailed(evt -> hydrationAdviceLabel.setText("Conseil hydratation indisponible (météo)."));

        Thread t = new Thread(task, "open-meteo-hydration-task");
        t.setDaemon(true);
        t.start();
    }

    private Integer safeParseInt(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return null;
        }
        if (!trimmed.matches("^\\d+$")) {
            return null;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (Exception ignored) {
            return null;
        }
    }

    private double parseEnvDouble(String name, double fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (Exception ignored) {
            return fallback;
        }
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
            refreshHistoryForSelectedDateAsync();
            updateStatsAsync();
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

        try {
            LocalDate pickedDate = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
            SanteQuotidienne item = new SanteQuotidienne();
            item.setUser(currentUser);
            item.setDate(LocalDateTime.of(pickedDate, LocalTime.now().withSecond(0).withNano(0)));
            item.setSourceDonnees(SanteDataSource.GOOGLE_FIT);

            // Toujours re-authentifier avant chaque import, selon besoin produit.
            GoogleFitApiServiceV2 fitService = new GoogleFitApiServiceV2();
            GoogleFitApiServiceV2.FitResult fitResult;
            showFeedback("Authentification Google en cours...", true);
            GoogleOAuthService.AuthResult authResult = new GoogleOAuthService().authenticate();
            if (!authResult.success()) {
                showFeedback(authResult.message(), false);
                return;
            }

            GoogleFitAccount googleFitAccount = new GoogleFitAccount();
            googleFitAccount.setGoogleAccountId(authResult.googleAccountId());
            googleFitAccount.setAccessToken(authResult.accessToken());
            googleFitAccount.setRefreshToken(authResult.refreshToken());
            googleFitAccount.setTokenExpiration(authResult.tokenExpiration());
            currentUser.setGoogleFitAccount(googleFitAccount);
            googleFitAccountRepository.saveOrUpdateForUser(userId, googleFitAccount);

            fitResult = fitService.fetchDailyData(
                    currentUser.getGoogleFitAccount(),
                    pickedDate
            );

            if (!fitResult.success() || fitResult.data() == null) {
                showFeedback(fitResult.message(), false);
                return;
            }

            fitService.applyFitDataToHealth(fitResult.data(), item);

            if (item.getPoids() == null || item.getTaille() == null) {
                // Fallback 1: champs saisis dans le formulaire
                if (item.getPoids() == null) {
                    Double poidsForm = parseOptionalDecimal(poidsField.getText(), "Poids");
                    if (poidsForm != null && poidsForm > 0) {
                        item.setPoids(poidsForm);
                    }
                }
                if (item.getTaille() == null) {
                    Double tailleForm = parseOptionalDecimal(tailleField.getText(), "Taille");
                    if (tailleForm != null && tailleForm > 0) {
                        item.setTaille(tailleForm);
                    }
                }

                // Fallback 2: derniere entree locale
                SanteQuotidienne latest = santeService.findLatestByUser(userId).orElse(null);
                if (latest != null) {
                    if (item.getPoids() == null && latest.getPoids() != null && latest.getPoids() > 0) {
                        item.setPoids(latest.getPoids());
                    }
                    if (item.getTaille() == null && latest.getTaille() != null && latest.getTaille() > 0) {
                        item.setTaille(latest.getTaille());
                    }
                }

            }

            if (item.getPoids() == null || item.getTaille() == null) {
                showFeedback("Google Fit n'a pas renvoye poids/taille et aucune valeur locale n'est disponible.", false);
                return;
            }
            if (item.getPoids() <= 0 || item.getPoids() >= 350) {
                throw new IllegalArgumentException("Poids invalide importe depuis Google Fit.");
            }
            if (!isValidTaille(item.getTaille())) {
                throw new IllegalArgumentException("Taille invalide importee depuis Google Fit.");
            }
            item.setImc(calculateImc(item.getPoids(), item.getTaille()));

            item.setActivitePhysique("");
            item.setAlimentation("");
            item.setHumeur(new ArrayList<>());
            item.setEauBue(null);

            // Validation des données
            if (item.getTensionArterielle() != null && (item.getTensionArterielle() <= 0 || item.getTensionArterielle() > 25)) {
                throw new IllegalArgumentException("Tension arterielle invalide: > 0 et <= 25.");
            }
            if ((item.getSommeil() != null && item.getSommeil() < 0)
                    || (item.getEauBue() != null && item.getEauBue() < 0)
                    || (item.getPas() != null && item.getPas() < 0)
                    || (item.getCalories() != null && item.getCalories() < 0)
                    || (item.getDureeActiviteMinutes() != null && item.getDureeActiviteMinutes() < 0)) {
                throw new IllegalArgumentException("Les valeurs numeriques optionnelles doivent etre >= 0.");
            }

            // Sauvegarder en base de données
            SanteQuotidienneService.CrudResult result = santeService.create(userId, item);
            if (!result.success()) {
                showFeedback(result.message(), false);
                return;
            }

            showFeedback("Donnees Google Fit reelles importees et enregistrees avec succes!", true);
            refreshHistoryForSelectedDateAsync();
            updateStatsAsync();
            selectEntryById(result.entryId());
        } catch (IllegalArgumentException exception) {
            showFeedback(exception.getMessage(), false);
        } catch (Exception exception) {
            showFeedback("Erreur lors de l'importation: " + exception.getMessage(), false);
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
            refreshHistoryForSelectedDateAsync();
            updateStatsAsync();
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
        refreshHistoryForSelectedDateAsync();
        updateStatsAsync();
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

    private void refreshHistoryForSelectedDateAsync() {
        Integer userId = currentUserId();
        int seq = historySeq.incrementAndGet();

        filteredEntries.clear();
        historyItems.clear();
        selectedEntry = null;
        selectedDetailsLabel.setText(userId == null ? "Utilisateur non connecte." : "Chargement...");

        if (userId == null) {
            return;
        }

        LocalDate selectedDate = historiqueDatePicker.getValue() == null ? LocalDate.now() : historiqueDatePicker.getValue();
        Task<List<SanteQuotidienne>> task = new Task<>() {
            @Override
            protected List<SanteQuotidienne> call() {
                return santeService.findByUserAndDate(userId, selectedDate);
            }
        };
        task.setOnSucceeded(evt -> {
            if (seq != historySeq.get()) {
                return;
            }
            List<SanteQuotidienne> entries = task.getValue();
            filteredEntries.clear();
            if (entries != null) {
                filteredEntries.addAll(entries);
            }
            historyItems.setAll(filteredEntries.stream().map(this::toHistoryLine).toList());

            if (filteredEntries.isEmpty()) {
                selectedEntry = null;
                selectedDetailsLabel.setText("Aucune entree pour la date selectionnee.");
                return;
            }
            historiqueListView.getSelectionModel().select(0);
        });
        task.setOnFailed(evt -> {
            if (seq != historySeq.get()) {
                return;
            }
            selectedDetailsLabel.setText("Chargement historique impossible.");
        });
        Thread t = new Thread(task, "sante-history-task");
        t.setDaemon(true);
        t.start();
    }

    private void updateStatsAsync() {
        Integer userId = currentUserId();
        int seq = statsSeq.incrementAndGet();
        if (userId == null) {
            statsLabel.setText("Utilisateur non connecte.");
            return;
        }
        statsLabel.setText("Chargement stats...");

        Task<SanteQuotidienneService.Stats> task = new Task<>() {
            @Override
            protected SanteQuotidienneService.Stats call() {
                return santeService.loadStats(userId);
            }
        };
        task.setOnSucceeded(evt -> {
            if (seq != statsSeq.get()) {
                return;
            }
            SanteQuotidienneService.Stats stats = task.getValue();
            if (stats == null || stats.totalEntries() <= 0) {
                statsLabel.setText("Aucune entree enregistree pour le moment.");
                return;
            }
            statsLabel.setText("Entrees: " + stats.totalEntries()
                    + " | IMC moyen: " + String.format("%.2f", stats.averageImc())
                    + " | Pas cumules: " + stats.totalSteps());
        });
        task.setOnFailed(evt -> {
            if (seq != statsSeq.get()) {
                return;
            }
            statsLabel.setText("Chargement stats impossible.");
        });
        Thread t = new Thread(task, "sante-stats-task");
        t.setDaemon(true);
        t.start();
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

    // (Removed custom scroll interception - handled globally.)

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
