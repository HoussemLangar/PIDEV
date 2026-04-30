package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.HealthSymptomPredictionService;
import com.santea.service.OpenAiSpeechToTextService;
import com.santea.service.OllamaChatbotService;
import com.santea.service.SanteQuotidienneService;
import com.santea.service.SymptomesQuotidiensService;
import com.santea.service.VoskSpeechToTextService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

public class SymptomesQuotidiensViewController implements Initializable {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final AudioFormat VOICE_FORMAT = new AudioFormat(16000.0f, 16, 1, true, false);

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
    @FXML
    private ScrollPane pageScrollPane;
    @FXML
    private ListView<String> chatbotMessagesListView;
    @FXML
    private TextField chatbotInputField;
    @FXML
    private Label predictionLabel;
    @FXML
    private Button chatbotVoiceButton;

    private final SymptomesQuotidiensService service = new SymptomesQuotidiensService();
    private final OllamaChatbotService chatbotService = new OllamaChatbotService();
    private final SanteQuotidienneService santeService = new SanteQuotidienneService();
    private final ObservableList<SymptomeOptionView> availableSymptoms = FXCollections.observableArrayList();
    private final ObservableList<SymptomesQuotidiensService.SymptomeDailyRow> historyRows = FXCollections.observableArrayList();
    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private final ObservableList<String> chatbotMessages = FXCollections.observableArrayList();

    private Integer selectedHistoryEntryId;
    private final AtomicInteger refreshSeq = new AtomicInteger(0);
    private final AtomicInteger symptomsLoadSeq = new AtomicInteger(0);

