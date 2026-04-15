package com.santea.controller;

import com.santea.config.DatabaseConfig;
import com.santea.model.Teleconsultation;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.repository.UserRepository;
import com.santea.service.AuthSession;
import com.santea.service.DatabaseService;
import com.santea.service.TeleconsultationService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class TeleconsultationScheduleController extends AppBaseViewController {
    @FXML private Button backButton;
    @FXML private Button submitButton;
    @FXML private Button cancelButton;
    @FXML private ComboBox<User> recipientCombo;
    @FXML private ComboBox<String> typeCombo;
    @FXML private DatePicker datePicker;
    @FXML private Spinner<Integer> hourSpinner;
    @FXML private Spinner<Integer> minuteSpinner;
    @FXML private TextArea descriptionArea;

    private final TeleconsultationService service = new TeleconsultationService();
    private final UserRepository userRepository = new UserRepository(new DatabaseService(DatabaseConfig.fromEnvironment()));

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        recipientCombo.setItems(FXCollections.observableArrayList(loadRecipients(currentUser)));
        recipientCombo.setCellFactory(param -> buildUserCell());
        recipientCombo.setButtonCell(buildUserCell());
        typeCombo.setItems(FXCollections.observableArrayList("general", "follow_up", "emergency", "diagnostic"));
        typeCombo.setValue("general");
        hourSpinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 10));
        minuteSpinner.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 0));
        backButton.setOnAction(event -> AppNavigator.showTeleconsultation());
        cancelButton.setOnAction(event -> AppNavigator.showTeleconsultation());
        submitButton.setOnAction(event -> submit());
    }

    private void submit() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || datePicker.getValue() == null || recipientCombo.getValue() == null) {
            showAlert("Erreur", "Veuillez compléter les champs requis.");
            return;
        }
        LocalDateTime scheduledAt = LocalDateTime.of(datePicker.getValue(), LocalTime.of(hourSpinner.getValue(), minuteSpinner.getValue()));
        Teleconsultation consultation = service.createTeleconsultation(
            currentUser,
            recipientCombo.getValue(),
            typeCombo.getValue(),
            descriptionArea.getText(),
            scheduledAt
        );
        if (consultation == null) {
            showAlert("Erreur", "Création impossible: créneau indisponible ou destinataire invalide.");
            return;
        }
        AppNavigator.showTeleconsultationShow(consultation.getId());
    }

    private List<User> loadRecipients(User currentUser) {
        String role = currentUser.getSubscriptionType() != null && !currentUser.getSubscriptionType().isBlank()
            ? currentUser.getSubscriptionType() : currentUser.getRole();
        if (role != null && (role.contains("MEDECIN") || role.contains("COACH") || role.contains("NUTRITIONNISTE"))) {
            return userRepository.findByRole("ROLE_PATIENT");
        }
        return userRepository.findByRoles(List.of("ROLE_MEDECIN", "ROLE_COACH", "ROLE_NUTRITIONNISTE"));
    }

    private ListCell<User> buildUserCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String full = ((item.getPrenom() == null ? "" : item.getPrenom()) + " " + (item.getNom() == null ? "" : item.getNom())).trim();
                    setText((full.isBlank() ? item.getEmail() : full) + " • " + item.getEmail());
                }
            }
        };
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
