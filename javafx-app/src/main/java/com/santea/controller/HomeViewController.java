package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.AuthorizationPolicyService;
import com.santea.service.HomeDashboardService;
import com.santea.ui.ConfirmDialogs;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HomeViewController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final AuthService authService = new AuthService();
    private final HomeDashboardService homeDashboardService = new HomeDashboardService();
    private final AuthorizationPolicyService authorizationPolicyService = new AuthorizationPolicyService();

    private String selectedTheme = "light";

    @FXML
    private BorderPane rootPane;

    @FXML
    private ScrollPane homeScroll;

    @FXML
    private Button loginButton;

    @FXML
    private Button registerButton;

    @FXML
    private Button adminDashboardButton;

    @FXML
    private Button accountAvatarButton;

    @FXML
    private StackPane notifWrap;

    @FXML
    private StackPane msgWrap;

    @FXML
    private Button notifButton;

    @FXML
    private Button msgButton;

    @FXML
    private Label notifBadgeLabel;

    @FXML
    private Label msgBadgeLabel;

    @FXML
    private StackPane heroSection;

    @FXML
    private VBox aboutSection;

    @FXML
    private VBox servicesSection;

    @FXML
    private VBox blogSection;

    @FXML
    private VBox contactSection;

    private ContextMenu notificationsMenu;
    private ContextMenu messagesMenu;
    private ContextMenu accountMenu;

    private HomeDashboardService.DashboardData dashboardData = HomeDashboardService.DashboardData.empty();

    @FXML
    private void initialize() {
        refreshAuthUi();
    }

    @FXML
    private void handleOpenLogin() {
        AppNavigator.showLogin();
    }

    @FXML
    private void handleOpenRegister() {
        AppNavigator.showRegister();
    }

    @FXML
    private void handleOpenAdminDashboard() {
        hideAllMenus();
        if (!AuthSession.isFaceVerified()) {
            AppNavigator.showAdminFaceVerification();
            return;
        }
        AppNavigator.showAdminDashboard();
    }

    @FXML
    private void handleOpenNotifications() {
        if (!AuthSession.isAuthenticated()) {
            return;
        }

        hideAllMenus();
        refreshDashboardData();
        notificationsMenu = buildNotificationsMenu();
        notificationsMenu.show(notifButton, Side.BOTTOM, 0, 8);
    }

    @FXML
    private void handleOpenMessages() {
        if (!AuthSession.isAuthenticated()) {
            return;
        }

        hideAllMenus();
        refreshDashboardData();
        messagesMenu = buildMessagesMenu();
        messagesMenu.show(msgButton, Side.BOTTOM, 0, 8);
    }

    @FXML
    private void handleToggleAccountDropdown() {
        if (!AuthSession.isAuthenticated()) {
            return;
        }

        if (accountMenu != null && accountMenu.isShowing()) {
            accountMenu.hide();
            return;
        }

        hideAllMenus();
        refreshDashboardData();
        accountMenu = buildAccountMenu();
        accountMenu.show(accountAvatarButton, Side.BOTTOM, 0, 8);
    }

    @FXML
    private void handleLogout() {
        hideAllMenus();
        ConfirmDialogs.confirmLogout(rootPane, () -> {
            authService.logout();
            refreshAuthUi();
            AppNavigator.showHome();
        });
    }

    @FXML
    private void handleScrollToTop() {
        scrollTo(heroSection);
    }

    @FXML
    private void handleScrollToAbout() {
        scrollTo(aboutSection);
    }

    @FXML
    private void handleScrollToServices() {
        scrollTo(servicesSection);
    }

    @FXML
    private void handleScrollToBlog() {
        scrollTo(blogSection);
    }

    @FXML
    private void handleScrollToContact() {
        scrollTo(contactSection);
    }

    @FXML
    private void handleOpenTeleconsultation() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showTeleconsultation();
    }

    @FXML
    private void handleOpenDocuments() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showDocumentSharing();
    }

    @FXML
    private void handleOpenMessaging() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showMessaging();
    }

    private void scrollTo(Node section) {
        if (homeScroll == null || section == null || homeScroll.getContent() == null) {
            AppNavigator.showHome();
            return;
        }

        double contentHeight = homeScroll.getContent().getBoundsInLocal().getHeight();
        double viewportHeight = homeScroll.getViewportBounds().getHeight();
        double denominator = contentHeight - viewportHeight;

        if (denominator <= 0) {
            homeScroll.setVvalue(0);
            return;
        }

        double y = section.getBoundsInParent().getMinY();
        double target = Math.max(0, Math.min(1, y / denominator));
        homeScroll.setVvalue(target);
    }

    private void refreshAuthUi() {
        boolean connected = AuthSession.isAuthenticated();

        setVisibleManaged(loginButton, !connected);
        setVisibleManaged(registerButton, !connected);
        setVisibleManaged(notifWrap, connected);
        setVisibleManaged(msgWrap, connected);
        setVisibleManaged(accountAvatarButton, connected);

        if (!connected) {
            hideAllMenus();
            setBadge(notifBadgeLabel, 0);
            setBadge(msgBadgeLabel, 0);
            return;
        }

        User user = AuthSession.getCurrentUser();
        if (user == null) {
            return;
        }

        boolean isAdmin = "ROLE_ADMIN".equalsIgnoreCase(user.getRole());
        setVisibleManaged(adminDashboardButton, connected && isAdmin);

        accountAvatarButton.setText(computeInitials(user));
        refreshDashboardData();
    }

    private void refreshDashboardData() {
        User user = AuthSession.getCurrentUser();
        if (user == null || user.getId() == null) {
            dashboardData = HomeDashboardService.DashboardData.empty();
            setBadge(notifBadgeLabel, 0);
            setBadge(msgBadgeLabel, 0);
            return;
        }

        dashboardData = homeDashboardService.loadForUser(user.getId());
        setBadge(notifBadgeLabel, dashboardData.unreadNotifications());
        setBadge(msgBadgeLabel, dashboardData.unreadMessages());
    }

    private ContextMenu buildNotificationsMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("home-popup-menu");

        VBox card = new VBox();
        card.getStyleClass().add("home-dropdown-card");

        HBox header = new HBox();
        header.getStyleClass().add("home-dropdown-header");
        header.setAlignment(Pos.CENTER_LEFT);

        HBox titleWrap = new HBox(8);
        titleWrap.setAlignment(Pos.CENTER_LEFT);
        FontIcon bellIcon = new FontIcon("fas-bell");
        bellIcon.getStyleClass().add("home-dropdown-header-icon");
        Label title = new Label("Notifications");
        title.getStyleClass().add("home-dropdown-title");
        titleWrap.getChildren().addAll(bellIcon, title);

        Button markAllRead = new Button("Tout lire");
        markAllRead.getStyleClass().add("home-dropdown-link-btn");
        markAllRead.setOnAction(event -> {
            User user = AuthSession.getCurrentUser();
            if (user != null && user.getId() != null) {
                homeDashboardService.markAllNotificationsRead(user.getId());
                refreshDashboardData();
                hideAllMenus();
                notificationsMenu = buildNotificationsMenu();
                notificationsMenu.show(notifButton, Side.BOTTOM, 0, 8);
            }
        });

        HBox.setHgrow(titleWrap, Priority.ALWAYS);
        header.getChildren().addAll(titleWrap, markAllRead);

        VBox content = new VBox();
        content.getStyleClass().add("home-dropdown-list");

        if (dashboardData.notifications().isEmpty()) {
            VBox emptyState = new VBox(8);
            emptyState.getStyleClass().add("home-dropdown-empty-state");
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(28, 0, 30, 0));
            FontIcon emptyIcon = new FontIcon("fas-bell-slash");
            emptyIcon.getStyleClass().add("home-dropdown-empty-icon");
            Label emptyText = new Label("Aucune notification");
            emptyText.getStyleClass().add("home-dropdown-empty-text");
            emptyState.getChildren().addAll(emptyIcon, emptyText);
            content.getChildren().add(emptyState);
        } else {
            for (HomeDashboardService.NotificationPreview notification : dashboardData.notifications()) {
                content.getChildren().add(createNotificationRow(notification));
            }
        }

        card.getChildren().addAll(header, content);
        CustomMenuItem container = new CustomMenuItem(card, false);
        container.setHideOnClick(false);
        menu.getItems().add(container);

        return menu;
    }

    private ContextMenu buildMessagesMenu() {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("home-popup-menu");

        VBox card = new VBox();
        card.getStyleClass().add("home-dropdown-card");

        HBox header = new HBox();
        header.getStyleClass().add("home-dropdown-header");
        header.setAlignment(Pos.CENTER_LEFT);

        HBox titleWrap = new HBox(8);
        titleWrap.setAlignment(Pos.CENTER_LEFT);
        FontIcon msgIcon = new FontIcon("fas-comments");
        msgIcon.getStyleClass().add("home-dropdown-header-icon");
        Label title = new Label("Messages");
        title.getStyleClass().add("home-dropdown-title");
        titleWrap.getChildren().addAll(msgIcon, title);

        Button seeAllBtn = new Button("Voir tout");
        seeAllBtn.getStyleClass().add("home-dropdown-link-btn");
        seeAllBtn.setOnAction(event -> {
            hideAllMenus();
            openMessagesPage();
        });

        HBox.setHgrow(titleWrap, Priority.ALWAYS);
        header.getChildren().addAll(titleWrap, seeAllBtn);

        VBox content = new VBox();
        content.getStyleClass().add("home-dropdown-list");

        if (dashboardData.conversations().isEmpty()) {
            VBox emptyState = new VBox(8);
            emptyState.getStyleClass().add("home-dropdown-empty-state");
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(40, 0, 44, 0));
            FontIcon emptyIcon = new FontIcon("fas-comments");
            emptyIcon.getStyleClass().add("home-dropdown-empty-icon");
            Label emptyText = new Label("Aucune conversation");
            emptyText.getStyleClass().add("home-dropdown-empty-text");
            emptyState.getChildren().addAll(emptyIcon, emptyText);
            content.getChildren().add(emptyState);
        } else {
            for (HomeDashboardService.ConversationPreview conversation : dashboardData.conversations()) {
                content.getChildren().add(createConversationRow(conversation));
            }
        }

        card.getChildren().addAll(header, content);
        CustomMenuItem container = new CustomMenuItem(card, false);
        container.setHideOnClick(false);
        menu.getItems().add(container);

        return menu;
    }

    private HBox createNotificationRow(HomeDashboardService.NotificationPreview notification) {
        HBox row = new HBox(12);
        row.getStyleClass().add("home-dropdown-item");
        row.setAlignment(Pos.TOP_LEFT);

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("home-dropdown-item-icon-wrap");
        FontIcon icon = new FontIcon("fas-bell");
        icon.getStyleClass().add("home-dropdown-item-icon");
        iconWrap.getChildren().add(icon);

        VBox textWrap = new VBox(4);
        textWrap.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(textWrap, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(safe(notification.titre()));
        title.getStyleClass().add("home-dropdown-item-title");
        Label when = new Label(formatRelativeDays(notification.sentAt()));
        when.getStyleClass().add("home-dropdown-time-badge");
        HBox.setHgrow(title, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, when);

        Label message = new Label(safe(notification.message()));
        message.getStyleClass().add("home-dropdown-item-subtitle");
        message.setWrapText(true);

        textWrap.getChildren().addAll(titleRow, message);
        row.getChildren().addAll(iconWrap, textWrap);
        row.setOnMouseClicked(event -> {
            hideAllMenus();
            openNotificationPage(notification);
        });

        return row;
    }

    private HBox createConversationRow(HomeDashboardService.ConversationPreview conversation) {
        HBox row = new HBox(12);
        row.getStyleClass().add("home-dropdown-item");
        row.setAlignment(Pos.TOP_LEFT);

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("home-dropdown-item-icon-wrap");
        FontIcon icon = new FontIcon("fas-comments");
        icon.getStyleClass().add("home-dropdown-item-icon");
        iconWrap.getChildren().add(icon);

        VBox textWrap = new VBox(4);
        textWrap.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(textWrap, Priority.ALWAYS);

        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(safe(conversation.otherUserDisplay()));
        title.getStyleClass().add("home-dropdown-item-title");
        Label when = new Label(formatRelativeDays(conversation.lastMessageAt()));
        when.getStyleClass().add("home-dropdown-time-badge");
        HBox.setHgrow(title, Priority.ALWAYS);
        titleRow.getChildren().addAll(title, when);

        String subtitleText = safe(conversation.lastMessage());
        if (conversation.unreadInConversation() > 0) {
            subtitleText = "(" + conversation.unreadInConversation() + ") " + subtitleText;
        }
        Label subtitle = new Label(subtitleText);
        subtitle.getStyleClass().add("home-dropdown-item-subtitle");
        subtitle.setWrapText(true);

        textWrap.getChildren().addAll(titleRow, subtitle);
        row.getChildren().addAll(iconWrap, textWrap);
        row.setOnMouseClicked(event -> {
            hideAllMenus();
            openConversationPage(conversation);
        });

        return row;
    }

    private String formatRelativeDays(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        long days = java.time.Duration.between(dateTime, LocalDateTime.now()).toDays();
        long normalized = Math.max(0, days);
        return normalized + " j";
    }

    private ContextMenu buildAccountMenu() {
        ContextMenu menu = new ContextMenu();
        menu.setAutoHide(true);

        User user = AuthSession.getCurrentUser();
        if (user == null) {
            return menu;
        }

        String role = resolveRole(user);
        boolean isPatient = "ROLE_PATIENT".equals(role);
        boolean isMedecin = "ROLE_MEDECIN".equals(role);
        boolean isPharmacien = "ROLE_PHARMACIEN".equals(role);
        boolean isAdmin = "ROLE_ADMIN".equals(role);
        boolean canTeleconsult = isPatient || isMedecin;
        boolean canPlan = Set.of("ROLE_PATIENT", "ROLE_MEDECIN", "ROLE_COACH", "ROLE_NUTRITIONNISTE").contains(role);
        boolean canAiTools = authorizationPolicyService.hasAiToolsAccess(user);

        VBox card = new VBox(10);
        card.getStyleClass().add("account-dropdown-card");
        card.setPrefWidth(320);

        StackPane headerWrap = new StackPane();
        headerWrap.getStyleClass().add("account-dropdown-header");

        Region bubbleTop = new Region();
        bubbleTop.getStyleClass().addAll("account-header-bubble", "account-header-bubble-top");
        Region bubbleBottom = new Region();
        bubbleBottom.getStyleClass().addAll("account-header-bubble", "account-header-bubble-bottom");

        VBox header = new VBox(8);
        header.setAlignment(Pos.CENTER);
        Label avatar = new Label(computeInitials(user));
        avatar.getStyleClass().add("account-avatar-large");
        Label name = new Label(resolveFullName(user));
        name.getStyleClass().add("account-name");
        Label email = new Label(safe(user.getEmail()));
        email.getStyleClass().add("account-email");
        Label roleLabel = new Label(humanizeRole(role));
        roleLabel.getStyleClass().add("account-role-pill");
        HBox scoreRow = new HBox(6);
        scoreRow.setAlignment(Pos.CENTER);
        Label scoreValue = new Label(computeProfileScore(user) + "/100");
        scoreValue.getStyleClass().add("account-score-value");
        Label scoreCaption = new Label("Score IA");
        scoreCaption.getStyleClass().add("account-score-caption");
        scoreRow.getChildren().addAll(scoreValue, scoreCaption);
        header.getChildren().addAll(avatar, name, email, roleLabel, scoreRow);
        headerWrap.getChildren().addAll(bubbleTop, bubbleBottom, header);

        VBox body = new VBox(8);
        body.getStyleClass().add("account-dropdown-body");

        if (isAdmin) {
            body.getChildren().add(createAccountButton("Dashboard Admin", "fas-chart-line", this::handleOpenAdminDashboard));
        }

        body.getChildren().add(createAccountButton("Paramètres", "fas-user", this::openProfilePage));
        body.getChildren().add(createAccountButton("Sécurité (MFA)", "fas-key", this::openMfaPage));

        Button accompagnementToggle = createAccountButton("Accompagnement", "fas-hand-holding-heart", true, () -> {
            // no-op, handled by toggle below
        });
        VBox accompagnementSubmenu = new VBox(6);
        accompagnementSubmenu.getStyleClass().add("account-submenu-box");
        accompagnementSubmenu.setVisible(false);
        accompagnementSubmenu.setManaged(false);

        accompagnementSubmenu.getChildren().add(createSubButton("Messages", this::openMessagesPage));
        accompagnementSubmenu.getChildren().add(createSubButton("Documents", this::openDocumentsPage));
        if (canTeleconsult) {
            accompagnementSubmenu.getChildren().add(createSubButton("Consultations video", this::openTeleconsultationPage));
        }
        if (canPlan) {
            accompagnementSubmenu.getChildren().add(createSubButton("Mes plans", this::openPlansPage));
        }
        if (isPatient) {
            accompagnementSubmenu.getChildren().add(createSubButton("Mon journal sante", this::openJournalPage));
            accompagnementSubmenu.getChildren().add(createSubButton("Mes symptomes", this::openSymptomsPage));
        }

        accompagnementToggle.setOnAction(event -> {
            boolean show = !accompagnementSubmenu.isVisible();
            accompagnementSubmenu.setVisible(show);
            accompagnementSubmenu.setManaged(show);
        });

        body.getChildren().add(accompagnementToggle);
        body.getChildren().add(accompagnementSubmenu);

        body.getChildren().add(createAccountButton("Abonnement", "fas-star", this::openSubscriptionPage));
        if (isPatient || isMedecin) {
            body.getChildren().add(createAccountButton("Rendez-vous", "fas-calendar-check", this::openAppointmentsPage));
        }
        if (isPharmacien) {
            body.getChildren().add(createAccountButton("Ma pharmacie", "fas-clinic-medical", this::openPharmacyPage));
        }
        if (canAiTools) {
            body.getChildren().add(createAccountButton("Outils IA", "fas-robot", this::openAiToolsPage));
        }

        Separator sep1 = new Separator();
        body.getChildren().add(sep1);

        HBox themeRow = new HBox(8);
        themeRow.getStyleClass().add("account-inline-row");
        Label themeLabel = new Label("Theme");
        themeLabel.getStyleClass().add("account-inline-label");
        HBox themeButtons = new HBox(6);
        HBox.setHgrow(themeButtons, Priority.ALWAYS);
        Button lightBtn = new Button("Clair");
        lightBtn.getStyleClass().add("account-mini-pill");
        lightBtn.setOnAction(event -> {
            selectedTheme = "light";
            applyTheme();
        });
        Button darkBtn = new Button("Sombre");
        darkBtn.getStyleClass().add("account-mini-pill");
        darkBtn.setOnAction(event -> {
            selectedTheme = "dark";
            applyTheme();
        });
        themeButtons.getChildren().addAll(lightBtn, darkBtn);
        themeRow.getChildren().addAll(themeLabel, themeButtons);

        HBox localeRow = new HBox(8);
        localeRow.getStyleClass().add("account-inline-row");
        Label localeLabel = new Label("Langue");
        localeLabel.getStyleClass().add("account-inline-label");
        HBox localeButtons = new HBox(6);
        HBox.setHgrow(localeButtons, Priority.ALWAYS);
        Button frBtn = new Button("FR");
        frBtn.getStyleClass().add("account-mini-pill");
        frBtn.setOnAction(event -> applyLocale("FR"));
        Button enBtn = new Button("EN");
        enBtn.getStyleClass().add("account-mini-pill");
        enBtn.setOnAction(event -> applyLocale("EN"));
        Button arBtn = new Button("AR");
        arBtn.getStyleClass().add("account-mini-pill");
        arBtn.setOnAction(event -> applyLocale("AR"));
        localeButtons.getChildren().addAll(frBtn, enBtn, arBtn);
        localeRow.getChildren().addAll(localeLabel, localeButtons);

        body.getChildren().addAll(themeRow, localeRow, new Separator());

        Button logoutBtn = createAccountButton("Déconnexion", "fas-sign-out-alt", this::handleLogout);
        logoutBtn.getStyleClass().add("account-logout-row");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        body.getChildren().add(logoutBtn);

        card.getChildren().addAll(headerWrap, body);

        CustomMenuItem container = new CustomMenuItem(card, false);
        container.setHideOnClick(false);
        menu.getItems().add(container);

        return menu;
    }

    private Button createAccountButton(String text, String iconLiteral, Runnable action) {
        return createAccountButton(text, iconLiteral, false, action);
    }

    private Button createAccountButton(String text, String iconLiteral, boolean showChevron, Runnable action) {
        Button button = new Button();
        button.getStyleClass().add("account-row-btn");
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        StackPane iconWrap = new StackPane();
        iconWrap.getStyleClass().add("account-row-icon-wrap");
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("account-row-icon");
        iconWrap.getChildren().add(icon);

        Label textLabel = new Label(text);
        textLabel.getStyleClass().add("account-row-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox content = new HBox(12);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().addAll(iconWrap, textLabel, spacer);
        if (showChevron) {
            FontIcon chevron = new FontIcon("fas-chevron-right");
            chevron.getStyleClass().add("account-row-chevron");
            content.getChildren().add(chevron);
        }

        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> {
            hideAllMenus();
            action.run();
        });
        return button;
    }

    private Button createSubButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("account-submenu-btn");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> {
            hideAllMenus();
            action.run();
        });
        return button;
    }

    private void openProfilePage() {
        AppNavigator.showProfileSettings();
    }

    private void openMfaPage() {
        AppNavigator.showProfileMfa();
    }

    private void openAiToolsPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showFeaturePage("Outils IA", "Analyse et recommandations", List.of(
                "Analyse des symptomes",
                "Score de risque",
                "Recommandation personnalisee"
        ));
    }

    private void openMessagesPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showMessaging();
    }

    private void openConversationPage(HomeDashboardService.ConversationPreview conversation) {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showMessaging();
    }

    private void openDocumentsPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showDocumentSharing();
    }

    private void openTeleconsultationPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showTeleconsultation();
    }

    private void openPlansPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showAccompanimentPlans();
    }

    private void openJournalPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showFeaturePage("Journal sante", "Suivi quotidien", List.of(
                "Poids et IMC",
                "Sommeil",
                "Hydratation",
                "Activite"
        ));
    }

    private void openSymptomsPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showFeaturePage("Mes symptomes", "Historique et suivi", List.of(
                "Declaration des symptomes",
                "Evolution dans le temps",
                "Alertes de suivi"
        ));
    }

    private void openSubscriptionPage() {
        AppNavigator.showSubscriptionPage();
    }

    private void openAppointmentsPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showFeaturePage("Rendez-vous", "Gestion des consultations", List.of(
                "Rendez-vous a venir",
                "Historique",
                "Prendre un nouveau rendez-vous"
        ));
    }

    private void openPharmacyPage() {
        if (!guardPremiumAccess()) {
            return;
        }
        AppNavigator.showFeaturePage("Ma pharmacie", "Espace pharmacie", List.of(
                "Stock des medicaments",
                "Ordonnances recues",
                "Demandes clients"
        ));
    }

    private void openNotificationPage(HomeDashboardService.NotificationPreview notification) {
        AppNavigator.showFeaturePage("Notification", safe(notification.titre()), List.of(
                "Date: " + formatDate(notification.sentAt()),
                "Message:",
                safe(notification.message())
        ));
    }

    private void setBadge(Label badge, int value) {
        if (badge == null) {
            return;
        }
        boolean visible = value > 0;
        badge.setVisible(visible);
        badge.setManaged(visible);
        badge.setText(value > 99 ? "99+" : String.valueOf(value));
    }

    private String resolveRole(User user) {
        if (user == null) {
            return "";
        }
        return authorizationPolicyService.effectiveRole(user);
    }

    private String resolveFullName(User user) {
        String full = (safe(user.getNom()) + " " + safe(user.getPrenom())).trim();
        return full.isBlank() ? "Utilisateur SANTEA" : full;
    }

    private String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return DATE_FORMAT.format(dateTime);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private int computeProfileScore(User user) {
        int score = 35;
        if (!safe(user.getNom()).isBlank()) {
            score += 15;
        }
        if (!safe(user.getPrenom()).isBlank()) {
            score += 15;
        }
        if (!safe(user.getEmail()).isBlank()) {
            score += 20;
        }
        if (user.getEmailVerified() != null && user.getEmailVerified()) {
            score += 15;
        }
        if (!safe(user.getSubscriptionStatus()).isBlank()) {
            score += 10;
        }
        return Math.min(score, 100);
    }

    private String humanizeRole(String role) {
        return switch (role) {
            case "ROLE_MEDECIN" -> "MEDECIN";
            case "ROLE_PHARMACIEN" -> "PHARMACIEN";
            case "ROLE_ADMIN" -> "ADMIN";
            case "ROLE_COACH" -> "COACH";
            case "ROLE_NUTRITIONNISTE" -> "NUTRITIONNISTE";
            default -> "PATIENT";
        };
    }

    private String computeInitials(User user) {
        String nom = safe(user.getNom());
        String prenom = safe(user.getPrenom());

        String first = nom.isBlank() ? "" : nom.substring(0, 1).toUpperCase();
        String second = prenom.isBlank() ? "" : prenom.substring(0, 1).toUpperCase();
        String initials = (first + second).trim();

        if (!initials.isBlank()) {
            return initials;
        }

        String email = safe(user.getEmail());
        return email.isBlank() ? "SU" : email.substring(0, 1).toUpperCase();
    }

    private void setVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void hideAllMenus() {
        if (notificationsMenu != null) {
            notificationsMenu.hide();
        }
        if (messagesMenu != null) {
            messagesMenu.hide();
        }
        if (accountMenu != null) {
            accountMenu.hide();
        }
    }

    private void applyTheme() {
        if (rootPane == null) {
            return;
        }
        if ("dark".equalsIgnoreCase(selectedTheme)) {
            rootPane.setStyle("-fx-background-color: #0f172a;");
        } else {
            rootPane.setStyle("");
        }
    }

    private void applyLocale(String locale) {
        User user = AuthSession.getCurrentUser();
        if (user != null) {
            user.setLocale(locale);
        }
    }

    private boolean guardPremiumAccess() {
        User user = AuthSession.getCurrentUser();
        AuthorizationPolicyService.AccessDecision decision = authorizationPolicyService.decisionForProtectedFeatures(user);
        if (decision.allowed()) {
            return true;
        }

        AppNavigator.showFeaturePage("Acces restreint", "Droits d'abonnement", List.of(
                decision.message(),
                "Statut actuel: " + (user == null ? "INCONNU" : safe(user.getSubscriptionStatus())),
                "Type actuel: " + (user == null ? "Aucun" : safe(user.getSubscriptionType()))
        ));
        return false;
    }
}
