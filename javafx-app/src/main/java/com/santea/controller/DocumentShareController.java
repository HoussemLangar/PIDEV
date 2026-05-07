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
    @FXML private Button updateButton;
    @FXML private Button cancelButton;
    @FXML private Button deleteButton;
    @FXML private ComboBox<User> recipientCombo;
    @FXML private ComboBox<String> permissionCombo;
    @FXML private DatePicker expiresAtPicker;
    @FXML private ListView<DocumentAccess> accessListView;

    private final DocumentStorageService service = new DocumentStorageService();
    private final UserRepository userRepository = new UserRepository(new DatabaseService(DatabaseConfig.fromEnvironment()));
    private SharedDocument document;
    private DocumentAccess selectedAccess;

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
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || document.getOwner() == null || !document.getOwner().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("Accès refusé: seul le propriétaire peut partager ce document");
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
        accessListView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> onAccessSelected(newValue));
        backButton.setOnAction(event -> AppNavigator.showDocumentShow(document.getId()));
        cancelButton.setOnAction(event -> AppNavigator.showDocumentShow(document.getId()));
        shareButton.setOnAction(event -> share());
        updateButton.setOnAction(event -> updateSelectedAccess());
        deleteButton.setOnAction(event -> deleteSelectedAccess());
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void render() {
        accessListView.setItems(FXCollections.observableArrayList(service.getAccessHistory(String.valueOf(document.getId()))));
    }

    private List<User> loadRecipients() {
        User owner = document.getOwner();
        String role = owner.getSubscriptionType() != null && !owner.getSubscriptionType().isBlank() ? owner.getSubscriptionType() : owner.getRole();
        if ("ROLE_PATIENT".equals(role)) {
            return userRepository.findByRoles(List.of("ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE")).stream()
                .filter(user -> user != null && user.getId() != null && !user.getId().equals(owner.getId()))
                .toList();
        }
        return userRepository.findByRole("ROLE_PATIENT").stream()
            .filter(user -> user != null && user.getId() != null && !user.getId().equals(owner.getId()))
            .toList();
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
        if (document.getOwner() != null && document.getOwner().getId() != null && document.getOwner().getId().equals(recipient.getId())) {
            showAlert("Erreur", "Vous ne pouvez pas partager un document avec vous-même.");
            return;
        }

        String permission = permissionCombo.getValue();
        if (permission == null || permission.isBlank() || !("view".equals(permission) || "download".equals(permission))) {
            showAlert("Erreur", "Permission invalide.");
            return;
        }

        if (expiresAtPicker.getValue() != null && expiresAtPicker.getValue().isBefore(java.time.LocalDate.now())) {
            showAlert("Erreur", "La date d'expiration ne peut pas être dans le passé.");
            return;
        }

        LocalDateTime expiresAt = expiresAtPicker.getValue() == null ? null : expiresAtPicker.getValue().atStartOfDay();
        DocumentAccess access = service.shareDocument(String.valueOf(document.getId()), recipient, permission, expiresAt);
        if (access == null) {
            showAlert("Erreur", "Partage refusé.");
            return;
        }
        render();
        resetSelection();
    }

    private void updateSelectedAccess() {
        if (selectedAccess == null || selectedAccess.getSharedWith() == null) {
            showAlert("Erreur", "Veuillez sélectionner un accès.");
            return;
        }

        String permission = permissionCombo.getValue();
        if (permission == null || permission.isBlank() || !List.of("view", "download").contains(permission)) {
            showAlert("Erreur", "Permission invalide.");
            return;
        }

        if (expiresAtPicker.getValue() != null && expiresAtPicker.getValue().isBefore(java.time.LocalDate.now())) {
            showAlert("Erreur", "La date d'expiration ne peut pas être dans le passé.");
            return;
        }

        LocalDateTime expiresAt = expiresAtPicker.getValue() == null ? null : expiresAtPicker.getValue().atStartOfDay();
        DocumentAccess updated = service.shareDocument(
            String.valueOf(document.getId()),
            selectedAccess.getSharedWith(),
            permission,
            expiresAt
        );

        if (updated == null) {
            showAlert("Erreur", "Mise à jour refusée.");
            return;
        }

        render();
        resetSelection();
    }

    private void deleteSelectedAccess() {
        DocumentAccess access = selectedAccess != null ? selectedAccess : accessListView.getSelectionModel().getSelectedItem();
        if (access == null || access.getSharedWith() == null || access.getSharedWith().getId() == null) {
            showAlert("Erreur", "Veuillez sélectionner un accès.");
            return;
        }

        boolean deleted = service.deleteAccess(String.valueOf(document.getId()), String.valueOf(access.getSharedWith().getId()));
        if (!deleted) {
            showAlert("Erreur", "Suppression refusée.");
            return;
        }

        render();
        resetSelection();
    }

    private void onAccessSelected(DocumentAccess access) {
        selectedAccess = access;
        boolean hasSelection = access != null && access.getSharedWith() != null;
        updateButton.setDisable(!hasSelection);
        deleteButton.setDisable(!hasSelection);

        if (!hasSelection) {
            recipientCombo.setDisable(false);
            return;
        }

        recipientCombo.setValue(access.getSharedWith());
        recipientCombo.setDisable(true);
        permissionCombo.setValue(access.getPermission() == null || access.getPermission().isBlank() ? "view" : access.getPermission());
        expiresAtPicker.setValue(access.getExpiresAt() == null ? null : access.getExpiresAt().toLocalDate());
    }

    private void resetSelection() {
        selectedAccess = null;
        accessListView.getSelectionModel().clearSelection();
        recipientCombo.setDisable(false);
        permissionCombo.setValue("view");
        expiresAtPicker.setValue(null);
        updateButton.setDisable(true);
        deleteButton.setDisable(true);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
