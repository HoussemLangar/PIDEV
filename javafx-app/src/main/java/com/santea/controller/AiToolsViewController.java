package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.AuthorizationPolicyService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class AiToolsViewController extends AppBaseViewController {
    private final AuthorizationPolicyService authorizationPolicyService = new AuthorizationPolicyService();

    @FXML
    private VBox lockedBanner;

    @FXML
    private VBox lockedHeroCard;

    @FXML
    private VBox toolsGrid;

    @FXML
    private Label accessInfoLabel;

    @FXML
    private Label lockDetailLabel;

    @Override
    public void initialize(java.net.URL location, java.util.ResourceBundle resources) {
        super.initialize(location, resources);

        User user = AuthSession.getCurrentUser();
        boolean hasAccess = authorizationPolicyService.hasAiToolsAccess(user);

        setVisibleManaged(lockedBanner, !hasAccess);
        setVisibleManaged(lockedHeroCard, !hasAccess);
        setVisibleManaged(toolsGrid, hasAccess);

        if (hasAccess) {
            accessInfoLabel.setText("Abonnement IA actif");
        } else {
            String status = user == null ? "INCONNU" : safe(user.getSubscriptionStatus());
            String type = user == null ? "Aucun" : safe(user.getSubscriptionType());
            accessInfoLabel.setText("Acces restreint");
            lockDetailLabel.setText("Statut: " + status + "  |  Type: " + type + "\nActivez l abonnement IA (5 DT / mois) pour debloquer tous les outils.");
        }
    }

    @FXML
    private void handleOpenSubscription() {
        AppNavigator.showSubscriptionPage();
    }

    @FXML
    private void handleOpenDocumentScanner() {
        openProtected(AppNavigator::showAiDocumentScannerPage);
    }

    @FXML
    private void handleOpenNutritionPlanner() {
        openProtected(AppNavigator::showAiNutritionPlannerPage);
    }

    @FXML
    private void handleOpenWorkoutPlanner() {
        openProtected(AppNavigator::showAiWorkoutPlannerPage);
    }

    @FXML
    private void handleOpenResultExplainer() {
        openProtected(AppNavigator::showAiResultExplainerPage);
    }

    private void openProtected(Runnable action) {
        User user = AuthSession.getCurrentUser();
        if (authorizationPolicyService.hasAiToolsAccess(user)) {
            action.run();
            return;
        }

        AppNavigator.showFeaturePage("Acces restreint", "Abonnement IA requis", java.util.List.of(
                "L acces aux Outils IA SANTEA necessite un abonnement IA actif (5 DT / mois).",
                "Activez l abonnement depuis la page Abonnement."
        ));
    }

    private void setVisibleManaged(VBox node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
