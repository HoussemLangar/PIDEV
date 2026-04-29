package com.santea.controller;

import com.santea.model.SharedDocument;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.DocumentStorageService;
import com.santea.service.DocumentClassificationClient;
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
import java.util.Set;

public class DocumentUploadController extends AppBaseViewController {
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        "pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx"
    );

    @FXML private Label selectedFileLabel;
    @FXML private Button backButton;
    @FXML private Button chooseFileButton;
    @FXML private Button uploadButton;
    @FXML private Button cancelButton;
    @FXML private ComboBox<String> documentTypeCombo;
    @FXML private Label classificationSummaryLabel;
    @FXML private TextArea descriptionArea;

    private final DocumentStorageService service = new DocumentStorageService();
    private final DocumentClassificationClient classifier = new DocumentClassificationClient();
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
        if (classificationSummaryLabel != null) {
            classificationSummaryLabel.setText("Aucune auto-détection pour le moment.");
        }
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.jpg", "*.jpeg", "*.png", "*.doc", "*.docx", "*.xls", "*.xlsx"));
        selectedFile = chooser.showOpenDialog(chooseFileButton.getScene().getWindow());
        if (selectedFile != null) {
            selectedFileLabel.setText(selectedFile.getName());
            if (classificationSummaryLabel != null) {
                classificationSummaryLabel.setText("Analyse du document en cours...");
            }
            // Try ML classification
            String description = descriptionArea.getText() != null ? descriptionArea.getText().trim() : "";
            classifyDocument(selectedFile.getName(), description);
        } else {
            selectedFileLabel.setText("Aucun fichier sélectionné");
        }
    }

    private void classifyDocument(String filename, String description) {
        if (!classifier.isAvailable()) {
            return; // ML service unavailable, user selects manually
        }
        
        // Run classification in background to avoid UI blocking
        Thread classificationThread = new Thread(() -> {
            DocumentClassificationClient.ClassificationResult result = classifier.classify(filename, description);
            javafx.application.Platform.runLater(() -> updateClassificationSummary(result));
        });
        classificationThread.setDaemon(true);
        classificationThread.start();
    }

    private void updateClassificationSummary(DocumentClassificationClient.ClassificationResult result) {
        if (classificationSummaryLabel == null) {
            return;
        }

        if (!result.success || result.predictedType == null) {
            classificationSummaryLabel.setText("Auto-détection indisponible : " + (result.error == null ? "service indisponible" : result.error));
            return;
        }

        String confidenceText = String.format("%.0f%%", result.confidence * 100);
        String summary = "Suggestion IA: " + result.predictedType + " (" + confidenceText + ")";
        if (result.candidateSummary != null && !result.candidateSummary.isBlank()) {
            summary += "\nTop candidats: " + result.candidateSummary;
        }
        classificationSummaryLabel.setText(summary);

        if (result.confidence > 0.5) {
            String mappedType = mapPredictedType(result.predictedType);
            if (mappedType != null && documentTypeCombo.getItems().contains(mappedType)) {
                documentTypeCombo.setValue(mappedType);
            }
        }
    }

    private String mapPredictedType(String predicted) {
        predicted = predicted.toLowerCase();
        if (predicted.contains("lab") || predicted.contains("analysis")) return "lab_results";
        if (predicted.contains("imaging") || predicted.contains("scan")) return "imaging";
        if (predicted.contains("prescription")) return "prescription";
        if (predicted.contains("report")) return "report";
        return null;
    }

    private void upload() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            showAlert("Erreur", "Session invalide.");
            return;
        }
        if (selectedFile == null) {
            showAlert("Erreur", "Veuillez choisir un fichier.");
            return;
        }
        if (!selectedFile.exists() || !selectedFile.isFile()) {
            showAlert("Erreur", "Fichier invalide.");
            return;
        }
        if (selectedFile.length() <= 0) {
            showAlert("Erreur", "Le fichier est vide.");
            return;
        }
        if (selectedFile.length() > MAX_FILE_SIZE_BYTES) {
            showAlert("Erreur", "Le fichier dépasse 50 MB.");
            return;
        }
        String fileName = selectedFile.getName() == null ? "" : selectedFile.getName().trim();
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            showAlert("Erreur", "Format non supporté. Utilisez PDF, Word, Excel ou Image.");
            return;
        }
        String documentType = documentTypeCombo.getValue();
        if (documentType == null || documentType.isBlank()) {
            showAlert("Erreur", "Veuillez sélectionner un type de document.");
            return;
        }
        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();
        if (description.length() > 2000) {
            showAlert("Erreur", "La description est trop longue (max 2000 caractères).");
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
                description,
                documentType
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
