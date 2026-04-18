package com.santea.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import java.net.URL;
import java.util.ResourceBundle;

public class AppBaseViewController implements Initializable {
    @FXML
    private StackPane contentContainer;

    @FXML
    private HomeViewController navbarIncludeController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Base initialization - override in subclasses
    }

    public void setContent(Parent content) {
        if (contentContainer != null) {
            contentContainer.getChildren().clear();
            if (content != null) {
                if (content instanceof Region region) {
                    region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                }

                Node nodeToDisplay;
                if (content instanceof ScrollPane scrollPane) {
                    scrollPane.setFitToWidth(true);
                    scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    nodeToDisplay = scrollPane;
                } else if (containsScrollPane(content)) {
                    nodeToDisplay = content;
                } else {
                    ScrollPane wrapper = new ScrollPane(content);
                    wrapper.getStyleClass().add("app-content-scroll");
                    wrapper.setFitToWidth(true);
                    wrapper.setPannable(true);
                    wrapper.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    wrapper.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                    nodeToDisplay = wrapper;
                }

                if (nodeToDisplay instanceof Region region) {
                    region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
                }

                StackPane.setAlignment(nodeToDisplay, Pos.TOP_LEFT);
                contentContainer.getChildren().add(nodeToDisplay);
            }
            contentContainer.requestLayout();
        }
    }

    public void refreshNavbarAuthState() {
        if (navbarIncludeController != null) {
            navbarIncludeController.refreshNavbarAuthState();
        }
    }

    private boolean containsScrollPane(Parent root) {
        for (Node child : root.getChildrenUnmodifiable()) {
            if (child instanceof ScrollPane) {
                return true;
            }
            if (child instanceof Parent nested && containsScrollPane(nested)) {
                return true;
            }
        }
        return false;
    }
}
