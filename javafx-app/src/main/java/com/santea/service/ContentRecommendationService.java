package com.santea.service;

import com.santea.model.User;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recommandation de contenu : mots-clés (rôle, abonnement, spécialités, likes / commentaires)
 * + fallback publications récentes — aligné sur symfony-app ContentRecommendationService::buildKeywords.
 */
public final class ContentRecommendationService {
    private ContentRecommendationService() {
    }

    public static List<String> buildKeywords(User user) {
        if (user == null) {
            return List.of();
        }

        List<String> keywords = new ArrayList<>();
        String role = normalize(user.getRole()).toUpperCase(Locale.ROOT);
        String subscriptionType = normalize(user.getSubscriptionType());

        Map<String, List<String>> map = Map.of(
                "ROLE_PATIENT", List.of("patient", "bien-etre", "sante"),
                "ROLE_MEDECIN", List.of("medecin", "medical", "clinique"),
                "ROLE_COACH", List.of("sport", "coaching", "entrainement"),
                "ROLE_NUTRITIONNISTE", List.of("nutrition", "alimentaire", "dietetique")
        );

        keywords.addAll(map.getOrDefault(role, List.of()));
        if (!subscriptionType.isBlank()) {
            keywords.add(subscriptionType.toLowerCase(Locale.ROOT));
        }

        if (user.getMedecin() != null && user.getMedecin().getSpecialite() != null) {
            keywords.add(user.getMedecin().getSpecialite());
        }
        if (user.getCoachSportif() != null && user.getCoachSportif().getSpecialite() != null) {
            keywords.add(user.getCoachSportif().getSpecialite());
        }
        if (user.getNutritionniste() != null && user.getNutritionniste().getSpecialite() != null) {
            keywords.add(user.getNutritionniste().getSpecialite());
        }

        LinkedHashMap<String, Boolean> unique = new LinkedHashMap<>();
        for (String k : keywords) {
            if (k == null) {
                continue;
            }
            String lower = k.trim().toLowerCase(Locale.ROOT);
            if (!lower.isEmpty()) {
                unique.put(lower, Boolean.TRUE);
            }
        }
        return new ArrayList<>(unique.keySet());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
