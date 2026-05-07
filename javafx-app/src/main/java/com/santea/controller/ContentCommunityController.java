package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.AuthorizationPolicyService;
import com.santea.service.ContentCommunityService;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;

public class ContentCommunityController implements Initializable {
    private static final String ALL_TYPES = "Tous les types";
    private static final String ALL_CATEGORIES = "Toutes les catégories";

    private final ContentCommunityService contentService = new ContentCommunityService();
    private final AuthorizationPolicyService authorizationPolicyService = new AuthorizationPolicyService();

    private User currentUser;
    private boolean canCreate;
    private boolean canUseMineFilter;
    private boolean mineOnly;

    private ContentCommunityService.ContentSummary selectedContent;
    private ContentCommunityService.ContentSummary editingContent;

    private enum Mode {
        LIST,
        CREATE,
        DETAIL
    }

    private Mode mode = Mode.LIST;

    @FXML
    private VBox blockedCard;

    @FXML
    private Label blockedMessageLabel;

    @FXML
    private VBox moduleContent;

    @FXML
    private Label heroChipLabel;

    @FXML
    private Label heroTitleLabel;

    @FXML
    private Label heroSubtitleLabel;

    @FXML
    private HBox filtersBar;

    @FXML
    private Button heroPrimaryButton;

    @FXML
    private Button heroBackButton;

    @FXML
    private TextField searchField;

    @FXML
    private ComboBox<String> typeFilterCombo;

    @FXML
    private ComboBox<String> categoryFilterCombo;

    @FXML
    private Button mineOnlyButton;

    @FXML
    private VBox listModeSection;

    @FXML
    private VBox recommendedSection;

    @FXML
    private FlowPane recommendedCardsPane;

    @FXML
    private FlowPane contentCardsPane;

    @FXML
    private VBox createModeSection;

    @FXML
    private TextField createTitleField;

    @FXML
    private ComboBox<String> createTypeCombo;

    @FXML
    private TextArea createDescriptionArea;

    @FXML
    private Label createBodyLabel;

    @FXML
    private TextArea createBodyArea;

    @FXML
    private ComboBox<String> createCategoryCombo;

    @FXML
    private TextField createTagsField;

    @FXML
    private Button saveContentButton;

    @FXML
    private VBox detailModeSection;

    @FXML
    private Label detailTypeBadge;

    @FXML
    private Label detailDateLabel;

    @FXML
    private Label detailAuthorLabel;

    @FXML
    private Label detailBodyLabel;

    @FXML
    private Button detailLikeButton;

    @FXML
    private Label detailCommentsCountLabel;

    @FXML
    private Button detailReportButton;

    @FXML
    private Button detailEditButton;

    @FXML
    private Button detailDeleteButton;

    @FXML
    private HBox adminModerationRow;

    @FXML
    private ComboBox<String> adminStatusCombo;

    @FXML
    private VBox commentsListBox;

    @FXML
    private TextArea newCommentArea;

    @FXML
    private VBox adminQueueSection;

    @FXML
    private FlowPane moderationCardsPane;

    @FXML
    private Label feedbackLabel;

    private PauseTransition feedbackHideTimer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        currentUser = AuthSession.getCurrentUser();
        if (currentUser == null || currentUser.getId() == null) {
            AppNavigator.showLogin();
            return;
        }

        ContentCommunityService.AccessDecision decision = contentService.canAccessModule(currentUser);
        if (!decision.allowed()) {
            moduleContent.setVisible(false);
            moduleContent.setManaged(false);
            blockedCard.setVisible(true);
            blockedCard.setManaged(true);
            blockedMessageLabel.setText(decision.message());
            return;
        }

        blockedCard.setVisible(false);
        blockedCard.setManaged(false);
        moduleContent.setVisible(true);
        moduleContent.setManaged(true);

