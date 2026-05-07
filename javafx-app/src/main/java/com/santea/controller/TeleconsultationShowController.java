package com.santea.controller;

import com.santea.model.Teleconsultation;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.navigation.ModuleContext;
import com.santea.service.AuthSession;
import com.santea.service.TeleconsultationService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TeleconsultationShowController extends AppBaseViewController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML private Button backButton;
    @FXML private Button joinButton;
    @FXML private Button approveButton;
    @FXML private Button rejectButton;
    @FXML private Button cancelButton;
    @FXML private Button endButton;
    @FXML private Button rescheduleButton;
    @FXML private Label statusLabel;
    @FXML private Label participantLabel;
    @FXML private Label participantInitialsLabel;
    @FXML private Label participantMetaLabel;
    @FXML private Label participantFullMetaLabel;
    @FXML private Label typeLabel;
    @FXML private Label dateLabel;
    @FXML private Label timeLabel;
    @FXML private Label durationLabel;
    @FXML private Label createdAtLabel;
    @FXML private Label initiatorLabel;
    @FXML private Label sidebarStatusLabel;
    @FXML private TextArea descriptionArea;
    @FXML private DatePicker rescheduleDatePicker;
    @FXML private TextArea rescheduleTimeField;

    private final TeleconsultationService service = new TeleconsultationService();
    private Teleconsultation consultation;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        consultation = loadConsultation();
        backButton.setOnAction(event -> AppNavigator.showTeleconsultation());
        joinButton.setOnAction(event -> AppNavigator.showTeleconsultationJoin(consultation.getId()));
        approveButton.setOnAction(event -> updateConsultation("approve"));
        rejectButton.setOnAction(event -> updateConsultation("reject"));
        cancelButton.setOnAction(event -> updateConsultation("cancel"));
        endButton.setOnAction(event -> updateConsultation("end"));
        rescheduleButton.setOnAction(event -> reschedule());
        rescheduleTimeField.setText("10:00");
        render();
    }

    private Teleconsultation loadConsultation() {
        Integer id = ModuleContext.getTeleconsultationId();
        if (id == null) {
            throw new IllegalStateException("Aucune téléconsultation sélectionnée");
        }
        Teleconsultation found = service.getTeleconsultation(String.valueOf(id));
        if (found == null) {
            throw new IllegalStateException("Téléconsultation introuvable");
        }
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || !isParticipant(found, currentUser)) {
            throw new IllegalStateException("Accès refusé à cette téléconsultation");
        }
        return found;
    }

    private boolean isParticipant(Teleconsultation consultation, User user) {
        if (consultation == null || user == null || user.getId() == null) {
            return false;
        }
        return (consultation.getInitiator() != null && consultation.getInitiator().getId() != null
                && consultation.getInitiator().getId().equals(user.getId()))
            || (consultation.getRecipient() != null && consultation.getRecipient().getId() != null
                && consultation.getRecipient().getId().equals(user.getId()));
    }

    private void render() {
        User otherUser = resolveOtherUser();
        participantLabel.setText(displayUser(otherUser));
        participantMetaLabel.setText(otherUser == null ? "" : safe(otherUser.getEmail()));
        participantFullMetaLabel.setText(otherUser == null ? "" : buildRoleLine(otherUser));
        participantInitialsLabel.setText(computeInitials(otherUser));
        typeLabel.setText(mapTypeLabel(consultation.getType()));
        dateLabel.setText(consultation.getScheduledAt() == null ? "--" : consultation.getScheduledAt().format(DATE_FORMAT));
        timeLabel.setText(consultation.getScheduledAt() == null ? "--" : consultation.getScheduledAt().format(TIME_FORMAT));
        durationLabel.setText(consultation.getDurationFormatted());
        createdAtLabel.setText(consultation.getCreatedAt() == null ? "--" : consultation.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        descriptionArea.setText(safe(consultation.getDescription()));
        statusLabel.setText(mapStatusLabel(consultation));
        sidebarStatusLabel.setText(mapStatusLabel(consultation));
        initiatorLabel.setText(displayUser(consultation.getInitiator()));
        statusLabel.getStyleClass().removeAll("tele-status-live", "tele-status-warning", "tele-status-pending", "tele-status-success", "tele-status-danger", "tele-status-neutral");
        statusLabel.getStyleClass().add(resolveStatusClass(consultation));

        joinButton.setDisable(!service.canStartConsultation(consultation));
        approveButton.setDisable(!canApprove());
        rejectButton.setDisable(!canApprove());
        cancelButton.setDisable(!canCancel());
        endButton.setDisable(!consultation.isOngoing());
        rescheduleButton.setDisable(!(consultation.isPending() || consultation.isRequested()));
    }

    private void updateConsultation(String action) {
        switch (action) {
            case "approve" -> service.approveConsultation(String.valueOf(consultation.getId()));
            case "reject" -> service.rejectConsultation(String.valueOf(consultation.getId()));
            case "cancel" -> service.cancelTeleconsultation(String.valueOf(consultation.getId()));
            case "end" -> service.endTeleconsultation(String.valueOf(consultation.getId()), consultation.getDescription());
            default -> {
                return;
            }
        }
        consultation = loadConsultation();
        render();
    }

    private void reschedule() {
        if (rescheduleDatePicker.getValue() == null) {
            showAlert("Erreur", "Veuillez sélectionner une date.");
            return;
        }
        try {
            String rawTime = rescheduleTimeField.getText() == null ? "" : rescheduleTimeField.getText().trim();
            if (rawTime.isBlank()) {
                showAlert("Erreur", "Veuillez saisir une heure (HH:mm).");
                return;
            }
            LocalTime time = LocalTime.parse(rawTime);
            LocalDateTime scheduledAt = LocalDateTime.of(rescheduleDatePicker.getValue(), time);
            if (scheduledAt.isBefore(LocalDateTime.now().plusMinutes(5))) {
                showAlert("Erreur", "Veuillez choisir un créneau futur (au moins 5 minutes). ");
                return;
            }
            service.rescheduleTeleconsultation(String.valueOf(consultation.getId()), scheduledAt);
            consultation = loadConsultation();
            render();
        } catch (Exception exception) {
            showAlert("Erreur", "Format d'heure invalide. Utilisez HH:mm.");
        }
    }

    private boolean canApprove() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || consultation == null || consultation.getRecipient() == null) {
            return false;
        }
        String role = currentUser.getSubscriptionType() != null && !currentUser.getSubscriptionType().isBlank()
            ? currentUser.getSubscriptionType() : currentUser.getRole();
        return consultation.isRequested()
            && consultation.getRecipient().getId().equals(currentUser.getId())
            && role != null && (role.contains("MEDECIN") || role.contains("COACH") || role.contains("NUTRITIONNISTE"));
    }

    private boolean canCancel() {
        User currentUser = AuthSession.getCurrentUser();
        return currentUser != null
            && consultation != null
            && consultation.getInitiator() != null
            && consultation.getInitiator().getId().equals(currentUser.getId())
            && !consultation.isCompleted()
            && !consultation.isCancel();
    }

    private User resolveOtherUser() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return null;
        }
        return consultation.getInitiator() != null && consultation.getInitiator().getId().equals(currentUser.getId())
            ? consultation.getRecipient() : consultation.getInitiator();
    }

    private String displayUser(User user) {
        if (user == null) {
            return "Utilisateur";
        }
        String full = ((user.getPrenom() == null ? "" : user.getPrenom()) + " " + (user.getNom() == null ? "" : user.getNom())).trim();
        return full.isBlank() ? safe(user.getEmail()) : full;
    }

    private String computeInitials(User user) {
        if (user == null) {
            return "US";
        }
        String prenom = safe(user.getPrenom());
        String nom = safe(user.getNom());
        String initials = (prenom.isBlank() ? "" : prenom.substring(0, 1).toUpperCase())
            + (nom.isBlank() ? "" : nom.substring(0, 1).toUpperCase());
        if (!initials.isBlank()) {
            return initials;
        }
        String email = safe(user.getEmail());
        return email.isBlank() ? "US" : email.substring(0, 1).toUpperCase();
    }

    private String buildRoleLine(User user) {
        String role = user.getSubscriptionType() != null && !user.getSubscriptionType().isBlank()
            ? user.getSubscriptionType() : user.getRole();
        return role == null ? "" : role;
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

    private String mapStatusLabel(Teleconsultation item) {
        if (item.isOngoing()) return "EN COURS";
        if (item.isRequested()) return "EN ATTENTE D'APPROBATION";
        if (item.isPending()) return "EN ATTENTE";
        if (item.isCompleted()) return "COMPLÉTÉE";
        if (item.isCancel()) return "ANNULÉE";
        return safe(item.getStatus());
    }

    private String resolveStatusClass(Teleconsultation item) {
        if (item.isOngoing()) return "tele-status-live";
        if (item.isRequested()) return "tele-status-warning";
        if (item.isPending()) return "tele-status-pending";
        if (item.isCompleted()) return "tele-status-success";
        if (item.isCancel()) return "tele-status-danger";
        return "tele-status-neutral";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
