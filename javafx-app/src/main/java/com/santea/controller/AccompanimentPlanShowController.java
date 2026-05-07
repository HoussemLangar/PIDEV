package com.santea.controller;

import com.santea.model.AccompanimentPlan;
import com.santea.model.PlanExercice;
import com.santea.model.PlanRegime;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.navigation.ModuleContext;
import com.santea.service.AccompanimentPlanService;
import com.santea.service.AuthSession;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;

public class AccompanimentPlanShowController extends AppBaseViewController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label titleLabel;
    @FXML private Label statusLabel;
    @FXML private Label patientNameLabel;
    @FXML private Label patientEmailLabel;
    @FXML private Label progressLabel;
    @FXML private Label durationLabel;
    @FXML private Label startDateLabel;
    @FXML private Label endDateLabel;
    @FXML private Label objectivesLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label coachLabel;
    @FXML private Label nutritionistLabel;
    @FXML private Label aiSummaryLabel;
    @FXML private VBox exerciseContainer;
    @FXML private VBox dietContainer;
    @FXML private Button backButton;
    @FXML private Button deleteButton;

    private final AccompanimentPlanService service = new AccompanimentPlanService();
    private AccompanimentPlan plan;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        Integer id = ModuleContext.getAccompanimentPlanId();
        plan = service.getPlan(id);
        if (plan == null || !service.canAccessPlan(plan, AuthSession.getCurrentUser())) {
            throw new IllegalStateException("Plan d'accompagnement introuvable");
        }
        backButton.setOnAction(event -> AppNavigator.showAccompanimentPlans());
        deleteButton.setOnAction(event -> {
            service.deletePlan(plan.getId(), AuthSession.getCurrentUser());
            AppNavigator.showAccompanimentPlans();
        });
        render();
    }

    private void render() {
        User patientUser = plan.getPatient() == null ? null : plan.getPatient().getUser();
        titleLabel.setText(plan.getTitle());
        statusLabel.setText(statusText(plan));
        statusLabel.getStyleClass().add(statusClass(plan));
        patientNameLabel.setText(displayUser(patientUser));
        patientEmailLabel.setText(patientUser == null ? "" : safe(patientUser.getEmail()));
        progressLabel.setText(plan.getProgressPercentage() + "%");
        durationLabel.setText(plan.getDurationWeeks() == null ? "--" : plan.getDurationWeeks() + " semaines");
        startDateLabel.setText(formatDate(plan.getStartDate()));
        endDateLabel.setText(formatDate(plan.getEndDate()));
        objectivesLabel.setText(safe(plan.getObjectives()));
        descriptionLabel.setText(safe(plan.getDescription()));
        coachLabel.setText(plan.getCoach() == null || plan.getCoach().getUser() == null ? "Non assigné" : displayUser(plan.getCoach().getUser()));
        nutritionistLabel.setText(plan.getNutritionist() == null || plan.getNutritionist().getUser() == null ? "Non assigné" : displayUser(plan.getNutritionist().getUser()));
        aiSummaryLabel.setText(buildAiSummary());
        deleteButton.setVisible(service.canDeletePlan(plan, AuthSession.getCurrentUser()));
        deleteButton.setManaged(deleteButton.isVisible());
        renderExercises();
        renderDiets();
    }

    private void renderExercises() {
        exerciseContainer.getChildren().clear();
        if (plan.getExercisePlans().isEmpty()) {
            exerciseContainer.getChildren().add(buildMiniEmpty("Aucun plan d'exercice"));
            return;
        }
        for (PlanExercice exercise : plan.getExercisePlans()) {
            VBox card = new VBox(6);
            card.getStyleClass().add("plan-detail-card");
            card.setPadding(new Insets(14));
            Label title = new Label(exercise.getTitre());
            title.getStyleClass().add("plan-card-title");
            Label meta = new Label(exercise.getFrequence() + " • " + (exercise.getDureMinutes() == null ? "--" : exercise.getDureMinutes() + " min"));
            meta.getStyleClass().add("plan-card-subtitle");
            Label desc = new Label(safe(exercise.getDescription()));
            desc.getStyleClass().add("plan-card-description");
            desc.setWrapText(true);
            card.getChildren().addAll(title, meta, desc);
            exerciseContainer.getChildren().add(card);
        }
    }

    private void renderDiets() {
        dietContainer.getChildren().clear();
        if (plan.getDietPlans().isEmpty()) {
            dietContainer.getChildren().add(buildMiniEmpty("Aucun plan nutritionnel"));
            return;
        }
        for (PlanRegime diet : plan.getDietPlans()) {
            VBox card = new VBox(6);
            card.getStyleClass().add("plan-detail-card");
            card.setPadding(new Insets(14));
            Label title = new Label(diet.getTitre());
            title.getStyleClass().add("plan-card-title");
            String calories = diet.getCaloriesJour() == null ? "" : " • " + diet.getCaloriesJour() + " cal/jour";
            Label meta = new Label(safe(diet.getTypeRegime()) + calories);
            meta.getStyleClass().add("plan-card-subtitle");
            Label desc = new Label(safe(diet.getDescription()));
            desc.getStyleClass().add("plan-card-description");
            desc.setWrapText(true);
            card.getChildren().addAll(title, meta, desc);
            dietContainer.getChildren().add(card);
        }
    }

    private HBox buildMiniEmpty(String text) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("plan-empty-mini");
        row.setPadding(new Insets(12));
        Label label = new Label(text);
        label.getStyleClass().add("plan-card-subtitle");
        row.getChildren().add(label);
        return row;
    }

    private String buildAiSummary() {
        return "Synthèse IA: plan axé sur " + (safe(plan.getObjectives()).isBlank() ? "l'accompagnement global" : safe(plan.getObjectives()).split("\n")[0].toLowerCase());
    }

    private String displayUser(User user) {
        if (user == null) return "Utilisateur";
        String full = (safe(user.getPrenom()) + " " + safe(user.getNom())).trim();
        return full.isBlank() ? safe(user.getEmail()) : full;
    }

    private String statusText(AccompanimentPlan plan) {
        if (plan.isActive()) return "ACTIF";
        if (plan.isCompleted()) return "TERMINÉ";
        if (plan.isCancelled()) return "ANNULÉ";
        if ("paused".equalsIgnoreCase(safe(plan.getStatus()))) return "EN PAUSE";
        return safe(plan.getStatus()).toUpperCase();
    }

    private String statusClass(AccompanimentPlan plan) {
        if (plan.isActive()) return "plan-status-active";
        if (plan.isCompleted()) return "plan-status-completed";
        if (plan.isCancelled()) return "plan-status-cancelled";
        return "plan-status-draft";
    }

    private String formatDate(java.time.LocalDateTime value) {
        return value == null ? "--" : value.format(DATE_FORMAT);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
