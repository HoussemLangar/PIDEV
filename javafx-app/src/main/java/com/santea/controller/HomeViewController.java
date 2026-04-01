package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.HomeDashboardService;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HomeViewController {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final AuthService authService = new AuthService();
    private final HomeDashboardService homeDashboardService = new HomeDashboardService();

    private String selectedTheme = "light";
    private String selectedLocale = "FR";

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
        authService.logout();
        refreshAuthUi();
        AppNavigator.showHome();
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

    private void scrollTo(Node section) {
        if (homeScroll == null || section == null || homeScroll.getContent() == null) {
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

        MenuItem title = new MenuItem("Notifications");
        title.setDisable(true);
        menu.getItems().add(title);

        MenuItem markAllRead = new MenuItem("Tout lire");
        markAllRead.setOnAction(event -> {
            User user = AuthSession.getCurrentUser();
            if (user != null && user.getId() != null) {
                homeDashboardService.markAllNotificationsRead(user.getId());
                refreshDashboardData();
            }
        });
        menu.getItems().add(markAllRead);
        menu.getItems().add(new SeparatorMenuItem());

        if (dashboardData.notifications().isEmpty()) {
            MenuItem empty = new MenuItem("Aucune notification");
            empty.setDisable(true);
            menu.getItems().add(empty);
            return menu;
        }

        for (HomeDashboardService.NotificationPreview notification : dashboardData.notifications()) {
            String prefix = notification.read() ? "" : "* ";
            String when = formatDate(notification.sentAt());
            String text = prefix + safe(notification.titre()) + "  " + when;
            MenuItem item = new MenuItem(text.trim());
            item.setOnAction(event -> openNotificationPage(notification));
            menu.getItems().add(item);
        }

        return menu;
    }

    private ContextMenu buildMessagesMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem title = new MenuItem("Messages");
        title.setDisable(true);
        menu.getItems().add(title);
        menu.getItems().add(new SeparatorMenuItem());

        if (dashboardData.conversations().isEmpty()) {
            MenuItem empty = new MenuItem("Aucune conversation");
            empty.setDisable(true);
            menu.getItems().add(empty);
            return menu;
        }

        for (HomeDashboardService.ConversationPreview conversation : dashboardData.conversations()) {
            String badge = conversation.unreadInConversation() > 0 ? "(" + conversation.unreadInConversation() + ") " : "";
            String text = badge + conversation.otherUserDisplay() + " - " + safe(conversation.lastMessage());
            MenuItem item = new MenuItem(text);
            item.setOnAction(event -> openConversationPage(conversation));
            menu.getItems().add(item);
        }

        return menu;
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
        boolean canAiTools = "ROLE_ADMIN".equals(role)
                || ("ACTIVE".equalsIgnoreCase(safe(user.getSubscriptionStatus()))
                && Set.of("ROLE_PATIENT", "ROLE_MEDECIN", "ROLE_COACH", "ROLE_NUTRITIONNISTE").contains(role));

        VBox card = new VBox(10);
        card.getStyleClass().add("account-dropdown-card");
        card.setPrefWidth(300);

        VBox header = new VBox(7);
        header.getStyleClass().add("account-dropdown-header");
        Label avatar = new Label(computeInitials(user));
        avatar.getStyleClass().add("account-avatar-large");
        Label name = new Label(resolveFullName(user));
        name.getStyleClass().add("account-name");
        Label email = new Label(safe(user.getEmail()));
        email.getStyleClass().add("account-email");
        Label roleLabel = new Label(humanizeRole(role));
        roleLabel.getStyleClass().add("account-role-pill");
        HBox scoreRow = new HBox(6);
        Label scoreValue = new Label(computeProfileScore(user) + "/100");
        scoreValue.getStyleClass().add("account-score-value");
        Label scoreCaption = new Label("Score IA");
        scoreCaption.getStyleClass().add("account-score-caption");
        scoreRow.getChildren().addAll(scoreValue, scoreCaption);
        header.getChildren().addAll(avatar, name, email, roleLabel, scoreRow);

        VBox body = new VBox(8);
        body.getStyleClass().add("account-dropdown-body");

        if (isAdmin) {
            body.getChildren().add(createAccountButton("Dashboard Admin", this::handleOpenAdminDashboard));
        }

        body.getChildren().add(createAccountButton("Parametres", this::openProfilePage));
        body.getChildren().add(createAccountButton("Securite (MFA)", this::openMfaPage));

        Button accompagnementToggle = createAccountButton("Accompagnement", () -> {
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

        body.getChildren().add(createAccountButton("Abonnement", this::openSubscriptionPage));
        if (isPatient || isMedecin) {
            body.getChildren().add(createAccountButton("Rendez-vous", this::openAppointmentsPage));
        }
        if (isPharmacien) {
            body.getChildren().add(createAccountButton("Ma pharmacie", this::openPharmacyPage));
        }
        if (canAiTools) {
            body.getChildren().add(createAccountButton("Outils IA", this::openAiToolsPage));
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
        frBtn.setOnAction(event -> selectedLocale = "FR");
        Button enBtn = new Button("EN");
        enBtn.getStyleClass().add("account-mini-pill");
        enBtn.setOnAction(event -> selectedLocale = "EN");
        Button arBtn = new Button("AR");
        arBtn.getStyleClass().add("account-mini-pill");
        arBtn.setOnAction(event -> selectedLocale = "AR");
        localeButtons.getChildren().addAll(frBtn, enBtn, arBtn);
        localeRow.getChildren().addAll(localeLabel, localeButtons);

        body.getChildren().addAll(themeRow, localeRow, new Separator());

        Button logoutBtn = new Button("Deconnexion");
        logoutBtn.getStyleClass().add("account-logout-btn");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(event -> handleLogout());
        body.getChildren().add(logoutBtn);

        card.getChildren().addAll(header, body);

        CustomMenuItem container = new CustomMenuItem(card, false);
        container.setHideOnClick(false);
        menu.getItems().add(container);

        return menu;
    }

    private Button createAccountButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("account-row-btn");
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
        User user = AuthSession.getCurrentUser();
        if (user == null) {
            return;
        }

        List<String> lines = new ArrayList<>();
        lines.add("Nom: " + safe(user.getNom()));
        lines.add("Prenom: " + safe(user.getPrenom()));
        lines.add("Email: " + safe(user.getEmail()));
        lines.add("Role: " + humanizeRole(resolveRole(user)));
        lines.add("Abonnement: " + safe(user.getSubscriptionStatus()));
        AppNavigator.showFeaturePage("Parametres du profil", "Gestion du profil utilisateur", lines);
    }

    private void openMfaPage() {
        AppNavigator.showFeaturePage("Securite MFA", "Protection du compte", List.of(
                "Etat MFA: actif/inactif",
                "Codes de recuperation",
                "Historique des sessions",
                "Validation en 2 etapes"
        ));
    }

    private void openAiToolsPage() {
        AppNavigator.showFeaturePage("Outils IA", "Analyse et recommandations", List.of(
                "Analyse des symptomes",
                "Score de risque",
                "Recommandation personnalisee"
        ));
    }

    private void openMessagesPage() {
        List<String> lines = new ArrayList<>();
        for (HomeDashboardService.ConversationPreview conversation : dashboardData.conversations()) {
            lines.add(conversation.otherUserDisplay() + " | " + safe(conversation.lastMessage()));
        }
        if (lines.isEmpty()) {
            lines.add("Aucune conversation.");
        }
        AppNavigator.showFeaturePage("Messages", "Messagerie securisee", lines);
    }

    private void openConversationPage(HomeDashboardService.ConversationPreview conversation) {
        AppNavigator.showFeaturePage("Conversation", "Discussion avec " + conversation.otherUserDisplay(), List.of(
                "Dernier message: " + safe(conversation.lastMessage()),
                "Date: " + formatDate(conversation.lastMessageAt()),
                "Non lus: " + conversation.unreadInConversation()
        ));
    }

    private void openDocumentsPage() {
        AppNavigator.showFeaturePage("Documents", "Gestion documentaire", List.of(
                "Voir mes documents",
                "Televerser un document",
                "Partager avec un professionnel"
        ));
    }

    private void openTeleconsultationPage() {
        AppNavigator.showFeaturePage("Teleconsultation", "Consultations video", List.of(
                "Planning des sessions",
                "Historique des consultations",
                "Lancer une consultation"
        ));
    }

    private void openPlansPage() {
        AppNavigator.showFeaturePage("Mes plans", "Accompagnement personnalise", List.of(
                "Plan nutrition",
                "Plan activite physique",
                "Suivi de progression"
        ));
    }

    private void openJournalPage() {
        AppNavigator.showFeaturePage("Journal sante", "Suivi quotidien", List.of(
                "Poids et IMC",
                "Sommeil",
                "Hydratation",
                "Activite"
        ));
    }

    private void openSymptomsPage() {
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
        AppNavigator.showFeaturePage("Rendez-vous", "Gestion des consultations", List.of(
                "Rendez-vous a venir",
                "Historique",
                "Prendre un nouveau rendez-vous"
        ));
    }

    private void openPharmacyPage() {
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
        return safe(user.getRole()).toUpperCase();
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
}
