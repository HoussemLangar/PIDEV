package com.santea.controller;

import com.santea.config.DatabaseConfig;
import com.santea.model.DocumentAccess;
import com.santea.model.SharedDocument;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.navigation.ModuleContext;
import com.santea.repository.UserRepository;
import com.santea.service.AuthSession;
import com.santea.service.DatabaseService;
import com.santea.service.DocumentStorageService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DocumentShareController extends AppBaseViewController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label documentNameLabel;
    @FXML private Button backButton;
    @FXML private Button shareButton;
    @FXML private Button cancelButton;
    @FXML private Button revokeButton;
    @FXML private ComboBox<User> recipientCombo;
    @FXML private ComboBox<String> permissionCombo;
    @FXML private DatePicker expiresAtPicker;
    @FXML private ListView<DocumentAccess> accessListView;

    private final DocumentStorageService service = new DocumentStorageService();
    private final UserRepository userRepository = new UserRepository(new DatabaseService(DatabaseConfig.fromEnvironment()));
    private SharedDocument document;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        Integer id = ModuleContext.getDocumentId();
        if (id == null) {
            throw new IllegalStateException("Aucun document sélectionné");
        }
        document = service.getDocument(String.valueOf(id));
        if (document == null) {
            throw new IllegalStateException("Document introuvable");
        }
        documentNameLabel.setText(document.getFileName());
        permissionCombo.setItems(FXCollections.observableArrayList("view", "download"));
        permissionCombo.setValue("view");
        recipientCombo.setItems(FXCollections.observableArrayList(loadRecipients()));
        recipientCombo.setCellFactory(param -> buildUserCell());
        recipientCombo.setButtonCell(buildUserCell());
        accessListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentAccess item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String who = item.getSharedWith() == null ? "Utilisateur" : item.getSharedWith().getEmail();
                    String expires = item.getExpiresAt() == null ? "Illimité" : item.getExpiresAt().format(DATE_TIME_FORMAT);
                    setText(who + " • " + item.getPermission() + " • expire: " + expires);
                }
            }
        });
        render();
        backButton.setOnAction(event -> AppNavigator.showDocumentShow(document.getId()));
        cancelButton.setOnAction(event -> AppNavigator.showDocumentShow(document.getId()));
        shareButton.setOnAction(event -> share());
        revokeButton.setOnAction(event -> revoke());
    }

    private void render() {
        accessListView.setItems(FXCollections.observableArrayList(service.getAccessHistory(String.valueOf(document.getId()))));
    }

    private List<User> loadRecipients() {
        User owner = document.getOwner();
        String role = owner.getSubscriptionType() != null && !owner.getSubscriptionType().isBlank() ? owner.getSubscriptionType() : owner.getRole();
        if ("ROLE_PATIENT".equals(role)) {
            return userRepository.findByRoles(List.of("ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE"));
        }
        return userRepository.findByRole("ROLE_PATIENT");
    }

    private ListCell<User> buildUserCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getEmail());
            }
        };
    }

    private void share() {
        User recipient = recipientCombo.getValue();
        if (recipient == null) {
            showAlert("Erreur", "Veuillez sélectionner un utilisateur.");
            return;
        }
        LocalDateTime expiresAt = expiresAtPicker.getValue() == null ? null : expiresAtPicker.getValue().atStartOfDay();
        DocumentAccess access = service.shareDocument(String.valueOf(document.getId()), recipient, permissionCombo.getValue(), expiresAt);
        if (access == null) {
            showAlert("Erreur", "Partage refusé.");
            return;
        }
        render();
    }

    private void revoke() {
        DocumentAccess access = accessListView.getSelectionModel().getSelectedItem();
        if (access == null || access.getSharedWith() == null) {
            showAlert("Erreur", "Veuillez sélectionner un accès.");
            return;
        }
        service.revokeAccess(String.valueOf(document.getId()), String.valueOf(access.getSharedWith().getId()));
        render();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
