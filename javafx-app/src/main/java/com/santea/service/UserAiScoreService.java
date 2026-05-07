package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;
import com.santea.model.UserScoreHistory;
import com.santea.model.UserScoreSnapshotType;
import com.santea.repository.UserScoreHistoryRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port Java/JDBC du service Symfony UserAiScoreService.
 *
 * Scores :
 *  - Activité        : 0-35  (dernière màj profil + sessions récentes 30j)
 *  - Ancienneté      : 0-25  (durée depuis inscription en mois)
 *  - Respect règles  : 0-25  (email vérifié, MFA, rappels activés, non supprimé)
 *  - Sanctions       : 0-15  (bans actifs, raison de ban, ban_until)
 *  ──────────────────────────────────────────────
 *  Total             : 0-100
 *
 * Seuil premium / mois offert : 80/100
 * Priorité support : haute ≥ 85 | normale ≥ 55 | standard < 55
 */
public class UserAiScoreService {

    private static final int PREMIUM_THRESHOLD   = 80;
    private static final int FREE_MONTH_THRESHOLD = 80;

    private final DatabaseService db;
    private final UserScoreHistoryRepository scoreHistoryRepo;

    public UserAiScoreService() {
        this.db = new DatabaseService(DatabaseConfig.fromEnvironment());
        this.scoreHistoryRepo = new UserScoreHistoryRepository(db);
    }

    // ─── Score global ─────────────────────────────────────────────────────────

    public int calculateScore(User user) {
        Map<String, Integer> bd = getBreakdown(user);
        int total = bd.values().stream().mapToInt(Integer::intValue).sum();
        return Math.max(0, Math.min(100, total));
    }

    public Map<String, Integer> getBreakdown(User user) {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("activity",          computeActivityScore(user));
        map.put("seniority",         computeSeniorityScore(user));
        map.put("ruleCompliance",    computeRuleComplianceScore(user));
        map.put("sanctionsHistory",  computeSanctionsHistoryScore(user));
        return map;
    }

    // ─── Priorité support ─────────────────────────────────────────────────────

    public String getSupportPriority(User user) {
        int score = calculateScore(user);
        if (score >= 85) return "high";
        if (score >= 55) return "normal";
        return "low";
    }

    public String getSupportPriorityLabel(User user) {
        return switch (getSupportPriority(user)) {
            case "high"   -> "Haute";
            case "normal" -> "Normale";
            default       -> "Standard";
        };
    }

    // ─── Éligibilité premium ──────────────────────────────────────────────────

    public boolean isPremiumEligible(User user) {
        return calculateScore(user) >= PREMIUM_THRESHOLD;
    }

    /**
     * Éligibilité pour le mois en cours : soit le score actuel dépasse le seuil,
     * soit il l'a déjà dépassé au moins une fois ce mois-ci (snapshot en DB).
     */
    public boolean isPremiumEligibleForCurrentMonth(User user) {
        if (user == null || user.getId() == null) return false;

        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd   = monthStart.plusMonths(1);

        if (scoreHistoryRepo.hasReachedThresholdBetween(
                user.getId(), PREMIUM_THRESHOLD, monthStart, monthEnd)) {
            return true;
        }
        return calculateScore(user) >= PREMIUM_THRESHOLD;
    }

    // ─── Message bénéfices ────────────────────────────────────────────────────

    public String getBenefitsMessage(User user) {
        int score        = calculateScore(user);
        boolean aiActive = hasActiveAiOffer(user);

        if (aiActive && score >= 85) {
            return "Excellent profil : priorité support haute et accès premium IA activé.";
        }
        if (aiActive && score >= PREMIUM_THRESHOLD) {
            return "Très bon profil : offre premium IA active et traitement support prioritaire.";
        }
        if (score >= PREMIUM_THRESHOLD) {
            return "Très bon profil : vous êtes éligible à l'offre IA. "
                    + "Activez un abonnement IA pour en bénéficier.";
        }
        if (score >= 55) {
            return "Bon profil : priorité support normale. "
                    + "Continuez votre activité pour atteindre 80/100 et obtenir 1 mois gratuit.";
        }
        return "Profil en progression : augmentez votre activité et le respect des règles "
                + "pour obtenir des offres premium.";
    }

    // ─── Résumé complet ───────────────────────────────────────────────────────

    public ProfileScoreSummary getProfileSummary(User user) {
        boolean aiActive = hasActiveAiOffer(user);
        int score = calculateScore(user);
        Map<String, Integer> bd = getBreakdown(user);

        return new ProfileScoreSummary(
                score,
                bd,
                getSupportPriority(user),
                getSupportPriorityLabel(user),
                isPremiumEligibleForCurrentMonth(user),
                aiActive,
                getBenefitsMessage(user),
                PREMIUM_THRESHOLD,
                FREE_MONTH_THRESHOLD,
                user != null ? user.getAiFreeMonthGrantedAt() : null
        );
    }

    // ─── Mois offert ──────────────────────────────────────────────────────────

    public boolean isEligibleForFreeMonth(User user) {
        return calculateScore(user) >= FREE_MONTH_THRESHOLD;
    }

    public boolean hasReceivedFreeMonth(User user) {
        return user != null && user.getAiFreeMonthGrantedAt() != null;
    }

    // ─── Historique quotidien ─────────────────────────────────────────────────

