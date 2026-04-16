package com.santea.ui;

import com.santea.Main;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.util.Optional;

public final class ModalDialogs {
    private ModalDialogs() {
    }

    public static <T> Optional<T> showDialog(Dialog<T> dialog, Window owner, String... styleClasses) {
        if (dialog == null) {
            return Optional.empty();
        }

        if (owner != null) {
            dialog.initOwner(owner);
            dialog.initModality(Modality.WINDOW_MODAL);
        }

        dialog.setResizable(false);
        DialogPane pane = dialog.getDialogPane();
        applyCss(pane);
        applyStyleClasses(pane, styleClasses);
        styleDialogButtons(dialog);

        WindowSnapshot snapshot = WindowSnapshot.capture(owner);
        Optional<T> result = dialog.showAndWait();
        snapshot.restore(owner);
        return result;
    }

    private static void applyCss(DialogPane pane) {
        URL cssResource = Main.class.getResource("/com/santea/styles/auth.css");
        if (cssResource == null) {
            return;
        }
        String css = cssResource.toExternalForm();
        if (!pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
    }

    private static void applyStyleClasses(DialogPane pane, String... styleClasses) {
        if (styleClasses == null) {
            return;
        }
        for (String styleClass : styleClasses) {
            if (styleClass == null || styleClass.isBlank()) {
                continue;
            }
            if (!pane.getStyleClass().contains(styleClass)) {
                pane.getStyleClass().add(styleClass);
            }
        }
    }

    private static void styleDialogButtons(Dialog<?> dialog) {
        for (ButtonType buttonType : dialog.getDialogPane().getButtonTypes()) {
            Node button = dialog.getDialogPane().lookupButton(buttonType);
            if (button == null) {
                continue;
            }

            button.getStyleClass().add("bo-modal-btn");
            ButtonBar.ButtonData data = buttonType.getButtonData();
            if (data == ButtonBar.ButtonData.OK_DONE || data == ButtonBar.ButtonData.APPLY || data == ButtonBar.ButtonData.FINISH) {
                button.getStyleClass().add("bo-modal-btn-primary");
            } else if (data == ButtonBar.ButtonData.CANCEL_CLOSE || data == ButtonBar.ButtonData.BACK_PREVIOUS) {
                button.getStyleClass().add("bo-modal-btn-secondary");
            } else if (data == ButtonBar.ButtonData.NO || data == ButtonBar.ButtonData.LEFT) {
                button.getStyleClass().add("bo-modal-btn-danger");
            }
        }
    }

    private record WindowSnapshot(boolean maximized, double width, double height, double x, double y) {
        static WindowSnapshot capture(Window owner) {
            if (owner instanceof Stage stage) {
                return new WindowSnapshot(
                        stage.isMaximized(),
                        stage.getWidth(),
                        stage.getHeight(),
                        stage.getX(),
                        stage.getY()
                );
            }
            return new WindowSnapshot(false, -1, -1, 0, 0);
        }

        void restore(Window owner) {
            if (!(owner instanceof Stage stage)) {
                return;
            }

            if (maximized) {
                stage.setMaximized(true);
                Platform.runLater(() -> stage.setMaximized(true));
                return;
            }

            if (width > 0 && height > 0) {
                double minWidth = stage.getMinWidth() > 0 ? stage.getMinWidth() : width;
                double minHeight = stage.getMinHeight() > 0 ? stage.getMinHeight() : height;
                stage.setWidth(Math.max(width, minWidth));
                stage.setHeight(Math.max(height, minHeight));
                stage.setX(x);
                stage.setY(y);
            }
        }
    }
}
