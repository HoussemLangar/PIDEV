package com.santea.service;

import com.santea.model.User;

import java.time.LocalDateTime;
import java.util.Set;

public class AuthorizationPolicyService {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private static final Set<String> PROFESSIONAL_ROLES = Set.of(
            "ROLE_MEDECIN",
            "ROLE_PHARMACIEN",
            "ROLE_COACH",
            "ROLE_NUTRITIONNISTE"
    );

        private final SubscriptionService subscriptionService = new SubscriptionService();

    public String effectiveRole(User user) {
        if (user == null) {
            return "";
        }

        String subscriptionType = safe(user.getSubscriptionType()).toUpperCase();
        if (!subscriptionType.isBlank() && !"AI_TOOLS".equals(subscriptionType)) {
            return subscriptionType;
        }

        return safe(user.getRole()).toUpperCase();
    }

    public String normalizedStatus(User user) {
        return safe(user == null ? null : user.getSubscriptionStatus()).toUpperCase();
    }

    public boolean isAdmin(User user) {
        return "ROLE_ADMIN".equalsIgnoreCase(effectiveRole(user)) || "ROLE_ADMIN".equalsIgnoreCase(safe(user == null ? null : user.getRole()));
    }

    public boolean isActive(User user) {
        if (isAdmin(user)) {
            return true;
        }
        return STATUS_ACTIVE.equals(normalizedStatus(user));
    }

    public boolean isPending(User user) {
        return STATUS_PENDING.equals(normalizedStatus(user));
    }

    public boolean isSkipped(User user) {
        return STATUS_SKIPPED.equals(normalizedStatus(user));
    }

    public boolean isExpired(User user) {
        if (user == null) {
            return false;
        }

        LocalDateTime endAt = user.getSubscriptionEndAt();
        if (endAt != null && endAt.isBefore(LocalDateTime.now()) && !isAdmin(user)) {
            return true;
        }

        return STATUS_EXPIRED.equals(normalizedStatus(user));
    }

    public boolean canCreateContent(User user) {
        if (!isActive(user)) {
            return false;
        }
        return PROFESSIONAL_ROLES.contains(effectiveRole(user));
    }

    public boolean canReservePharmacy(User user) {
        if (!isActive(user)) {
            return false;
        }
        String role = effectiveRole(user);
        return "ROLE_PATIENT".equals(role) || "ROLE_MEDECIN".equals(role);
    }

    public boolean canManagePharmacy(User user) {
        return isActive(user) && "ROLE_PHARMACIEN".equals(effectiveRole(user));
    }

    public boolean hasAiToolsAccess(User user) {
        if (isAdmin(user)) {
            return true;
        }

        if (isActive(user) && "AI_TOOLS".equalsIgnoreCase(safe(user.getSubscriptionType()))) {
            return true;
        }

        // Backward compatibility: trust active AI_TOOLS records persisted in abonnements.
        return subscriptionService.hasActiveSubscriptionOfType(user, "AI_TOOLS");
    }

    public AccessDecision decisionForProtectedFeatures(User user) {
        if (isAdmin(user)) {
            return AccessDecision.allow("Acces admin autorise.");
        }

        if (isExpired(user)) {
            return AccessDecision.deny("Abonnement expire. Veuillez renouveler votre abonnement.");
        }

        if (isPending(user)) {
            return AccessDecision.deny("Abonnement en attente. Veuillez finaliser votre souscription.");
        }

        if (isSkipped(user)) {
            return AccessDecision.deny("Mode decouverte actif: fonctionnalite premium non disponible.");
        }

        if (!isActive(user)) {
            return AccessDecision.deny("Abonnement inactif.");
        }

        return AccessDecision.allow("Acces autorise.");
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record AccessDecision(boolean allowed, String message) {
        public static AccessDecision allow(String message) {
            return new AccessDecision(true, message);
        }

        public static AccessDecision deny(String message) {
            return new AccessDecision(false, message);
        }
    }
}
