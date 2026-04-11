package com.santea.ui;

import com.santea.Main;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;

public final class ConfirmDialogs {
    private static final String LOGOUT_OVERLAY_KEY = "logout-confirm-overlay";

    private ConfirmDialogs() {
    }

    public static void confirmLogout(Node ownerNode, Runnable onConfirm) {
        if (ownerNode == null || ownerNode.getScene() == null) {
            if (onConfirm != null) {
                onConfirm.run();
            }
            return;
        }

        Scene scene = ownerNode.getScene();
        Parent sceneRoot = scene.getRoot();
        StackPane hostRoot;

        if (sceneRoot instanceof StackPane stackPaneRoot) {
            hostRoot = stackPaneRoot;
        } else {
            hostRoot = new StackPane();
            hostRoot.getChildren().add(sceneRoot);
            scene.setRoot(hostRoot);
        }

        if (hostRoot.getProperties().containsKey(LOGOUT_OVERLAY_KEY)) {
            return;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("logout-confirm-overlay");
        overlay.setPickOnBounds(true);
        overlay.prefWidthProperty().bind(hostRoot.widthProperty());
        overlay.prefHeightProperty().bind(hostRoot.heightProperty());

        VBox card = new VBox(12);
        card.getStyleClass().add("logout-confirm-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setFillWidth(true);
        card.setMaxWidth(420);
        card.setPrefHeight(Region.USE_COMPUTED_SIZE);
        card.setMinHeight(Region.USE_PREF_SIZE);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.CENTER);

        Label title = new Label("Se deconnecter");
        title.getStyleClass().add("logout-confirm-title");

        Label content = new Label("Voulez-vous vraiment vous deconnecter ?");
        content.getStyleClass().add("logout-confirm-content");
        content.setWrapText(true);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button cancelButton = new Button("Annuler");
        cancelButton.getStyleClass().add("logout-confirm-cancel");
        Button confirmButton = new Button("Se deconnecter");
        confirmButton.getStyleClass().add("logout-confirm-ok");
        actions.getChildren().addAll(cancelButton, confirmButton);

        card.getChildren().addAll(title, content, actions);
        overlay.getChildren().add(card);

        URL cssResource = Main.class.getResource("/com/santea/styles/auth.css");
        if (cssResource != null) {
            overlay.getStylesheets().add(cssResource.toExternalForm());
        }

        cancelButton.setOnAction(event -> removeOverlay(hostRoot, overlay));
        confirmButton.setOnAction(event -> {
            removeOverlay(hostRoot, overlay);
            if (onConfirm != null) {
                onConfirm.run();
            }
        });

        hostRoot.getProperties().put(LOGOUT_OVERLAY_KEY, Boolean.TRUE);
        hostRoot.getChildren().add(overlay);
    }

    private static void removeOverlay(StackPane hostRoot, StackPane overlay) {
        hostRoot.getChildren().remove(overlay);
        hostRoot.getProperties().remove(LOGOUT_OVERLAY_KEY);
    }
}
