package com.santea.controller;

import com.santea.model.Patient;
import com.santea.model.PlanExercice;
import com.santea.model.PlanRegime;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.navigation.ModuleContext;
import com.santea.service.AccompanimentPlanService;
import com.santea.service.AuthSession;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AccompanimentPlanCreateController extends AppBaseViewController {
    @FXML private Label patientNameLabel;
    @FXML private Label patientMetaLabel;
    @FXML private TextField titleField;
    @FXML private TextArea objectivesArea;
    @FXML private TextArea descriptionArea;
    @FXML private ComboBox<String> statusCombo;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private DatePicker startDatePicker;
    @FXML private TextField goalField;
    @FXML private TextField dietStyleField;
    @FXML private TextField allergiesField;
    @FXML private TextField levelField;
    @FXML private Spinner<Integer> nutritionDaysSpinner;
    @FXML private Spinner<Integer> daysPerWeekSpinner;
    @FXML private Spinner<Integer> minutesSpinner;
    @FXML private TextArea constraintsArea;
    @FXML private Label aiSummaryLabel;

    private final AccompanimentPlanService service = new AccompanimentPlanService();
    private Patient patient;
    private AccompanimentPlanService.AiSuggestions latestSuggestions;
    private Task<AccompanimentPlanService.AiSuggestions> suggestionsTask;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        Integer patientUserId = ModuleContext.getAccompanimentPatientUserId();
        patient = service.getPatientByUserId(patientUserId);
        if (patient == null) {
            throw new IllegalStateException("Patient introuvable");
        }
        patientNameLabel.setText(displayUser(patient.getUser()));
        patientMetaLabel.setText(patient.getUser() == null ? "" : safe(patient.getUser().getEmail()));
        statusCombo.getItems().setAll("active", "paused", "completed", "cancelled");
        statusCombo.getSelectionModel().select("active");
        durationSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 52, 8));
        nutritionDaysSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 14, 7));
        daysPerWeekSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 7, 3));
        minutesSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 120, 30, 5));
        durationSpinner.getValueFactory().setValue(8);
        nutritionDaysSpinner.getValueFactory().setValue(7);
        daysPerWeekSpinner.getValueFactory().setValue(3);
        minutesSpinner.getValueFactory().setValue(30);
        startDatePicker.setValue(LocalDate.now());
        aiSummaryLabel.setText("Aucune suggestion générée pour le moment.");
    }

    @FXML
    private void handleGenerateSuggestions() {
        if (suggestionsTask != null && suggestionsTask.isRunning()) {
            return;
        }

        final String goal = safe(goalField.getText());
        final String dietStyle = safe(dietStyleField.getText());
        final String allergies = safe(allergiesField.getText());
        final int nutritionDays = nutritionDaysSpinner.getValue();
        final String level = safe(levelField.getText());
        final int daysPerWeek = daysPerWeekSpinner.getValue();
        final int minutes = minutesSpinner.getValue();
        final String constraints = safe(constraintsArea.getText());

        setGenerationInProgress(true);
        aiSummaryLabel.setText("Génération des suggestions en cours...");

        suggestionsTask = new Task<>() {
            @Override
            protected AccompanimentPlanService.AiSuggestions call() {
                return service.buildAiSuggestions(
                    goal,
                    dietStyle,
                    allergies,
                    nutritionDays,
                    level,
                    daysPerWeek,
                    minutes,
                    constraints
                );
            }
        };

        suggestionsTask.setOnSucceeded(event -> {
            latestSuggestions = suggestionsTask.getValue();
            if (latestSuggestions != null) {
                titleField.setText(latestSuggestions.suggestedTitle());
                objectivesArea.setText(latestSuggestions.suggestedObjectives());
                descriptionArea.setText(latestSuggestions.suggestedDescription());
                aiSummaryLabel.setText(latestSuggestions.summary());
            } else {
                aiSummaryLabel.setText("Aucune suggestion générée.");
            }
            setGenerationInProgress(false);
            suggestionsTask = null;
        });

        suggestionsTask.setOnFailed(event -> {
            Throwable error = suggestionsTask.getException();
            aiSummaryLabel.setText("Échec de génération des suggestions.");
            showAlert("Erreur", error == null ? "Erreur inattendue." : "Erreur lors de la génération: " + safe(error.getMessage()));
            setGenerationInProgress(false);
            suggestionsTask = null;
        });

        Thread thread = new Thread(suggestionsTask, "accompaniment-suggestions-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void setGenerationInProgress(boolean inProgress) {
        goalField.setDisable(inProgress);
        dietStyleField.setDisable(inProgress);
        allergiesField.setDisable(inProgress);
        levelField.setDisable(inProgress);
        constraintsArea.setDisable(inProgress);
        nutritionDaysSpinner.setDisable(inProgress);
        daysPerWeekSpinner.setDisable(inProgress);
        minutesSpinner.setDisable(inProgress);
    }

    @FXML
    private void handleCreatePlan() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        String validationError = validateForm();
        if (validationError != null) {
            showAlert("Erreur", validationError);
            return;
        }

        List<PlanExercice> exercises = latestSuggestions == null ? new ArrayList<>() : latestSuggestions.exercisePlans();
        List<PlanRegime> diets = latestSuggestions == null ? new ArrayList<>() : latestSuggestions.dietPlans();
        var plan = service.createPlan(
            currentUser,
            patient,
            safe(titleField.getText()),
            safe(objectivesArea.getText()),
            safe(descriptionArea.getText()),
            statusCombo.getValue(),
            startDatePicker.getValue() == null ? LocalDateTime.now() : startDatePicker.getValue().atStartOfDay(),
            durationSpinner.getValue(),
            exercises,
            diets
        );
        if (plan == null) {
            showAlert("Erreur", "Création du plan impossible pour ce profil.");
            return;
        }
        AppNavigator.showAccompanimentPlanShow(plan.getId());
    }

    private String validateForm() {
        String title = safe(titleField.getText());
        String objectives = safe(objectivesArea.getText());
        String description = safe(descriptionArea.getText());

        if (title.isBlank()) {
            return "Veuillez renseigner un titre.";
        }
        if (title.length() < 3 || title.length() > 120) {
            return "Le titre doit contenir entre 3 et 120 caractères.";
        }

        if (objectives.isBlank()) {
            return "Veuillez renseigner les objectifs.";
        }
        if (objectives.length() < 10 || objectives.length() > 2000) {
            return "Les objectifs doivent contenir entre 10 et 2000 caractères.";
        }

        if (description.isBlank()) {
            return "Veuillez renseigner la description.";
        }
        if (description.length() < 10 || description.length() > 4000) {
            return "La description doit contenir entre 10 et 4000 caractères.";
        }

        String status = statusCombo.getValue();
        if (status == null || status.isBlank()) {
            return "Veuillez sélectionner un statut.";
        }

        if (startDatePicker.getValue() == null) {
            return "Veuillez sélectionner une date de début.";
        }

        Integer duration = durationSpinner.getValue();
        if (duration == null || duration < 1 || duration > 52) {
            return "La durée doit être entre 1 et 52 semaines.";
        }

        Integer nutritionDays = nutritionDaysSpinner.getValue();
        if (nutritionDays == null || nutritionDays < 1 || nutritionDays > 14) {
            return "Le nombre de jours nutrition doit être entre 1 et 14.";
        }

        Integer daysPerWeek = daysPerWeekSpinner.getValue();
        if (daysPerWeek == null || daysPerWeek < 1 || daysPerWeek > 7) {
            return "Le nombre de jours/semaine doit être entre 1 et 7.";
        }

        Integer minutes = minutesSpinner.getValue();
        if (minutes == null || minutes < 10 || minutes > 120) {
            return "La durée d'activité doit être entre 10 et 120 minutes.";
        }

        if (safe(goalField.getText()).length() > 250) {
            return "L'objectif principal est trop long (max 250 caractères).";
        }
        if (safe(dietStyleField.getText()).length() > 120) {
            return "Le style alimentaire est trop long (max 120 caractères).";
        }
        if (safe(allergiesField.getText()).length() > 250) {
            return "Le champ allergies est trop long (max 250 caractères).";
        }
        if (safe(levelField.getText()).length() > 80) {
            return "Le niveau d'activité est trop long (max 80 caractères).";
        }
        if (safe(constraintsArea.getText()).length() > 2000) {
            return "Les contraintes sont trop longues (max 2000 caractères).";
        }

        return null;
    }

    @FXML
    private void handleCancel() {
        AppNavigator.showAccompanimentPlans();
    }

    private String displayUser(User user) {
        if (user == null) return "Utilisateur";
        String full = (safe(user.getPrenom()) + " " + safe(user.getNom())).trim();
        return full.isBlank() ? safe(user.getEmail()) : full;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
