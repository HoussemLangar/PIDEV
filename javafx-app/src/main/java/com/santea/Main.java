package com.santea;

import com.santea.navigation.AppNavigator;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main extends Application {
    
    @Override
    public void start(Stage primaryStage) {
        Parent root;

        try {
            AppNavigator.initialize(primaryStage);
            AppNavigator.showHome();
        } catch (Throwable throwable) {
            String startupError = stackTraceToString(throwable);
            root = buildStartupErrorView(startupError);
            primaryStage.setScene(new javafx.scene.Scene(root, 1320, 760));
        }

        primaryStage.setTitle("SANTÉA");
        primaryStage.setMinWidth(1180);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }
    
    public static void main(String[] args) {
        if (!hasGraphicalDisplay()) {
            System.err.println("JavaFX ne peut pas ouvrir d'interface: DISPLAY/WAYLAND_DISPLAY est absent.");
            System.err.println("Lance l'application depuis une session graphique (desktop local, VNC, ou SSH -X). ");
            return;
        }
        try {
            launch(args);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private static boolean hasGraphicalDisplay() {
        String display = System.getenv("DISPLAY");
        String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
        return (display != null && !display.isBlank()) || (waylandDisplay != null && !waylandDisplay.isBlank());
    }

    private Parent buildStartupErrorView(String details) {
        Label title = new Label("Erreur de démarrage JavaFX");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label subtitle = new Label("L'application n'a pas pu charger ses ressources. Détails :");

        TextArea area = new TextArea(details);
        area.setEditable(false);
        area.setWrapText(false);
        VBox.setVgrow(area, Priority.ALWAYS);

        VBox box = new VBox(10, title, subtitle, area);
        box.setStyle("-fx-padding: 16;");
        return box;
    }

    private String stackTraceToString(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < 8) {
            builder.append(current.getClass().getName()).append(": ")
                    .append(current.getMessage() == null ? "" : current.getMessage())
                    .append("\n");
            current = current.getCause();
            depth++;
        }
        return builder.toString();
    }
}
