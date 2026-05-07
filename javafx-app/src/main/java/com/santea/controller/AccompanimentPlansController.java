package com.santea.controller;

import com.santea.model.AccompanimentPlan;
import com.santea.model.Patient;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccompanimentPlansController extends AppBaseViewController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Label pageTitleLabel;
    @FXML private Label pageSubtitleLabel;
    @FXML private Label activeCountLabel;
    @FXML private Label secondaryCountLabel;
    @FXML private Label tertiaryCountLabel;
    @FXML private Label secondaryCaptionLabel;
    @FXML private Label tertiaryCaptionLabel;
    @FXML private VBox createSection;
    @FXML private VBox patientCreateContainer;
    @FXML private VBox plansContainer;

    private final AccompanimentPlanService service = new AccompanimentPlanService();

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        loadData();
    }

    private void loadData() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        boolean isPatient = isPatient(currentUser);
        boolean isMedecin = isMedecin(currentUser);
        pageTitleLabel.setText(isPatient ? "Plans d'accompagnement" : "Mes plans d'accompagnement");
        pageSubtitleLabel.setText(isPatient
            ? "Suivez vos programmes personnalisés"
            : "Gérez vos programmes personnalisés pour vos patients");

        List<AccompanimentPlan> plans = service.findPlansForUser(currentUser);
        Map<String, Integer> stats = service.getStatistics(currentUser);
        activeCountLabel.setText(String.valueOf(stats.getOrDefault("active", 0)));
        if (isPatient) {
            secondaryCountLabel.setText(String.valueOf(stats.getOrDefault("completed", 0)));
            tertiaryCountLabel.setText(String.valueOf(stats.getOrDefault("cancelled", 0)));
            secondaryCaptionLabel.setText("Plans terminés");
            tertiaryCaptionLabel.setText("Plans interrompus");
        } else {
            secondaryCountLabel.setText(String.valueOf(stats.getOrDefault("uniquePatients", 0)));
            tertiaryCountLabel.setText(String.valueOf(stats.getOrDefault("completed", 0)));
            secondaryCaptionLabel.setText("Patients suivis");
            tertiaryCaptionLabel.setText("Plans complétés");
        }

        createSection.setManaged(!isPatient && !isMedecin);
        createSection.setVisible(!isPatient && !isMedecin);
        if (!isPatient && !isMedecin) {
            renderPatients(patientCreateContainer, service.findAvailablePatients());
        }
        renderPlans(plansContainer, plans, isPatient, isMedecin);
    }

    private void renderPatients(VBox container, List<Patient> patients) {
        container.getChildren().clear();
        if (patients.isEmpty()) {
            container.getChildren().add(buildEmptyState("Aucun patient disponible", "Il n'y a pas de patients enregistrés pour le moment.", null));
            return;
        }
        for (Patient patient : patients) {
            HBox row = new HBox(14);
            row.getStyleClass().add("plan-patient-row");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(16));

            VBox avatar = new VBox();
            avatar.setAlignment(Pos.CENTER);
            avatar.getStyleClass().add("plan-avatar-chip");
            Label avatarText = new Label(initials(patient.getUser()));
            avatarText.getStyleClass().add("plan-avatar-text");
            avatar.getChildren().add(avatarText);

            VBox info = new VBox(4);
            Label name = new Label(displayUser(patient.getUser()));
            name.getStyleClass().add("plan-card-title");
            Label email = new Label(patient.getUser() == null ? "" : safe(patient.getUser().getEmail()));
            email.getStyleClass().add("plan-card-subtitle");
            info.getChildren().addAll(name, email);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button createButton = new Button("Créer un plan");
            createButton.getStyleClass().addAll("btn-primary", "empty-state-button");
            createButton.setOnAction(event -> AppNavigator.showAccompanimentPlanCreate(patient.getUser().getId()));

            row.getChildren().addAll(avatar, info, spacer, createButton);
            container.getChildren().add(row);
        }
    }

    private void renderPlans(VBox container, List<AccompanimentPlan> plans, boolean isPatient, boolean isMedecin) {
        container.getChildren().clear();
        if (plans.isEmpty()) {
            String subtitle = isPatient
                ? "Commencez un nouveau programme personnalisé."
                : "Créez votre premier plan pour commencer à accompagner vos patients.";
            container.getChildren().add(buildEmptyState("Aucun plan d'accompagnement", subtitle, null));
            return;
        }
        for (AccompanimentPlan plan : plans) {
            VBox card = new VBox(12);
            card.getStyleClass().add("plan-list-card");
            card.setPadding(new Insets(18));

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);
            Label title = new Label(plan.getTitle());
            title.getStyleClass().add("plan-card-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label status = new Label(statusLabel(plan));
            status.getStyleClass().addAll("plan-status-pill", statusClass(plan));
            header.getChildren().addAll(title, spacer, status);

            Label patientLine = new Label(isPatient
                ? buildProfessionalLine(plan)
                : "Patient: " + displayUser(plan.getPatient() == null ? null : plan.getPatient().getUser()));
            patientLine.getStyleClass().add("plan-card-subtitle");

            Label objectives = new Label(safe(plan.getObjectives()).isBlank() ? "Objectifs non précisés." : safe(plan.getObjectives()));
            objectives.getStyleClass().add("plan-card-description");
            objectives.setWrapText(true);

            VBox progressShell = new VBox(6);
            Label progressCaption = new Label("Progression " + plan.getProgressPercentage() + "%");
            progressCaption.getStyleClass().add("plan-card-subtitle");
            Region track = new Region();
            track.getStyleClass().add("plan-progress-track");
            Region bar = new Region();
            bar.getStyleClass().add("plan-progress-bar");
            bar.setPrefWidth(Math.max(20, plan.getProgressPercentage() * 2.2));
            StackPaneLike progress = new StackPaneLike(track, bar);
            progressShell.getChildren().addAll(progressCaption, progress);

            Label meta = new Label("Début: " + formatDate(plan.getStartDate()) + " • Durée: " + (plan.getDurationWeeks() == null ? "--" : plan.getDurationWeeks() + " semaines"));
            meta.getStyleClass().add("plan-card-subtitle");

            HBox actions = new HBox(8);
            Button viewButton = new Button("Voir");
            viewButton.getStyleClass().add("btn-info");
            viewButton.setOnAction(event -> {
                event.consume();
                AppNavigator.showAccompanimentPlanShow(plan.getId());
            });
            actions.getChildren().add(viewButton);
            if (!isPatient && !isMedecin && service.canDeletePlan(plan, AuthSession.getCurrentUser())) {
                Button deleteButton = new Button("Supprimer");
                deleteButton.getStyleClass().add("btn-danger");
                deleteButton.setOnAction(event -> {
                    event.consume();
                    service.deletePlan(plan.getId(), AuthSession.getCurrentUser());
                    loadData();
                });
                actions.getChildren().add(deleteButton);
            }

            card.getChildren().addAll(header, patientLine, objectives, progressShell, meta, actions);
            card.setOnMouseClicked(event -> AppNavigator.showAccompanimentPlanShow(plan.getId()));
            container.getChildren().add(card);
        }
    }

    private VBox buildEmptyState(String title, String subtitle, Runnable action) {
        VBox box = new VBox(8);
        box.getStyleClass().add("plan-empty-state");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36));
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("tele-empty-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("tele-empty-subtitle");
        subtitleLabel.setWrapText(true);
        box.getChildren().addAll(titleLabel, subtitleLabel);
        if (action != null) {
            Button button = new Button("Créer un plan");
            button.getStyleClass().addAll("btn-primary", "empty-state-button");
            button.setOnAction(event -> action.run());
            box.getChildren().add(button);
        }
        return box;
    }

    private boolean isPatient(User user) {
        return "ROLE_PATIENT".equals(role(user));
    }

    private boolean isMedecin(User user) {
        return "ROLE_MEDECIN".equals(role(user));
    }

    private String role(User user) {
        return safe(user == null ? "" : (safe(user.getSubscriptionType()).isBlank() ? user.getRole() : user.getSubscriptionType()));
    }

    private String displayUser(User user) {
        if (user == null) {
            return "Utilisateur";
        }
        String full = (safe(user.getPrenom()) + " " + safe(user.getNom())).trim();
        return full.isBlank() ? safe(user.getEmail()) : full;
    }

    private String buildProfessionalLine(AccompanimentPlan plan) {
        List<String> parts = new ArrayList<>();
        if (plan.getCoach() != null && plan.getCoach().getUser() != null) {
            parts.add("Coach: " + displayUser(plan.getCoach().getUser()));
        }
        if (plan.getNutritionist() != null && plan.getNutritionist().getUser() != null) {
            parts.add("Nutritionniste: " + displayUser(plan.getNutritionist().getUser()));
        }
        return parts.isEmpty() ? "Aucun professionnel assigné" : String.join(" • ", parts);
    }

    private String statusLabel(AccompanimentPlan plan) {
        String status = safe(plan.getStatus());
        return switch (status) {
            case "active" -> "ACTIF";
            case "completed" -> "TERMINÉ";
            case "cancelled" -> "ANNULÉ";
            case "paused" -> "EN PAUSE";
            default -> status.toUpperCase();
        };
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

    private String initials(User user) {
        String first = safe(user == null ? "" : user.getPrenom());
        String last = safe(user == null ? "" : user.getNom());
        String result = (first.isEmpty() ? "" : first.substring(0, 1)) + (last.isEmpty() ? "" : last.substring(0, 1));
        if (result.isBlank()) {
            String email = safe(user == null ? "" : user.getEmail());
            return email.length() >= 2 ? email.substring(0, 2).toUpperCase() : "PL";
        }
        return result.toUpperCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class StackPaneLike extends VBox {
        private StackPaneLike(Region track, Region bar) {
            setAlignment(Pos.CENTER_LEFT);
            getStyleClass().add("plan-progress-shell");
            track.setPrefHeight(10);
            bar.setPrefHeight(10);
            getChildren().addAll(track, bar);
            bar.setTranslateY(-10);
        }
    }
}
