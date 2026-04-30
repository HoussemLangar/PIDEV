package com.santea.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Lightweight host for app pages.
 *
 * The app uses a global ScrollPane for scrolling. Content is allowed to grow beyond the viewport
 * to enable native scrolling behavior.
 */
public class AppBaseViewController implements Initializable {
    @FXML
    private StackPane contentContainer;

    @FXML
    private ScrollPane appScrollPane;

    @FXML
    private HomeViewController navbarIncludeController;

    private final VBox scrollContentWrapper = new VBox();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (contentContainer != null) {
            // Allow content to grow beyond viewport for scrolling to work
            contentContainer.setMinHeight(Region.USE_PREF_SIZE);
            contentContainer.setPrefHeight(Region.USE_COMPUTED_SIZE);
            contentContainer.setMaxHeight(Double.MAX_VALUE);
        }

        if (appScrollPane != null) {
            // Always keep the outer scroll pane visible; pages may also contain their own ScrollPane.
            // Nesting scroll panes isn't ideal UX, but it prevents a blank screen when a page defines
            // an inner ScrollPane (the previous implementation hid the outer ScrollPane entirely).
            appScrollPane.setVisible(true);
            appScrollPane.setManaged(true);
            configureScrollPane(appScrollPane);
            prepareScrollWrapper();
        }
    }

    public void setContent(Parent content) {
        if (contentContainer == null) {
            return;
        }

        contentContainer.getChildren().clear();
        if (content == null) {
            if (appScrollPane != null) {
                appScrollPane.setContent(scrollContentWrapper);
            }
            contentContainer.requestLayout();
            return;
        }

        if (appScrollPane != null) {
            appScrollPane.setVisible(true);
            appScrollPane.setManaged(true);
            configureScrollPane(appScrollPane);
            prepareScrollWrapper();

            ScrollPane foundPageScrollPane = findScrollPaneById(content, "pageScrollPane");
            if (foundPageScrollPane != null) {
                // Prefer the page's own ScrollPane. Disable outer vertical scrolling to avoid conflicts.
                appScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                appScrollPane.setPannable(false);
                foundPageScrollPane.setFitToWidth(true);
                foundPageScrollPane.setFitToHeight(false);
                foundPageScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                setWrappedContent(foundPageScrollPane);
                return;
            }

            // Avoid nested ScrollPane issues: many pages already define their own ScrollPane.
            // If the page root is a ScrollPane, we host only its content in the global ScrollPane.
            if (content instanceof ScrollPane pageScrollPane) {
                var pageContent = pageScrollPane.getContent();
                if (pageContent != null) {
                    normalizeScrollableNode(pageContent);
                    setWrappedContent(pageContent);
                } else {
                    setWrappedContent(scrollContentWrapper);
                }
                return;
            }

            // Many pages are built as VBox(root) + ScrollPane(pageScrollPane). Unwrap the inner ScrollPane so
            // only the global ScrollPane handles scrolling.
            if (content instanceof VBox vboxRoot) {
                unwrapFirstDirectScrollPaneChild(vboxRoot);
            }

            normalizeScrollableNode(content);
            setWrappedContent(content);
            return;
        }

        if (content instanceof Region region) {
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            region.setMinHeight(Region.USE_PREF_SIZE);
            region.setPrefHeight(Region.USE_COMPUTED_SIZE);
        }

        StackPane.setAlignment(content, Pos.TOP_LEFT);
        contentContainer.getChildren().add(content);
        contentContainer.requestLayout();
    }

    public void refreshNavbarAuthState() {
        if (navbarIncludeController != null) {
            navbarIncludeController.refreshNavbarAuthState();
        }
    }

    private void configureScrollPane(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setPannable(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private void prepareScrollWrapper() {
        scrollContentWrapper.setFillWidth(true);
        scrollContentWrapper.setMinHeight(Region.USE_PREF_SIZE);
        scrollContentWrapper.setPrefHeight(Region.USE_COMPUTED_SIZE);
        scrollContentWrapper.setMaxHeight(Double.MAX_VALUE);
    }

    private void setWrappedContent(Node node) {
        scrollContentWrapper.getChildren().setAll(node);
        VBox.setVgrow(node, Priority.NEVER);
        appScrollPane.setContent(scrollContentWrapper);
    }

    private void normalizeScrollableNode(Node node) {
        if (node instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMinHeight(Region.USE_PREF_SIZE);
            region.setPrefHeight(Region.USE_COMPUTED_SIZE);
            // Prevent layouts from collapsing to viewport height in a ScrollPane.
            region.setMaxHeight(Region.USE_PREF_SIZE);
        }
    }

    private void unwrapFirstDirectScrollPaneChild(VBox root) {
        if (root == null) {
            return;
        }
        for (int i = 0; i < root.getChildren().size(); i++) {
            Node child = root.getChildren().get(i);
            if (child instanceof ScrollPane scrollPane) {
                Node inner = scrollPane.getContent();
                if (inner != null) {
                    root.getChildren().set(i, inner);
                }
                return;
            }
        }
    }

    private ScrollPane findScrollPaneById(Node root, String targetId) {
        if (root instanceof ScrollPane scrollPane) {
            if (targetId.equals(scrollPane.getId()) || targetId.equals(scrollPane.getProperties().get("fx:id"))) {
                return scrollPane;
            }
        }
        if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                ScrollPane found = findScrollPaneById(child, targetId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