    /**
     * Enregistre le snapshot quotidien si pas encore fait aujourd'hui.
     * Retourne true si une nouvelle entrée a été insérée.
     */
    public boolean recordDailyHistory(User user) {
        if (user == null || user.getId() == null) return false;

        Map<String, Integer> bd = getBreakdown(user);
        return scoreHistoryRepo.insertSnapshot(
                user.getId(),
                calculateScore(user),
                bd.get("activity"),
                bd.get("seniority"),
                bd.get("ruleCompliance"),
                bd.get("sanctionsHistory"),
                UserScoreSnapshotType.DAILY
        );
    }

    /** Retourne les N derniers snapshots pour cet utilisateur. */
    public List<UserScoreHistory> getRecentHistory(User user, int limit) {
        if (user == null || user.getId() == null) return List.of();
        return scoreHistoryRepo.findRecentForUser(user.getId(), limit);
    }

    // ─── Calculs internes ─────────────────────────────────────────────────────

    /**
     * Activité (max 35 pts) :
     *   - Dernière mise à jour profil    : ≤3j→20, ≤14j→14, ≤30j→8, sinon→4
     *   - Sessions récentes (30j)        : ≥20→15, ≥8→10, ≥3→6, ≥1→3
     */
    private int computeActivityScore(User user) {
        if (user == null) return 0;
        int score = 0;

        // Dernière mise à jour du profil
        if (user.getUpdatedAt() != null) {
            long days = ChronoUnit.DAYS.between(user.getUpdatedAt(), LocalDateTime.now());
            if (days < 0) days = -days;
            if      (days <= 3)  score += 20;
            else if (days <= 14) score += 14;
            else if (days <= 30) score += 8;
            else                 score += 4;
        }

        // Sessions récentes (30 derniers jours)
        int recentSessions = countRecentSessions(user);
        if      (recentSessions >= 20) score += 15;
        else if (recentSessions >= 8)  score += 10;
        else if (recentSessions >= 3)  score += 6;
        else if (recentSessions >= 1)  score += 3;

        return Math.min(35, score);
    }

    /**
     * Ancienneté (max 25 pts) :
     *   ≥24 mois→25, ≥12→20, ≥6→14, ≥3→9, sinon→5
     */
    private int computeSeniorityScore(User user) {
        if (user == null || user.getCreatedAt() == null) return 0;
        long months = ChronoUnit.MONTHS.between(user.getCreatedAt(), LocalDateTime.now());
        if      (months >= 24) return 25;
        else if (months >= 12) return 20;
        else if (months >= 6)  return 14;
        else if (months >= 3)  return 9;
        return 5;
    }

    /**
     * Respect des règles (max 25 pts) :
     *   email vérifié +10, MFA activé +8, rappels +4, non supprimé +3
     */
    private int computeRuleComplianceScore(User user) {
        if (user == null) return 0;
        int score = 0;
        if (Boolean.TRUE.equals(user.getEmailVerified()))   score += 10;
        if (Boolean.TRUE.equals(user.getMfaEnabled()))      score += 8;
        if (Boolean.TRUE.equals(user.getReminderEnabled())) score += 4;
        if (user.getDeletedAt() == null)                     score += 3;
        return Math.min(25, score);
    }

    /**
     * Historique sanctions (max 15 pts) :
     *   - Banni effectivement → 0
     *   - isBanned → -10
     *   - banReason non nul → -3
     *   - banUntil non nul → -2
     */
    private int computeSanctionsHistoryScore(User user) {
        if (user == null) return 0;
        // Banni effectif (banned ET banUntil dans le futur OU pas de banUntil)
        boolean bannedEffective = Boolean.TRUE.equals(user.getIsBanned())
                && (user.getBanUntil() == null || user.getBanUntil().isAfter(LocalDateTime.now()));
        if (bannedEffective) return 0;

        int score = 15;
        if (Boolean.TRUE.equals(user.getIsBanned()))                          score -= 10;
        if (user.getBanReason() != null && !user.getBanReason().isBlank())    score -= 3;
        if (user.getBanUntil() != null)                                        score -= 2;
        return Math.max(0, score);
    }

    // ─── Helpers DB ───────────────────────────────────────────────────────────

    private int countRecentSessions(User user) {
        String sql = "SELECT COUNT(*) FROM user_sessions "
                + "WHERE user_id = ? AND last_activity_at >= ?";
        LocalDateTime since = LocalDateTime.now().minusDays(30);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            ps.setObject(2, since);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            // Colonne ou table absente → 0 session connue
            return 0;
        }
    }

    private boolean hasActiveAiOffer(User user) {
        if (user == null) return false;
        // Vérifie abonnement actif de type AI_TOOLS dans la table abonnements
        String sql = "SELECT COUNT(*) FROM abonnement "
                + "WHERE user_id = ? AND type = 'AI_TOOLS' "
                + "AND status = 'ACTIVE' "
                + "AND (end_date IS NULL OR end_date > NOW())";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, user.getId());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            return false;
        }
    }

    // ─── Record résumé ────────────────────────────────────────────────────────

    public record ProfileScoreSummary(
            int score,
            Map<String, Integer> breakdown,
            String supportPriority,
            String supportPriorityLabel,
            boolean premiumEligible,
            boolean aiOfferActive,
            String benefitsMessage,
            int premiumThreshold,
            int freeMonthThreshold,
            LocalDateTime freeMonthGrantedAt
    ) {}
}
