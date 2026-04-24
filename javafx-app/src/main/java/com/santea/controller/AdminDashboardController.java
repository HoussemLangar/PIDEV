package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AdminDashboardService;
import com.santea.service.AdminOperationsService;
import com.santea.service.AuthService;
import com.santea.service.AuthSession;
import com.santea.service.PharmacyService;
import com.santea.ui.ConfirmDialogs;
import com.santea.ui.ModalDialogs;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class AdminDashboardController {
    private static final int PAGE_SIZE = 8;
    private static final List<String> PROFESSIONAL_ROLES = List.of(
            "ROLE_MEDECIN",
            "ROLE_PHARMACIEN",
            "ROLE_COACH",
            "ROLE_NUTRITIONNISTE"
    );
    private static final List<String> EDITABLE_ROLES = List.of(
        "ROLE_ADMIN",
        "ROLE_PATIENT",
        "ROLE_MEDECIN",
        "ROLE_PHARMACIEN",
        "ROLE_COACH",
        "ROLE_NUTRITIONNISTE"
    );
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final AdminDashboardService adminDashboardService = new AdminDashboardService();
    private final AdminOperationsService adminOperationsService = new AdminOperationsService();
    private final PharmacyService pharmacyService = new PharmacyService();
    private final AuthService authService = new AuthService();

    private List<AdminOperationsService.AdminUserRow> usersCache = List.of();
    private List<AdminOperationsService.ValidationUserRow> validationCache = List.of();
    private List<AdminOperationsService.ActiveSessionRow> sessionsCache = List.of();
    private List<AdminOperationsService.SuspiciousLoginRow> suspiciousCache = List.of();
    private List<AdminOperationsService.SubscriptionRow> subscriptionsCache = List.of();
    private List<AdminOperationsService.RevenueRow> revenuesCache = List.of();
    private List<AdminOperationsService.CommunityRow> communityCache = List.of();
    private List<AdminOperationsService.AppointmentAdminRow> appointmentsAdminCache = List.of();
    private List<AdminOperationsService.AccompanimentAdminRow> accompanimentsCache = List.of();
    private List<AdminOperationsService.PendingApprovalRow> pendingApprovalsCache = List.of();
    private List<AdminOperationsService.ModerationRow> moderationCache = List.of();
    private List<AdminOperationsService.UserScoreRow> scoresCache = List.of();
    private List<PharmacyService.PharmacyRow> pharmaciesAdminCache = List.of();
    private List<PharmacyService.MedicamentRow> medicamentsAdminCache = List.of();
    private List<PharmacyService.StockRow> stocksAdminCache = List.of();
    private List<PharmacyService.ReservationRow> reservationsAdminCache = List.of();

    private int usersPage;
    private int validationPage;
    private int sessionsPage;
    private int suspiciousPage;
    private int subscriptionsPage;
    private int revenuesPage;
    private int pharmaciesAdminPage;
    private int medicamentsAdminPage;
    private int stocksAdminPage;
    private int reservationsAdminPage;
    private int communityPage;
    private int appointmentsAdminPage;
    private int accompanimentsPage;

    private boolean usersGridMode = true;
    private boolean validationGridMode = true;
    private boolean sessionsGridMode = true;
    private boolean suspiciousGridMode = true;
    private boolean subscriptionsGridMode = true;
    private boolean revenuesGridMode = true;
    private boolean pharmaciesAdminGridMode = true;
    private boolean medicamentsAdminGridMode = true;
    private boolean stocksAdminGridMode = true;
    private boolean reservationsAdminGridMode = true;
    private boolean communityGridMode = true;
    private boolean appointmentsAdminGridMode = true;
    private boolean accompanimentsGridMode = true;

    @FXML
    private Label headerTitleLabel;
    @FXML
    private Label headerSubtitleLabel;

    @FXML
    private Button navDashboardButton;
    @FXML
    private Button navUsersButton;
    @FXML
    private Button navValidationButton;
    @FXML
    private Button navSessionsButton;
    @FXML
    private Button navSuspiciousButton;
    @FXML
    private Button navSubscriptionsButton;
    @FXML
    private Button navRevenuesButton;
    @FXML
    private Button navPharmaciesAdminButton;
    @FXML
    private Button navMedicamentsAdminButton;
    @FXML
    private Button navStocksAdminButton;
    @FXML
    private Button navReservationsAdminButton;
    @FXML
    private Button navAccompanimentsButton;
    @FXML
    private Button navCommunityButton;
    @FXML
    private Button navAppointmentsAdminButton;

    @FXML
    private VBox pageDashboard;
    @FXML
    private VBox pageUsers;
    @FXML
    private VBox pageValidation;
    @FXML
    private VBox pageSessions;
    @FXML
    private VBox pageSuspicious;
    @FXML
    private VBox pageSubscriptions;
    @FXML
    private VBox pageRevenues;
    @FXML
    private VBox pagePharmaciesAdmin;
    @FXML
    private VBox pageMedicamentsAdmin;
    @FXML
    private VBox pageStocksAdmin;
    @FXML
    private VBox pageReservationsAdmin;
    @FXML
    private VBox pageAccompaniments;
    @FXML
    private VBox pageCommunity;
    @FXML
    private VBox pageAppointmentsAdmin;

    @FXML
    private Label usersTotalLabel;
    @FXML
    private Label usersBannedLabel;
    @FXML
    private Label usersActiveLabel;
    @FXML
    private Label activeSubscriptionsLabel;
    @FXML
    private Label subscriptionsExpiredMiniLabel;
    @FXML
    private Label subscriptionsCancelledMiniLabel;
    @FXML
    private Label monthlyRevenueLabel;
    @FXML
    private Label totalRevenueMiniLabel;
    @FXML
    private Label pendingPaymentsLabel;

    @FXML
    private Label usersVerifiedLabel;
    @FXML
    private Label usersPendingLabel;
    @FXML
    private Label suspiciousBlockedLabel;
    @FXML
    private Label paymentsSucceededLabel;
    @FXML
    private Label activeSessionsLabel;
    @FXML
    private Label moderationPendingLabel;
    @FXML
    private Label scoreAverageLabel;

    @FXML
    private LineChart<String, Number> registrationsLineChart;
    @FXML
    private PieChart revenuePieChart;

    @FXML
    private Label adminFeedbackLabel;

    @FXML
    private Label usersTotalCountLabel;
    @FXML
    private Label usersScoreAverageLabel;
    @FXML
    private TextField usersSearchField;
    @FXML
    private ComboBox<String> usersRoleFilterCombo;
    @FXML
    private ComboBox<String> usersStatusFilterCombo;
    @FXML
    private Button usersListModeButton;
    @FXML
    private Button usersGridModeButton;
    @FXML
    private FlowPane usersRowsBox;
    @FXML
    private Label usersPaginationInfoLabel;
    @FXML
    private Button usersPrevPageButton;
    @FXML
    private Button usersNextPageButton;

    @FXML
    private Label validationTotalLabel;
    @FXML
    private Label validationEmailConfirmedLabel;
    @FXML
    private Label validationEmailNotConfirmedLabel;
    @FXML
    private Label validationAdminPendingLabel;
    @FXML
    private TextField validationSearchField;
    @FXML
    private ComboBox<String> validationEmailFilterCombo;
    @FXML
    private Button validationListModeButton;
    @FXML
    private Button validationGridModeButton;
    @FXML
    private FlowPane validationRowsBox;
    @FXML
    private Label validationPaginationInfoLabel;
    @FXML
    private Button validationPrevPageButton;
    @FXML
    private Button validationNextPageButton;

    @FXML
    private TextField sessionsSearchField;
    @FXML
    private ComboBox<String> sessionsDeviceFilterCombo;
    @FXML
    private Button sessionsListModeButton;
    @FXML
    private Button sessionsGridModeButton;
    @FXML
    private FlowPane sessionsRowsBox;
    @FXML
    private Label sessionsPaginationInfoLabel;
    @FXML
    private Button sessionsPrevPageButton;
    @FXML
    private Button sessionsNextPageButton;

    @FXML
    private TextField suspiciousSearchField;
    @FXML
    private ComboBox<String> suspiciousBlockedFilterCombo;
    @FXML
    private Button suspiciousListModeButton;
    @FXML
    private Button suspiciousGridModeButton;
    @FXML
    private FlowPane suspiciousRowsBox;
    @FXML
    private Label suspiciousPaginationInfoLabel;
    @FXML
    private Button suspiciousPrevPageButton;
    @FXML
    private Button suspiciousNextPageButton;

    @FXML
    private Label subscriptionsTotalLabel;
    @FXML
    private Label subscriptionsActiveLabel;
    @FXML
    private Label subscriptionsExpiredLabel;
    @FXML
    private Label subscriptionsCancelledLabel;
    @FXML
    private TextField subscriptionsSearchField;
    @FXML
    private ComboBox<String> subscriptionsStatusFilterCombo;
    @FXML
    private Button subscriptionsListModeButton;
    @FXML
    private Button subscriptionsGridModeButton;
    @FXML
    private FlowPane subscriptionsRowsBox;
    @FXML
    private Label subscriptionsPaginationInfoLabel;
    @FXML
    private Button subscriptionsPrevPageButton;
    @FXML
    private Button subscriptionsNextPageButton;

    @FXML
    private Label revenueTotalInvoicesLabel;
    @FXML
    private Label revenueTotalAmountLabel;
    @FXML
    private Label revenueMonthlyAmountLabel;
    @FXML
    private TextField revenuesSearchField;
    @FXML
    private ComboBox<String> revenuesPlanFilterCombo;
    @FXML
    private Button revenuesListModeButton;
    @FXML
    private Button revenuesGridModeButton;
    @FXML
    private FlowPane revenuesRowsBox;
    @FXML
    private Label revenuesPaginationInfoLabel;
    @FXML
    private Button revenuesPrevPageButton;
    @FXML
    private Button revenuesNextPageButton;

    @FXML
    private Label pharmaciesAdminTotalLabel;
    @FXML
    private Label pharmaciesAdminActiveLabel;
    @FXML
    private Label pharmaciesAdminInactiveLabel;
    @FXML
    private TextField pharmaciesAdminSearchField;
    @FXML
    private ComboBox<String> pharmaciesAdminStatusFilterCombo;
    @FXML
    private Button pharmaciesAdminListModeButton;
    @FXML
    private Button pharmaciesAdminGridModeButton;
    @FXML
    private FlowPane pharmaciesAdminRowsBox;
    @FXML
    private Label pharmaciesAdminPaginationInfoLabel;
    @FXML
    private Button pharmaciesAdminPrevPageButton;
    @FXML
    private Button pharmaciesAdminNextPageButton;

    @FXML
    private Label medicamentsAdminTotalLabel;
    @FXML
    private Label medicamentsAdminTypeCountLabel;
    @FXML
    private TextField medicamentsAdminSearchField;
    @FXML
    private ComboBox<String> medicamentsAdminTypeFilterCombo;
    @FXML
    private Button medicamentsAdminListModeButton;
    @FXML
    private Button medicamentsAdminGridModeButton;
    @FXML
    private FlowPane medicamentsAdminRowsBox;
    @FXML
    private Label medicamentsAdminPaginationInfoLabel;
    @FXML
    private Button medicamentsAdminPrevPageButton;
    @FXML
    private Button medicamentsAdminNextPageButton;

    @FXML
    private Label stocksAdminTotalLabel;
    @FXML
    private Label stocksAdminCriticalLabel;
    @FXML
    private TextField stocksAdminSearchField;
    @FXML
    private ComboBox<String> stocksAdminLevelFilterCombo;
    @FXML
    private Button stocksAdminListModeButton;
    @FXML
    private Button stocksAdminGridModeButton;
    @FXML
    private FlowPane stocksAdminRowsBox;
    @FXML
    private Label stocksAdminPaginationInfoLabel;
    @FXML
    private Button stocksAdminPrevPageButton;
    @FXML
    private Button stocksAdminNextPageButton;

    @FXML
    private Label reservationsAdminTotalLabel;
    @FXML
    private Label reservationsAdminPendingLabel;
    @FXML
    private Label reservationsAdminConfirmedLabel;
    @FXML
    private Label reservationsAdminClosedLabel;
    @FXML
    private TextField reservationsAdminSearchField;
    @FXML
    private ComboBox<String> reservationsAdminStatusFilterCombo;
    @FXML
    private Button reservationsAdminListModeButton;
    @FXML
    private Button reservationsAdminGridModeButton;
    @FXML
    private FlowPane reservationsAdminRowsBox;
    @FXML
    private Label reservationsAdminPaginationInfoLabel;
    @FXML
    private Button reservationsAdminPrevPageButton;
    @FXML
    private Button reservationsAdminNextPageButton;

    @FXML
    private Label accompanimentsTotalLabel;
    @FXML
    private Label accompanimentsActiveLabel;
    @FXML
    private Label accompanimentsCompletedLabel;
    @FXML
    private Label accompanimentsCancelledLabel;
    @FXML
    private TextField accompanimentsSearchField;
    @FXML
    private ComboBox<String> accompanimentsStatusFilterCombo;
    @FXML
    private ComboBox<String> accompanimentsAssignedFilterCombo;
    @FXML
    private Button accompanimentsListModeButton;
    @FXML
    private Button accompanimentsGridModeButton;
    @FXML
    private FlowPane accompanimentsRowsBox;
    @FXML
    private Label accompanimentsPaginationInfoLabel;
    @FXML
    private Button accompanimentsPrevPageButton;
    @FXML
    private Button accompanimentsNextPageButton;

    @FXML
    private Label communityTotalLabel;
    @FXML
    private Label communityPendingLabel;
    @FXML
    private Label communityPublishedLabel;
    @FXML
    private Label communityRejectedLabel;
    @FXML
    private TextField communitySearchField;
    @FXML
    private ComboBox<String> communityStatusFilterCombo;
    @FXML
    private ComboBox<String> communityTypeFilterCombo;
    @FXML
    private Button communityListModeButton;
    @FXML
    private Button communityGridModeButton;
    @FXML
    private FlowPane communityRowsBox;
    @FXML
    private Label communityPaginationInfoLabel;
    @FXML
    private Button communityPrevPageButton;
    @FXML
    private Button communityNextPageButton;

    @FXML
    private Label appointmentsAdminTotalLabel;
    @FXML
    private Label appointmentsAdminPendingLabel;
    @FXML
    private Label appointmentsAdminConfirmedLabel;
    @FXML
    private Label appointmentsAdminRefusedLabel;
    @FXML
    private Label appointmentsAdminCancelledLabel;
    @FXML
    private TextField appointmentsAdminSearchField;
    @FXML
    private ComboBox<String> appointmentsAdminStatusFilterCombo;
    @FXML
    private ComboBox<String> appointmentsAdminDateFilterCombo;
    @FXML
    private Button appointmentsAdminListModeButton;
    @FXML
    private Button appointmentsAdminGridModeButton;
    @FXML
    private FlowPane appointmentsAdminRowsBox;
    @FXML
    private Label appointmentsAdminPaginationInfoLabel;
    @FXML
    private Button appointmentsAdminPrevPageButton;
    @FXML
    private Button appointmentsAdminNextPageButton;

    @FXML
    private VBox latestBansBox;
    @FXML
    private VBox pendingApprovalsBox;
    @FXML
    private VBox activeSessionsBox;
    @FXML
    private VBox suspiciousLoginsBox;
    @FXML
    private VBox moderationQueueBox;
    @FXML
    private VBox userScoresBox;

    @FXML
    private void initialize() {
        if (!AuthSession.isFaceVerified()) {
            AppNavigator.showAdminFaceVerification();
            return;
        }

        setupFilterControls();
        refresh();
        showPage(pageDashboard, navDashboardButton, "Dashboard Admin", "Vue d'ensemble de la plateforme SANTEA");
    }

    private void setupFilterControls() {
        usersRoleFilterCombo.setItems(FXCollections.observableArrayList(
                "Tous les roles", "ROLE_ADMIN", "ROLE_PATIENT", "ROLE_MEDECIN", "ROLE_PHARMACIEN", "ROLE_COACH", "ROLE_NUTRITIONNISTE"
        ));
        usersRoleFilterCombo.getSelectionModel().selectFirst();

        usersStatusFilterCombo.setItems(FXCollections.observableArrayList(
                "Tous les statuts", "Actif", "Banni", "Email verifie", "Validation en attente"
        ));
        usersStatusFilterCombo.getSelectionModel().selectFirst();

        validationEmailFilterCombo.setItems(FXCollections.observableArrayList(
                "Tous", "Email verifie", "Email non verifie", "Admin approuve", "Admin en attente"
        ));
        validationEmailFilterCombo.getSelectionModel().selectFirst();

        sessionsDeviceFilterCombo.setItems(FXCollections.observableArrayList(
                "Tous devices", "Desktop", "Mobile", "Inconnu"
        ));
        sessionsDeviceFilterCombo.getSelectionModel().selectFirst();

        suspiciousBlockedFilterCombo.setItems(FXCollections.observableArrayList(
                "Toutes alertes", "Bloquees", "Non bloquees"
        ));
        suspiciousBlockedFilterCombo.getSelectionModel().selectFirst();

        subscriptionsStatusFilterCombo.setItems(FXCollections.observableArrayList(
                "Tous statuts", "actif", "annule", "expire"
        ));
        subscriptionsStatusFilterCombo.getSelectionModel().selectFirst();

        revenuesPlanFilterCombo.setItems(FXCollections.observableArrayList(
                "Tous plans"
        ));
        revenuesPlanFilterCombo.getSelectionModel().selectFirst();

        communityStatusFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous statuts", "en_attente", "publie", "valide", "rejete"
        ));
        communityStatusFilterCombo.getSelectionModel().selectFirst();

        communityTypeFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous types", "article", "video", "pdf", "lien"
        ));
        communityTypeFilterCombo.getSelectionModel().selectFirst();

        appointmentsAdminStatusFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous statuts", "en_attente", "confirme", "refuse", "annule"
        ));
        appointmentsAdminStatusFilterCombo.getSelectionModel().selectFirst();

        appointmentsAdminDateFilterCombo.setItems(FXCollections.observableArrayList(
            "Toutes dates", "Aujourd'hui", "7 prochains jours", "Passe"
        ));
        appointmentsAdminDateFilterCombo.getSelectionModel().selectFirst();

        pharmaciesAdminStatusFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous statuts", "Actives", "Inactives"
        ));
        pharmaciesAdminStatusFilterCombo.getSelectionModel().selectFirst();

        medicamentsAdminTypeFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous types"
        ));
        medicamentsAdminTypeFilterCombo.getSelectionModel().selectFirst();

        stocksAdminLevelFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous niveaux", "Disponible", "Critique (<=5)", "Rupture"
        ));
        stocksAdminLevelFilterCombo.getSelectionModel().selectFirst();

        reservationsAdminStatusFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous statuts", "en_attente", "confirmee", "refusee", "annulee", "expiree"
        ));
        reservationsAdminStatusFilterCombo.getSelectionModel().selectFirst();

        accompanimentsStatusFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous statuts", "active", "paused", "completed", "cancelled"
        ));
        accompanimentsStatusFilterCombo.getSelectionModel().selectFirst();

        accompanimentsAssignedFilterCombo.setItems(FXCollections.observableArrayList(
            "Tous suivis", "Avec coach", "Avec nutritionniste", "Complet (coach + nutritionniste)"
        ));
        accompanimentsAssignedFilterCombo.getSelectionModel().selectFirst();

        updateModeButtons(usersListModeButton, usersGridModeButton, usersGridMode);
        updateModeButtons(validationListModeButton, validationGridModeButton, validationGridMode);
        updateModeButtons(sessionsListModeButton, sessionsGridModeButton, sessionsGridMode);
        updateModeButtons(suspiciousListModeButton, suspiciousGridModeButton, suspiciousGridMode);
        updateModeButtons(subscriptionsListModeButton, subscriptionsGridModeButton, subscriptionsGridMode);
        updateModeButtons(revenuesListModeButton, revenuesGridModeButton, revenuesGridMode);
        updateModeButtons(pharmaciesAdminListModeButton, pharmaciesAdminGridModeButton, pharmaciesAdminGridMode);
        updateModeButtons(medicamentsAdminListModeButton, medicamentsAdminGridModeButton, medicamentsAdminGridMode);
        updateModeButtons(stocksAdminListModeButton, stocksAdminGridModeButton, stocksAdminGridMode);
        updateModeButtons(reservationsAdminListModeButton, reservationsAdminGridModeButton, reservationsAdminGridMode);
        updateModeButtons(accompanimentsListModeButton, accompanimentsGridModeButton, accompanimentsGridMode);
        updateModeButtons(communityListModeButton, communityGridModeButton, communityGridMode);
        updateModeButtons(appointmentsAdminListModeButton, appointmentsAdminGridModeButton, appointmentsAdminGridMode);
    }

    @FXML
    private void handleBackHome() {
        AppNavigator.showHome();
    }

    @FXML
    private void handleSanteQuotidienneAdmin() {
        try {
            AppNavigator.showSanteQuotidienneAdmin();
        } catch (Exception exception) {
            exception.printStackTrace();
            showFeedback("Erreur ouverture Santé Quotidienne admin : " + exception.getMessage(), false);
        }
    }

    @FXML
    private void handleSymptomesAdmin() {
        try {
            AppNavigator.showSymptomesListeAdmin();
        } catch (Exception exception) {
            exception.printStackTrace();
            showFeedback("Erreur ouverture Symptomes admin : " + exception.getMessage(), false);
        }
    }

    @FXML
    private void handleSymptomesPatientsDashboard() {
        try {
            AppNavigator.showSymptomesMedecinDashboardPage();
        } catch (Exception exception) {
            exception.printStackTrace();
            showFeedback("Erreur ouverture dashboard sante patients : " + exception.getMessage(), false);
        }
    }

    @FXML
    private void handleRefresh() {
        refresh();
        showFeedback("Dashboard rafraichi.", true);
    }

    @FXML
    private void handleNavDashboard() {
        showPage(pageDashboard, navDashboardButton, "Dashboard Admin", "Vue d'ensemble de la plateforme SANTEA");
    }

    @FXML
    private void handleNavUsers() {
        showPage(pageUsers, navUsersButton, "Utilisateurs", "Gestion complete des comptes, roles et securite.");
    }

    @FXML
    private void handleNavValidation() {
        showPage(pageValidation, navValidationButton, "Validation des comptes", "Filtrer, valider et suivre les confirmations.");
    }

    @FXML
    private void handleNavSessions() {
        showPage(pageSessions, navSessionsButton, "Sessions actives", "Controle des connexions et revocation des sessions.");
    }

    @FXML
    private void handleNavSuspicious() {
        showPage(pageSuspicious, navSuspiciousButton, "Connexions suspectes", "Alertes de securite et actions de blocage.");
    }

    @FXML
    private void handleNavSubscriptions() {
        showPage(pageSubscriptions, navSubscriptionsButton, "Abonnements", "Suivi des plans, renouvellements et annulations.");
    }

    @FXML
    private void handleNavRevenues() {
        showPage(pageRevenues, navRevenuesButton, "Revenus & facturation", "Factures, montants et export financier.");
    }

    @FXML
    private void handleNavPharmaciesAdmin() {
        showPage(pagePharmaciesAdmin, navPharmaciesAdminButton, "Pharmacies", "Administration des pharmacies, disponibilite et coordination.");
    }

    @FXML
    private void handleNavMedicamentsAdmin() {
        showPage(pageMedicamentsAdmin, navMedicamentsAdminButton, "Medicaments", "Catalogue, metadonnees et maintenance des references.");
    }

    @FXML
    private void handleNavStocksAdmin() {
        showPage(pageStocksAdmin, navStocksAdminButton, "Stocks", "Pilotage des stocks par pharmacie et alertes de criticite.");
    }

    @FXML
    private void handleNavReservationsAdmin() {
        showPage(pageReservationsAdmin, navReservationsAdminButton, "Reservations pharmacie", "Suivi des reservations patients et decisions pharmaciens.");
    }

    @FXML
    private void handleNavAccompaniments() {
        showPage(pageAccompaniments, navAccompanimentsButton, "Accompagnements", "Gestion des plans d'accompagnement, statuts et suivi des patients.");
    }

    @FXML
    private void handleNavCommunity() {
        showPage(pageCommunity, navCommunityButton, "Blog", "Moderation, validation et publication des contenus du blog.");
    }

    @FXML
    private void handleNavAppointmentsAdmin() {
        showPage(pageAppointmentsAdmin, navAppointmentsAdminButton, "Rendez-vous", "Pilotage admin des demandes et statuts de rendez-vous.");
    }

    @FXML
    private void handleLogout() {
        ConfirmDialogs.confirmLogout(usersTotalLabel, () -> {
            authService.logout();
            AppNavigator.showLogin();
        });
    }

    @FXML
    private void handleApproveAllPending() {
        AdminOperationsService.ActionResult result = adminOperationsService.approveAllPendingProfessionalAccounts("ROLE_MEDECIN");
        showFeedback(result.message(), result.success());
        refresh();
    }

    @FXML
    private void handleResendAllValidation() {
        int sent = 0;
        int failures = 0;
        for (AdminOperationsService.ValidationUserRow row : validationCache) {
            if (!row.emailVerified()) {
                AdminOperationsService.ActionResult result = adminOperationsService.resendValidationEmail(row.id());
                if (result.success()) {
                    sent++;
                } else {
                    failures++;
                }
            }
        }
        boolean success = failures == 0;
        String message = success
                ? "Emails de verification renvoyes: " + sent
                : "Emails renvoyes: " + sent + " | echecs: " + failures;
        showFeedback(message, success);
    }

    @FXML
    private void handleExportCsv() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choisir le dossier d'export CSV");
        File initialDir = Path.of(System.getProperty("user.home", ".")).toFile();
        if (initialDir.exists() && initialDir.isDirectory()) {
            chooser.setInitialDirectory(initialDir);
        }

        File selectedDirectory = chooser.showDialog(resolveOwnerWindow());
        if (selectedDirectory == null) {
            showFeedback("Export CSV annule.", false);
            return;
        }

        AdminOperationsService.ActionResult result = adminOperationsService.exportCsvReports(selectedDirectory.toPath());
        showFeedback(result.message(), result.success());
    }

    @FXML
    private void handleRevokeLatestSession() {
        handleNavSessions();
        showFeedback("Section sessions ouverte. Selectionnez une session a revoquer.", true);
    }

    @FXML
    private void handleReviewSuspicious() {
        handleNavSuspicious();
        showFeedback("Section connexions suspectes ouverte.", true);
    }

    @FXML
    private void handleVoiceAssistant() {
        TextInputDialog dialog = new TextInputDialog("ouvre utilisateurs");
        dialog.setTitle("Assistant Vocal Admin");
        dialog.setHeaderText("Commande admin");
        dialog.setContentText("Exemple: ouvre utilisateurs, export csv, ouvre revenus");
        Optional<String> command = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-voice-pane");

        if (command.isEmpty() || command.get().isBlank()) {
            showFeedback("Commande annulee.", false);
            return;
        }

        String normalized = normalize(command.get());
        if (normalized.contains("utilisateur")) {
            handleNavUsers();
            showFeedback("Ouverture section utilisateurs.", true);
            return;
        }
        if (normalized.contains("validation")) {
            handleNavValidation();
            showFeedback("Ouverture section validation.", true);
            return;
        }
        if (normalized.contains("session")) {
            handleNavSessions();
            showFeedback("Ouverture section sessions.", true);
            return;
        }
        if (normalized.contains("suspect") || normalized.contains("secur")) {
            handleNavSuspicious();
            showFeedback("Ouverture section securite.", true);
            return;
        }
        if (normalized.contains("abonnement")) {
            handleNavSubscriptions();
            showFeedback("Ouverture section abonnements.", true);
            return;
        }
        if (normalized.contains("revenu") || normalized.contains("factur")) {
            handleNavRevenues();
            showFeedback("Ouverture section revenus.", true);
            return;
        }
        if (normalized.contains("pharmacie") && !normalized.contains("reservation")) {
            handleNavPharmaciesAdmin();
            showFeedback("Ouverture section pharmacies.", true);
            return;
        }
        if (normalized.contains("medicament")) {
            handleNavMedicamentsAdmin();
            showFeedback("Ouverture section medicaments.", true);
            return;
        }
        if (normalized.contains("stock")) {
            handleNavStocksAdmin();
            showFeedback("Ouverture section stocks.", true);
            return;
        }
        if (normalized.contains("reservation") && normalized.contains("pharma")) {
            handleNavReservationsAdmin();
            showFeedback("Ouverture section reservations pharmacie.", true);
            return;
        }
        if (normalized.contains("accompagnement") || normalized.contains("plan accompagnement") || normalized.contains("plan patient")) {
            handleNavAccompaniments();
            showFeedback("Ouverture section accompagnements.", true);
            return;
        }
        if (normalized.contains("community") || normalized.contains("communaute") || normalized.contains("blog") || normalized.contains("contenu")) {
            handleNavCommunity();
            showFeedback("Ouverture section blog.", true);
            return;
        }
        if (normalized.contains("rendez") || normalized.contains("appointment") || normalized.contains("rdv")) {
            handleNavAppointmentsAdmin();
            showFeedback("Ouverture section rendez-vous.", true);
            return;
        }

        AdminOperationsService.AdminVoiceResult result = adminOperationsService.handleVoiceCommand(command.get());
        showFeedback(result.message(), result.success());
        refresh();
    }

    @FXML
    private void handleUsersApplyFilters() {
        usersPage = 0;
        renderUsersPage();
    }

    @FXML
    private void handleUsersPrevPage() {
        usersPage = Math.max(0, usersPage - 1);
        renderUsersPage();
    }

    @FXML
    private void handleUsersNextPage() {
        usersPage++;
        renderUsersPage();
    }

    @FXML
    private void handleUsersListMode() {
        usersGridMode = false;
        renderUsersPage();
    }

    @FXML
    private void handleUsersGridMode() {
        usersGridMode = true;
        renderUsersPage();
    }

    @FXML
    private void handleValidationApplyFilters() {
        validationPage = 0;
        renderValidationPage();
    }

    @FXML
    private void handleValidationPrevPage() {
        validationPage = Math.max(0, validationPage - 1);
        renderValidationPage();
    }

    @FXML
    private void handleValidationNextPage() {
        validationPage++;
        renderValidationPage();
    }

    @FXML
    private void handleValidationListMode() {
        validationGridMode = false;
        renderValidationPage();
    }

    @FXML
    private void handleValidationGridMode() {
        validationGridMode = true;
        renderValidationPage();
    }

    @FXML
    private void handleSessionsApplyFilters() {
        sessionsPage = 0;
        renderSessionsPage();
    }

    @FXML
    private void handleSessionsPrevPage() {
        sessionsPage = Math.max(0, sessionsPage - 1);
        renderSessionsPage();
    }

    @FXML
    private void handleSessionsNextPage() {
        sessionsPage++;
        renderSessionsPage();
    }

    @FXML
    private void handleSessionsListMode() {
        sessionsGridMode = false;
        renderSessionsPage();
    }

    @FXML
    private void handleSessionsGridMode() {
        sessionsGridMode = true;
        renderSessionsPage();
    }

    @FXML
    private void handleSuspiciousApplyFilters() {
        suspiciousPage = 0;
        renderSuspiciousPage();
    }

    @FXML
    private void handleSuspiciousPrevPage() {
        suspiciousPage = Math.max(0, suspiciousPage - 1);
        renderSuspiciousPage();
    }

    @FXML
    private void handleSuspiciousNextPage() {
        suspiciousPage++;
        renderSuspiciousPage();
    }

    @FXML
    private void handleSuspiciousListMode() {
        suspiciousGridMode = false;
        renderSuspiciousPage();
    }

    @FXML
    private void handleSuspiciousGridMode() {
        suspiciousGridMode = true;
        renderSuspiciousPage();
    }

    @FXML
    private void handleSubscriptionsApplyFilters() {
        subscriptionsPage = 0;
        renderSubscriptionsPage();
    }

    @FXML
    private void handleSubscriptionsPrevPage() {
        subscriptionsPage = Math.max(0, subscriptionsPage - 1);
        renderSubscriptionsPage();
    }

    @FXML
    private void handleSubscriptionsNextPage() {
        subscriptionsPage++;
        renderSubscriptionsPage();
    }

    @FXML
    private void handleSubscriptionsListMode() {
        subscriptionsGridMode = false;
        renderSubscriptionsPage();
    }

    @FXML
    private void handleSubscriptionsGridMode() {
        subscriptionsGridMode = true;
        renderSubscriptionsPage();
    }

    @FXML
    private void handleRevenuesApplyFilters() {
        revenuesPage = 0;
        renderRevenuesPage();
    }

    @FXML
    private void handleRevenuesPrevPage() {
        revenuesPage = Math.max(0, revenuesPage - 1);
        renderRevenuesPage();
    }

    @FXML
    private void handleRevenuesNextPage() {
        revenuesPage++;
        renderRevenuesPage();
    }

    @FXML
    private void handleRevenuesListMode() {
        revenuesGridMode = false;
        renderRevenuesPage();
    }

    @FXML
    private void handleRevenuesGridMode() {
        revenuesGridMode = true;
        renderRevenuesPage();
    }

    @FXML
    private void handlePharmaciesAdminApplyFilters() {
        pharmaciesAdminPage = 0;
        renderPharmaciesAdminPage();
    }

    @FXML
    private void handlePharmaciesAdminPrevPage() {
        pharmaciesAdminPage = Math.max(0, pharmaciesAdminPage - 1);
        renderPharmaciesAdminPage();
    }

    @FXML
    private void handlePharmaciesAdminNextPage() {
        pharmaciesAdminPage++;
        renderPharmaciesAdminPage();
    }

    @FXML
    private void handlePharmaciesAdminListMode() {
        pharmaciesAdminGridMode = false;
        renderPharmaciesAdminPage();
    }

    @FXML
    private void handlePharmaciesAdminGridMode() {
        pharmaciesAdminGridMode = true;
        renderPharmaciesAdminPage();
    }

    @FXML
    private void handlePharmaciesAdminCreate() {
        openPharmacyEditorDialog(null);
    }

    @FXML
    private void handleMedicamentsAdminApplyFilters() {
        medicamentsAdminPage = 0;
        renderMedicamentsAdminPage();
    }

    @FXML
    private void handleMedicamentsAdminPrevPage() {
        medicamentsAdminPage = Math.max(0, medicamentsAdminPage - 1);
        renderMedicamentsAdminPage();
    }

    @FXML
    private void handleMedicamentsAdminNextPage() {
        medicamentsAdminPage++;
        renderMedicamentsAdminPage();
    }

    @FXML
    private void handleMedicamentsAdminListMode() {
        medicamentsAdminGridMode = false;
        renderMedicamentsAdminPage();
    }

    @FXML
    private void handleMedicamentsAdminGridMode() {
        medicamentsAdminGridMode = true;
        renderMedicamentsAdminPage();
    }

    @FXML
    private void handleMedicamentsAdminCreate() {
        openMedicamentEditorDialog(null);
    }

    @FXML
    private void handleStocksAdminApplyFilters() {
        stocksAdminPage = 0;
        renderStocksAdminPage();
    }

    @FXML
    private void handleStocksAdminPrevPage() {
        stocksAdminPage = Math.max(0, stocksAdminPage - 1);
        renderStocksAdminPage();
    }

    @FXML
    private void handleStocksAdminNextPage() {
        stocksAdminPage++;
        renderStocksAdminPage();
    }

    @FXML
    private void handleStocksAdminListMode() {
        stocksAdminGridMode = false;
        renderStocksAdminPage();
    }

    @FXML
    private void handleStocksAdminGridMode() {
        stocksAdminGridMode = true;
        renderStocksAdminPage();
    }

    @FXML
    private void handleStocksAdminCreate() {
        openCreateStockDialog();
    }

    @FXML
    private void handleReservationsAdminApplyFilters() {
        reservationsAdminPage = 0;
        renderReservationsAdminPage();
    }

    @FXML
    private void handleReservationsAdminPrevPage() {
        reservationsAdminPage = Math.max(0, reservationsAdminPage - 1);
        renderReservationsAdminPage();
    }

    @FXML
    private void handleReservationsAdminNextPage() {
        reservationsAdminPage++;
        renderReservationsAdminPage();
    }

    @FXML
    private void handleReservationsAdminListMode() {
        reservationsAdminGridMode = false;
        renderReservationsAdminPage();
    }

    @FXML
    private void handleReservationsAdminGridMode() {
        reservationsAdminGridMode = true;
        renderReservationsAdminPage();
    }

    @FXML
    private void handleAccompanimentsApplyFilters() {
        accompanimentsPage = 0;
        renderAccompanimentsPage();
    }

    @FXML
    private void handleAccompanimentsPrevPage() {
        accompanimentsPage = Math.max(0, accompanimentsPage - 1);
        renderAccompanimentsPage();
    }

    @FXML
    private void handleAccompanimentsNextPage() {
        accompanimentsPage++;
        renderAccompanimentsPage();
    }

    @FXML
    private void handleAccompanimentsListMode() {
        accompanimentsGridMode = false;
        renderAccompanimentsPage();
    }

    @FXML
    private void handleAccompanimentsGridMode() {
        accompanimentsGridMode = true;
        renderAccompanimentsPage();
    }

    @FXML
    private void handleCommunityApplyFilters() {
        communityPage = 0;
        renderCommunityPage();
    }

    @FXML
    private void handleCommunityPrevPage() {
        communityPage = Math.max(0, communityPage - 1);
        renderCommunityPage();
    }

    @FXML
    private void handleCommunityNextPage() {
        communityPage++;
        renderCommunityPage();
    }

    @FXML
    private void handleCommunityListMode() {
        communityGridMode = false;
        renderCommunityPage();
    }

    @FXML
    private void handleCommunityGridMode() {
        communityGridMode = true;
        renderCommunityPage();
    }

    @FXML
    private void handleAppointmentsAdminApplyFilters() {
        appointmentsAdminPage = 0;
        renderAppointmentsAdminPage();
    }

    @FXML
    private void handleAppointmentsAdminPrevPage() {
        appointmentsAdminPage = Math.max(0, appointmentsAdminPage - 1);
        renderAppointmentsAdminPage();
    }

    @FXML
    private void handleAppointmentsAdminNextPage() {
        appointmentsAdminPage++;
        renderAppointmentsAdminPage();
    }

    @FXML
    private void handleAppointmentsAdminListMode() {
        appointmentsAdminGridMode = false;
        renderAppointmentsAdminPage();
    }

    @FXML
    private void handleAppointmentsAdminGridMode() {
        appointmentsAdminGridMode = true;
        renderAppointmentsAdminPage();
    }

    private void showPage(VBox page, Button navButton, String title, String subtitle) {
        List<VBox> pages = List.of(
                pageDashboard,
                pageUsers,
                pageValidation,
                pageSessions,
                pageSuspicious,
                pageSubscriptions,
                pageRevenues,
            pagePharmaciesAdmin,
            pageMedicamentsAdmin,
            pageStocksAdmin,
            pageReservationsAdmin,
                pageAccompaniments,
                pageCommunity,
                pageAppointmentsAdmin
        );
        for (VBox current : pages) {
            boolean active = current == page;
            current.setVisible(active);
            current.setManaged(active);
        }

        List<Button> navButtons = List.of(
                navDashboardButton,
                navUsersButton,
                navValidationButton,
                navSessionsButton,
                navSuspiciousButton,
                navSubscriptionsButton,
                navRevenuesButton,
            navPharmaciesAdminButton,
            navMedicamentsAdminButton,
            navStocksAdminButton,
            navReservationsAdminButton,
                navAccompanimentsButton,
                navCommunityButton,
                navAppointmentsAdminButton
        );
        for (Button current : navButtons) {
            current.getStyleClass().remove("bo-nav-active");
        }
        navButton.getStyleClass().add("bo-nav-active");

        headerTitleLabel.setText(title);
        headerSubtitleLabel.setText(subtitle);
    }

    private void refresh() {
        AdminDashboardService.AdminDashboardData data = adminDashboardService.loadData();
        AdminOperationsService.SubscriptionStats subscriptionStats = adminOperationsService.loadSubscriptionStats();
        AdminOperationsService.RevenueStats revenueStats = adminOperationsService.loadRevenueStats();
        AdminOperationsService.CommunityStats communityStats = adminOperationsService.loadCommunityStats();
        AdminOperationsService.AppointmentAdminStats appointmentStats = adminOperationsService.loadAppointmentAdminStats();
        AdminOperationsService.AccompanimentAdminStats accompanimentStats = adminOperationsService.loadAccompanimentAdminStats();

        usersTotalLabel.setText(String.valueOf(data.totalUsers()));
        usersBannedLabel.setText(String.valueOf(data.bannedUsers()));
        usersActiveLabel.setText(String.valueOf(Math.max(0, data.totalUsers() - data.bannedUsers())));

        activeSubscriptionsLabel.setText(String.valueOf(data.activeSubscriptions()));
        subscriptionsExpiredMiniLabel.setText(String.valueOf(subscriptionStats.expired()));
        subscriptionsCancelledMiniLabel.setText(String.valueOf(subscriptionStats.cancelled()));

        monthlyRevenueLabel.setText(data.monthlyRevenueDisplay());
        totalRevenueMiniLabel.setText(money(revenueStats.totalRevenue()) + " TND");
        pendingPaymentsLabel.setText(String.valueOf(data.pendingPayments()));

        usersVerifiedLabel.setText(String.valueOf(data.verifiedUsers()));
        usersPendingLabel.setText(String.valueOf(data.pendingUsers()));
        suspiciousBlockedLabel.setText(String.valueOf(data.blockedSuspicious()));
        paymentsSucceededLabel.setText(String.valueOf(data.succeededPayments()));
        activeSessionsLabel.setText(String.valueOf(data.activeSessions()));
        moderationPendingLabel.setText(String.valueOf(data.pendingModeration()));
        scoreAverageLabel.setText(String.valueOf(adminOperationsService.averageUserScore()));

        subscriptionsTotalLabel.setText("Total: " + subscriptionStats.total());
        subscriptionsActiveLabel.setText("Actifs: " + subscriptionStats.active());
        subscriptionsExpiredLabel.setText("Expires: " + subscriptionStats.expired());
        subscriptionsCancelledLabel.setText("Annules: " + subscriptionStats.cancelled());

        revenueTotalInvoicesLabel.setText("Factures: " + revenueStats.totalInvoices());
        revenueTotalAmountLabel.setText("Revenus: " + money(revenueStats.totalRevenue()) + " TND");
        revenueMonthlyAmountLabel.setText("Ce mois: " + money(revenueStats.monthlyRevenue()) + " TND");

        communityTotalLabel.setText("Total: " + communityStats.total());
        communityPendingLabel.setText("En attente: " + communityStats.pending());
        communityPublishedLabel.setText("Publies/Valides: " + communityStats.published());
        communityRejectedLabel.setText("Rejetes: " + communityStats.rejected());

        appointmentsAdminTotalLabel.setText("Total: " + appointmentStats.total());
        appointmentsAdminPendingLabel.setText("En attente: " + appointmentStats.pending());
        appointmentsAdminConfirmedLabel.setText("Confirmes: " + appointmentStats.confirmed());
        appointmentsAdminRefusedLabel.setText("Refuses: " + appointmentStats.refused());
        appointmentsAdminCancelledLabel.setText("Annules: " + appointmentStats.cancelled());

        accompanimentsTotalLabel.setText("Total: " + accompanimentStats.total());
        accompanimentsActiveLabel.setText("Actifs: " + accompanimentStats.active());
        accompanimentsCompletedLabel.setText("Termines: " + accompanimentStats.completed());
        accompanimentsCancelledLabel.setText("Annules: " + accompanimentStats.cancelled());

        User currentUser = AuthSession.getCurrentUser();
        if (currentUser != null && currentUser.getId() != null) {
            PharmacyService.ModuleData moduleData = pharmacyService.loadModuleData(currentUser, "", "");
            pharmaciesAdminCache = moduleData.managedPharmacies();
            medicamentsAdminCache = moduleData.medicaments();
            stocksAdminCache = moduleData.managedStocks();
            reservationsAdminCache = moduleData.incomingReservations();
        } else {
            pharmaciesAdminCache = List.of();
            medicamentsAdminCache = List.of();
            stocksAdminCache = List.of();
            reservationsAdminCache = List.of();
        }

        long activePharmacies = pharmaciesAdminCache.stream().filter(PharmacyService.PharmacyRow::active).count();
        long criticalStocks = stocksAdminCache.stream().filter(stock -> stock.quantite() <= 5).count();
        long reservationsPending = reservationsAdminCache.stream().filter(r -> "en_attente".equalsIgnoreCase(safe(r.statut()))).count();
        long reservationsConfirmed = reservationsAdminCache.stream().filter(r -> "confirmee".equalsIgnoreCase(safe(r.statut()))).count();
        long reservationsClosed = reservationsAdminCache.size() - reservationsPending - reservationsConfirmed;

        pharmaciesAdminTotalLabel.setText("Total: " + pharmaciesAdminCache.size());
        pharmaciesAdminActiveLabel.setText("Actives: " + activePharmacies);
        pharmaciesAdminInactiveLabel.setText("Inactives: " + Math.max(0, pharmaciesAdminCache.size() - activePharmacies));

        medicamentsAdminTotalLabel.setText("Total: " + medicamentsAdminCache.size());
        medicamentsAdminTypeCountLabel.setText("Types: " + medicamentsAdminCache.stream()
                .map(row -> safe(row.type()).toLowerCase(Locale.ROOT))
                .filter(type -> !type.isBlank())
                .distinct()
                .count());

        stocksAdminTotalLabel.setText("Total: " + stocksAdminCache.size());
        stocksAdminCriticalLabel.setText("Critiques: " + criticalStocks);

        reservationsAdminTotalLabel.setText("Total: " + reservationsAdminCache.size());
        reservationsAdminPendingLabel.setText("En attente: " + reservationsPending);
        reservationsAdminConfirmedLabel.setText("Confirmees: " + reservationsConfirmed);
        reservationsAdminClosedLabel.setText("Cloturees: " + Math.max(0, reservationsClosed));

        usersCache = adminOperationsService.listUsersForAdmin(300);
        validationCache = adminOperationsService.listValidationUsers(300);
        sessionsCache = adminOperationsService.listActiveSessions();
        suspiciousCache = adminOperationsService.listSuspiciousLogins();
        subscriptionsCache = adminOperationsService.listSubscriptionsForAdmin(300);
        revenuesCache = adminOperationsService.listRevenuesForAdmin(300);
        communityCache = adminOperationsService.listCommunityForAdmin(300);
        appointmentsAdminCache = adminOperationsService.listAppointmentsForAdmin(300);
        accompanimentsCache = adminOperationsService.listAccompanimentsForAdmin(300);
        pendingApprovalsCache = adminOperationsService.listPendingProfessionalApprovals();
        moderationCache = adminOperationsService.listModerationQueue();
        scoresCache = adminOperationsService.listTopUserScores(50);

        refreshDynamicFilterData();

        renderRegistrationChart();
        renderRevenuePieChart();

        renderDashboardLists(data.latestBans());
        renderUsersPage();
        renderValidationPage();
        renderSessionsPage();
        renderSuspiciousPage();
        renderSubscriptionsPage();
        renderRevenuesPage();
        renderPharmaciesAdminPage();
        renderMedicamentsAdminPage();
        renderStocksAdminPage();
        renderReservationsAdminPage();
        renderAccompanimentsPage();
        renderCommunityPage();
        renderAppointmentsAdminPage();

        renderQuickPanels();
    }

    private void refreshDynamicFilterData() {
        String selectedPlan = revenuesPlanFilterCombo.getValue();
        List<String> plans = revenuesCache.stream()
                .map(r -> safe(r.planType()))
                .filter(s -> !s.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        plans.add(0, "Tous plans");
        revenuesPlanFilterCombo.setItems(FXCollections.observableArrayList(plans));
        if (selectedPlan != null && plans.contains(selectedPlan)) {
            revenuesPlanFilterCombo.setValue(selectedPlan);
        } else {
            revenuesPlanFilterCombo.getSelectionModel().selectFirst();
        }

        String selectedType = medicamentsAdminTypeFilterCombo.getValue();
        List<String> types = medicamentsAdminCache.stream()
                .map(row -> safe(row.type()))
                .filter(type -> !type.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        types.add(0, "Tous types");
        medicamentsAdminTypeFilterCombo.setItems(FXCollections.observableArrayList(types));
        if (selectedType != null && types.contains(selectedType)) {
            medicamentsAdminTypeFilterCombo.setValue(selectedType);
        } else {
            medicamentsAdminTypeFilterCombo.getSelectionModel().selectFirst();
        }
    }

    private void renderRegistrationChart() {
        if (registrationsLineChart == null) {
            return;
        }

        Map<LocalDate, Integer> daily = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        for (int i = 29; i >= 0; i--) {
            daily.put(now.minusDays(i), 0);
        }

        for (AdminOperationsService.AdminUserRow row : usersCache) {
            if (row.createdAt() == null) {
                continue;
            }
            LocalDate day = row.createdAt().toLocalDate();
            if (daily.containsKey(day)) {
                daily.put(day, daily.get(day) + 1);
            }
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Inscriptions");
        for (Map.Entry<LocalDate, Integer> entry : daily.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey().format(DAY_LABEL_FORMATTER), entry.getValue()));
        }

        registrationsLineChart.getData().clear();
        registrationsLineChart.getData().add(series);
        registrationsLineChart.setCreateSymbols(false);
        registrationsLineChart.setAnimated(false);
    }

    private void renderRevenuePieChart() {
        if (revenuePieChart == null) {
            return;
        }

        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        totals.put("Medecin", BigDecimal.ZERO);
        totals.put("Pharmacien", BigDecimal.ZERO);
        totals.put("Coach", BigDecimal.ZERO);
        totals.put("Nutritionniste", BigDecimal.ZERO);
        totals.put("Patient", BigDecimal.ZERO);

        for (AdminOperationsService.RevenueRow row : revenuesCache) {
            String plan = normalizePlanLabel(row.planType());
            totals.put(plan, totals.getOrDefault(plan, BigDecimal.ZERO).add(row.totalTtc()));
        }

        List<PieChart.Data> pieData = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : totals.entrySet()) {
            pieData.add(new PieChart.Data(entry.getKey(), entry.getValue().doubleValue()));
        }

        revenuePieChart.setData(FXCollections.observableArrayList(pieData));
        revenuePieChart.setLegendVisible(true);
        revenuePieChart.setClockwise(true);
        revenuePieChart.setLabelsVisible(false);
        revenuePieChart.setAnimated(false);
    }

    private void renderDashboardLists(List<String> latestBans) {
        renderLatestBans(limitRows(latestBans, 5));
        renderPendingApprovals(limitRows(pendingApprovalsCache, 5));
        renderSessions(limitRows(sessionsCache, 5));
        renderSuspiciousLogins(limitRows(suspiciousCache, 5));
        renderModeration(limitRows(moderationCache, 5));
        renderUserScores(limitRows(scoresCache, 8));
    }

    private void renderQuickPanels() {
        AdminOperationsService.ValidationStats validationStats = adminOperationsService.loadValidationStats();
        validationTotalLabel.setText("Total: " + validationStats.totalUsers());
        validationEmailConfirmedLabel.setText("Email verifies: " + validationStats.emailConfirmed());
        validationEmailNotConfirmedLabel.setText("Email non verifies: " + validationStats.emailNotConfirmed());
        validationAdminPendingLabel.setText("Admin en attente: " + validationStats.adminPending());
    }

    private void renderUsersPage() {
        String keyword = normalize(usersSearchField.getText());
        String roleFilter = safe(usersRoleFilterCombo.getValue());
        String statusFilter = safe(usersStatusFilterCombo.getValue());

        List<AdminOperationsService.AdminUserRow> filtered = usersCache.stream()
                .filter(user -> keyword.isBlank() || normalize(user.displayName() + " " + user.email()).contains(keyword))
                .filter(user -> "Tous les roles".equals(roleFilter) || roleFilter.equalsIgnoreCase(safe(user.role())))
                .filter(user -> matchUserStatus(user, statusFilter))
                .sorted(Comparator.comparing(AdminOperationsService.AdminUserRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        usersTotalCountLabel.setText("Total: " + filtered.size() + " / " + usersCache.size());
        usersScoreAverageLabel.setText("Score moyen: " + averageScore(filtered) + "/100");

        PageSlice<AdminOperationsService.AdminUserRow> page = page(filtered, usersPage);
        usersPage = page.pageIndex();

        applyContainerMode(usersRowsBox, usersGridMode);
        usersRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            usersRowsBox.getChildren().add(createEmptyCard("Aucun utilisateur trouve."));
        } else {
            for (AdminOperationsService.AdminUserRow user : page.items()) {
                String status = user.banned() ? "BANNI" : (user.subscriptionStatus().isBlank() ? "ACTIF" : user.subscriptionStatus());
                String details = "Role: " + user.role()
                        + " | Score IA: " + user.score() + "/100"
                        + " | Priorite support: " + user.supportPriority()
                        + " | Statut: " + status
                        + " | Cree le: " + formatDateTime(user.createdAt());

                Button editButton = buildRowButton("Editer", "admin-btn-xs-secondary", () -> openEditUserDialog(user));
                Button resetButton = buildRowButton("Reset MDP", "admin-btn-xs-secondary", () -> triggerAdminPasswordReset(user));
                Button deleteButton = buildRowButton("Supprimer", "admin-btn-xs-danger", () -> confirmDeleteUser(user));

                Button banButton = user.banned()
                        ? buildRowButton("Debannir", "admin-btn-xs-warning", () -> {
                            AdminOperationsService.ActionResult result = adminOperationsService.unbanUser(user.id());
                            showFeedback(result.message(), result.success());
                            refresh();
                        })
                        : buildRowButton("Bannir", "admin-btn-xs-warning", () -> {
                            AdminOperationsService.ActionResult result = adminOperationsService.banUser(user.id(), "Bannissement admin", null);
                            showFeedback(result.message(), result.success());
                            refresh();
                        });

                Node row = usersGridMode
                        ? createGridCard(user.displayName() + " - " + user.email(), details, status, editButton, resetButton, deleteButton, banButton)
                        : createListRow(user.displayName() + " - " + user.email(), details, editButton, resetButton, deleteButton, banButton);

                usersRowsBox.getChildren().add(row);
            }
        }

        updateModeButtons(usersListModeButton, usersGridModeButton, usersGridMode);
        updatePagination(page, usersPaginationInfoLabel, usersPrevPageButton, usersNextPageButton);
    }

    private void renderValidationPage() {
        String keyword = normalize(validationSearchField.getText());
        String validationFilter = safe(validationEmailFilterCombo.getValue());

        List<AdminOperationsService.ValidationUserRow> filtered = validationCache.stream()
                .filter(row -> keyword.isBlank() || normalize(row.displayName() + " " + row.email()).contains(keyword))
                .filter(row -> matchValidationFilter(row, validationFilter))
                .sorted(Comparator.comparing(AdminOperationsService.ValidationUserRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<AdminOperationsService.ValidationUserRow> page = page(filtered, validationPage);
        validationPage = page.pageIndex();

        applyContainerMode(validationRowsBox, validationGridMode);
        validationRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            validationRowsBox.getChildren().add(createEmptyCard("Aucun compte a valider."));
        } else {
            for (AdminOperationsService.ValidationUserRow row : page.items()) {
                String statusBadge = row.adminApproved() ? "APPROUVE" : "EN ATTENTE";
                String details = "Email confirme: " + (row.emailVerified() ? "OUI" : "NON")
                        + " | Admin valide: " + (row.adminApproved() ? "OUI" : "NON")
                        + " | Cree le: " + formatDateTime(row.createdAt());

                List<Button> actions = new ArrayList<>();
                if (row.emailVerified() && !row.adminApproved()) {
                    actions.add(buildRowButton("Approuver", "admin-btn-xs-primary", () -> {
                        AdminOperationsService.ActionResult result = adminOperationsService.approveValidationUser(row.id());
                        showFeedback(result.message(), result.success());
                        refresh();
                    }));
                }

                actions.add(buildRowButton("Renvoyer email", "admin-btn-xs-secondary", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.resendValidationEmail(row.id());
                    showFeedback(result.message(), result.success());
                }));

                Node rowNode = validationGridMode
                        ? createGridCard(row.displayName() + " - " + row.email(), details, statusBadge, actions.toArray(new Button[0]))
                        : createListRow(row.displayName() + " - " + row.email(), details, actions.toArray(new Button[0]));
                validationRowsBox.getChildren().add(rowNode);
            }
        }

        updateModeButtons(validationListModeButton, validationGridModeButton, validationGridMode);
        updatePagination(page, validationPaginationInfoLabel, validationPrevPageButton, validationNextPageButton);
    }

    private void renderSessionsPage() {
        String keyword = normalize(sessionsSearchField.getText());
        String deviceFilter = safe(sessionsDeviceFilterCombo.getValue());

        List<AdminOperationsService.ActiveSessionRow> filtered = sessionsCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        "#" + row.userId() + " " + safe(row.sessionToken()) + " " + safe(row.ipAddress()) + " " + safe(row.deviceLabel())
                ).contains(keyword))
                .filter(row -> matchDeviceFilter(row, deviceFilter))
                .sorted(Comparator.comparing(AdminOperationsService.ActiveSessionRow::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<AdminOperationsService.ActiveSessionRow> page = page(filtered, sessionsPage);
        sessionsPage = page.pageIndex();

        applyContainerMode(sessionsRowsBox, sessionsGridMode);
        sessionsRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            sessionsRowsBox.getChildren().add(createEmptyCard("Aucune session active."));
        } else {
            for (AdminOperationsService.ActiveSessionRow session : page.items()) {
                String title = "Utilisateur #" + session.userId() + " - " + safe(session.deviceLabel());
                String details = "IP: " + safe(session.ipAddress())
                        + " | Derniere activite: " + formatDateTime(session.lastSeenAt())
                        + " | Token: " + shortToken(session.sessionToken());

                Button revokeButton = buildRowButton("Deconnecter", "admin-btn-xs-danger", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.revokeSession(session.id());
                    showFeedback(result.message(), result.success());
                    refresh();
                });

                Node node = sessionsGridMode
                        ? createGridCard(title, details, "ACTIVE", revokeButton)
                        : createListRow(title, details, revokeButton);
                sessionsRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(sessionsListModeButton, sessionsGridModeButton, sessionsGridMode);
        updatePagination(page, sessionsPaginationInfoLabel, sessionsPrevPageButton, sessionsNextPageButton);
    }

    private void renderSuspiciousPage() {
        String keyword = normalize(suspiciousSearchField.getText());
        String blockedFilter = safe(suspiciousBlockedFilterCombo.getValue());

        List<AdminOperationsService.SuspiciousLoginRow> filtered = suspiciousCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        safe(row.email()) + " " + safe(row.ipAddress()) + " " + safe(row.reason())
                ).contains(keyword))
                .filter(row -> matchSuspiciousFilter(row, blockedFilter))
                .sorted(Comparator.comparing(AdminOperationsService.SuspiciousLoginRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<AdminOperationsService.SuspiciousLoginRow> page = page(filtered, suspiciousPage);
        suspiciousPage = page.pageIndex();

        applyContainerMode(suspiciousRowsBox, suspiciousGridMode);
        suspiciousRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            suspiciousRowsBox.getChildren().add(createEmptyCard("Aucune alerte de securite."));
        } else {
            for (AdminOperationsService.SuspiciousLoginRow row : page.items()) {
                String title = safe(row.email()).isBlank() ? "Utilisateur inconnu" : row.email();
                String badge = row.blocked() ? "BLOQUE" : "A SURVEILLER";
                String details = "Raison: " + safe(row.reason())
                        + " | IP: " + safe(row.ipAddress())
                        + " | Date: " + formatDateTime(row.createdAt());

                Button toggleButton = buildRowButton(
                        row.blocked() ? "Debloquer" : "Bloquer",
                        row.blocked() ? "admin-btn-xs-secondary" : "admin-btn-xs-warning",
                        () -> {
                            AdminOperationsService.ActionResult result = adminOperationsService.markSuspiciousLoginBlocked(row.id(), !row.blocked());
                            showFeedback(result.message(), result.success());
                            refresh();
                        }
                );

                Node node = suspiciousGridMode
                        ? createGridCard(title, details, badge, toggleButton)
                        : createListRow(title, details, toggleButton);
                suspiciousRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(suspiciousListModeButton, suspiciousGridModeButton, suspiciousGridMode);
        updatePagination(page, suspiciousPaginationInfoLabel, suspiciousPrevPageButton, suspiciousNextPageButton);
    }

    private void renderSubscriptionsPage() {
        String keyword = normalize(subscriptionsSearchField.getText());
        String statusFilter = normalize(subscriptionsStatusFilterCombo.getValue());

        List<AdminOperationsService.SubscriptionRow> filtered = subscriptionsCache.stream()
                .filter(row -> keyword.isBlank() || normalize(row.displayName() + " " + row.email() + " " + row.type()).contains(keyword))
                .filter(row -> statusFilter.isBlank() || "tous statuts".equals(statusFilter) || normalize(row.status()).contains(statusFilter))
                .sorted(Comparator.comparing(AdminOperationsService.SubscriptionRow::dateFin, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<AdminOperationsService.SubscriptionRow> page = page(filtered, subscriptionsPage);
        subscriptionsPage = page.pageIndex();

        applyContainerMode(subscriptionsRowsBox, subscriptionsGridMode);
        subscriptionsRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            subscriptionsRowsBox.getChildren().add(createEmptyCard("Aucun abonnement trouve."));
        } else {
            for (AdminOperationsService.SubscriptionRow row : page.items()) {
                String details = "Type: " + row.type()
                        + " | Statut: " + row.status()
                        + " | Debut: " + formatDate(row.dateDebut())
                        + " | Fin: " + formatDate(row.dateFin())
                        + " | Prix: " + money(row.price()) + " TND";

                Button renewButton = buildRowButton("Renouveler +30j", "admin-btn-xs-primary", () -> {
                    LocalDate base = row.dateFin() == null ? LocalDate.now() : row.dateFin();
                    LocalDate newEnd = base.isBefore(LocalDate.now()) ? LocalDate.now().plusDays(30) : base.plusDays(30);
                    AdminOperationsService.ActionResult result = adminOperationsService.updateSubscription(row.id(), row.type(), "actif", newEnd);
                    showFeedback(result.message(), result.success());
                    refresh();
                });

                Button cancelButton = buildRowButton("Annuler", "admin-btn-xs-danger", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.cancelSubscription(row.id());
                    showFeedback(result.message(), result.success());
                    refresh();
                });

                Node node = subscriptionsGridMode
                        ? createGridCard(row.displayName() + " - " + row.email(), details, safe(row.status()).toUpperCase(Locale.ROOT), renewButton, cancelButton)
                        : createListRow(row.displayName() + " - " + row.email(), details, renewButton, cancelButton);
                subscriptionsRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(subscriptionsListModeButton, subscriptionsGridModeButton, subscriptionsGridMode);
        updatePagination(page, subscriptionsPaginationInfoLabel, subscriptionsPrevPageButton, subscriptionsNextPageButton);
    }

    private void renderRevenuesPage() {
        String keyword = normalize(revenuesSearchField.getText());
        String planFilter = safe(revenuesPlanFilterCombo.getValue());

        List<AdminOperationsService.RevenueRow> filtered = revenuesCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        row.invoiceNumber() + " " + row.displayName() + " " + row.planType() + " " + row.currency()
                ).contains(keyword))
                .filter(row -> "Tous plans".equals(planFilter) || safe(row.planType()).equalsIgnoreCase(planFilter))
                .sorted(Comparator.comparing(AdminOperationsService.RevenueRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<AdminOperationsService.RevenueRow> page = page(filtered, revenuesPage);
        revenuesPage = page.pageIndex();

        applyContainerMode(revenuesRowsBox, revenuesGridMode);
        revenuesRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            revenuesRowsBox.getChildren().add(createEmptyCard("Aucune facture disponible."));
        } else {
            for (AdminOperationsService.RevenueRow row : page.items()) {
                String details = "Plan: " + safe(row.planType())
                        + " | Total TTC: " + money(row.totalTtc()) + " " + safe(row.currency())
                        + " | Date: " + formatDateTime(row.createdAt());

                Button downloadButton = buildRowButton("Telecharger PDF", "admin-btn-xs-secondary", () -> {
                    String pdfPath = row.pdfPath();
                    if (pdfPath == null || pdfPath.isBlank()) {
                        showFeedback("PDF indisponible pour la facture " + row.invoiceNumber(), false);
                        return;
                    }

                    Path path = Path.of(pdfPath);
                    if (!Files.exists(path)) {
                        showFeedback("Fichier PDF introuvable: " + path.toAbsolutePath(), false);
                        return;
                    }

                    String fileName = ("facture_" + safe(row.invoiceNumber()) + ".pdf").replaceAll("[^A-Za-z0-9._-]", "_");
                    FileChooser chooser = new FileChooser();
                    chooser.setTitle("Enregistrer la facture PDF");
                    chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier PDF", "*.pdf"));
                    chooser.setInitialFileName(fileName);

                    File destination = chooser.showSaveDialog(resolveOwnerWindow());
                    if (destination == null) {
                        showFeedback("Telechargement PDF annule.", false);
                        return;
                    }

                    try {
                        Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        showFeedback("PDF enregistre: " + destination.toPath().toAbsolutePath(), true);
                    } catch (Exception copyException) {
                        showFeedback("Impossible d'enregistrer le PDF: " + copyException.getMessage(), false);
                    }
                });

                Node node = revenuesGridMode
                        ? createGridCard("Facture " + row.invoiceNumber() + " - " + row.displayName(), details, money(row.totalTtc()) + " " + safe(row.currency()), downloadButton)
                        : createListRow("Facture " + row.invoiceNumber() + " - " + row.displayName(), details, downloadButton);
                revenuesRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(revenuesListModeButton, revenuesGridModeButton, revenuesGridMode);
        updatePagination(page, revenuesPaginationInfoLabel, revenuesPrevPageButton, revenuesNextPageButton);
    }

    private void renderPharmaciesAdminPage() {
        String keyword = normalize(pharmaciesAdminSearchField.getText());
        String statusFilter = safe(pharmaciesAdminStatusFilterCombo.getValue());

        List<PharmacyService.PharmacyRow> filtered = pharmaciesAdminCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        row.nom() + " " + row.adresse() + " " + row.telephone() + " " + row.email()
                ).contains(keyword))
                .filter(row -> matchPharmacyStatus(row.active(), statusFilter))
                .sorted(Comparator
                        .comparing(PharmacyService.PharmacyRow::active)
                        .reversed()
                        .thenComparing(PharmacyService.PharmacyRow::nom, String.CASE_INSENSITIVE_ORDER))
                .toList();

        PageSlice<PharmacyService.PharmacyRow> page = page(filtered, pharmaciesAdminPage);
        pharmaciesAdminPage = page.pageIndex();

        applyContainerMode(pharmaciesAdminRowsBox, pharmaciesAdminGridMode);
        pharmaciesAdminRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            pharmaciesAdminRowsBox.getChildren().add(createEmptyCard("Aucune pharmacie trouvee."));
        } else {
            for (PharmacyService.PharmacyRow row : page.items()) {
                String details = "Adresse: " + safe(row.adresse())
                        + " | Tel: " + (safe(row.telephone()).isBlank() ? "-" : safe(row.telephone()))
                        + " | Email: " + (safe(row.email()).isBlank() ? "-" : safe(row.email()))
                        + " | Horaires: " + (safe(row.horaires()).isBlank() ? "-" : safe(row.horaires()));
                String badge = row.active() ? "ACTIVE" : "INACTIVE";

                Button editButton = buildRowButton("Editer", "admin-btn-xs-secondary", () -> openPharmacyEditorDialog(row));
                Button toggleButton = buildRowButton(
                        row.active() ? "Desactiver" : "Activer",
                        row.active() ? "admin-btn-xs-warning" : "admin-btn-xs-primary",
                        () -> togglePharmacyActive(row)
                );
                Button deleteButton = buildRowButton("Supprimer", "admin-btn-xs-danger", () -> confirmDeletePharmacy(row));

                String title = "#" + row.id() + " - " + safe(row.nom());
                Node node = pharmaciesAdminGridMode
                        ? createGridCard(title, details, badge, editButton, toggleButton, deleteButton)
                        : createListRow(title, details, editButton, toggleButton, deleteButton);
                pharmaciesAdminRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(pharmaciesAdminListModeButton, pharmaciesAdminGridModeButton, pharmaciesAdminGridMode);
        updatePagination(page, pharmaciesAdminPaginationInfoLabel, pharmaciesAdminPrevPageButton, pharmaciesAdminNextPageButton);
    }

    private void renderMedicamentsAdminPage() {
        String keyword = normalize(medicamentsAdminSearchField.getText());
        String typeFilter = safe(medicamentsAdminTypeFilterCombo.getValue());

        List<PharmacyService.MedicamentRow> filtered = medicamentsAdminCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        row.nom() + " " + row.type() + " " + row.codeBarre() + " " + row.laboratoire() + " " + row.description()
                ).contains(keyword))
                .filter(row -> "Tous types".equals(typeFilter) || safe(row.type()).equalsIgnoreCase(typeFilter))
                .sorted(Comparator.comparing(PharmacyService.MedicamentRow::nom, String.CASE_INSENSITIVE_ORDER))
                .toList();

        PageSlice<PharmacyService.MedicamentRow> page = page(filtered, medicamentsAdminPage);
        medicamentsAdminPage = page.pageIndex();

        applyContainerMode(medicamentsAdminRowsBox, medicamentsAdminGridMode);
        medicamentsAdminRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            medicamentsAdminRowsBox.getChildren().add(createEmptyCard("Aucun medicament trouve."));
        } else {
            for (PharmacyService.MedicamentRow row : page.items()) {
                String details = "Type: " + (safe(row.type()).isBlank() ? "-" : safe(row.type()))
                        + " | Forme: " + (safe(row.forme()).isBlank() ? "-" : safe(row.forme()))
                        + " | Dosage: " + (safe(row.dosage()).isBlank() ? "-" : safe(row.dosage()))
                        + " | Prix: " + (safe(row.prix()).isBlank() ? "-" : safe(row.prix()))
                        + " | Labo: " + (safe(row.laboratoire()).isBlank() ? "-" : safe(row.laboratoire()))
                        + " | Code barre: " + (safe(row.codeBarre()).isBlank() ? "-" : safe(row.codeBarre()));

                Button editButton = buildRowButton("Editer", "admin-btn-xs-secondary", () -> openMedicamentEditorDialog(row));
                Button deleteButton = buildRowButton("Supprimer", "admin-btn-xs-danger", () -> confirmDeleteMedicament(row));

                String badge = safe(row.type()).isBlank() ? "MEDICAMENT" : safe(row.type()).toUpperCase(Locale.ROOT);
                String title = "#" + row.id() + " - " + safe(row.nom());
                Node node = medicamentsAdminGridMode
                        ? createGridCard(title, details, badge, editButton, deleteButton)
                        : createListRow(title, details, editButton, deleteButton);
                medicamentsAdminRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(medicamentsAdminListModeButton, medicamentsAdminGridModeButton, medicamentsAdminGridMode);
        updatePagination(page, medicamentsAdminPaginationInfoLabel, medicamentsAdminPrevPageButton, medicamentsAdminNextPageButton);
    }

    private void renderStocksAdminPage() {
        String keyword = normalize(stocksAdminSearchField.getText());
        String levelFilter = safe(stocksAdminLevelFilterCombo.getValue());

        List<PharmacyService.StockRow> filtered = stocksAdminCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        row.pharmacyNom() + " " + row.pharmacyAdresse() + " " + row.medicamentNom() + " " + row.medicamentType()
                ).contains(keyword))
                .filter(row -> matchStockLevel(row.quantite(), levelFilter))
                .sorted(Comparator
                        .comparingInt(PharmacyService.StockRow::quantite)
                        .thenComparing(PharmacyService.StockRow::pharmacyNom, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(PharmacyService.StockRow::medicamentNom, String.CASE_INSENSITIVE_ORDER))
                .toList();

        PageSlice<PharmacyService.StockRow> page = page(filtered, stocksAdminPage);
        stocksAdminPage = page.pageIndex();

        applyContainerMode(stocksAdminRowsBox, stocksAdminGridMode);
        stocksAdminRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            stocksAdminRowsBox.getChildren().add(createEmptyCard("Aucune ligne de stock."));
        } else {
            for (PharmacyService.StockRow row : page.items()) {
                String details = "Pharmacie: " + safe(row.pharmacyNom())
                        + " | Adresse: " + safe(row.pharmacyAdresse())
                        + " | Medicament: " + safe(row.medicamentNom())
                        + " | Type: " + (safe(row.medicamentType()).isBlank() ? "-" : safe(row.medicamentType()))
                        + " | Dosage: " + (safe(row.medicamentDosage()).isBlank() ? "-" : safe(row.medicamentDosage()))
                        + " | Qte: " + row.quantite()
                        + " | Prix vente: " + (safe(row.prixVente()).isBlank() ? "-" : safe(row.prixVente()));

                String badge = row.quantite() == 0 ? "RUPTURE" : (row.quantite() <= 5 ? "CRITIQUE" : "DISPONIBLE");

                Button editButton = buildRowButton("Editer", "admin-btn-xs-secondary", () -> openEditStockDialog(row));
                Button deleteButton = buildRowButton("Supprimer", "admin-btn-xs-danger", () -> confirmDeleteStock(row));

                String title = "Stock #" + row.id() + " - " + safe(row.medicamentNom());
                Node node = stocksAdminGridMode
                        ? createGridCard(title, details, badge, editButton, deleteButton)
                        : createListRow(title, details, editButton, deleteButton);
                stocksAdminRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(stocksAdminListModeButton, stocksAdminGridModeButton, stocksAdminGridMode);
        updatePagination(page, stocksAdminPaginationInfoLabel, stocksAdminPrevPageButton, stocksAdminNextPageButton);
    }

    private void renderReservationsAdminPage() {
        String keyword = normalize(reservationsAdminSearchField.getText());
        String statusFilter = normalize(reservationsAdminStatusFilterCombo.getValue());

        List<PharmacyService.ReservationRow> filtered = reservationsAdminCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        "#" + row.id() + " " + row.patientDisplay() + " " + row.pharmacyNom() + " " + row.medicamentNom()
                ).contains(keyword))
                .filter(row -> statusFilter.isBlank() || "tous statuts".equals(statusFilter) || normalize(row.statut()).equals(statusFilter))
                .sorted(Comparator.comparing(PharmacyService.ReservationRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<PharmacyService.ReservationRow> page = page(filtered, reservationsAdminPage);
        reservationsAdminPage = page.pageIndex();

        applyContainerMode(reservationsAdminRowsBox, reservationsAdminGridMode);
        reservationsAdminRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            reservationsAdminRowsBox.getChildren().add(createEmptyCard("Aucune reservation medicament."));
        } else {
            for (PharmacyService.ReservationRow row : page.items()) {
                String status = safe(row.statut()).toLowerCase(Locale.ROOT);
                String details = "Patient: " + safe(row.patientDisplay())
                        + " | Pharmacie: " + safe(row.pharmacyNom())
                        + " | Medicament: " + safe(row.medicamentNom())
                        + " | Qte: " + row.quantite()
                        + " | Total: " + (safe(row.prixTotal()).isBlank() ? "-" : safe(row.prixTotal()))
                        + " | Creee le: " + formatDateTime(row.createdAt())
                        + " | Expire le: " + formatDateTime(row.expiresAt());

                List<Button> actions = new ArrayList<>();
                if ("en_attente".equals(status)) {
                    actions.add(buildRowButton("Confirmer", "admin-btn-xs-primary", () -> applyReservationDecision(row.id(), "confirm")));
                    actions.add(buildRowButton("Refuser", "admin-btn-xs-danger", () -> applyReservationDecision(row.id(), "reject")));
                }
                if ("en_attente".equals(status) || "confirmee".equals(status)) {
                    actions.add(buildRowButton("Annuler", "admin-btn-xs-warning", () -> applyReservationDecision(row.id(), "cancel")));
                }

                String title = "Reservation #" + row.id() + " - " + safe(row.medicamentNom());
                String badge = row.statusLabel().toUpperCase(Locale.ROOT);
                Node node = reservationsAdminGridMode
                        ? createGridCard(title, details, badge, actions.toArray(new Button[0]))
                        : createListRow(title, details, actions.toArray(new Button[0]));
                reservationsAdminRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(reservationsAdminListModeButton, reservationsAdminGridModeButton, reservationsAdminGridMode);
        updatePagination(page, reservationsAdminPaginationInfoLabel, reservationsAdminPrevPageButton, reservationsAdminNextPageButton);
    }

    private void openPharmacyEditorDialog(PharmacyService.PharmacyRow existing) {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        boolean editing = existing != null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Editer pharmacie" : "Ajouter pharmacie");
        dialog.setHeaderText(editing
                ? "Modifier la pharmacie #" + existing.id()
                : "Creation d'une pharmacie");

        ButtonType saveButtonType = new ButtonType(editing ? "Mettre a jour" : "Creer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveButtonType);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("bo-edit-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(8, 4, 4, 4));

        TextField nomField = new TextField(editing ? safe(existing.nom()) : "");
        TextField adresseField = new TextField(editing ? safe(existing.adresse()) : "");
        TextField telephoneField = new TextField(editing ? safe(existing.telephone()) : "");
        TextField emailField = new TextField(editing ? safe(existing.email()) : "");
        TextField horairesField = new TextField(editing ? safe(existing.horaires()) : "");
        TextField latitudeField = new TextField(editing ? safe(existing.latitude()) : "");
        TextField longitudeField = new TextField(editing ? safe(existing.longitude()) : "");
        CheckBox activeCheck = new CheckBox("Pharmacie active");
        activeCheck.setSelected(!editing || existing.active());

        int row = 0;
        grid.add(new Label("Nom"), 0, row);
        grid.add(nomField, 1, row++);
        grid.add(new Label("Adresse"), 0, row);
        grid.add(adresseField, 1, row++);
        grid.add(new Label("Telephone"), 0, row);
        grid.add(telephoneField, 1, row++);
        grid.add(new Label("Email"), 0, row);
        grid.add(emailField, 1, row++);
        grid.add(new Label("Horaires"), 0, row);
        grid.add(horairesField, 1, row++);
        grid.add(new Label("Latitude"), 0, row);
        grid.add(latitudeField, 1, row++);
        grid.add(new Label("Longitude"), 0, row);
        grid.add(longitudeField, 1, row++);
        grid.add(activeCheck, 1, row);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-edit-user-pane");
        if (result.isEmpty() || result.get() != saveButtonType) {
            return;
        }

        PharmacyService.PharmacyDraft draft = new PharmacyService.PharmacyDraft(
                nomField.getText(),
                adresseField.getText(),
                telephoneField.getText(),
                emailField.getText(),
                horairesField.getText(),
                latitudeField.getText(),
                longitudeField.getText(),
                activeCheck.isSelected()
        );

        PharmacyService.ActionResult actionResult = editing
                ? pharmacyService.updatePharmacy(currentUser, existing.id(), draft)
                : pharmacyService.createPharmacy(currentUser, draft);
        showFeedback(actionResult.message(), actionResult.success());
        if (actionResult.success()) {
            refresh();
        }
    }

    private void togglePharmacyActive(PharmacyService.PharmacyRow row) {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        PharmacyService.PharmacyDraft draft = new PharmacyService.PharmacyDraft(
                row.nom(),
                row.adresse(),
                row.telephone(),
                row.email(),
                row.horaires(),
                row.latitude(),
                row.longitude(),
                !row.active()
        );
        PharmacyService.ActionResult result = pharmacyService.updatePharmacy(currentUser, row.id(), draft);
        showFeedback(result.message(), result.success());
        if (result.success()) {
            refresh();
        }
    }

    private void confirmDeletePharmacy(PharmacyService.PharmacyRow row) {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Supprimer pharmacie");
        dialog.setHeaderText("Supprimer la pharmacie #" + row.id() + " - " + safe(row.nom()) + " ?");
        dialog.getDialogPane().setContent(new Label("Cette action supprime definitivement la pharmacie et ses liaisons."));
        ButtonType confirmType = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, confirmType);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-delete-pane");
        if (result.isEmpty() || result.get() != confirmType) {
            return;
        }

        PharmacyService.ActionResult actionResult = pharmacyService.deletePharmacy(currentUser, row.id());
        showFeedback(actionResult.message(), actionResult.success());
        if (actionResult.success()) {
            refresh();
        }
    }

    private void openMedicamentEditorDialog(PharmacyService.MedicamentRow existing) {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        boolean editing = existing != null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(editing ? "Editer medicament" : "Ajouter medicament");
        dialog.setHeaderText(editing
                ? "Modifier le medicament #" + existing.id()
                : "Creation d'un medicament");

        ButtonType saveButtonType = new ButtonType(editing ? "Mettre a jour" : "Creer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveButtonType);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("bo-edit-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(8, 4, 4, 4));

        TextField nomField = new TextField(editing ? safe(existing.nom()) : "");
        TextField typeField = new TextField(editing ? safe(existing.type()) : "");
        TextField formeField = new TextField(editing ? safe(existing.forme()) : "");
        TextField dosageField = new TextField(editing ? safe(existing.dosage()) : "");
        TextField prixField = new TextField(editing ? safe(existing.prix()) : "");
        TextField laboratoireField = new TextField(editing ? safe(existing.laboratoire()) : "");
        TextField codeBarreField = new TextField(editing ? safe(existing.codeBarre()) : "");
        TextField descriptionField = new TextField(editing ? safe(existing.description()) : "");

        int row = 0;
        grid.add(new Label("Nom"), 0, row);
        grid.add(nomField, 1, row++);
        grid.add(new Label("Type"), 0, row);
        grid.add(typeField, 1, row++);
        grid.add(new Label("Forme"), 0, row);
        grid.add(formeField, 1, row++);
        grid.add(new Label("Dosage"), 0, row);
        grid.add(dosageField, 1, row++);
        grid.add(new Label("Prix"), 0, row);
        grid.add(prixField, 1, row++);
        grid.add(new Label("Laboratoire"), 0, row);
        grid.add(laboratoireField, 1, row++);
        grid.add(new Label("Code barre"), 0, row);
        grid.add(codeBarreField, 1, row++);
        grid.add(new Label("Description"), 0, row);
        grid.add(descriptionField, 1, row);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-edit-user-pane");
        if (result.isEmpty() || result.get() != saveButtonType) {
            return;
        }

        PharmacyService.MedicamentDraft draft = new PharmacyService.MedicamentDraft(
                nomField.getText(),
                typeField.getText(),
                descriptionField.getText(),
                formeField.getText(),
                dosageField.getText(),
                prixField.getText(),
                0,
                laboratoireField.getText(),
                codeBarreField.getText()
        );

        PharmacyService.ActionResult actionResult = editing
                ? pharmacyService.updateMedicament(currentUser, existing.id(), draft)
                : pharmacyService.createMedicament(currentUser, draft);
        showFeedback(actionResult.message(), actionResult.success());
        if (actionResult.success()) {
            refresh();
        }
    }

    private void confirmDeleteMedicament(PharmacyService.MedicamentRow row) {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Supprimer medicament");
        dialog.setHeaderText("Supprimer le medicament #" + row.id() + " - " + safe(row.nom()) + " ?");
        dialog.getDialogPane().setContent(new Label("Cette action supprime definitivement le medicament selectionne."));
        ButtonType confirmType = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, confirmType);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-delete-pane");
        if (result.isEmpty() || result.get() != confirmType) {
            return;
        }

        PharmacyService.ActionResult actionResult = pharmacyService.deleteMedicament(currentUser, row.id());
        showFeedback(actionResult.message(), actionResult.success());
        if (actionResult.success()) {
            refresh();
        }
    }

    private void openCreateStockDialog() {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        if (pharmaciesAdminCache.isEmpty() || medicamentsAdminCache.isEmpty()) {
            showFeedback("Ajout de stock impossible: pharmacies ou medicaments indisponibles.", false);
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Ajouter stock");
        dialog.setHeaderText("Creer une ligne de stock pharmacie");

        ButtonType saveButtonType = new ButtonType("Creer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveButtonType);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("bo-edit-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(8, 4, 4, 4));

        ComboBox<PharmacyService.PharmacyRow> pharmacyCombo = new ComboBox<>(FXCollections.observableArrayList(pharmaciesAdminCache));
        pharmacyCombo.setPrefWidth(300);
        pharmacyCombo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PharmacyService.PharmacyRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.nom() + " - " + item.adresse());
            }
        });
        pharmacyCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PharmacyService.PharmacyRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.nom());
            }
        });
        pharmacyCombo.getSelectionModel().selectFirst();

        ComboBox<PharmacyService.MedicamentRow> medicamentCombo = new ComboBox<>(FXCollections.observableArrayList(medicamentsAdminCache));
        medicamentCombo.setPrefWidth(300);
        medicamentCombo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(PharmacyService.MedicamentRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.nom() + (safe(item.type()).isBlank() ? "" : " [" + item.type() + "]"));
            }
        });
        medicamentCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PharmacyService.MedicamentRow item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.nom());
            }
        });
        medicamentCombo.getSelectionModel().selectFirst();

        TextField quantiteField = new TextField("1");
        TextField prixField = new TextField();

        int row = 0;
        grid.add(new Label("Pharmacie"), 0, row);
        grid.add(pharmacyCombo, 1, row++);
        grid.add(new Label("Medicament"), 0, row);
        grid.add(medicamentCombo, 1, row++);
        grid.add(new Label("Quantite"), 0, row);
        grid.add(quantiteField, 1, row++);
        grid.add(new Label("Prix vente"), 0, row);
        grid.add(prixField, 1, row);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-edit-user-pane");
        if (result.isEmpty() || result.get() != saveButtonType) {
            return;
        }

        PharmacyService.PharmacyRow selectedPharmacy = pharmacyCombo.getValue();
        PharmacyService.MedicamentRow selectedMedicament = medicamentCombo.getValue();
        int quantite = parsePositiveInt(quantiteField.getText(), 0);

        if (selectedPharmacy == null || selectedMedicament == null || quantite <= 0) {
            showFeedback("Selection pharmacie/medicament et quantite valide requises.", false);
            return;
        }

        PharmacyService.StockDraft draft = new PharmacyService.StockDraft(
                selectedPharmacy.id(),
                selectedMedicament.id(),
                quantite,
                prixField.getText()
        );
        PharmacyService.ActionResult actionResult = pharmacyService.createStock(currentUser, draft);
        showFeedback(actionResult.message(), actionResult.success());
        if (actionResult.success()) {
            refresh();
        }
    }

    private void openEditStockDialog(PharmacyService.StockRow row) {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editer stock");
        dialog.setHeaderText("Mise a jour du stock #" + row.id() + " - " + safe(row.medicamentNom()));

        ButtonType saveButtonType = new ButtonType("Mettre a jour", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveButtonType);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("bo-edit-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(8, 4, 4, 4));

        TextField quantiteField = new TextField(String.valueOf(row.quantite()));
        TextField prixField = new TextField(safe(row.prixVente()));

        int currentRow = 0;
        grid.add(new Label("Quantite"), 0, currentRow);
        grid.add(quantiteField, 1, currentRow++);
        grid.add(new Label("Prix vente"), 0, currentRow);
        grid.add(prixField, 1, currentRow);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-edit-user-pane");
        if (result.isEmpty() || result.get() != saveButtonType) {
            return;
        }

        int quantite = parsePositiveInt(quantiteField.getText(), -1);
        if (quantite < 0) {
            showFeedback("Quantite invalide.", false);
            return;
        }

        PharmacyService.ActionResult actionResult = pharmacyService.updateStock(currentUser, row.id(), quantite, prixField.getText());
        showFeedback(actionResult.message(), actionResult.success());
        if (actionResult.success()) {
            refresh();
        }
    }

    private void confirmDeleteStock(PharmacyService.StockRow row) {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Supprimer stock");
        dialog.setHeaderText("Supprimer le stock #" + row.id() + " - " + safe(row.medicamentNom()) + " ?");
        dialog.getDialogPane().setContent(new Label("La ligne de stock sera supprimee definitivement."));
        ButtonType confirmType = new ButtonType("Supprimer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, confirmType);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-delete-pane");
        if (result.isEmpty() || result.get() != confirmType) {
            return;
        }

        PharmacyService.ActionResult actionResult = pharmacyService.deleteStock(currentUser, row.id());
        showFeedback(actionResult.message(), actionResult.success());
        if (actionResult.success()) {
            refresh();
        }
    }

    private void applyReservationDecision(int reservationId, String decision) {
        User currentUser = currentAdminUser();
        if (currentUser == null) {
            return;
        }

        PharmacyService.ActionResult result;
        switch (decision) {
            case "confirm" -> result = pharmacyService.confirmReservation(currentUser, reservationId);
            case "reject" -> result = pharmacyService.rejectReservation(currentUser, reservationId);
            case "cancel" -> result = pharmacyService.cancelReservation(currentUser, reservationId);
            default -> {
                showFeedback("Action reservation inconnue.", false);
                return;
            }
        }

        showFeedback(result.message(), result.success());
        if (result.success()) {
            refresh();
        }
    }

    private void renderAccompanimentsPage() {
        String keyword = normalize(accompanimentsSearchField.getText());
        String statusFilter = normalize(accompanimentsStatusFilterCombo.getValue());
        String assignedFilter = safe(accompanimentsAssignedFilterCombo.getValue());

        List<AdminOperationsService.AccompanimentAdminRow> filtered = accompanimentsCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        "#" + row.id() + " " + row.title() + " " + row.patientDisplay() + " " + row.coachDisplay() + " " + row.nutritionistDisplay()
                ).contains(keyword))
                .filter(row -> statusFilter.isBlank() || "tous statuts".equals(statusFilter) || normalize(row.status()).equals(statusFilter))
                .filter(row -> matchAccompanimentAssignedFilter(row, assignedFilter))
                .sorted(Comparator
                        .comparing(AdminOperationsService.AccompanimentAdminRow::startDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdminOperationsService.AccompanimentAdminRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<AdminOperationsService.AccompanimentAdminRow> page = page(filtered, accompanimentsPage);
        accompanimentsPage = page.pageIndex();

        applyContainerMode(accompanimentsRowsBox, accompanimentsGridMode);
        accompanimentsRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            accompanimentsRowsBox.getChildren().add(createEmptyCard("Aucun plan d'accompagnement."));
        } else {
            for (AdminOperationsService.AccompanimentAdminRow row : page.items()) {
                String status = normalizeStatusLabel(row.status());
                String details = "Patient: " + safe(row.patientDisplay())
                        + " | Coach: " + safe(row.coachDisplay())
                        + " | Nutritionniste: " + safe(row.nutritionistDisplay())
                        + " | Debut: " + formatDate(row.startDate())
                        + " | Fin: " + formatDate(row.endDate())
                        + " | Duree: " + row.durationWeeks() + " sem"
                        + " | Exercices: " + row.exerciseCount()
                        + " | Regimes: " + row.dietCount();

                Button activeButton = buildRowButton("Activer", "admin-btn-xs-primary", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateAccompanimentStatusByAdmin(row.id(), "active");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button pauseButton = buildRowButton("Pause", "admin-btn-xs-warning", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateAccompanimentStatusByAdmin(row.id(), "paused");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button completeButton = buildRowButton("Terminer", "admin-btn-xs-secondary", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateAccompanimentStatusByAdmin(row.id(), "completed");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button cancelButton = buildRowButton("Annuler", "admin-btn-xs-danger", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateAccompanimentStatusByAdmin(row.id(), "cancelled");
                    showFeedback(result.message(), result.success());
                    refresh();
                });

                String title = "#" + row.id() + " - " + safe(row.title());
                Node node = accompanimentsGridMode
                        ? createGridCard(title, details, status, activeButton, pauseButton, completeButton, cancelButton)
                        : createListRow(title, details, activeButton, pauseButton, completeButton, cancelButton);
                accompanimentsRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(accompanimentsListModeButton, accompanimentsGridModeButton, accompanimentsGridMode);
        updatePagination(page, accompanimentsPaginationInfoLabel, accompanimentsPrevPageButton, accompanimentsNextPageButton);
    }

    private void renderCommunityPage() {
        String keyword = normalize(communitySearchField.getText());
        String statusFilter = normalize(communityStatusFilterCombo.getValue());
        String typeFilter = normalize(communityTypeFilterCombo.getValue());

        List<AdminOperationsService.CommunityRow> filtered = communityCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        "#" + row.id() + " " + row.title() + " " + row.authorDisplay() + " " + row.category() + " " + row.tags()
                ).contains(keyword))
                .filter(row -> statusFilter.isBlank() || "tous statuts".equals(statusFilter) || normalize(row.status()).equals(statusFilter))
                .filter(row -> typeFilter.isBlank() || "tous types".equals(typeFilter) || normalize(row.type()).equals(typeFilter))
                .sorted(Comparator.comparing(AdminOperationsService.CommunityRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<AdminOperationsService.CommunityRow> page = page(filtered, communityPage);
        communityPage = page.pageIndex();

        applyContainerMode(communityRowsBox, communityGridMode);
        communityRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            communityRowsBox.getChildren().add(createEmptyCard("Aucun contenu blog."));
        } else {
            for (AdminOperationsService.CommunityRow row : page.items()) {
                String status = normalizeStatusLabel(row.status());
                String details = "Type: " + safe(row.type())
                        + " | Categorie: " + safe(row.category())
                        + " | Tags: " + safe(row.tags())
                        + " | Likes: " + row.likesCount()
                        + " | Commentaires: " + row.commentsCount()
                        + " | Score: " + String.format(Locale.ROOT, "%.3f", row.score())
                        + " | MAJ: " + formatDateTime(row.updatedAt());

                Button validateButton = buildRowButton("Valider", "admin-btn-xs-primary", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateCommunityStatusByAdmin(row.id(), "valide", "Validation admin dashboard");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button publishButton = buildRowButton("Publier", "admin-btn-xs-secondary", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateCommunityStatusByAdmin(row.id(), "publie", "Publication admin dashboard");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button rejectButton = buildRowButton("Rejeter", "admin-btn-xs-danger", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateCommunityStatusByAdmin(row.id(), "rejete", "Rejet admin dashboard");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button pendingButton = buildRowButton("En attente", "admin-btn-xs-warning", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateCommunityStatusByAdmin(row.id(), "en_attente", "Retour file moderation");
                    showFeedback(result.message(), result.success());
                    refresh();
                });

                String title = "#" + row.id() + " - " + safe(row.title()) + " (" + safe(row.authorDisplay()) + ")";
                Node node = communityGridMode
                        ? createGridCard(title, details, status, validateButton, publishButton, rejectButton, pendingButton)
                        : createListRow(title, details, validateButton, publishButton, rejectButton, pendingButton);
                communityRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(communityListModeButton, communityGridModeButton, communityGridMode);
        updatePagination(page, communityPaginationInfoLabel, communityPrevPageButton, communityNextPageButton);
    }

    private void renderAppointmentsAdminPage() {
        String keyword = normalize(appointmentsAdminSearchField.getText());
        String statusFilter = normalize(appointmentsAdminStatusFilterCombo.getValue());
        String dateFilter = safe(appointmentsAdminDateFilterCombo.getValue());

        List<AdminOperationsService.AppointmentAdminRow> filtered = appointmentsAdminCache.stream()
                .filter(row -> keyword.isBlank() || normalize(
                        "#" + row.id() + " " + row.patientDisplay() + " " + row.doctorDisplay() + " " + row.motif()
                ).contains(keyword))
                .filter(row -> statusFilter.isBlank() || "tous statuts".equals(statusFilter) || normalize(row.status()).equals(statusFilter))
                .filter(row -> matchAppointmentDateFilter(row.date(), dateFilter))
                .sorted(Comparator
                        .comparing(AdminOperationsService.AppointmentAdminRow::date, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdminOperationsService.AppointmentAdminRow::time, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        PageSlice<AdminOperationsService.AppointmentAdminRow> page = page(filtered, appointmentsAdminPage);
        appointmentsAdminPage = page.pageIndex();

        applyContainerMode(appointmentsAdminRowsBox, appointmentsAdminGridMode);
        appointmentsAdminRowsBox.getChildren().clear();

        if (page.items().isEmpty()) {
            appointmentsAdminRowsBox.getChildren().add(createEmptyCard("Aucun rendez-vous."));
        } else {
            for (AdminOperationsService.AppointmentAdminRow row : page.items()) {
                String status = normalizeStatusLabel(row.status());
                String dateTime = formatDate(row.date()) + " " + formatTime(row.time());
                String details = "Patient: " + safe(row.patientDisplay())
                        + " | Medecin: " + safe(row.doctorDisplay())
                        + " | Date: " + dateTime
                        + " | Motif: " + safe(row.motif());

                Button confirmButton = buildRowButton("Confirmer", "admin-btn-xs-primary", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateAppointmentStatusByAdmin(row.id(), "confirme");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button refuseButton = buildRowButton("Refuser", "admin-btn-xs-danger", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateAppointmentStatusByAdmin(row.id(), "refuse");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button cancelButton = buildRowButton("Annuler", "admin-btn-xs-warning", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateAppointmentStatusByAdmin(row.id(), "annule");
                    showFeedback(result.message(), result.success());
                    refresh();
                });
                Button pendingButton = buildRowButton("En attente", "admin-btn-xs-secondary", () -> {
                    AdminOperationsService.ActionResult result = adminOperationsService.updateAppointmentStatusByAdmin(row.id(), "en_attente");
                    showFeedback(result.message(), result.success());
                    refresh();
                });

                String title = "#" + row.id() + " - " + safe(row.patientDisplay()) + " -> " + safe(row.doctorDisplay());
                Node node = appointmentsAdminGridMode
                        ? createGridCard(title, details, status, confirmButton, refuseButton, cancelButton, pendingButton)
                        : createListRow(title, details, confirmButton, refuseButton, cancelButton, pendingButton);
                appointmentsAdminRowsBox.getChildren().add(node);
            }
        }

        updateModeButtons(appointmentsAdminListModeButton, appointmentsAdminGridModeButton, appointmentsAdminGridMode);
        updatePagination(page, appointmentsAdminPaginationInfoLabel, appointmentsAdminPrevPageButton, appointmentsAdminNextPageButton);
    }

    private void renderLatestBans(List<String> rows) {
        latestBansBox.getChildren().clear();
        if (rows.isEmpty()) {
            latestBansBox.getChildren().add(createEmptyLabel("Aucun compte banni recemment."));
            return;
        }

        for (String row : rows) {
            Label item = new Label(row);
            item.getStyleClass().add("admin-line");
            latestBansBox.getChildren().add(item);
        }
    }

    private void renderPendingApprovals(List<AdminOperationsService.PendingApprovalRow> rows) {
        pendingApprovalsBox.getChildren().clear();
        if (rows.isEmpty()) {
            pendingApprovalsBox.getChildren().add(createEmptyLabel("Aucun compte professionnel en attente."));
            return;
        }

        for (AdminOperationsService.PendingApprovalRow row : rows) {
            String title = row.displayName() + " (" + safe(row.currentRole()) + ")";
            String details = safe(row.email()) + " - inscrit le " + formatDateTime(row.createdAt());

            Button approveButton = buildRowButton("Approuver", "admin-btn-xs-primary", () -> approvePendingUser(row));
            Button banButton = buildRowButton("Bannir", "admin-btn-xs-danger", () -> banPendingUser(row.id()));

            pendingApprovalsBox.getChildren().add(createListRow(title, details, approveButton, banButton));
        }
    }

    private void renderSessions(List<AdminOperationsService.ActiveSessionRow> rows) {
        activeSessionsBox.getChildren().clear();
        if (rows.isEmpty()) {
            activeSessionsBox.getChildren().add(createEmptyLabel("Aucune session active."));
            return;
        }

        for (AdminOperationsService.ActiveSessionRow row : rows) {
            String device = safe(row.deviceLabel()).isBlank() ? "Device inconnu" : safe(row.deviceLabel());
            String title = "User #" + row.userId() + " - " + device;
            String details = "IP " + safe(row.ipAddress()) + " - derniere activite " + formatDateTime(row.lastSeenAt());

            Button revokeButton = buildRowButton("Revoquer", "admin-btn-xs-danger", () -> {
                AdminOperationsService.ActionResult result = adminOperationsService.revokeSession(row.id());
                showFeedback(result.message(), result.success());
                refresh();
            });

            activeSessionsBox.getChildren().add(createListRow(title, details, revokeButton));
        }
    }

    private void renderSuspiciousLogins(List<AdminOperationsService.SuspiciousLoginRow> rows) {
        suspiciousLoginsBox.getChildren().clear();
        if (rows.isEmpty()) {
            suspiciousLoginsBox.getChildren().add(createEmptyLabel("Aucune connexion suspecte."));
            return;
        }

        for (AdminOperationsService.SuspiciousLoginRow row : rows) {
            String title = safe(row.email()).isBlank() ? "Utilisateur inconnu" : row.email();
            String details = "IP " + safe(row.ipAddress()) + " - " + safe(row.reason()) + " - " + formatDateTime(row.createdAt());
            boolean currentlyBlocked = row.blocked();

            Button toggleButton = buildRowButton(
                    currentlyBlocked ? "Debloquer" : "Bloquer",
                    currentlyBlocked ? "admin-btn-xs-secondary" : "admin-btn-xs-warning",
                    () -> {
                        AdminOperationsService.ActionResult result = adminOperationsService.markSuspiciousLoginBlocked(row.id(), !currentlyBlocked);
                        showFeedback(result.message(), result.success());
                        refresh();
                    }
            );

            suspiciousLoginsBox.getChildren().add(createListRow(title, details, toggleButton));
        }
    }

    private void renderModeration(List<AdminOperationsService.ModerationRow> rows) {
        moderationQueueBox.getChildren().clear();
        if (rows.isEmpty()) {
            moderationQueueBox.getChildren().add(createEmptyLabel("Aucun contenu en moderation."));
            return;
        }

        for (AdminOperationsService.ModerationRow row : rows) {
            String title = "#" + row.id() + " - " + safe(row.title());
            String details = "Type: " + safe(row.type()) + " | Statut: " + safe(row.status()) + " | Auteur: " + safe(row.authorEmail());

            Button approveButton = buildRowButton("Valider", "admin-btn-xs-primary", () -> {
                AdminOperationsService.ActionResult result = adminOperationsService.moderateContent(row.id(), "valide", "Valide via dashboard JavaFX");
                showFeedback(result.message(), result.success());
                refresh();
            });

            Button rejectButton = buildRowButton("Rejeter", "admin-btn-xs-danger", () -> {
                AdminOperationsService.ActionResult result = adminOperationsService.moderateContent(row.id(), "rejete", "Rejete via dashboard JavaFX");
                showFeedback(result.message(), result.success());
                refresh();
            });

            moderationQueueBox.getChildren().add(createListRow(title, details, approveButton, rejectButton));
        }
    }

    private void renderUserScores(List<AdminOperationsService.UserScoreRow> rows) {
        userScoresBox.getChildren().clear();
        if (rows.isEmpty()) {
            userScoresBox.getChildren().add(createEmptyLabel("Aucune donnee de scoring utilisateur."));
            return;
        }

        for (AdminOperationsService.UserScoreRow row : rows) {
            String title = row.displayName() + " - " + safe(row.role());
            String details = "Score: " + row.score() + "/100 | Priorite support: " + safe(row.supportPriority()) + " | " + safe(row.email());
            userScoresBox.getChildren().add(createListRow(title, details));
        }
    }

    private void approvePendingUser(AdminOperationsService.PendingApprovalRow row) {
        ChoiceDialog<String> roleDialog = new ChoiceDialog<>("ROLE_MEDECIN", PROFESSIONAL_ROLES);
        roleDialog.setTitle("Validation compte professionnel");
        roleDialog.setHeaderText("Choisir le role a attribuer");
        roleDialog.setContentText("Role:");

        Optional<String> roleChoice = ModalDialogs.showDialog(roleDialog, resolveOwnerWindow(), "bo-modal-pane", "bo-approval-pane");
        if (roleChoice.isEmpty()) {
            showFeedback("Validation annulee.", false);
            return;
        }

        AdminOperationsService.ActionResult result = adminOperationsService.approveProfessionalAccount(row.id(), roleChoice.get());
        showFeedback(result.message(), result.success());
        refresh();
    }

    private void banPendingUser(int userId) {
        TextInputDialog reasonDialog = new TextInputDialog("Bannissement admin");
        reasonDialog.setTitle("Bannir utilisateur");
        reasonDialog.setHeaderText("Motif de bannissement");
        reasonDialog.setContentText("Motif:");

        Optional<String> reason = ModalDialogs.showDialog(reasonDialog, resolveOwnerWindow(), "bo-modal-pane", "bo-ban-pane");
        if (reason.isEmpty()) {
            showFeedback("Bannissement annule.", false);
            return;
        }

        AdminOperationsService.ActionResult result = adminOperationsService.banUser(userId, reason.get(), null);
        showFeedback(result.message(), result.success());
        refresh();
    }

    private void openEditUserDialog(AdminOperationsService.AdminUserRow user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editer utilisateur");
        dialog.setHeaderText("Modifier " + user.displayName() + " (ID " + user.id() + ")");

        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveButtonType);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("bo-edit-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(8, 4, 4, 4));

        TextField nomField = new TextField(safe(user.nom()));
        TextField prenomField = new TextField(safe(user.prenom()));
        TextField emailField = new TextField(safe(user.email()));

        ComboBox<String> roleCombo = new ComboBox<>(FXCollections.observableArrayList(EDITABLE_ROLES));
        if (EDITABLE_ROLES.contains(safe(user.role()))) {
            roleCombo.setValue(safe(user.role()));
        } else {
            roleCombo.setValue("ROLE_PATIENT");
        }

        CheckBox emailVerifiedCheck = new CheckBox("Email verifie");
        emailVerifiedCheck.setSelected(user.emailVerified());

        CheckBox adminApprovedCheck = new CheckBox("Compte approuve par admin");
        adminApprovedCheck.setSelected(user.adminApproved());

        CheckBox bannedCheck = new CheckBox("Compte banni");
        bannedCheck.setSelected(user.banned());

        int row = 0;
        grid.add(new Label("Nom"), 0, row);
        grid.add(nomField, 1, row++);
        grid.add(new Label("Prenom"), 0, row);
        grid.add(prenomField, 1, row++);
        grid.add(new Label("Email"), 0, row);
        grid.add(emailField, 1, row++);
        grid.add(new Label("Role"), 0, row);
        grid.add(roleCombo, 1, row++);
        grid.add(emailVerifiedCheck, 1, row++);
        grid.add(adminApprovedCheck, 1, row++);
        grid.add(bannedCheck, 1, row);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-edit-user-pane");
        if (result.isEmpty() || result.get() != saveButtonType) {
            showFeedback("Edition utilisateur annulee.", false);
            return;
        }

        AdminOperationsService.ActionResult updateResult = adminOperationsService.updateUserByAdmin(
                user.id(),
                nomField.getText(),
                prenomField.getText(),
                emailField.getText(),
                roleCombo.getValue(),
                emailVerifiedCheck.isSelected(),
                adminApprovedCheck.isSelected(),
                bannedCheck.isSelected()
        );
        showFeedback(updateResult.message(), updateResult.success());
        if (updateResult.success()) {
            refresh();
        }
    }

    private void confirmDeleteUser(AdminOperationsService.AdminUserRow user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Supprimer utilisateur");
        dialog.setHeaderText("Confirmer la suppression de " + user.displayName());

        Label warningLabel = new Label("Cette action effectue une suppression logique du compte dans le back-office.");
        warningLabel.setWrapText(true);
        warningLabel.getStyleClass().add("bo-delete-warning");

        Label irreversibleLabel = new Label("Vous pourrez conserver l'historique, mais le compte sera masque de la liste active.");
        irreversibleLabel.setWrapText(true);
        irreversibleLabel.getStyleClass().add("bo-delete-hint");

        VBox content = new VBox(10, warningLabel, irreversibleLabel);
        content.getStyleClass().add("bo-delete-content");
        dialog.getDialogPane().setContent(content);

        ButtonType confirmType = new ButtonType("Supprimer", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL, confirmType);

        Optional<ButtonType> result = ModalDialogs.showDialog(dialog, resolveOwnerWindow(), "bo-modal-pane", "bo-delete-pane");
        if (result.isEmpty() || result.get() != confirmType) {
            showFeedback("Suppression annulee.", false);
            return;
        }

        AdminOperationsService.ActionResult deleteResult = adminOperationsService.softDeleteUserByAdmin(user.id());
        showFeedback(deleteResult.message(), deleteResult.success());
        if (deleteResult.success()) {
            refresh();
        }
    }

    private void triggerAdminPasswordReset(AdminOperationsService.AdminUserRow user) {
        if (user == null || user.id() <= 0) {
            showFeedback("Utilisateur invalide pour la reinitialisation.", false);
            return;
        }

        AdminOperationsService.ActionResult result = adminOperationsService.triggerPasswordResetByAdmin(user.id());
        showFeedback(result.message(), result.success());
    }

    private Window resolveOwnerWindow() {
        if (headerTitleLabel != null && headerTitleLabel.getScene() != null) {
            return headerTitleLabel.getScene().getWindow();
        }
        if (adminFeedbackLabel != null && adminFeedbackLabel.getScene() != null) {
            return adminFeedbackLabel.getScene().getWindow();
        }
        return null;
    }

    private Node createListRow(String title, String details, Button... actions) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("admin-row-title");

        Label detailsLabel = new Label(details);
        detailsLabel.setWrapText(true);
        detailsLabel.getStyleClass().add("admin-row-meta");

        VBox textCol = new VBox(2, titleLabel, detailsLabel);
        textCol.getStyleClass().add("admin-row-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10);
        row.getStyleClass().addAll("admin-row", "bo-list-row");
        row.setPrefWidth(1040);
        row.getChildren().addAll(textCol, spacer);

        if (actions != null && actions.length > 0) {
            HBox actionBox = new HBox(6);
            actionBox.getStyleClass().add("admin-row-actions");
            actionBox.getChildren().addAll(actions);
            row.getChildren().add(actionBox);
        }

        return row;
    }

    private Node createGridCard(String title, String details, String badge, Button... actions) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("bo-grid-title");

        Label badgeLabel = new Label(safe(badge));
        badgeLabel.getStyleClass().add("bo-grid-badge");

        Label detailsLabel = new Label(details);
        detailsLabel.setWrapText(true);
        detailsLabel.getStyleClass().add("bo-grid-details");

        HBox top = new HBox(8, titleLabel, badgeLabel);
        top.getStyleClass().add("bo-grid-top");

        VBox root = new VBox(10, top, detailsLabel);
        root.getStyleClass().add("bo-grid-card");

        if (actions != null && actions.length > 0) {
            HBox actionBox = new HBox(6);
            actionBox.getStyleClass().add("bo-grid-actions");
            actionBox.getChildren().addAll(actions);
            root.getChildren().add(actionBox);
        }

        return root;
    }

    private Label createEmptyLabel(String text) {
        Label empty = new Label(text);
        empty.getStyleClass().add("admin-line");
        return empty;
    }

    private Node createEmptyCard(String text) {
        Label empty = new Label(text);
        empty.getStyleClass().add("bo-empty-card");
        return empty;
    }

    private Button buildRowButton(String label, String styleClass, Runnable action) {
        Button button = new Button(label);
        button.getStyleClass().add("admin-btn-xs");
        if (styleClass != null && !styleClass.isBlank()) {
            button.getStyleClass().add(styleClass);
        }
        button.setOnAction(event -> action.run());
        return button;
    }

    private void applyContainerMode(FlowPane pane, boolean gridMode) {
        pane.getStyleClass().removeAll("bo-items-list", "bo-items-grid");
        pane.getStyleClass().add(gridMode ? "bo-items-grid" : "bo-items-list");
        pane.setPrefWrapLength(gridMode ? 1120 : 2200);
    }

    private void updateModeButtons(Button listButton, Button gridButton, boolean gridMode) {
        listButton.getStyleClass().remove("bo-view-btn-active");
        gridButton.getStyleClass().remove("bo-view-btn-active");
        if (gridMode) {
            gridButton.getStyleClass().add("bo-view-btn-active");
        } else {
            listButton.getStyleClass().add("bo-view-btn-active");
        }
    }

    private void updatePagination(PageSlice<?> page, Label infoLabel, Button prevButton, Button nextButton) {
        infoLabel.setText("Affichage " + page.from() + "-" + page.to() + " sur " + page.total());
        prevButton.setDisable(page.pageIndex() <= 0);
        nextButton.setDisable(page.pageIndex() >= page.maxPage());
    }

    private <T> PageSlice<T> page(List<T> rows, int requestedPage) {
        int total = rows.size();
        int maxPage = total == 0 ? 0 : (total - 1) / PAGE_SIZE;
        int pageIndex = Math.max(0, Math.min(requestedPage, maxPage));

        int fromIndex = Math.min(pageIndex * PAGE_SIZE, total);
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);

        List<T> pageRows = rows.subList(fromIndex, toIndex);
        int fromDisplay = total == 0 ? 0 : fromIndex + 1;
        int toDisplay = total == 0 ? 0 : toIndex;

        return new PageSlice<>(pageRows, pageIndex, total, fromDisplay, toDisplay, maxPage);
    }

    private boolean matchUserStatus(AdminOperationsService.AdminUserRow user, String filter) {
        return switch (filter) {
            case "Actif" -> !user.banned();
            case "Banni" -> user.banned();
            case "Email verifie" -> user.emailVerified();
            case "Validation en attente" -> !user.adminApproved();
            default -> true;
        };
    }

    private boolean matchValidationFilter(AdminOperationsService.ValidationUserRow row, String filter) {
        return switch (filter) {
            case "Email verifie" -> row.emailVerified();
            case "Email non verifie" -> !row.emailVerified();
            case "Admin approuve" -> row.adminApproved();
            case "Admin en attente" -> !row.adminApproved();
            default -> true;
        };
    }

    private boolean matchDeviceFilter(AdminOperationsService.ActiveSessionRow row, String filter) {
        String device = normalize(row.deviceLabel());
        if ("Desktop".equals(filter)) {
            return device.contains("desktop") || device.contains("windows") || device.contains("linux") || device.contains("mac");
        }
        if ("Mobile".equals(filter)) {
            return device.contains("android") || device.contains("ios") || device.contains("mobile");
        }
        if ("Inconnu".equals(filter)) {
            return device.isBlank();
        }
        return true;
    }

    private boolean matchSuspiciousFilter(AdminOperationsService.SuspiciousLoginRow row, String filter) {
        if ("Bloquees".equals(filter)) {
            return row.blocked();
        }
        if ("Non bloquees".equals(filter)) {
            return !row.blocked();
        }
        return true;
    }

    private boolean matchPharmacyStatus(boolean active, String filter) {
        if ("Actives".equals(filter)) {
            return active;
        }
        if ("Inactives".equals(filter)) {
            return !active;
        }
        return true;
    }

    private boolean matchStockLevel(int quantity, String filter) {
        if ("Disponible".equals(filter)) {
            return quantity > 5;
        }
        if ("Critique (<=5)".equals(filter)) {
            return quantity > 0 && quantity <= 5;
        }
        if ("Rupture".equals(filter)) {
            return quantity <= 0;
        }
        return true;
    }

    private boolean matchAccompanimentAssignedFilter(AdminOperationsService.AccompanimentAdminRow row, String filter) {
        boolean hasCoach = !safe(row.coachDisplay()).isBlank() && !"-".equals(safe(row.coachDisplay()));
        boolean hasNutritionist = !safe(row.nutritionistDisplay()).isBlank() && !"-".equals(safe(row.nutritionistDisplay()));

        return switch (filter) {
            case "Avec coach" -> hasCoach;
            case "Avec nutritionniste" -> hasNutritionist;
            case "Complet (coach + nutritionniste)" -> hasCoach && hasNutritionist;
            default -> true;
        };
    }

    private boolean matchAppointmentDateFilter(LocalDate date, String filter) {
        if (date == null) {
            return "Toutes dates".equals(filter);
        }

        LocalDate today = LocalDate.now();
        if ("Aujourd'hui".equals(filter)) {
            return date.equals(today);
        }
        if ("7 prochains jours".equals(filter)) {
            return !date.isBefore(today) && !date.isAfter(today.plusDays(7));
        }
        if ("Passe".equals(filter)) {
            return date.isBefore(today);
        }
        return true;
    }

    private String normalizeStatusLabel(String raw) {
        String normalized = normalize(raw);
        return switch (normalized) {
            case "en_attente", "pending", "requested" -> "EN ATTENTE";
            case "publie", "published" -> "PUBLIE";
            case "valide", "approved" -> "VALIDE";
            case "rejete", "rejected" -> "REJETE";
            case "confirme", "confirmed" -> "CONFIRME";
            case "refuse", "refused" -> "REFUSE";
            case "annule", "cancelled", "canceled" -> "ANNULE";
            case "active", "en_cours" -> "ACTIF";
            case "paused", "pause", "suspendu", "suspended" -> "EN PAUSE";
            case "completed", "termine", "terminé" -> "TERMINE";
            default -> safe(raw).toUpperCase(Locale.ROOT);
        };
    }

    private int averageScore(List<AdminOperationsService.AdminUserRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        int sum = 0;
        for (AdminOperationsService.AdminUserRow row : rows) {
            sum += row.score();
        }
        return Math.round((float) sum / rows.size());
    }

    private String shortToken(String token) {
        String safeToken = safe(token);
        if (safeToken.length() <= 12) {
            return safeToken;
        }
        return safeToken.substring(0, 6) + "..." + safeToken.substring(safeToken.length() - 4);
    }

    private String normalizePlanLabel(String raw) {
        String value = normalize(raw);
        if (value.contains("medecin")) {
            return "Medecin";
        }
        if (value.contains("pharmacien")) {
            return "Pharmacien";
        }
        if (value.contains("coach")) {
            return "Coach";
        }
        if (value.contains("nutrition")) {
            return "Nutritionniste";
        }
        return "Patient";
    }

    private <T> List<T> limitRows(List<T> rows, int max) {
        if (rows == null || rows.isEmpty() || max <= 0) {
            return List.of();
        }
        if (rows.size() <= max) {
            return rows;
        }
        return rows.subList(0, max);
    }

    private User currentAdminUser() {
        User user = AuthSession.getCurrentUser();
        if (user == null || user.getId() == null) {
            showFeedback("Session admin invalide. Reconnectez-vous.", false);
            return null;
        }
        return user;
    }

    private int parsePositiveInt(String raw, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(raw == null ? "" : raw.trim()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT)
                .replace('é', 'e')
                .replace('è', 'e')
                .replace('ê', 'e')
                .replace('à', 'a')
                .replace('ù', 'u')
                .replace('ô', 'o')
                .replace('î', 'i')
                .replace('ï', 'i');
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String formatDate(LocalDate value) {
        if (value == null) {
            return "-";
        }
        return value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String formatTime(LocalTime value) {
        if (value == null) {
            return "--:--";
        }
        return value.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "-" : DATE_TIME_FORMATTER.format(value);
    }

    private void showFeedback(String message, boolean success) {
        if (adminFeedbackLabel == null) {
            return;
        }
        adminFeedbackLabel.setText(message);
        adminFeedbackLabel.getStyleClass().removeAll("alert-success", "alert-danger");
        adminFeedbackLabel.getStyleClass().add(success ? "alert-success" : "alert-danger");
        adminFeedbackLabel.setVisible(true);
        adminFeedbackLabel.setManaged(true);
    }

    private record PageSlice<T>(
            List<T> items,
            int pageIndex,
            int total,
            int from,
            int to,
            int maxPage
    ) {
    }
}
