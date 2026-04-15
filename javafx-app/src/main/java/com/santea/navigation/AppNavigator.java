package com.santea.navigation;

import com.santea.Main;
import com.santea.controller.AppBaseViewController;
import com.santea.controller.AuthBaseViewController;
import com.santea.controller.BannedViewController;
import com.santea.controller.FeaturePageController;
import com.santea.model.User;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.List;

public final class AppNavigator {
    private static Stage primaryStage;
    private static Scene mainScene;

    private AppNavigator() {
    }

    public static void initialize(Stage stage) {
        primaryStage = stage;
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
        showAppPage("/com/santea/fxml/admin_dashboard.fxml");
    }

    public static void showAdminFaceVerification() {
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

    public static void showSanteQuotidiennePage() {
        showAppPage("/com/santea/fxml/sante_quotidienne.fxml");
    }

    private static void showAppPage(String contentFxmlPath) {
        Parent contentRoot = loadFxml(contentFxmlPath);
        showInAppBase(contentRoot);
    }

    private static void showInAppBase(Parent contentRoot) {
        FXMLLoader baseLoader = getLoader("/com/santea/fxml/app_base.fxml");
        Parent baseRoot;

        try {
            baseRoot = baseLoader.load();
            AppBaseViewController baseController = baseLoader.getController();
            baseController.setContent(contentRoot);
        } catch (IOException exception) {
            throw new RuntimeException("Impossible de charger la base applicative", exception);
        }

        applyScene(buildScene(baseRoot));
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
            return loader.load();
        } catch (IOException exception) {
            throw new RuntimeException("Impossible de charger la vue: " + resourcePath, exception);
        }
    }

    private static FXMLLoader getLoader(String resourcePath) {
        URL url = Main.class.getResource(resourcePath);
        if (url == null) {
            throw new RuntimeException("Ressource FXML introuvable: " + resourcePath);
        }
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

    private static void applyScene(Scene scene) {
        if (primaryStage == null) {
            throw new IllegalStateException("Stage principal non initialise.");
        }

        boolean wasMaximized = primaryStage.isMaximized();
        double previousWidth = primaryStage.getWidth();
        double previousHeight = primaryStage.getHeight();
        double previousX = primaryStage.getX();
        double previousY = primaryStage.getY();

        mainScene = scene;
        primaryStage.setScene(mainScene);

        if (wasMaximized) {
            primaryStage.setMaximized(true);
            return;
        }

        if (previousWidth > 0 && previousHeight > 0) {
            primaryStage.setWidth(previousWidth);
            primaryStage.setHeight(previousHeight);
            primaryStage.setX(previousX);
            primaryStage.setY(previousY);
        }
    }
}
