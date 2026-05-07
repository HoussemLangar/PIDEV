package com.santea.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;

import java.util.ArrayList;
import java.util.List;

public final class ScrollSupport {
    private static final double SPEED = 0.0032;

    private ScrollSupport() {
    }

    public static Parent wrapOrEnhance(Parent root) {
        if (root == null) {
            return null;
        }

        List<ScrollPane> panes = new ArrayList<>();
        collectScrollPanes(root, panes);

        if (panes.isEmpty()) {
            ScrollPane wrapper = new ScrollPane(root);
            wrapper.setFitToWidth(true);
            wrapper.setPannable(true);
            wrapper.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
            installForwardingScroll(wrapper);
            return wrapper;
        }

        for (ScrollPane pane : panes) {
            if (pane == null) {
                continue;
            }
            pane.setFitToWidth(true);
            pane.setPannable(true);
            if (pane.getVbarPolicy() == ScrollPane.ScrollBarPolicy.NEVER) {
                pane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            }
            installForwardingScroll(pane);
        }

        return root;
    }

    private static void collectScrollPanes(Node node, List<ScrollPane> out) {
        if (node instanceof ScrollPane sp) {
            out.add(sp);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectScrollPanes(child, out);
            }
        }
    }

    private static void installForwardingScroll(ScrollPane pane) {
        if (pane == null) {
            return;
        }

        // 1) Scroll on the pane itself
        pane.addEventFilter(ScrollEvent.SCROLL, event -> forwardScroll(pane, event));

        // 2) Scroll from any child node inside the pane
        Node content = pane.getContent();
        if (content != null) {
            bindRecursive(content, pane);
        }

        // 3) Scroll from the whole scene (some controls consume scroll)
        pane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            installSceneFilter(newScene, pane);
        });

        if (pane.getScene() != null) {
            installSceneFilter(pane.getScene(), pane);
        } else {
            Platform.runLater(() -> {
                Scene scene = pane.getScene();
                if (scene != null) {
                    installSceneFilter(scene, pane);
                }
            });
        }
    }

    private static void installSceneFilter(Scene scene, ScrollPane pane) {
        if (scene == null || pane == null) {
            return;
        }
        // Avoid installing multiple times for same pane+scene
        String key = "scrollSupport.installed." + System.identityHashCode(pane);
        Object already = scene.getProperties().get(key);
        if (Boolean.TRUE.equals(already)) {
            return;
        }
        scene.getProperties().put(key, Boolean.TRUE);
        scene.addEventFilter(ScrollEvent.SCROLL, event -> forwardScroll(pane, event));
    }

    private static void bindRecursive(Node node, ScrollPane pane) {
        if (node == null || pane == null) {
            return;
        }
        node.addEventFilter(ScrollEvent.SCROLL, event -> forwardScroll(pane, event));
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                bindRecursive(child, pane);
            }
        }
    }

    private static void forwardScroll(ScrollPane pane, ScrollEvent event) {
        if (pane == null || event == null) {
            return;
        }
        double delta = event.getDeltaY();
        if (Math.abs(delta) < 0.01) {
            return;
        }

        // If there's nothing to scroll, let event pass through
        if (pane.getContent() == null) {
            return;
        }

        double newValue = pane.getVvalue() - delta * SPEED;
        pane.setVvalue(Math.max(0.0, Math.min(1.0, newValue)));
        event.consume();
    }
}

