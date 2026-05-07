package com.santea.controller;

import com.santea.model.User;
import com.santea.navigation.AppNavigator;
import com.santea.service.AuthSession;
import com.santea.service.AuthorizationPolicyService;

import java.util.List;

public abstract class AiToolBaseController extends AppBaseViewController {
    private final AuthorizationPolicyService authorizationPolicyService = new AuthorizationPolicyService();

    protected boolean guardAiToolsAccess() {
        User user = AuthSession.getCurrentUser();
        if (user == null) {
            AppNavigator.showLogin();
            return false;
        }

        if (authorizationPolicyService.hasAiToolsAccess(user)) {
            return true;
        }

        String status = safeOrDefault(user.getSubscriptionStatus(), "INCONNU");
        String type = safeOrDefault(user.getSubscriptionType(), "Aucun");

        AppNavigator.showFeaturePage("Acces restreint", "Abonnement IA requis", List.of(
                "L acces aux outils IA necessite un abonnement IA actif (5 DT / mois).",
                "Statut actuel: " + status,
                "Type actuel: " + type
        ));
        return false;
    }

    private String safeOrDefault(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? fallback : trimmed;
    }
}