        canCreate = contentService.canCreate(currentUser);
        String effectiveRole = authorizationPolicyService.effectiveRole(currentUser);
        canUseMineFilter = !"ROLE_PATIENT".equalsIgnoreCase(effectiveRole);
        setVisibleManaged(mineOnlyButton, canUseMineFilter);
        if (!canUseMineFilter) {
            mineOnly = false;
        }
        setupFilters();
        setupCreateForm();
        setupAdminWidgets();
        hideFeedback();
        refreshAll();
        setMode(Mode.LIST);
    }

    @FXML
    private void handleOpenSubscription() {
        AppNavigator.showSubscriptionPage();
    }

    @FXML
    private void handleHeroPrimaryAction() {
        if (!canCreate) {
            return;
        }

        editingContent = null;
        clearCreateForm();
        saveContentButton.setText("Publier (en attente)");
        setMode(Mode.CREATE);
    }

    @FXML
    private void handleHeroBackAction() {
        setMode(Mode.LIST);
    }

    @FXML
    private void handleToggleMine() {
        if (!canUseMineFilter) {
            return;
        }

        mineOnly = !mineOnly;
        mineOnlyButton.setText(mineOnly ? "Mon contenu ✓" : "Mon contenu");
        mineOnlyButton.getStyleClass().remove("blog-filter-btn-active");
        if (mineOnly) {
            mineOnlyButton.getStyleClass().add("blog-filter-btn-active");
        }
        refreshFeed();
    }

    @FXML
    private void handleResetFilters() {
        searchField.clear();
        typeFilterCombo.setValue(ALL_TYPES);
        categoryFilterCombo.setValue(ALL_CATEGORIES);
        mineOnly = false;
        if (canUseMineFilter) {
            mineOnlyButton.setText("Mon contenu");
            mineOnlyButton.getStyleClass().remove("blog-filter-btn-active");
        }
        refreshFeed();
    }

    @FXML
    private void handleCancelCreate() {
        editingContent = null;
        clearCreateForm();
        setMode(Mode.LIST);
    }

    @FXML
    private void handleSaveContent() {
        ContentCommunityService.ContentDraft draft = new ContentCommunityService.ContentDraft(
                safe(createTitleField.getText()),
                safe(createTypeCombo.getValue()),
                safe(createDescriptionArea.getText()),
                safe(createBodyArea.getText()),
                normalizedCategory(createCategoryCombo.getValue()),
                safe(createTagsField.getText())
        );

        ContentCommunityService.ActionResult result;
        if (editingContent == null) {
            result = contentService.createContent(currentUser, draft);
        } else {
            result = contentService.updateContent(currentUser, editingContent.id(), draft);
        }

        showFeedback(result.message(), result.success());
        boolean proceed = result.success() || result.numericValue() > 0;
        if (!proceed) {
            return;
        }

        int createdOrUpdatedId = editingContent == null ? result.numericValue() : editingContent.id();
        editingContent = null;
        clearCreateForm();
        refreshAll();
        if (createdOrUpdatedId > 0) {
            openDetail(createdOrUpdatedId);
        } else {
            setMode(Mode.LIST);
        }
    }

    @FXML
    private void handleToggleLike() {
        if (selectedContent == null) {
            return;
        }

        ContentCommunityService.ActionResult result = contentService.toggleLike(currentUser, selectedContent.id());
        showFeedback(result.message(), result.success());
        if (result.success()) {
            refreshAll();
            openDetail(selectedContent.id());
        }
    }

    @FXML
    private void handleReportSelected() {
        if (selectedContent == null) {
            return;
        }

        ContentCommunityService.ActionResult result = contentService.reportContent(currentUser, selectedContent.id(), "");
        showFeedback(result.message(), result.success());
        if (result.success()) {
            refreshAll();
            openDetail(selectedContent.id());
        }
    }

    @FXML
    private void handleEditSelected() {
        if (selectedContent == null || !isOwnerOrAdmin(selectedContent)) {
            showFeedback("Modification non autorisée.", false);
            return;
        }

        editingContent = selectedContent;
        fillCreateForm(selectedContent);
        saveContentButton.setText("Mettre à jour");
        setMode(Mode.CREATE);
    }

    @FXML
    private void handleDeleteSelected() {
        if (selectedContent == null || !isOwnerOrAdmin(selectedContent)) {
            showFeedback("Suppression non autorisée.", false);
            return;
        }

        ContentCommunityService.ActionResult result = contentService.deleteContent(currentUser, selectedContent.id());
        showFeedback(result.message(), result.success());
        if (result.success()) {
            selectedContent = null;
            refreshAll();
            setMode(Mode.LIST);
        }
    }

    @FXML
    private void handleApplyAdminStatus() {
        if (selectedContent == null || !authorizationPolicyService.isAdmin(currentUser)) {
            return;
        }

        String status = safe(adminStatusCombo.getValue());
        if (status.isBlank()) {
            return;
        }

        ContentCommunityService.ActionResult result = contentService.updateContentStatus(currentUser, selectedContent.id(), status);
        showFeedback(result.message(), result.success());
        if (result.success()) {
            refreshAll();
            openDetail(selectedContent.id());
        }
    }

    @FXML
    private void handleAddComment() {
        if (selectedContent == null) {
            return;
        }

        ContentCommunityService.ActionResult result = contentService.addComment(currentUser, selectedContent.id(), safe(newCommentArea.getText()));
        showFeedback(result.message(), result.success());
        if (result.success()) {
            newCommentArea.clear();
            refreshAll();
            openDetail(selectedContent.id());
        }
    }

    private void setupFilters() {
        typeFilterCombo.setItems(FXCollections.observableArrayList(
                ALL_TYPES,
                "article",
                "video",
                "pdf",
                "lien"
        ));
        typeFilterCombo.setValue(ALL_TYPES);

        categoryFilterCombo.setItems(FXCollections.observableArrayList(
                ALL_CATEGORIES,
                "sante",
                "prevention",
                "nutrition",
                "sport",
                "bien-etre",
                "medical"
        ));
        categoryFilterCombo.setValue(ALL_CATEGORIES);

        searchField.textProperty().addListener((obs, oldV, newV) -> refreshFeed());
        typeFilterCombo.valueProperty().addListener((obs, oldV, newV) -> refreshFeed());
        categoryFilterCombo.valueProperty().addListener((obs, oldV, newV) -> refreshFeed());
    }

    private void setupCreateForm() {
        createTypeCombo.setItems(FXCollections.observableArrayList("article", "video", "pdf", "lien"));
        createTypeCombo.setValue("article");

        createCategoryCombo.setItems(FXCollections.observableArrayList(
                "sante",
                "prevention",
                "nutrition",
                "sport",
                "bien-etre",
                "medical"
        ));
        createCategoryCombo.setValue("sante");

        createTypeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            String type = safe(newV).toLowerCase(Locale.ROOT);
            if ("lien".equals(type)) {
                createBodyLabel.setText("Lien *");
                createBodyArea.setPromptText("https://...");
            } else if ("pdf".equals(type)) {
                createBodyLabel.setText("Chemin PDF *");
                createBodyArea.setPromptText("/chemin/fichier.pdf");
            } else if ("video".equals(type)) {
                createBodyLabel.setText("Contenu vidéo *");
                createBodyArea.setPromptText("Description ou lien vidéo");
            } else {
                createBodyLabel.setText("Article *");
                createBodyArea.setPromptText("Rédigez votre article");
            }
        });
    }

    private void setupAdminWidgets() {
        adminStatusCombo.setItems(FXCollections.observableArrayList("en_attente", "valide", "publie", "rejete"));
        adminStatusCombo.setValue("en_attente");
    }

    private void refreshAll() {
        refreshFeed();
        renderAdminQueue();
    }

    private void refreshFeed() {
        ContentCommunityService.FeedData data = contentService.loadFeed(currentUser, currentFeedFilter());
        List<ContentCommunityService.ContentSummary> items = data.items();
        if (mineOnly && currentUser != null && currentUser.getId() != null) {
            int currentUserId = currentUser.getId();
            items = items.stream()
                    .filter(item -> item.authorId() != null && item.authorId().intValue() == currentUserId)
                    .toList();
        }

        // Recommandations masquées quand on force l'affichage des contenus personnels.
        renderRecommended(mineOnly ? List.of() : data.recommended());
        renderCards(items);

        if (selectedContent != null) {
            Optional<ContentCommunityService.ContentSummary> found = items.stream()
                    .filter(item -> item.id() == selectedContent.id())
                    .findFirst();
            found.ifPresent(value -> selectedContent = value);
        }
    }

    private ContentCommunityService.FeedFilter currentFeedFilter() {
        String type = toFilterValue(typeFilterCombo.getValue(), ALL_TYPES);
        String category = toFilterValue(categoryFilterCombo.getValue(), ALL_CATEGORIES);
        return new ContentCommunityService.FeedFilter(type, "", safe(searchField.getText()), category, canUseMineFilter && mineOnly);
    }

    private void renderRecommended(List<ContentCommunityService.ContentSummary> recommended) {
        recommendedCardsPane.getChildren().clear();

        boolean show = recommended != null && !recommended.isEmpty();
        recommendedSection.setVisible(show);
        recommendedSection.setManaged(show);
        if (!show) {
            return;
        }

        for (ContentCommunityService.ContentSummary item : recommended) {
            recommendedCardsPane.getChildren().add(createContentCard(item, true));
        }
    }

    private void renderCards(List<ContentCommunityService.ContentSummary> items) {
        contentCardsPane.getChildren().clear();

        if (items == null || items.isEmpty()) {
            Label empty = new Label("Aucun contenu disponible.");
            empty.getStyleClass().add("blog-empty-text");
            contentCardsPane.getChildren().add(empty);
            return;
        }

        for (ContentCommunityService.ContentSummary item : items) {
            contentCardsPane.getChildren().add(createContentCard(item, false));
        }
    }

    private Node createContentCard(ContentCommunityService.ContentSummary item, boolean compact) {
        VBox card = new VBox(8);
        card.getStyleClass().add(compact ? "blog-reco-card" : "blog-content-card");
        card.setPrefWidth(compact ? 255 : 248);

        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(item.type().toUpperCase(Locale.ROOT));
        typeBadge.getStyleClass().add("blog-type-badge");

        Label statusBadge = new Label(humanStatus(item.status()));
        statusBadge.getStyleClass().add("blog-status-badge");
        applyStatusStyle(statusBadge, item.status());

        Label date = new Label(ContentCommunityService.formatDateTime(item.publishedAtOrCreated()));
        date.getStyleClass().add("blog-date-text");

        top.getChildren().addAll(typeBadge, statusBadge, date);

        Label author = new Label(item.authorDisplay());
        author.getStyleClass().add("blog-author-text");

        Label title = new Label(item.title());
        title.getStyleClass().add("blog-card-title");
        title.setWrapText(true);

        String source = item.description().isBlank() ? item.content() : item.description();
        Label desc = new Label(excerpt(source, compact ? 70 : 95));
        desc.getStyleClass().add("blog-card-desc");
        desc.setWrapText(true);

        Label score = new Label("Score moyen: " + item.scoreLabel() + " (" + item.scoreCount() + " commentaires validés)");
        score.getStyleClass().add("blog-score-text");

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button like = new Button("❤ " + item.likesCount());
        like.getStyleClass().add("blog-meta-btn");
        like.setOnAction(event -> {
            ContentCommunityService.ActionResult result = contentService.toggleLike(currentUser, item.id());
            showFeedback(result.message(), result.success());
            if (result.success()) {
                refreshAll();
            }
        });

        Label comments = new Label("💬 ( " + item.commentsCount() + " )");
        comments.getStyleClass().add("blog-meta-btn");

        Button see = new Button("Voir");
        see.getStyleClass().add("blog-view-btn");
        see.setOnAction(event -> openDetail(item.id()));

        actions.getChildren().addAll(like, comments, see);

        card.getChildren().addAll(top, author, title, desc, score, actions);
        return card;
    }

    private void openDetail(int contentId) {
        ContentCommunityService.ContentDetail detail = contentService.loadDetail(currentUser, contentId);
        if (detail == null) {
            showFeedback("Contenu introuvable ou non accessible.", false);
            return;
        }

        selectedContent = detail.content();
        renderDetail(detail);
        setMode(Mode.DETAIL);
    }

    private void renderDetail(ContentCommunityService.ContentDetail detail) {
        ContentCommunityService.ContentSummary content = detail.content();

        detailTypeBadge.setText(content.type().toUpperCase(Locale.ROOT));
        detailDateLabel.setText(ContentCommunityService.formatDateTime(content.publishedAtOrCreated()));
        detailAuthorLabel.setText(content.authorDisplay());
        detailBodyLabel.setText(content.content());

        detailLikeButton.setText((detail.likedByCurrentUser() ? "❤ " : "♡ ") + content.likesCount());
        detailCommentsCountLabel.setText("Commentaires ( " + content.commentsCount() + " )");

        boolean canReport = !isOwnerOrAdmin(content);
        detailReportButton.setVisible(canReport);
        detailReportButton.setManaged(canReport);

        boolean canOwnerActions = isOwnerOrAdmin(content);
        detailEditButton.setVisible(canOwnerActions);
        detailEditButton.setManaged(canOwnerActions);
        detailDeleteButton.setVisible(canOwnerActions);
        detailDeleteButton.setManaged(canOwnerActions);

        boolean isAdmin = authorizationPolicyService.isAdmin(currentUser);
        adminModerationRow.setVisible(isAdmin);
        adminModerationRow.setManaged(isAdmin);
        if (isAdmin) {
            adminStatusCombo.setValue(content.status());
        }

        commentsListBox.getChildren().clear();
        List<ContentCommunityService.CommentSummary> comments = detail.comments();
        if (comments == null || comments.isEmpty()) {
            Label empty = new Label("Aucun commentaire.");
            empty.getStyleClass().add("blog-empty-text");
            commentsListBox.getChildren().add(empty);
            return;
        }

        for (ContentCommunityService.CommentSummary comment : comments) {
            commentsListBox.getChildren().add(createCommentRow(comment));
        }
    }

    private Node createCommentRow(ContentCommunityService.CommentSummary comment) {
        VBox row = new VBox(4);
        row.getStyleClass().add("blog-comment-row");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label author = new Label(comment.authorDisplay());
        author.getStyleClass().add("blog-comment-author");

        Label meta = new Label(comment.sentiment() + " | "
                + String.format(Locale.ROOT, "%.2f", comment.score()) + " | "
                + ContentCommunityService.formatDateTime(comment.createdAt()));
        meta.getStyleClass().add("blog-comment-meta");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        boolean canDelete = authorizationPolicyService.isAdmin(currentUser)
                || (currentUser.getId() != null && currentUser.getId() == comment.userId());

        if (canDelete) {
            Button delete = new Button("Supprimer");
            delete.getStyleClass().add("blog-danger-btn");
            delete.setOnAction(event -> {
                ContentCommunityService.ActionResult result = contentService.deleteComment(currentUser, comment.id());
                showFeedback(result.message(), result.success());
                if (result.success() && selectedContent != null) {
                    refreshAll();
                    openDetail(selectedContent.id());
                }
            });
            header.getChildren().addAll(author, meta, spacer, delete);
        } else {
            header.getChildren().addAll(author, meta);
        }

        Label body = new Label(comment.message());
        body.getStyleClass().add("blog-comment-body");
        body.setWrapText(true);

        row.getChildren().addAll(header, body);
        return row;
    }

    private void renderAdminQueue() {
        boolean isAdmin = authorizationPolicyService.isAdmin(currentUser);
        adminQueueSection.setVisible(isAdmin);
        adminQueueSection.setManaged(isAdmin);

        if (!isAdmin) {
            return;
        }

        moderationCardsPane.getChildren().clear();
        List<ContentCommunityService.ModerationItem> items = contentService.loadModerationItems(
                currentUser,
                ContentCommunityService.ModerationFilter.defaultFilter()
        );

        if (items.isEmpty()) {
            Label empty = new Label("Aucun contenu en modération.");
            empty.getStyleClass().add("blog-empty-text");
            moderationCardsPane.getChildren().add(empty);
            return;
        }

        for (ContentCommunityService.ModerationItem item : items.stream().limit(12).toList()) {
            moderationCardsPane.getChildren().add(createModerationCard(item));
        }
    }

    private Node createModerationCard(ContentCommunityService.ModerationItem item) {
        VBox card = new VBox(8);
        card.getStyleClass().add("blog-moderation-card");
        card.setPrefWidth(300);

        Label title = new Label("#" + item.id() + " " + item.title());
        title.getStyleClass().add("blog-card-title");
        title.setWrapText(true);

        Label meta = new Label("Type: " + item.type() + " | Statut: " + item.status());
        meta.getStyleClass().add("blog-card-desc");

        Label author = new Label("Auteur: " + item.author());
        author.getStyleClass().add("blog-author-text");

        HBox actions = new HBox(6);
        Button open = new Button("Voir");
        open.getStyleClass().add("blog-view-btn");
        open.setOnAction(event -> openDetail(item.id()));

        Button validate = new Button("Valider");
        validate.getStyleClass().add("blog-filter-btn");
        validate.setOnAction(event -> {
            ContentCommunityService.ActionResult result = contentService.updateContentStatus(currentUser, item.id(), "valide");
            showFeedback(result.message(), result.success());
            if (result.success()) {
                refreshAll();
            }
        });

        Button reject = new Button("Rejeter");
        reject.getStyleClass().add("blog-danger-btn");
        reject.setOnAction(event -> {
            ContentCommunityService.ActionResult result = contentService.updateContentStatus(currentUser, item.id(), "rejete");
            showFeedback(result.message(), result.success());
            if (result.success()) {
                refreshAll();
            }
        });

        actions.getChildren().addAll(open, validate, reject);
        card.getChildren().addAll(title, meta, author, actions);
        return card;
    }

    private void setMode(Mode targetMode) {
        mode = targetMode;

        boolean listMode = mode == Mode.LIST;
        boolean createMode = mode == Mode.CREATE;
        boolean detailMode = mode == Mode.DETAIL;

        listModeSection.setVisible(listMode);
        listModeSection.setManaged(listMode);
        createModeSection.setVisible(createMode);
        createModeSection.setManaged(createMode);
        detailModeSection.setVisible(detailMode);
        detailModeSection.setManaged(detailMode);

        filtersBar.setVisible(listMode);
        filtersBar.setManaged(listMode);

        if (listMode) {
            heroChipLabel.setText("Blog & Forum");
            heroTitleLabel.setText("Articles, Forum & Guides");
            heroSubtitleLabel.setText("Explorez les contenus validés, échangez et laissez vos commentaires.");
            heroPrimaryButton.setVisible(canCreate);
            heroPrimaryButton.setManaged(canCreate);
            heroBackButton.setVisible(false);
            heroBackButton.setManaged(false);
            return;
        }

        heroPrimaryButton.setVisible(false);
        heroPrimaryButton.setManaged(false);
        heroBackButton.setVisible(true);
        heroBackButton.setManaged(true);

        if (createMode) {
            heroChipLabel.setText(editingContent == null ? "Créer un contenu" : "Modifier un contenu");
            heroTitleLabel.setText(editingContent == null ? "Créer un contenu" : "Modifier le contenu");
            heroSubtitleLabel.setText("Articles, vidéos, PDF ou liens externes. Validation admin requise.");
            return;
        }

        if (selectedContent != null) {
            heroChipLabel.setText(selectedContent.type().toUpperCase(Locale.ROOT));
            heroTitleLabel.setText(selectedContent.title());
            String subtitle = selectedContent.description().isBlank()
                    ? excerpt(selectedContent.content(), 90)
                    : selectedContent.description();
            heroSubtitleLabel.setText(subtitle);
        } else {
            heroChipLabel.setText("ARTICLE");
            heroTitleLabel.setText("Détail du contenu");
            heroSubtitleLabel.setText("");
        }
    }

    private void fillCreateForm(ContentCommunityService.ContentSummary content) {
        createTitleField.setText(content.title());
        createTypeCombo.setValue(content.type());
        createDescriptionArea.setText(content.description());
        createBodyArea.setText(content.content());
        createCategoryCombo.setValue(content.category().isBlank() ? "sante" : content.category());
        createTagsField.setText(content.tags());
    }

    private void clearCreateForm() {
        createTitleField.clear();
        createTypeCombo.setValue("article");
        createDescriptionArea.clear();
        createBodyArea.clear();
        createCategoryCombo.setValue("sante");
        createTagsField.clear();
    }

    private boolean isOwnerOrAdmin(ContentCommunityService.ContentSummary content) {
        if (content == null || currentUser == null || currentUser.getId() == null) {
            return false;
        }

        if (authorizationPolicyService.isAdmin(currentUser)) {
            return true;
        }

        return content.authorId() != null && content.authorId().intValue() == currentUser.getId().intValue();
    }

    private void applyStatusStyle(Label badge, String status) {
        badge.getStyleClass().removeAll("blog-status-ok", "blog-status-warn", "blog-status-error", "blog-status-pending");
        String normalized = safe(status).toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "valide", "publie" -> badge.getStyleClass().add("blog-status-ok");
            case "rejete" -> badge.getStyleClass().add("blog-status-error");
            case "en_attente" -> badge.getStyleClass().add("blog-status-pending");
            default -> badge.getStyleClass().add("blog-status-warn");
        }
    }

    private String humanStatus(String status) {
        String normalized = safe(status).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "en_attente" -> "En attente";
            case "valide" -> "Validé";
            case "publie" -> "Publié";
            case "rejete" -> "Rejeté";
            default -> normalized;
        };
    }

    private String toFilterValue(String raw, String allLabel) {
        String normalized = safe(raw);
        if (normalized.isBlank() || allLabel.equalsIgnoreCase(normalized)) {
            return "";
        }
        return normalized;
    }

    private String normalizedCategory(String category) {
        String value = safe(category);
        if (value.isBlank() || ALL_CATEGORIES.equalsIgnoreCase(value)) {
            return "";
        }
        return value;
    }

    private String excerpt(String text, int max) {
        String value = safe(text);
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 1)) + "...";
    }

    private void showFeedback(String message, boolean success) {
        String normalizedMessage = safe(message);
        if (normalizedMessage.isEmpty()) {
            hideFeedback();
            return;
        }

        feedbackLabel.setText(normalizedMessage);
        feedbackLabel.getStyleClass().removeAll("blog-feedback-success", "blog-feedback-error");
        feedbackLabel.getStyleClass().add(success ? "blog-feedback-success" : "blog-feedback-error");
        feedbackLabel.setVisible(true);
        feedbackLabel.setManaged(true);
        scheduleFeedbackAutoHide();
    }

    private void scheduleFeedbackAutoHide() {
        if (feedbackHideTimer == null) {
            feedbackHideTimer = new PauseTransition(Duration.seconds(10));
            feedbackHideTimer.setOnFinished(event -> hideFeedback());
        }
        feedbackHideTimer.stop();
        feedbackHideTimer.playFromStart();
    }

    private void hideFeedback() {
        if (feedbackHideTimer != null) {
            feedbackHideTimer.stop();
        }
        if (feedbackLabel == null) {
            return;
        }
        feedbackLabel.setText("");
        feedbackLabel.setVisible(false);
        feedbackLabel.setManaged(false);
    }

    private void setVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