    private volatile boolean voiceRecording = false;
    private TargetDataLine voiceLine;
    private java.io.ByteArrayOutputStream voiceBuffer;
    private Thread voiceThread;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("🎯 SymptomesQuotidiensViewController.initialize() called");
        try {
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
            if (chatbotMessagesListView != null) {
                chatbotMessagesListView.setItems(chatbotMessages);
            }
            appendChatbotMessage("Bot", "Bonjour, je suis l'assistant Sante SANTEA. Decris tes symptomes et je t'aide.");
            appendChatbotMessage("Bot", "Important: ceci ne remplace pas un avis medical. En urgence, contacte les services de secours.");
            appendChatbotMessage("Bot", "Chatbot local (Ollama/Rasa) avec fallback offline. Ecris ton symptome et je te reponds.");

            symptomsListView.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<SymptomeOptionView>) change -> updateSelectedCount());
            symptomDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> refreshHistoryAsync());
            periodFilterCombo.valueProperty().addListener((obs, oldValue, newValue) -> refreshHistoryAsync());
            historyListView.getSelectionModel().selectedIndexProperty().addListener((obs, oldIndex, newIndex) -> handleHistorySelection(newIndex == null ? -1 : newIndex.intValue()));

            loadSymptomsAsync();
            refreshHistoryAsync();
            updateSelectedCount();
            selectedHistoryLabel.setText("Aucune ligne selectionnee.");
            if (predictionLabel != null) {
                predictionLabel.setText("Ajoute des données pour voir une analyse sur cette période.");
            }
            System.out.println("✅ SymptomesQuotidiensViewController.initialize() completed successfully");
        } catch (Exception e) {
            System.out.println("❌ Error in initialize(): " + e.getMessage());
            e.printStackTrace();
        }
        // Scrolling is handled by the ScrollPane itself + the global scroll bridge in AppBaseViewController.
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
            refreshHistoryAsync();
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
            refreshHistoryAsync();
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
            refreshHistoryAsync();
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
        loadSymptomsAsync();
        refreshHistoryAsync();
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

    @FXML
    private void handleSendChatbotMessage() {
        if (chatbotInputField == null) {
            return;
        }
        String message = chatbotInputField.getText() == null ? "" : chatbotInputField.getText().trim();
        if (message.isBlank()) {
            return;
        }
        chatbotInputField.clear();
        appendChatbotMessage("Vous", message);
        appendChatbotMessage("Bot", "Analyse en cours...");

        Task<OllamaChatbotService.ChatResult> task = new Task<>() {
            @Override
            protected OllamaChatbotService.ChatResult call() {
                return chatbotService.ask(message);
            }
        };

        task.setOnSucceeded(event -> {
            removeLastPendingBotLine();
            OllamaChatbotService.ChatResult result = task.getValue();
            if (result == null || result.messages() == null || result.messages().isEmpty()) {
                appendChatbotMessage("Bot", "Je n'ai pas pu repondre pour le moment.");
                return;
            }
            for (String line : result.messages()) {
                appendChatbotMessage("Bot", line);
            }
        });

        task.setOnFailed(event -> {
            removeLastPendingBotLine();
            Throwable ex = task.getException();
            appendChatbotMessage("Bot", "Erreur chatbot: " + (ex == null ? "inconnue" : ex.getMessage()));
        });

        Thread thread = new Thread(task, "chatbot-task");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void handleVoiceInput() {
        if (voiceRecording) {
            stopVoiceRecordingAndSend();
        } else {
            startVoiceRecording();
        }
    }

    private void startVoiceRecording() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, VOICE_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                appendChatbotMessage("Bot", "Micro non supporte sur cette machine.");
                return;
            }

            voiceLine = (TargetDataLine) AudioSystem.getLine(info);
            voiceLine.open(VOICE_FORMAT);
            voiceLine.start();

            voiceBuffer = new java.io.ByteArrayOutputStream();
            voiceRecording = true;
            if (chatbotVoiceButton != null) {
                chatbotVoiceButton.setText("⏹");
            }
            appendChatbotMessage("Bot", "Parle maintenant... (reclique sur 🎤 pour envoyer)");

            voiceThread = new Thread(() -> {
                byte[] data = new byte[4096];
                while (voiceRecording && voiceLine != null && voiceLine.isOpen()) {
                    int n = voiceLine.read(data, 0, data.length);
                    if (n > 0 && voiceBuffer != null) {
                        voiceBuffer.write(data, 0, n);
                    }
                }
            }, "voice-record-thread");
            voiceThread.setDaemon(true);
            voiceThread.start();
        } catch (Exception e) {
            appendChatbotMessage("Bot", "Erreur micro: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            voiceRecording = false;
            if (chatbotVoiceButton != null) {
                chatbotVoiceButton.setText("🎤");
            }
        }
    }

    private void stopVoiceRecordingAndSend() {
        voiceRecording = false;
        if (chatbotVoiceButton != null) {
            chatbotVoiceButton.setText("🎤");
        }

        try {
            if (voiceLine != null) {
                voiceLine.stop();
                voiceLine.close();
            }
        } catch (Exception ignored) {
        }

        byte[] pcm = voiceBuffer == null ? new byte[0] : voiceBuffer.toByteArray();
        if (pcm.length < 2000) {
            appendChatbotMessage("Bot", "Je n'ai rien entendu. Reessaie.");
            return;
        }

        appendChatbotMessage("Bot", "Transcription vocale en cours...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                if (VoskSpeechToTextService.isConfigured()) {
                    return VoskSpeechToTextService.transcribePcmToText(pcm, VOICE_FORMAT);
                }
                // Optional fallback to OpenAI STT if configured.
                return OpenAiSpeechToTextService.transcribePcmToText(pcm, VOICE_FORMAT);
            }
        };

        task.setOnSucceeded(evt -> {
            String text = task.getValue();
            if (text == null || text.isBlank()) {
                appendChatbotMessage("Bot", "Je n'ai pas pu transcrire.");
                return;
            }
            // Auto-send as a normal chatbot message
            if (chatbotInputField != null) {
                chatbotInputField.setText(text.trim());
            }
            handleSendChatbotMessage();
        });
        task.setOnFailed(evt -> {
            Throwable ex = task.getException();
            appendChatbotMessage("Bot", "Transcription echouee: " + (ex == null ? "inconnue" : ex.getMessage()));
            appendChatbotMessage("Bot", "Astuce: definis VOSK_MODEL_PATH (open source) ou OPENAI_API_KEY (cloud) pour activer la saisie vocale.");
        });

        Thread t = new Thread(task, "voice-stt-task");
        t.setDaemon(true);
        t.start();
    }

    private void appendChatbotMessage(String author, String text) {
        String safeAuthor = author == null ? "Bot" : author;
        String safeText = text == null ? "" : text;
        chatbotMessages.add(safeAuthor + " : " + safeText);
        if (chatbotMessagesListView != null) {
            chatbotMessagesListView.scrollTo(Math.max(0, chatbotMessages.size() - 1));
        }
    }

    private void removeLastPendingBotLine() {
        if (chatbotMessages.isEmpty()) {
            return;
        }
        int lastIndex = chatbotMessages.size() - 1;
        String last = chatbotMessages.get(lastIndex);
        if (last != null && (last.startsWith("Bot : Analyse en cours") || last.startsWith("Bot : Initialisation du moteur Rasa en cours"))) {
            chatbotMessages.remove(lastIndex);
        }
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

    private void loadSymptomsAsync() {
        int seq = symptomsLoadSeq.incrementAndGet();
        System.out.println("🔄 [Seq=" + seq + "] Starting loadSymptomsAsync...");
        
        Task<List<SymptomesQuotidiensService.SymptomeChoice>> task = new Task<>() {
            @Override
            protected List<SymptomesQuotidiensService.SymptomeChoice> call() {
                try {
                    List<SymptomesQuotidiensService.SymptomeChoice> result = service.loadAvailableSymptoms();
                    System.out.println("✅ [Seq=" + seq + "] Loaded " + result.size() + " symptoms");
                    return result;
                } catch (Exception e) {
                    System.out.println("❌ [Seq=" + seq + "] Exception in loadAvailableSymptoms: " + e.getMessage());
                    e.printStackTrace();
                    return List.of();
                }
            }
        };
        task.setOnSucceeded(evt -> {
            if (seq != symptomsLoadSeq.get()) {
                System.out.println("⏭️ [Seq=" + seq + "] Skipped (newer request exists)");
                return;
            }
            List<SymptomesQuotidiensService.SymptomeChoice> rows = task.getValue();
            System.out.println("📝 [Seq=" + seq + "] Setting symptoms in UI, count: " + rows.size());
            availableSymptoms.setAll(rows.stream()
                    .map(row -> new SymptomeOptionView(row.id(), row.nom(), row.categorie()))
                    .toList());
            updateSelectedCount();
        });
        task.setOnFailed(evt -> {
            System.out.println("❌ [Seq=" + seq + "] Task failed: " + task.getException());
            task.getException().printStackTrace();
            showFeedback("Chargement symptomes impossible.", false);
        });
        Thread t = new Thread(task, "symptoms-load-task");
        t.setDaemon(true);
        t.start();
        System.out.println("🚀 [Seq=" + seq + "] Task started in background thread");
    }

    private void refreshHistoryAsync() {
        Integer userId = currentUserId();
        int seq = refreshSeq.incrementAndGet();
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

        historyInfoLabel.setText("Chargement...");
        Task<List<SymptomesQuotidiensService.SymptomeDailyRow>> task = new Task<>() {
            @Override
            protected List<SymptomesQuotidiensService.SymptomeDailyRow> call() {
                return service.loadRowsForUser(userId, selectedDate, filter);
            }
        };
        task.setOnSucceeded(evt -> {
            if (seq != refreshSeq.get()) {
                return;
            }
            List<SymptomesQuotidiensService.SymptomeDailyRow> rows = task.getValue();
            historyRows.setAll(rows);
            historyItems.setAll(rows.stream().map(this::toHistoryLine).toList());

            if (rows.isEmpty()) {
                historyInfoLabel.setText("Aucun symptome pour le filtre " + selectedPeriodLabel() + ".");
            } else {
                historyInfoLabel.setText(rows.size() + " symptome(s) trouves pour " + selectedPeriodLabel() + ".");
            }

            updatePredictionAsync(userId, selectedDate, filter, rows);
        });
        task.setOnFailed(evt -> {
            if (seq != refreshSeq.get()) {
                return;
            }
            historyInfoLabel.setText("Chargement historique impossible.");
        });
        Thread t = new Thread(task, "symptoms-history-task");
        t.setDaemon(true);
        t.start();
    }

    private void updatePredictionAsync(int userId,
                                       LocalDate anchorDate,
                                       SymptomesQuotidiensService.PeriodFilter filter,
                                       List<SymptomesQuotidiensService.SymptomeDailyRow> symptomRows) {
        if (predictionLabel == null) {
            return;
        }

        LocalDate safeAnchor = anchorDate == null ? LocalDate.now() : anchorDate;
        SymptomesQuotidiensService.PeriodFilter safeFilter = filter == null ? SymptomesQuotidiensService.PeriodFilter.DAY : filter;

        LocalDate startTmp = safeAnchor;
        LocalDate endTmp = safeAnchor;
        switch (safeFilter) {
            case WEEK -> {
                // monday..sunday
                startTmp = safeAnchor.minusDays((safeAnchor.getDayOfWeek().getValue() + 6) % 7);
                endTmp = startTmp.plusDays(6);
            }
            case MONTH -> {
                startTmp = safeAnchor.withDayOfMonth(1);
                endTmp = safeAnchor.withDayOfMonth(safeAnchor.lengthOfMonth());
            }
            case DAY -> {
            }
        }
        final LocalDate start = startTmp;
        final LocalDate end = endTmp;

        predictionLabel.setText("Analyse en cours...");
        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                List<com.santea.model.SanteQuotidienne> health = santeService.findByUserBetweenDates(userId, start, end);
                List<String> lines = HealthSymptomPredictionService.buildInsights(symptomRows, health, start, end);
                return (lines == null || lines.isEmpty()) ? "Aucune donnée à analyser." : String.join("\n", lines);
            }
        };
        task.setOnSucceeded(evt -> predictionLabel.setText(task.getValue()));
        task.setOnFailed(evt -> predictionLabel.setText("Analyse indisponible (erreur)."));

        Thread t = new Thread(task, "symptom-prediction-task");
        t.setDaemon(true);
        t.start();
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
