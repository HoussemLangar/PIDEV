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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DocumentUploadController extends AppBaseViewController {
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_CLASSIFIER_CHARS = 4000;
    private static final int MAX_CLASSIFIER_BYTES = 200_000;
    private static final Pattern PDF_TEXT_PATTERN = Pattern.compile("\\(([^\\)]{2,})\\)\\s*Tj", Pattern.DOTALL);
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
            classifyDocument(selectedFile, description);
        } else {
            selectedFileLabel.setText("Aucun fichier sélectionné");
        }
    }

    private void classifyDocument(File file, String description) {
        if (!classifier.isAvailable()) {
            return; // ML service unavailable, user selects manually
        }
        
        // Run classification in background to avoid UI blocking
        Thread classificationThread = new Thread(() -> {
            String filename = file == null ? "" : file.getName();
            String content = extractTextForClassification(file);
            DocumentClassificationClient.ClassificationResult result = classifier.classify(filename, description, content);
            javafx.application.Platform.runLater(() -> updateClassificationSummary(result));
        });
        classificationThread.setDaemon(true);
        classificationThread.start();
    }

    private String extractTextForClassification(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return "";
        }

        String name = file.getName() == null ? "" : file.getName();
        String ext = extensionOf(name);

        if ("pdf".equals(ext)) {
            return extractTextFromPdf(file);
        }
        if ("docx".equals(ext)) {
            return extractTextFromZipEntry(file, "word/document.xml");
        }
        if ("xlsx".equals(ext)) {
            return extractTextFromZipEntry(file, "xl/sharedStrings.xml");
        }
        if (Set.of("txt", "csv", "log", "md").contains(ext)) {
            return readTextFile(file);
        }

        return "";
    }

    private String readTextFile(File file) {
        try {
            return normalizeExtractedText(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            return "";
        }
    }

    private String extractTextFromPdf(File file) {
        String pdfBoxText = extractTextFromPdfWithPdfBox(file);
        if (!pdfBoxText.isBlank()) {
            return pdfBoxText;
        }
        return extractTextFromPdfRaw(file);
    }

    private String extractTextFromPdfWithPdfBox(File file) {
        try (PDDocument document = PDDocument.load(file)) {
            if (document.isEncrypted()) {
                try {
                    document.setAllSecurityToBeRemoved(true);
                } catch (RuntimeException ignored) {
                    return "";
                }
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setEndPage(2);
            String text = stripper.getText(document);
            return normalizeExtractedText(text);
        } catch (IOException ignored) {
            return "";
        }
    }

    private String extractTextFromPdfRaw(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String raw = new String(bytes, StandardCharsets.ISO_8859_1);
            Matcher matcher = PDF_TEXT_PATTERN.matcher(raw);
            StringBuilder out = new StringBuilder();
            while (matcher.find() && out.length() < MAX_CLASSIFIER_CHARS * 2) {
                String chunk = matcher.group(1);
                if (chunk != null && chunk.length() > 1) {
                    out.append(chunk.replace("\\\\n", " ").replace("\\\\r", " ")).append(' ');
                }
            }
            return normalizeExtractedText(out.toString());
        } catch (IOException ignored) {
            return "";
        }
    }

    private String extractTextFromZipEntry(File file, String entryName) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    String xml = readStreamToString(zis, MAX_CLASSIFIER_BYTES);
                    return extractTextFromXml(xml);
                }
            }
        } catch (IOException ignored) {
            return "";
        }
        return "";
    }

    private String extractTextFromXml(String xml) {
        if (xml == null || xml.isBlank()) {
            return "";
        }
        String withoutTags = xml.replaceAll("<[^>]+>", " ");
        return normalizeExtractedText(withoutTags);
    }

    private String readStreamToString(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            int remaining = maxBytes - total;
            if (remaining <= 0) {
                break;
            }
            int toWrite = Math.min(read, remaining);
            out.write(buffer, 0, toWrite);
            total += toWrite;
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private String normalizeExtractedText(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.replace('\u0000', ' ');
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return limitLength(cleaned, MAX_CLASSIFIER_CHARS);
    }

    private String limitLength(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars);
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
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
