package com.santea.navigation;

import com.santea.Main;
import com.santea.controller.AuthBaseViewController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

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
        mainScene = buildScene(homeRoot);
        primaryStage.setScene(mainScene);
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

        mainScene = buildScene(baseRoot);
        primaryStage.setScene(mainScene);
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
        Scene scene = new Scene(root, 1320, 760);
        URL cssResource = Main.class.getResource("/com/santea/styles/auth.css");
        if (cssResource == null) {
            throw new RuntimeException("Ressource CSS introuvable: /com/santea/styles/auth.css");
        }
        scene.getStylesheets().add(cssResource.toExternalForm());
        return scene;
    }
}
