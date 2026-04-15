package com.santea.controller;

import com.santea.model.SharedDocument;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.DocumentStorageService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;

public class DocumentUploadController extends AppBaseViewController {
    @FXML private Label selectedFileLabel;
    @FXML private Button backButton;
    @FXML private Button chooseFileButton;
    @FXML private Button uploadButton;
    @FXML private Button cancelButton;
    @FXML private ComboBox<String> documentTypeCombo;
    @FXML private TextArea descriptionArea;

    private final DocumentStorageService service = new DocumentStorageService();
    private File selectedFile;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser != null && "ROLE_PATIENT".equals(currentUser.getSubscriptionType() != null && !currentUser.getSubscriptionType().isBlank() ? currentUser.getSubscriptionType() : currentUser.getRole())) {
            documentTypeCombo.setItems(FXCollections.observableArrayList("analysis", "report", "lab_results", "imaging"));
        } else {
            documentTypeCombo.setItems(FXCollections.observableArrayList("analysis", "report", "lab_results", "imaging", "prescription", "other"));
        }
        documentTypeCombo.setValue(documentTypeCombo.getItems().get(0));
        backButton.setOnAction(event -> AppNavigator.showDocumentSharing());
        cancelButton.setOnAction(event -> AppNavigator.showDocumentSharing());
        chooseFileButton.setOnAction(event -> chooseFile());
        uploadButton.setOnAction(event -> upload());
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.jpg", "*.jpeg", "*.png", "*.doc", "*.docx", "*.xls", "*.xlsx"));
        selectedFile = chooser.showOpenDialog(chooseFileButton.getScene().getWindow());
        selectedFileLabel.setText(selectedFile == null ? "Aucun fichier sélectionné" : selectedFile.getName());
    }

    private void upload() {
        User currentUser = AuthSession.getCurrentUser();
        if (selectedFile == null || currentUser == null) {
            showAlert("Erreur", "Veuillez choisir un fichier.");
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(selectedFile.toPath());
            String mimeType = Files.probeContentType(selectedFile.toPath());
            SharedDocument document = service.uploadDocument(
                selectedFile.getName(),
                bytes,
                mimeType == null ? "application/octet-stream" : mimeType,
                currentUser,
                descriptionArea.getText(),
                documentTypeCombo.getValue()
            );
            if (document == null) {
                showAlert("Erreur", "Upload refusé.");
                return;
            }
            AppNavigator.showDocumentShow(document.getId());
        } catch (Exception exception) {
            showAlert("Erreur", exception.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
