package com.santea.navigation;

import com.santea.Main;
import com.santea.controller.AppBaseViewController;
import com.santea.controller.AuthBaseViewController;
import com.santea.controller.BannedViewController;
import com.santea.controller.FeaturePageController;
import com.santea.model.User;
import com.santea.service.AuthSession;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public final class AppNavigator {
    private static final String SHORTCUTS_INSTALLED_KEY = "appNavigator.shortcutsInstalled";
    private static Stage primaryStage;
    private static Scene mainScene;
    private static Parent appBaseRoot;
    private static AppBaseViewController appBaseController;

    private AppNavigator() {
    }

    public static void initialize(Stage stage) {
        primaryStage = stage;
        primaryStage.setFullScreenExitHint("");
    }

    public static void toggleFullScreen() {
        if (primaryStage == null) {
            throw new IllegalStateException("Stage principal non initialise.");
        }
        primaryStage.setFullScreen(!primaryStage.isFullScreen());
    }

    public static boolean isFullScreen() {
        return primaryStage != null && primaryStage.isFullScreen();
    }

    public static void showHome() {
        Parent homeRoot = loadFxml("/com/santea/fxml/home.fxml");
        applyScene(buildScene(homeRoot));
    }

    public static void showLogin() {
        showAuthPage("/com/santea/fxml/login.fxml");
    }

    public static void showRegister() {
        showAuthPage("/com/santea/fxml/register.fxml");
    }

    public static void showForgotPassword() {
        showAuthPage("/com/santea/fxml/forgot_password.fxml");
    }

    public static void showFeaturePage(String title, String subtitle, List<String> lines) {
        FXMLLoader loader = getLoader("/com/santea/fxml/feature_page.fxml");
        Parent root;

        try {
            root = loader.load();
            FeaturePageController controller = loader.getController();
            controller.setData(title, subtitle, lines);
        } catch (IOException exception) {
            throw new RuntimeException("Impossible de charger la page module", exception);
        }

        showInAppBase(root);
    }

    public static void showBanned(User user) {
        FXMLLoader loader = getLoader("/com/santea/fxml/banned.fxml");
        Parent root;

        try {
            root = loader.load();
            BannedViewController controller = loader.getController();
            controller.setUser(user);
        } catch (IOException exception) {
            throw new RuntimeException("Impossible de charger la page compte suspendu", exception);
        }

        showInAppBase(root);
    }

    public static void showAdminDashboard() {
        if (!isAdminSession()) {
            showLogin();
            return;
        }
        showAppPage("/com/santea/fxml/admin_dashboard.fxml");
    }

    public static void showAdminFaceVerification() {
        if (!isAdminSession()) {
            showLogin();
            return;
        }
        showAppPage("/com/santea/fxml/admin_face_verification.fxml");
    }

    public static void showSubscriptionPage() {
        showAppPage("/com/santea/fxml/subscription.fxml");
    }

    public static void showProfileSettings() {
        showAppPage("/com/santea/fxml/profile_settings.fxml");
    }

    public static void showProfileMfa() {
        showAppPage("/com/santea/fxml/profile_mfa.fxml");
    }

    public static void showTeleconsultation() {
        showAppPage("/com/santea/fxml/teleconsultation.fxml");
    }

    public static void showTeleconsultationSchedule() {
        showAppPage("/com/santea/fxml/teleconsultation_schedule.fxml");
    }

    public static void showTeleconsultationShow(int id) {
        ModuleContext.setTeleconsultationId(id);
        showAppPage("/com/santea/fxml/teleconsultation_show.fxml");
    }

    public static void showTeleconsultationJoin(int id) {
        ModuleContext.setTeleconsultationId(id);
        showAppPage("/com/santea/fxml/teleconsultation_join.fxml");
    }

    public static void showDocumentSharing() {
        showAppPage("/com/santea/fxml/document_sharing.fxml");
    }

    public static void showDocumentUpload() {
        showAppPage("/com/santea/fxml/document_upload.fxml");
    }

    public static void showDocumentShow(int id) {
        ModuleContext.setDocumentId(id);
        showAppPage("/com/santea/fxml/document_show.fxml");
    }

    public static void showDocumentShare(int id) {
        ModuleContext.setDocumentId(id);
        showAppPage("/com/santea/fxml/document_share.fxml");
    }

    public static void showMessaging() {
        showAppPage("/com/santea/fxml/messaging.fxml");
    }

    public static void showMessageShow(int id) {
        ModuleContext.setConversationId(id);
        showAppPage("/com/santea/fxml/message_show.fxml");
    }

    public static void showAccompanimentPlans() {
        showAppPage("/com/santea/fxml/accompaniment_plans.fxml");
    }

    public static void showAccompanimentPlanShow(int id) {
        ModuleContext.setAccompanimentPlanId(id);
        showAppPage("/com/santea/fxml/accompaniment_plan_show.fxml");
    }

    public static void showAccompanimentPlanCreate(int patientUserId) {
        ModuleContext.setAccompanimentPatientUserId(patientUserId);
        showAppPage("/com/santea/fxml/accompaniment_plan_create.fxml");
    }

    public static void showAppointmentsPage() {
        showAppPage("/com/santea/fxml/appointments.fxml");
    }

    public static void showPharmacyPage() {
        showAppPage("/com/santea/fxml/pharmacy.fxml");
    }

    public static void showContentCommunityPage() {
        showAppPage("/com/santea/fxml/content_community.fxml");
    }

    public static void showAiToolsPage() {
        showAppPage("/com/santea/fxml/ai_tools.fxml");
    }

    public static void showAiDocumentScannerPage() {
        showAppPage("/com/santea/fxml/ai_document_scanner.fxml");
    }

    public static void showAiNutritionPlannerPage() {
        showAppPage("/com/santea/fxml/ai_nutrition_planner.fxml");
    }

    public static void showAiWorkoutPlannerPage() {
        showAppPage("/com/santea/fxml/ai_workout_planner.fxml");
    }

    public static void showAiResultExplainerPage() {
        showAppPage("/com/santea/fxml/ai_result_explainer.fxml");
    }

    public static void showSanteQuotidiennePage() {
        showAppPage("/com/santea/fxml/sante_quotidienne.fxml");
    }

    public static void showSanteQuotidienneAdmin() {
        if (!isAdminSession()) {
            showLogin();
            return;
        }
        showAppPage("/com/santea/fxml/sante_quotidienne_admin.fxml");
    }

    public static void showSymptomesListeAdmin() {
        if (!isAdminSession()) {
            showLogin();
            return;
        }
        showAppPage("/com/santea/fxml/symptomes_liste_admin.fxml");
    }

    public static void showSymptomesQuotidiensPage() {
        showAppPage("/com/santea/fxml/symptomes_quotidiens.fxml");
    }

    public static void showSymptomesMedecinDashboardPage() {
        showAppPage("/com/santea/fxml/symptomes_medecin_dashboard.fxml");
    }

    private static void showAppPage(String contentFxmlPath) {
        Parent contentRoot = loadFxml(contentFxmlPath);
        showInAppBase(contentRoot);
    }

    private static boolean isAdminSession() {
        User user = AuthSession.getCurrentUser();
        return user != null && "ROLE_ADMIN".equalsIgnoreCase(user.getRole());
    }

    private static void showInAppBase(Parent contentRoot) {
        ensureAppBaseLoaded();
        appBaseController.setContent(contentRoot);
        appBaseController.refreshNavbarAuthState();

        if (mainScene != null && mainScene.getRoot() == appBaseRoot) {
            return;
        }

        Scene existingScene = appBaseRoot.getScene();
        if (existingScene != null) {
            applyScene(existingScene);
            return;
        }

        applyScene(buildAppScene(appBaseRoot));
    }

    private static void ensureAppBaseLoaded() {
        if (appBaseRoot != null && appBaseController != null) {
            return;
        }

        FXMLLoader baseLoader = getLoader("/com/santea/fxml/app_base.fxml");
        try {
            appBaseRoot = baseLoader.load();
            appBaseController = baseLoader.getController();
        } catch (IOException exception) {
            throw new RuntimeException("Impossible de charger la base applicative", exception);
        }
    }

    private static void showAuthPage(String contentFxmlPath) {
        FXMLLoader baseLoader = getLoader("/com/santea/fxml/auth_base.fxml");
        Parent baseRoot;

        try {
            baseRoot = baseLoader.load();
            AuthBaseViewController authBaseController = baseLoader.getController();
            Parent contentRoot = loadFxml(contentFxmlPath);
            authBaseController.setContent(contentRoot);
        } catch (IOException exception) {
            throw new RuntimeException("Impossible de charger la base d'authentification", exception);
        }

        applyScene(buildScene(baseRoot));
    }

    private static Parent loadFxml(String resourcePath) {
        FXMLLoader loader = getLoader(resourcePath);
        try {
            System.out.println("📂 Chargement FXML: " + resourcePath);
            Parent root = loader.load();
            System.out.println("✅ FXML chargé: " + resourcePath);
            return root;
        } catch (IOException exception) {
            System.err.println("❌ IOException lors du chargement FXML " + resourcePath);
            exception.printStackTrace();
            throw new RuntimeException("Impossible de charger la vue: " + resourcePath, exception);
        } catch (Exception exception) {
            System.err.println("❌ Exception lors du chargement FXML " + resourcePath);
            exception.printStackTrace();
            throw new RuntimeException("Erreur lors du chargement de la vue: " + resourcePath, exception);
        }
    }

    private static FXMLLoader getLoader(String resourcePath) {
        System.out.println("🔍 Recherche ressource: " + resourcePath);
        URL url = Main.class.getResource(resourcePath);
        if (url == null) {
            System.err.println("❌ Ressource NOT FOUND: " + resourcePath);
            System.err.println("   Classpath location: " + Main.class.getProtectionDomain().getCodeSource().getLocation());
            throw new RuntimeException("Ressource FXML introuvable: " + resourcePath);
        }
        System.out.println("✓ Ressource trouvée: " + url);
        return new FXMLLoader(url);
    }

    private static Scene buildScene(Parent root) {
        Scene scene = new Scene(root);
        URL cssResource = Main.class.getResource("/com/santea/styles/auth.css");
        if (cssResource == null) {
            throw new RuntimeException("Ressource CSS introuvable: /com/santea/styles/auth.css");
        }
        scene.getStylesheets().add(cssResource.toExternalForm());
        return scene;
    }

    private static Scene buildAppScene(Parent root) {
        Scene scene = new Scene(root);
        URL sharedCssResource = Main.class.getResource("/com/santea/styles/auth.css");
        if (sharedCssResource == null) {
            throw new RuntimeException("Ressource CSS introuvable: /com/santea/styles/auth.css");
        }
        URL cssResource = Main.class.getResource("/com/santea/styles/app.css");
        if (cssResource == null) {
            throw new RuntimeException("Ressource CSS introuvable: /com/santea/styles/app.css");
        }
        scene.getStylesheets().add(sharedCssResource.toExternalForm());
        scene.getStylesheets().add(cssResource.toExternalForm());
        return scene;
    }

    private static void applyScene(Scene scene) {
        if (primaryStage == null) {
            throw new IllegalStateException("Stage principal non initialise.");
        }

        boolean wasFullScreen = primaryStage.isFullScreen();
        boolean wasMaximized = primaryStage.isMaximized();
        double previousWidth = primaryStage.getWidth();
        double previousHeight = primaryStage.getHeight();
        double previousX = primaryStage.getX();
        double previousY = primaryStage.getY();
        double minWidth = primaryStage.getMinWidth();
        double minHeight = primaryStage.getMinHeight();

        mainScene = scene;
        primaryStage.setScene(mainScene);
        installGlobalShortcuts(mainScene);

        if (wasFullScreen) {
            primaryStage.setFullScreen(true);
            return;
        }

        boolean invalidPreviousSize = (minWidth > 0 && previousWidth > 0 && previousWidth < minWidth)
                || (minHeight > 0 && previousHeight > 0 && previousHeight < minHeight);

        if (wasMaximized || invalidPreviousSize) {
            primaryStage.setMaximized(true);
            Platform.runLater(() -> primaryStage.setMaximized(true));
            return;
        }

        if (previousWidth > 0 && previousHeight > 0) {
            double targetWidth = minWidth > 0 ? Math.max(previousWidth, minWidth) : previousWidth;
            double targetHeight = minHeight > 0 ? Math.max(previousHeight, minHeight) : previousHeight;
            primaryStage.setWidth(targetWidth);
            primaryStage.setHeight(targetHeight);
            primaryStage.setX(previousX);
            primaryStage.setY(previousY);
        }
    }

    private static void installGlobalShortcuts(Scene scene) {
        if (scene == null) {
            return;
        }

        if (Boolean.TRUE.equals(scene.getProperties().get(SHORTCUTS_INSTALLED_KEY))) {
            return;
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F11) {
                toggleFullScreen();
                event.consume();
            }
        });
        scene.getProperties().put(SHORTCUTS_INSTALLED_KEY, Boolean.TRUE);
    }
}
