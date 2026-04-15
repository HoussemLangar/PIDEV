package com.santea.controller;

import com.santea.model.SharedDocument;
import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.DocumentStorageService;
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

public class DocumentSharingController extends AppBaseViewController {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @FXML private VBox ownedDocumentsContainer;
    @FXML private VBox sharedDocumentsContainer;
    @FXML private Label storageBadgeLabel;
    @FXML private Button uploadDocumentButton;
    @FXML private TabPane documentTabs;

    private final DocumentStorageService documentService = new DocumentStorageService();

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);
        uploadDocumentButton.setOnAction(event -> AppNavigator.showDocumentUpload());
        loadData();
    }

    private void loadData() {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        List<SharedDocument> ownDocuments = documentService.findByOwner(currentUser);
        List<SharedDocument> sharedDocuments = documentService.findSharedWithUser(currentUser);
        storageBadgeLabel.setText(documentService.formatFileSize((int) documentService.getTotalStorageByUser(currentUser)) + " utilisés");
        applyTabLabels(ownDocuments.size(), sharedDocuments.size());
        renderGroup(ownedDocumentsContainer, ownDocuments, true);
        renderGroup(sharedDocumentsContainer, sharedDocuments, false);
    }

    private void applyTabLabels(int ownCount, int sharedCount) {
        if (documentTabs == null || documentTabs.getTabs().size() < 2) {
            return;
        }
        List<Tab> tabs = documentTabs.getTabs();
        tabs.get(0).setText("Mes documents (" + ownCount + ")");
        tabs.get(1).setText("Partagés avec moi (" + sharedCount + ")");
    }

    private void renderGroup(VBox container, List<SharedDocument> documents, boolean ownerView) {
        container.getChildren().clear();
        if (documents.isEmpty()) {
            VBox box = new VBox(8);
            box.getStyleClass().add("doc-empty-state");
            box.setAlignment(Pos.CENTER);
            box.setPadding(new Insets(36));
            Label title = new Label(ownerView ? "Aucun document trouvé" : "Aucun document partagé");
            title.getStyleClass().add("tele-empty-title");
            Label subtitle = new Label("Les documents apparaîtront ici.");
            subtitle.getStyleClass().add("tele-empty-subtitle");
            box.getChildren().addAll(title, subtitle);
            if (ownerView) {
                Button actionButton = new Button("Uploader un document");
                actionButton.getStyleClass().addAll("btn-success", "empty-state-button");
                actionButton.setOnAction(event -> AppNavigator.showDocumentUpload());
                box.getChildren().add(actionButton);
            }
            container.getChildren().add(box);
            return;
        }
        for (SharedDocument document : documents) {
            HBox row = new HBox(12);
            row.getStyleClass().add("doc-row-card");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(14, 16, 14, 16));

            VBox iconShell = new VBox();
            iconShell.setAlignment(Pos.CENTER);
            iconShell.getStyleClass().add("doc-list-icon");
            Label iconText = new Label(resolveTypeAbbreviation(document));
            iconText.getStyleClass().add("doc-list-icon-text");
            iconShell.getChildren().add(iconText);

            VBox main = new VBox(4);
            Label title = new Label(document.getFileName());
            title.getStyleClass().add("doc-row-title");
            Label subtitle = new Label((document.getDocumentType() == null ? "document" : document.getDocumentType())
                + " • " + document.getFileSizeFormatted()
                + " • " + (document.getUploadedAt() == null ? "--" : document.getUploadedAt().format(DATE_TIME_FORMAT)));
            subtitle.getStyleClass().add("doc-row-subtitle");
            HBox badges = new HBox(8);
            Label typeBadge = new Label(document.getDocumentType() == null ? "Document" : document.getDocumentType());
            typeBadge.getStyleClass().addAll("doc-row-badge", "doc-row-shared-badge");
            Label visibilityBadge = new Label(document.isPublic() ? "Public" : "Privé");
            visibilityBadge.getStyleClass().addAll("doc-row-badge", document.isPublic() ? "doc-row-public-badge" : "doc-row-owner-badge");
            badges.getChildren().addAll(typeBadge, visibilityBadge);
            main.getChildren().addAll(title, subtitle, badges);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox actions = new HBox(8);
            Button openButton = buildActionButton("i", "Voir", "btn-secondary");
            openButton.getStyleClass().add("btn-secondary");
            openButton.setOnAction(event -> {
                event.consume();
                AppNavigator.showDocumentShow(document.getId());
            });
            actions.getChildren().add(openButton);
            if (canDownload(document)) {
                Button downloadButton = buildActionButton("v", "Telecharger", "btn-info");
                downloadButton.getStyleClass().add("btn-info");
                downloadButton.setOnAction(event -> {
                    event.consume();
                    downloadDocument(document);
                });
                actions.getChildren().add(downloadButton);
            }
            if (ownerView) {
                Button shareButton = buildActionButton("+", "Partager", "btn-success");
                shareButton.getStyleClass().add("btn-success");
                shareButton.setOnAction(event -> {
                    event.consume();
                    AppNavigator.showDocumentShare(document.getId());
                });
                actions.getChildren().add(shareButton);
            }
            row.getChildren().addAll(iconShell, main, spacer, actions);
            row.setOnMouseClicked(event -> AppNavigator.showDocumentShow(document.getId()));
            container.getChildren().add(row);
        }
    }

    private boolean canDownload(SharedDocument document) {
        User currentUser = AuthSession.getCurrentUser();
        return currentUser != null && document != null
            && documentService.hasAccess(String.valueOf(document.getId()), String.valueOf(currentUser.getId()), "download");
    }

    private void downloadDocument(SharedDocument document) {
        User currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || document == null) {
            return;
        }
        byte[] content = documentService.downloadDocument(String.valueOf(document.getId()), String.valueOf(currentUser.getId()));
        if (content == null) {
            return;
        }
        loadData();
    }

    private String resolveTypeAbbreviation(SharedDocument document) {
        String type = document.getDocumentType() == null ? "" : document.getDocumentType().trim();
        if (type.length() >= 3) {
            return type.substring(0, 3).toUpperCase();
        }
        if (!type.isEmpty()) {
            return type.toUpperCase();
        }
        return "DOC";
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
}
