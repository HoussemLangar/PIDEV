package com.santea.controller;

import com.santea.model.Teleconsultation;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.TeleconsultationService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class TeleconsultationController extends AppBaseViewController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm");

    @FXML private VBox ongoingContainer;
    @FXML private VBox upcomingContainer;
    @FXML private VBox pastContainer;
    @FXML private Label completedCountLabel;
    @FXML private Label pendingCountLabel;
    @FXML private Label hoursCountLabel;
    @FXML private Label averageMinutesLabel;
    @FXML private Button scheduleConsultationButton;
    @FXML private TabPane consultationTabs;

    private final TeleconsultationService teleconsultationService = new TeleconsultationService();

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        scheduleConsultationButton.setOnAction(event -> AppNavigator.showTeleconsultationSchedule());
        loadData();
    }

    private void loadData() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }

        List<Teleconsultation> ongoing = teleconsultationService.findOngoing(currentUser);
        List<Teleconsultation> upcoming = teleconsultationService.findUpcoming(currentUser);
        List<Teleconsultation> past = teleconsultationService.findPast(currentUser);
        Map<String, Object> stats = teleconsultationService.getStatistics(currentUser.getId().toString());

        completedCountLabel.setText(String.valueOf(stats.getOrDefault("completed", 0)));
        pendingCountLabel.setText(String.valueOf(stats.getOrDefault("pending", 0)));
        long totalDuration = ((Number) stats.getOrDefault("totalDuration", 0)).longValue();
        long avgDuration = ((Number) stats.getOrDefault("avgDuration", 0)).longValue();
        hoursCountLabel.setText(String.valueOf(Math.round(totalDuration / 3600.0)));
        averageMinutesLabel.setText(String.valueOf(Math.round(avgDuration / 60.0)));
        applyTabLabels(ongoing.size(), upcoming.size(), past.size());

        renderGroup(ongoingContainer, ongoing, true);
        renderGroup(upcomingContainer, upcoming, false);
        renderPastGroup(pastContainer, past);
    }

    private void applyTabLabels(int ongoingCount, int upcomingCount, int pastCount) {
        if (consultationTabs == null || consultationTabs.getTabs().size() < 3) {
            return;
        }
        List<Tab> tabs = consultationTabs.getTabs();
        tabs.get(0).setText("En cours (" + ongoingCount + ")");
        tabs.get(1).setText("À venir (" + upcomingCount + ")");
        tabs.get(2).setText("Historique (" + pastCount + ")");
    }

    private void renderGroup(VBox container, List<Teleconsultation> consultations, boolean ongoing) {
        container.getChildren().clear();
        if (consultations.isEmpty()) {
            container.getChildren().add(buildEmptyState(
                ongoing ? "Aucune consultation en cours" : "Aucune consultation programmée",
                ongoing ? null : "Planifier une consultation",
                ongoing ? null : AppNavigator::showTeleconsultationSchedule
            ));
            return;
        }
        for (Teleconsultation consultation : consultations) {
            VBox card = new VBox(10);
            card.getStyleClass().addAll("tele-consultation-card", ongoing ? "tele-ongoing-card" : "tele-upcoming-card");
            card.setPadding(new Insets(18));

            User otherUser = resolveOtherUser(consultation);

            HBox header = new HBox(10);
            header.setAlignment(Pos.CENTER_LEFT);
            VBox titleGroup = new VBox(4);
            Label title = new Label(mapTypeLabel(consultation.getType()));
            title.getStyleClass().add("tele-card-title");
            Label participantLabel = new Label("Participant");
            participantLabel.getStyleClass().add("tele-card-label");
            titleGroup.getChildren().addAll(title, participantLabel);
            Region headerSpacer = new Region();
            HBox.setHgrow(headerSpacer, Priority.ALWAYS);
            Label status = new Label(mapStatusLabel(consultation));
            status.getStyleClass().addAll("tele-status-pill", resolveStatusClass(consultation));
            header.getChildren().addAll(buildAvatarChip(otherUser), titleGroup, headerSpacer, status);

            Label participant = new Label(displayUser(otherUser));
            participant.getStyleClass().add("tele-card-participant");
            Label when = new Label(consultation.getScheduledAt() == null ? "Date non définie" : consultation.getScheduledAt().format(DATE_TIME_FORMAT));
            when.getStyleClass().add("tele-card-subtitle");
            Label meta = new Label(consultation.getDescription() == null || consultation.getDescription().isBlank()
                ? "Visioconférence sécurisée disponible depuis le détail."
                : consultation.getDescription());
            meta.getStyleClass().add("tele-card-description");

            HBox actions = new HBox(8);
            Button viewButton = buildActionButton("i", "Voir", "btn-secondary");
            viewButton.getStyleClass().add("btn-secondary");
            viewButton.setOnAction(event -> {
                event.consume();
                AppNavigator.showTeleconsultationShow(consultation.getId());
            });
            actions.getChildren().add(viewButton);
            if (teleconsultationService.canStartConsultation(consultation)) {
                Button joinButton = buildActionButton(">", "Rejoindre", "btn-success");
                joinButton.getStyleClass().add("btn-success");
                joinButton.setOnAction(event -> {
                    event.consume();
                    AppNavigator.showTeleconsultationJoin(consultation.getId());
                });
                actions.getChildren().add(0, joinButton);
            }
            if (canApprove(consultation)) {
                Button approveButton = buildActionButton("+", "Approuver", "btn-success");
                approveButton.getStyleClass().add("btn-success");
                approveButton.setOnAction(event -> {
                    event.consume();
                    teleconsultationService.approveConsultation(String.valueOf(consultation.getId()));
                    loadData();
                });
                actions.getChildren().add(approveButton);
            }
            if (canCancel(consultation)) {
                Button cancelButton = buildActionButton("x", "Annuler", "btn-danger");
                cancelButton.getStyleClass().add("btn-danger");
                cancelButton.setOnAction(event -> {
                    event.consume();
                    teleconsultationService.cancelTeleconsultation(String.valueOf(consultation.getId()));
                    loadData();
                });
                actions.getChildren().add(cancelButton);
            }

            card.getChildren().addAll(header, participant, when, meta, actions);
            card.setOnMouseClicked(event -> AppNavigator.showTeleconsultationShow(consultation.getId()));
            container.getChildren().add(card);
        }
    }

    private void renderPastGroup(VBox container, List<Teleconsultation> consultations) {
        container.getChildren().clear();
        if (consultations.isEmpty()) {
            container.getChildren().add(buildEmptyState("Aucune consultation passée", null, null));
            return;
        }
        for (Teleconsultation consultation : consultations) {
            HBox row = new HBox(12);
            row.getStyleClass().add("tele-history-row");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(14, 16, 14, 16));
            VBox info = new VBox(4);
            Label title = new Label(mapTypeLabel(consultation.getType()) + " • " + displayUser(resolveOtherUser(consultation)));
            title.getStyleClass().add("tele-card-title");
            Label date = new Label(consultation.getScheduledAt() == null ? "Date non définie" : consultation.getScheduledAt().format(DATE_TIME_FORMAT));
            date.getStyleClass().add("tele-card-subtitle");
            info.getChildren().addAll(title, date);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label status = new Label(mapStatusLabel(consultation));
            status.getStyleClass().addAll("tele-status-pill", resolveStatusClass(consultation));
            Button detailsButton = buildActionButton(">", "Details", "btn-secondary");
            detailsButton.getStyleClass().add("btn-secondary");
            detailsButton.setOnAction(event -> {
                event.consume();
                AppNavigator.showTeleconsultationShow(consultation.getId());
            });
            Label arrow = new Label(">");
            arrow.getStyleClass().add("tele-row-arrow");
            row.getChildren().addAll(info, spacer, status, detailsButton, arrow);
            row.setOnMouseClicked(event -> AppNavigator.showTeleconsultationShow(consultation.getId()));
            container.getChildren().add(row);
        }
    }

    private VBox buildEmptyState(String title, String actionLabel, Runnable action) {
        VBox box = new VBox(8);
        box.getStyleClass().add("tele-empty-state");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36));
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("tele-empty-title");
        Label subtitleLabel = new Label("Les consultations apparaîtront ici.");
        subtitleLabel.getStyleClass().add("tele-empty-subtitle");
        box.getChildren().addAll(titleLabel, subtitleLabel);
        if (actionLabel != null && action != null) {
            Button actionButton = new Button(actionLabel);
            actionButton.getStyleClass().addAll("btn-primary", "empty-state-button");
            actionButton.setOnAction(event -> action.run());
            box.getChildren().add(actionButton);
        }
        return box;
    }

    private boolean canApprove(Teleconsultation consultation) {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || consultation == null || consultation.getRecipient() == null) {
            return false;
        }
        String role = safe(currentUser.getSubscriptionType()).isBlank() ? safe(currentUser.getRole()) : safe(currentUser.getSubscriptionType());
        return consultation.isRequested()
            && consultation.getRecipient().getId().equals(currentUser.getId())
            && (role.contains("MEDECIN") || role.contains("COACH") || role.contains("NUTRITIONNISTE"));
    }

    private boolean canCancel(Teleconsultation consultation) {
        User currentUser = AuthSession.getCurrentUser();
        return currentUser != null
            && consultation != null
            && consultation.getInitiator() != null
            && consultation.getInitiator().getId().equals(currentUser.getId())
            && !consultation.isCompleted()
            && !consultation.isCancel();
    }

    private User resolveOtherUser(Teleconsultation consultation) {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || consultation == null) {
            return null;
        }
        if (consultation.getInitiator() != null && consultation.getInitiator().getId().equals(currentUser.getId())) {
            return consultation.getRecipient();
        }
        return consultation.getInitiator();
    }

    private String displayUser(User user) {
        if (user == null) {
            return "Utilisateur";
        }
        String full = ((user.getPrenom() == null ? "" : user.getPrenom()) + " " + (user.getNom() == null ? "" : user.getNom())).trim();
        return full.isBlank() ? (user.getEmail() == null ? "Utilisateur" : user.getEmail()) : full;
    }

    private HBox buildAvatarChip(User user) {
        HBox avatar = new HBox();
        avatar.setAlignment(Pos.CENTER);
        avatar.getStyleClass().add("tele-list-avatar");
        Label initials = new Label(initialsFor(user));
        initials.getStyleClass().add("tele-list-avatar-text");
        avatar.getChildren().add(initials);
        return avatar;
    }

    private String initialsFor(User user) {
        if (user == null) {
            return "US";
        }
        String first = safe(user.getPrenom());
        String last = safe(user.getNom());
        String initials = (first.isEmpty() ? "" : first.substring(0, 1)) + (last.isEmpty() ? "" : last.substring(0, 1));
        if (initials.isBlank()) {
            String email = safe(user.getEmail());
            return email.length() >= 2 ? email.substring(0, 2).toUpperCase() : "US";
        }
        return initials.toUpperCase();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private Button buildActionButton(String symbol, String text, String... styleClasses) {
        Button button = new Button();
        button.getStyleClass().add("row-action-button");
        button.getStyleClass().addAll(styleClasses);
        HBox graphic = new HBox(6);
        graphic.getStyleClass().add("row-action-graphic");
        Label icon = new Label(symbol);
        icon.getStyleClass().add("row-action-icon");
        Label label = new Label(text);
        label.getStyleClass().add("row-action-label");
        graphic.getChildren().addAll(icon, label);
        button.setGraphic(graphic);
        return button;
    }

    private String mapTypeLabel(String type) {
        if (type == null) return "Consultation";
        return switch (type) {
            case "general" -> "Consultation générale";
            case "follow_up" -> "Suivi";
            case "emergency" -> "Urgence";
            case "diagnostic" -> "Diagnostic";
            default -> type;
        };
    }

    private String mapStatusLabel(Teleconsultation consultation) {
        if (consultation.isOngoing()) return "EN COURS";
        if (consultation.isRequested()) return "EN ATTENTE D'APPROBATION";
        if (consultation.isPending()) return "EN ATTENTE";
        if (consultation.isCompleted()) return "COMPLÉTÉE";
        if (consultation.isCancel()) return "ANNULÉE";
        return consultation.getStatus();
    }

    private String resolveStatusClass(Teleconsultation consultation) {
        if (consultation.isOngoing()) return "tele-status-live";
        if (consultation.isRequested()) return "tele-status-warning";
        if (consultation.isPending()) return "tele-status-pending";
        if (consultation.isCompleted()) return "tele-status-success";
        if (consultation.isCancel()) return "tele-status-danger";
        return "tele-status-neutral";
    }
}
