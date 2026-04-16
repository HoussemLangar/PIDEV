package com.santea.controller;

import com.santea.model.DocumentAccess;
import com.santea.model.SharedDocument;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.navigation.ModuleContext;
import com.santea.service.AuthSession;
import com.santea.service.DocumentStorageService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DocumentShowController extends AppBaseViewController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private Label documentNameLabel;
    @FXML private Label documentOwnerLabel;
    @FXML private Label documentTypeLabel;
    @FXML private Label documentTypeBadgeLabel;
    @FXML private Label documentVisibilityBadgeLabel;
    @FXML private Label documentMimeTypeLabel;
    @FXML private Label documentSizeLabel;
    @FXML private Label documentUploadedLabel;
    @FXML private Label sharedWithCountLabel;
    @FXML private Label totalAccessCountLabel;
    @FXML private TextArea documentDescriptionArea;
    @FXML private ListView<DocumentAccess> accessListView;
    @FXML private Button backButton;
    @FXML private Button downloadButton;
    @FXML private Button shareButton;
    @FXML private Button deleteButton;

    private final DocumentStorageService service = new DocumentStorageService();
    private SharedDocument document;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        accessListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentAccess item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String who = item.getSharedWith() == null ? "Utilisateur" : (item.getSharedWith().getEmail() == null ? "Utilisateur" : item.getSharedWith().getEmail());
                    String expires = item.getExpiresAt() == null ? "Illimité" : item.getExpiresAt().format(DATE_TIME_FORMAT);
                    setText(who + " • " + item.getPermission() + " • expire: " + expires);
                }
            }
        });
        Integer id = ModuleContext.getDocumentId();
        if (id == null) {
            throw new IllegalStateException("Aucun document sélectionné");
        }
        document = service.getDocument(String.valueOf(id));
        if (document == null) {
            throw new IllegalStateException("Document introuvable");
        }
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || !service.hasAccess(String.valueOf(document.getId()), String.valueOf(currentUser.getId()), "view")) {
            throw new IllegalStateException("Accès refusé à ce document");
        }
        render();
        backButton.setOnAction(event -> AppNavigator.showDocumentSharing());
        downloadButton.setOnAction(event -> download());
        shareButton.setOnAction(event -> AppNavigator.showDocumentShare(document.getId()));
        deleteButton.setOnAction(event -> delete());
    }

    private void render() {
        User currentUser = AuthSession.getCurrentUser();
        boolean isOwner = currentUser != null && document.getOwner() != null && document.getOwner().getId().equals(currentUser.getId());
        documentNameLabel.setText(document.getFileName());
        documentOwnerLabel.setText(document.getOwner() == null ? "" : document.getOwner().getEmail());
        documentTypeLabel.setText(document.getDocumentType() == null ? "--" : document.getDocumentType());
        documentTypeBadgeLabel.setText((document.getDocumentType() == null ? "AUTRE" : document.getDocumentType()).toUpperCase());
        documentVisibilityBadgeLabel.setText(document.isPublic() ? "PUBLIC" : "PRIVÉ");
        documentMimeTypeLabel.setText(document.getMimeType() == null ? "" : document.getMimeType());
        documentSizeLabel.setText(document.getFileSizeFormatted());
        documentUploadedLabel.setText(document.getUploadedAt() == null ? "--" : document.getUploadedAt().format(DATE_TIME_FORMAT));
        documentDescriptionArea.setText(document.getDescription() == null ? "" : document.getDescription());
        var accesses = FXCollections.observableArrayList(isOwner
            ? service.getAccessHistory(String.valueOf(document.getId()))
            : java.util.List.<DocumentAccess>of());
        accessListView.setItems(accesses);
        sharedWithCountLabel.setText(String.valueOf(accesses.size()));
        int totalAccesses = accesses.stream().mapToInt(item -> item.getAccessCount() == null ? 0 : item.getAccessCount()).sum();
        totalAccessCountLabel.setText(String.valueOf(totalAccesses));
        shareButton.setDisable(!isOwner);
        deleteButton.setDisable(!isOwner);
    }

    private void download() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        byte[] content = service.downloadDocument(String.valueOf(document.getId()), String.valueOf(currentUser.getId()));
        if (content == null) {
            showAlert("Erreur", "Vous n'avez pas la permission de télécharger ce document.");
            return;
        }

        String suggestedName = (document.getFileName() == null || document.getFileName().isBlank())
            ? "document-" + document.getId()
            : document.getFileName();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Enregistrer le document");
        fileChooser.setInitialFileName(suggestedName);
        Window owner = downloadButton != null && downloadButton.getScene() != null
            ? downloadButton.getScene().getWindow()
            : null;
        java.io.File destination = fileChooser.showSaveDialog(owner);
        if (destination == null) {
            return;
        }

        try {
            Path outputPath = destination.toPath();
            Files.write(outputPath, content);
        } catch (IOException exception) {
            showAlert("Erreur", "Impossible d'enregistrer le fichier téléchargé.");
            return;
        }

        render();
        showAlert("Succès", "Document téléchargé: " + destination.getName());
    }

    private void delete() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer le document");
        confirm.setHeaderText("Supprimer ce document ?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK && service.deleteDocument(String.valueOf(document.getId()), String.valueOf(currentUser.getId()))) {
                AppNavigator.showDocumentSharing();
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
