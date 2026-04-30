package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ContentCommunityService {
    private static final Set<String> PROFESSIONAL_ROLES = Set.of(
            "ROLE_MEDECIN",
            "ROLE_PHARMACIEN",
            "ROLE_COACH",
            "ROLE_NUTRITIONNISTE"
    );

    private final DatabaseService databaseService;
    private final AuthorizationPolicyService authorizationPolicyService;
    private final ForbiddenWordsFilterService forbiddenWordsFilterService;
    private final CommentModerationService commentModerationService;
    private final CommentSentimentScoringService commentSentimentScoringService;

    public ContentCommunityService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
        this.authorizationPolicyService = new AuthorizationPolicyService();
        this.forbiddenWordsFilterService = new ForbiddenWordsFilterService(loadForbiddenWords());
        this.commentModerationService = new CommentModerationService(forbiddenWordsFilterService);
        this.commentSentimentScoringService = new CommentSentimentScoringService();
        ensureTables();
    }

    public AccessDecision canAccessModule(User user) {
        if (user == null || user.getId() == null) {
            return AccessDecision.deny("Session invalide.");
        }

        if (isBannedEffective(user)) {
            return AccessDecision.deny("Compte banni. Acces refuse.");
        }

        AuthorizationPolicyService.AccessDecision policyDecision = authorizationPolicyService.decisionForProtectedFeatures(user);
        if (!policyDecision.allowed()) {
            return AccessDecision.deny(policyDecision.message());
        }

        return AccessDecision.allow("Acces autorise.");
    }

    public boolean canCreate(User user) {
        if (user == null || user.getId() == null || isBannedEffective(user)) {
            return false;
        }

        return authorizationPolicyService.canCreateContent(user);
    }

    public FeedData loadFeed(User user, FeedFilter filter) {
        if (user == null || user.getId() == null) {
            return FeedData.empty();
        }

        List<ContentSummary> all = findVisibleContents(user, filter);
        List<ContentSummary> recommended = findRecommended(user, 6);

        return new FeedData(all, recommended);
    }

    public List<HomeHighlight> loadHomeHighlights(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 8));
        if (!databaseService.canConnect()) {
            return List.of();
        }

        String sql = "SELECT c.id, c.titre, c.description, c.contenu, c.categorie, c.type "
                + "FROM contenu c "
                + "WHERE c.statut IN ('publie', 'valide') "
                + "ORDER BY COALESCE(c.date_publication, c.created_at) DESC, c.id DESC "
                + "LIMIT ?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, safeLimit);

            List<HomeHighlight> rows = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String title = safe(rs.getString("titre"));
                    String description = safe(rs.getString("description"));
                    String content = safe(rs.getString("contenu"));
                    String excerptSource = description.isBlank() ? content : description;

                    rows.add(new HomeHighlight(
                            rs.getInt("id"),
                            title,
                            shortText(excerptSource, 150),
                            safe(rs.getString("categorie")),
                            safe(rs.getString("type"))
                    ));
                }
            }

            return rows;
        } catch (SQLException exception) {
            return List.of();
        }
    }

    public ContentDetail loadDetail(User user, int contentId) {
        if (user == null || user.getId() == null || contentId <= 0) {
            return null;
        }

        ContentSummary summary = findContentSummaryById(contentId);
        if (summary == null || !canSeeContent(user, summary)) {
            return null;
        }

        List<CommentSummary> comments = findPublishedComments(contentId);
        boolean likedByCurrentUser = hasLike(user.getId(), contentId);

        return new ContentDetail(summary, comments, likedByCurrentUser);
    }

    public ActionResult createContent(User user, ContentDraft draft) {
        if (!canCreate(user)) {
            return ActionResult.failure("Vous n'avez pas le droit de creer du contenu.");
        }

        ValidationResult validation = validateDraft(draft, false);
        if (!validation.isValid()) {
            return ActionResult.failure(validation.message());
        }

        String sql = "INSERT INTO contenu (auteur_id, titre, type, description, contenu, categorie, tags, statut, date_publication, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'en_attente', NULL, ?, ?)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setInt(1, user.getId());
            statement.setString(2, normalize(draft.titre()));
            statement.setString(3, normalize(draft.type()).toLowerCase(Locale.ROOT));
            statement.setString(4, nullIfBlank(draft.description()));
            statement.setString(5, normalize(draft.contenu()));
            statement.setString(6, nullIfBlank(draft.categorie()));
            statement.setString(7, nullIfBlank(draft.tags()));
            statement.setTimestamp(8, now);
            statement.setTimestamp(9, now);
            statement.executeUpdate();

            int createdId = 0;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    createdId = keys.getInt(1);
                }
            }

            if (createdId > 0) {
                updateArticleScore(createdId, false);
            }
            return ActionResult.success("Contenu cree et place en attente de validation.", createdId);
        } catch (SQLException exception) {
            return ActionResult.failure("Creation impossible: " + safe(exception.getMessage()));
        }
    }

    public ActionResult updateContent(User user, int contentId, ContentDraft draft) {
        if (user == null || user.getId() == null || contentId <= 0) {
            return ActionResult.failure("Requete invalide.");
        }

        ContentSummary existing = findContentSummaryById(contentId);
        if (existing == null) {
            return ActionResult.failure("Contenu introuvable.");
        }

        if (!isOwner(user, existing) && !authorizationPolicyService.isAdmin(user)) {
            return ActionResult.failure("Acces refuse.");
        }

        ValidationResult validation = validateDraft(draft, true);
        if (!validation.isValid()) {
            return ActionResult.failure(validation.message());
        }

        String sql = "UPDATE contenu SET titre=?, type=?, description=?, contenu=?, categorie=?, tags=?, statut='en_attente', date_publication=NULL, updated_at=? WHERE id=?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(draft.titre()));
            statement.setString(2, normalize(draft.type()).toLowerCase(Locale.ROOT));
            statement.setString(3, nullIfBlank(draft.description()));
            statement.setString(4, normalize(draft.contenu()));
            statement.setString(5, nullIfBlank(draft.categorie()));
            statement.setString(6, nullIfBlank(draft.tags()));
            statement.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            statement.setInt(8, contentId);
            statement.executeUpdate();
            updateArticleScore(contentId, false);
            return ActionResult.success("Contenu mis a jour et repasse en attente.", contentId);
        } catch (SQLException exception) {
            return ActionResult.failure("Mise a jour impossible: " + safe(exception.getMessage()));
        }
    }

    public ActionResult deleteContent(User user, int contentId) {
        if (user == null || user.getId() == null || contentId <= 0) {
            return ActionResult.failure("Requete invalide.");
        }

        ContentSummary existing = findContentSummaryById(contentId);
        if (existing == null) {
            return ActionResult.failure("Contenu introuvable.");
        }

        if (!isOwner(user, existing) && !authorizationPolicyService.isAdmin(user)) {
            return ActionResult.failure("Acces refuse.");
        }

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM contenu WHERE id = ?")) {
            statement.setInt(1, contentId);
            statement.executeUpdate();
            return ActionResult.success("Contenu supprime.", contentId);
        } catch (SQLException exception) {
            return ActionResult.failure("Suppression impossible: " + safe(exception.getMessage()));
        }
    }

    public ActionResult toggleLike(User user, int contentId) {
        if (user == null || user.getId() == null || contentId <= 0) {
            return ActionResult.failure("Requete invalide.");
        }

        if (!canInteract(user)) {
            return ActionResult.failure("Acces interaction refuse.");
        }

        ContentSummary summary = findContentSummaryById(contentId);
        if (summary == null || !canSeeContent(user, summary)) {
            return ActionResult.failure("Contenu indisponible.");
        }

        String selectSql = "SELECT id FROM likes WHERE user_id = ? AND contenu_id = ? LIMIT 1";
        String insertSql = "INSERT INTO likes (contenu_id, user_id, created_at) VALUES (?, ?, ?)";
        String deleteSql = "DELETE FROM likes WHERE id = ?";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement select = connection.prepareStatement(selectSql)) {
            select.setInt(1, user.getId());
            select.setInt(2, contentId);

            Integer likeId = null;
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    likeId = rs.getInt("id");
                }
            }

            boolean liked;
            if (likeId == null) {
                try (PreparedStatement insert = connection.prepareStatement(insertSql)) {
                    insert.setInt(1, contentId);
                    insert.setInt(2, user.getId());
                    insert.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    insert.executeUpdate();
                }
                liked = true;
            } else {
                try (PreparedStatement delete = connection.prepareStatement(deleteSql)) {
                    delete.setInt(1, likeId);
                    delete.executeUpdate();
                }
                liked = false;
            }

            int likesCount = countLikes(contentId);
            return ActionResult.success(liked ? "Like ajoute." : "Like retire.", likesCount);
        } catch (SQLException exception) {
            return ActionResult.failure("Operation like impossible: " + safe(exception.getMessage()));
        }
    }

    public ActionResult addComment(User user, int contentId, String message) {
        if (user == null || user.getId() == null || contentId <= 0) {
            return ActionResult.failure("Requete invalide.");
        }

        if (!canInteract(user)) {
            return ActionResult.failure("Acces interaction refuse.");
        }

        ContentSummary summary = findContentSummaryById(contentId);
        if (summary == null || !canSeeContent(user, summary)) {
            return ActionResult.failure("Contenu indisponible.");
        }

        String normalized = normalize(message);
        if (normalized.length() < 3 || normalized.length() > 1000) {
            return ActionResult.failure("Le commentaire doit contenir entre 3 et 1000 caracteres.");
        }

        CommentModerationService.ModerationOutcome moderation = commentModerationService.moderate(normalized);
        if (moderation.blocked()) {
            return ActionResult.failure("Commentaire supprimé pour contenu inapproprié.");
        }

        CommentSentimentScoringService.SentimentAnalysis analysis = commentSentimentScoringService.analyze(normalized);
        double signedScore = commentSentimentScoringService.toSignedScore(analysis);
        int note = (int) Math.round(signedScore * 100.0);

        String sql = "INSERT INTO commentaires (contenu_id, user_id, commentaire, note, statut, created_at, updated_at) VALUES (?, ?, ?, ?, 'publie', ?, ?)";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setInt(1, contentId);
            statement.setInt(2, user.getId());
            statement.setString(3, normalized);
            statement.setInt(4, note);
            statement.setTimestamp(5, now);
            statement.setTimestamp(6, now);
            statement.executeUpdate();

            int createdId = 0;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    createdId = keys.getInt(1);
                }
            }

            updateArticleScore(contentId, false);
            return ActionResult.success("Commentaire ajoute.", createdId);
        } catch (SQLException exception) {
            return ActionResult.failure("Ajout commentaire impossible: " + safe(exception.getMessage()));
        }
    }

    public ActionResult deleteComment(User user, int commentId) {
        if (user == null || user.getId() == null || commentId <= 0) {
            return ActionResult.failure("Requete invalide.");
        }

        String sql = "SELECT id, user_id, contenu_id FROM commentaires WHERE id = ? LIMIT 1";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, commentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return ActionResult.failure("Commentaire introuvable.");
                }

                int authorId = rs.getInt("user_id");
                int contentId = rs.getInt("contenu_id");
                boolean admin = authorizationPolicyService.isAdmin(user);
                if (authorId != user.getId() && !admin) {
                    return ActionResult.failure("Acces refuse.");
                }

                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM commentaires WHERE id = ?")) {
                    delete.setInt(1, commentId);
                    delete.executeUpdate();
                }

                updateArticleScore(contentId, false);
                return ActionResult.success("Commentaire supprime.", commentId);
            }
        } catch (SQLException exception) {
            return ActionResult.failure("Suppression commentaire impossible: " + safe(exception.getMessage()));
        }
    }

    public List<ModerationItem> loadModerationItems(User user, ModerationFilter filter) {
        if (user == null || !authorizationPolicyService.isAdmin(user)) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.id, c.titre, c.type, c.statut, c.created_at, c.updated_at, c.auteur_id, ")
                .append("u.nom, u.prenom, u.email, ")
                .append("(SELECT COUNT(*) FROM commentaires cm WHERE cm.contenu_id = c.id) AS comments_count, ")
                .append("(SELECT COUNT(*) FROM likes l WHERE l.contenu_id = c.id) AS likes_count ")
                .append("FROM contenu c ")
                .append("LEFT JOIN users u ON u.id = c.auteur_id WHERE 1=1 ");

        List<Object> params = new ArrayList<>();
        if (filter != null) {
            if (!blank(filter.status())) {
                sql.append("AND c.statut = ? ");
                params.add(normalize(filter.status()).toLowerCase(Locale.ROOT));
            }
            if (!blank(filter.type())) {
                sql.append("AND c.type = ? ");
                params.add(normalize(filter.type()).toLowerCase(Locale.ROOT));
            }
            if (!blank(filter.query())) {
                sql.append("AND (LOWER(c.titre) LIKE ? OR LOWER(COALESCE(c.description,'')) LIKE ? OR LOWER(COALESCE(c.tags,'')) LIKE ?) ");
                String queryLike = "%" + normalize(filter.query()).toLowerCase(Locale.ROOT) + "%";
                params.add(queryLike);
                params.add(queryLike);
                params.add(queryLike);
            }
        }

        sql.append("ORDER BY c.created_at DESC LIMIT 150");

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);

            List<ModerationItem> rows = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    rows.add(new ModerationItem(
                            id,
                            safe(rs.getString("titre")),
                            safe(rs.getString("type")),
                            safe(rs.getString("statut")),
                            mapAuthorDisplay(rs.getString("nom"), rs.getString("prenom"), rs.getString("email")),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            toLocalDateTime(rs.getTimestamp("updated_at")),
                            rs.getInt("likes_count"),
                            rs.getInt("comments_count")
                    ));
                }
            }
            return rows;
        } catch (SQLException exception) {
            return List.of();
        }
    }

    public ActionResult updateContentStatus(User user, int contentId, String newStatus) {
        if (user == null || !authorizationPolicyService.isAdmin(user)) {
            return ActionResult.failure("Acces admin requis.");
        }

        String status = normalize(newStatus).toLowerCase(Locale.ROOT);
        if (!Set.of("en_attente", "publie", "valide", "rejete").contains(status)) {
            return ActionResult.failure("Statut invalide.");
        }

        String sql = "UPDATE contenu SET statut = ?, date_publication = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setString(1, status);
            if ("publie".equals(status) || "valide".equals(status)) {
                statement.setTimestamp(2, now);
            } else {
                statement.setTimestamp(2, null);
            }
            statement.setTimestamp(3, now);
            statement.setInt(4, contentId);
            int changed = statement.executeUpdate();
            if (changed == 1) {
                return ActionResult.success("Statut mis a jour.", contentId);
            }
            return ActionResult.failure("Contenu introuvable.");
        } catch (SQLException exception) {
            return ActionResult.failure("Mise a jour statut impossible: " + safe(exception.getMessage()));
        }
    }

    private List<ContentSummary> findVisibleContents(User user, FeedFilter filter) {
        if (user == null || user.getId() == null || !databaseService.canConnect()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT c.id, c.auteur_id, c.titre, c.type, c.description, c.contenu, c.categorie, c.tags, c.statut, ")
                .append("c.date_publication, c.created_at, c.updated_at, ")
                .append("u.nom, u.prenom, u.email, u.role, u.subscription_type, ")
                .append("(SELECT COUNT(*) FROM likes l WHERE l.contenu_id = c.id) AS likes_count, ")
                .append("(SELECT COUNT(*) FROM commentaires cm WHERE cm.contenu_id = c.id AND cm.statut = 'publie') AS comments_count, ")
                .append("COALESCE(s.score_article, 0) AS score_article, COALESCE(s.nb_commentaires, 0) AS score_count ")
                .append("FROM contenu c ")
                .append("LEFT JOIN users u ON u.id = c.auteur_id ")
                .append("LEFT JOIN article_scores s ON s.contenu_id = c.id WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (filter != null) {
            if (!blank(filter.type())) {
                sql.append("AND c.type = ? ");
                params.add(normalize(filter.type()).toLowerCase(Locale.ROOT));
            }
            if (!blank(filter.status())) {
                sql.append("AND c.statut = ? ");
                params.add(normalize(filter.status()).toLowerCase(Locale.ROOT));
            }
            if (!blank(filter.category())) {
                sql.append("AND LOWER(COALESCE(c.categorie,'')) = ? ");
                params.add(normalize(filter.category()).toLowerCase(Locale.ROOT));
            }
            if (!blank(filter.query())) {
                sql.append("AND (LOWER(c.titre) LIKE ? OR LOWER(COALESCE(c.description,'')) LIKE ? OR LOWER(COALESCE(c.tags,'')) LIKE ?) ");
                String queryLike = "%" + normalize(filter.query()).toLowerCase(Locale.ROOT) + "%";
                params.add(queryLike);
                params.add(queryLike);
                params.add(queryLike);
            }
            if (filter.ownerOnly()) {
                sql.append("AND c.auteur_id = ? ");
                params.add(user.getId());
            }
        }

        sql.append("ORDER BY COALESCE(s.score_article, 0) DESC, COALESCE(c.date_publication, c.created_at) DESC, c.id DESC LIMIT 200");

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);

            List<ContentSummary> all = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    ContentSummary summary = mapContentSummary(rs);
                    if (canSeeContent(user, summary)) {
                        all.add(summary);
                    }
                }
            }

            return all;
        } catch (SQLException exception) {
            return List.of();
        }
    }

    private ContentSummary findContentSummaryById(int contentId) {
        String sql = "SELECT c.id, c.auteur_id, c.titre, c.type, c.description, c.contenu, c.categorie, c.tags, c.statut, "
                + "c.date_publication, c.created_at, c.updated_at, "
                + "u.nom, u.prenom, u.email, u.role, u.subscription_type, "
                + "(SELECT COUNT(*) FROM likes l WHERE l.contenu_id = c.id) AS likes_count, "
                + "(SELECT COUNT(*) FROM commentaires cm WHERE cm.contenu_id = c.id AND cm.statut = 'publie') AS comments_count, "
                + "COALESCE(s.score_article, 0) AS score_article, COALESCE(s.nb_commentaires, 0) AS score_count "
                + "FROM contenu c "
                + "LEFT JOIN users u ON u.id = c.auteur_id "
                + "LEFT JOIN article_scores s ON s.contenu_id = c.id "
                + "WHERE c.id = ? LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, contentId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapContentSummary(rs);
            }
        } catch (SQLException exception) {
            return null;
        }
    }

    private ContentSummary mapContentSummary(ResultSet rs) throws SQLException {
        int contentId = rs.getInt("id");
        int authorId = rs.getInt("auteur_id");
        String authorRole = safe(rs.getString("subscription_type"));
        if (authorRole.isBlank()) {
            authorRole = safe(rs.getString("role"));
        }

        return new ContentSummary(
                contentId,
                rs.wasNull() ? null : authorId,
                safe(rs.getString("titre")),
                safe(rs.getString("type")),
                safe(rs.getString("description")),
                safe(rs.getString("contenu")),
                safe(rs.getString("categorie")),
                safe(rs.getString("tags")),
                safe(rs.getString("statut")),
                toLocalDateTime(rs.getTimestamp("date_publication")),
                toLocalDateTime(rs.getTimestamp("created_at")),
                toLocalDateTime(rs.getTimestamp("updated_at")),
                mapAuthorDisplay(rs.getString("nom"), rs.getString("prenom"), rs.getString("email")),
                authorRole,
                rs.getInt("likes_count"),
                rs.getInt("comments_count"),
                rs.getDouble("score_article"),
                rs.getInt("score_count")
        );
    }

    private boolean canSeeContent(User user, ContentSummary summary) {
        if (user == null || summary == null) {
            return false;
        }

        if (authorizationPolicyService.isAdmin(user)) {
            return true;
        }

        if (!authorizationPolicyService.isActive(user) || isBannedEffective(user)) {
            return false;
        }

        boolean owner = isOwner(user, summary);
        boolean published = isPublished(summary.status());

        String effectiveRole = authorizationPolicyService.effectiveRole(user);
        if ("ROLE_PATIENT".equalsIgnoreCase(effectiveRole)) {
            if (owner) {
                return true;
            }
            return published
                    && "article".equalsIgnoreCase(summary.type())
                    && isProfessionalRole(summary.authorEffectiveRole());
        }

        return owner || published;
    }

    private boolean canInteract(User user) {
        if (user == null || user.getId() == null || isBannedEffective(user)) {
            return false;
        }

        return authorizationPolicyService.isActive(user) || authorizationPolicyService.isAdmin(user);
    }

    private boolean isOwner(User user, ContentSummary summary) {
        return user != null
                && user.getId() != null
                && summary != null
                && summary.authorId() != null
                && user.getId().intValue() == summary.authorId().intValue();
    }

    private boolean isPublished(String status) {
        String normalized = normalize(status).toLowerCase(Locale.ROOT);
        return "publie".equals(normalized) || "valide".equals(normalized);
    }

    private boolean isProfessionalRole(String role) {
        return PROFESSIONAL_ROLES.contains(normalize(role).toUpperCase(Locale.ROOT));
    }

    private boolean hasLike(Integer userId, int contentId) {
        if (userId == null || contentId <= 0) {
            return false;
        }

        String sql = "SELECT id FROM likes WHERE user_id = ? AND contenu_id = ? LIMIT 1";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, contentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException exception) {
            return false;
        }
    }

    private int countLikes(int contentId) {
        String sql = "SELECT COUNT(*) AS total FROM likes WHERE contenu_id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, contentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("total") : 0;
            }
        } catch (SQLException exception) {
            return 0;
        }
    }

    private List<CommentSummary> findPublishedComments(int contentId) {
        String sql = "SELECT c.id, c.user_id, c.commentaire, c.note, c.created_at, u.nom, u.prenom, u.email "
                + "FROM commentaires c "
                + "LEFT JOIN users u ON u.id = c.user_id "
                + "WHERE c.contenu_id = ? AND c.statut = 'publie' "
                + "ORDER BY c.created_at ASC";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, contentId);
            List<CommentSummary> comments = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int note = rs.getInt("note");
                    double score = Math.max(-1.0, Math.min(1.0, ((double) note) / 100.0));
                    comments.add(new CommentSummary(
                            rs.getInt("id"),
                            rs.getInt("user_id"),
                            mapAuthorDisplay(rs.getString("nom"), rs.getString("prenom"), rs.getString("email")),
                            safe(rs.getString("commentaire")),
                            toLocalDateTime(rs.getTimestamp("created_at")),
                            score,
                            score > 0 ? "POSITIVE" : (score < 0 ? "NEGATIVE" : "NEUTRAL")
                    ));
                }
            }
            return comments;
        } catch (SQLException exception) {
            return List.of();
        }
    }

    /**
     * Comme ContentRecommendationService Symfony : requête SQL mots-clés (publié/validé ou auteur),
     * sinon fallback publications récentes (+ contenus de l'utilisateur).
     */
    private List<ContentSummary> findRecommended(User user, int limit) {
        if (user == null || user.getId() == null || !databaseService.canConnect()) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(12, limit));
        LinkedHashSet<String> keywordSet = new LinkedHashSet<>(ContentRecommendationService.buildKeywords(user));
        keywordSet.addAll(loadUserInteractionKeywords(user.getId()));
        List<String> keywords = keywordSet.stream()
                .map(k -> k.toLowerCase(Locale.ROOT).trim())
                .filter(s -> !s.isBlank())
                .toList();

        if (!keywords.isEmpty()) {
            List<ContentSummary> matched = queryRecommendedWithKeywords(user.getId(), keywords, safeLimit);
            matched = filterAccessibleRecommended(user, matched);
            if (!matched.isEmpty()) {
                return matched.stream().limit(safeLimit).toList();
            }
        }

        List<ContentSummary> fallback = queryRecommendedFallback(user.getId(), safeLimit);
        return filterAccessibleRecommended(user, fallback).stream().limit(safeLimit).toList();
    }

    private List<ContentSummary> filterAccessibleRecommended(User user, List<ContentSummary> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().filter(s -> canSeeContent(user, s)).toList();
    }

    private String sqlContentSummaryBase() {
        return "SELECT c.id, c.auteur_id, c.titre, c.type, c.description, c.contenu, c.categorie, c.tags, c.statut, "
                + "c.date_publication, c.created_at, c.updated_at, "
                + "u.nom, u.prenom, u.email, u.role, u.subscription_type, "
                + "(SELECT COUNT(*) FROM likes l WHERE l.contenu_id = c.id) AS likes_count, "
                + "(SELECT COUNT(*) FROM commentaires cm WHERE cm.contenu_id = c.id AND cm.statut = 'publie') AS comments_count, "
                + "COALESCE(s.score_article, 0) AS score_article, COALESCE(s.nb_commentaires, 0) AS score_count "
                + "FROM contenu c "
                + "LEFT JOIN users u ON u.id = c.auteur_id "
                + "LEFT JOIN article_scores s ON s.contenu_id = c.id ";
    }

    private List<ContentSummary> queryRecommendedWithKeywords(int userId, List<String> keywords, int safeLimit) {
        StringBuilder sql = new StringBuilder(sqlContentSummaryBase());
        sql.append("WHERE (c.statut = 'publie' OR c.auteur_id = ?) AND (");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("(LOWER(c.titre) LIKE ? OR LOWER(COALESCE(c.description,'')) LIKE ? OR ")
                    .append("LOWER(COALESCE(c.tags,'')) LIKE ? OR LOWER(COALESCE(c.categorie,'')) LIKE ?)");
            String like = "%" + keywords.get(i) + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append(") ORDER BY COALESCE(c.date_publication, c.created_at) DESC, c.id DESC LIMIT ?");
        params.add(safeLimit);

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            bindParams(statement, params);
            return mapContentSummaryList(statement);
        } catch (SQLException exception) {
            return List.of();
        }
    }

    private List<ContentSummary> queryRecommendedFallback(int userId, int safeLimit) {
        String sql = sqlContentSummaryBase()
                + "WHERE (c.statut = 'publie' OR c.auteur_id = ?) "
                + "ORDER BY COALESCE(c.date_publication, c.created_at) DESC, c.id DESC LIMIT ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, safeLimit);
            return mapContentSummaryList(statement);
        } catch (SQLException exception) {
            return List.of();
        }
    }

    private List<ContentSummary> mapContentSummaryList(PreparedStatement statement) throws SQLException {
        List<ContentSummary> list = new ArrayList<>();
        try (ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                list.add(mapContentSummary(rs));
            }
        }
        return list;
    }

    private List<String> loadUserInteractionKeywords(int userId) {
        String sql = "SELECT c.categorie, c.tags "
                + "FROM contenu c "
                + "WHERE c.id IN (SELECT l.contenu_id FROM likes l WHERE l.user_id = ?) "
                + "OR c.id IN (SELECT cm.contenu_id FROM commentaires cm WHERE cm.user_id = ?)";

        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String category = safe(rs.getString("categorie"));
                    if (!category.isBlank()) {
                        keywords.add(category.toLowerCase(Locale.ROOT));
                    }

                    String tags = safe(rs.getString("tags"));
                    if (!tags.isBlank()) {
                        for (String part : tags.split(",")) {
                            String tag = part == null ? "" : part.trim().toLowerCase(Locale.ROOT);
                            if (!tag.isBlank()) {
                                keywords.add(tag);
                            }
                        }
                    }
                }
            }
        } catch (SQLException ignored) {
        }

        return new ArrayList<>(keywords);
    }

    private ValidationResult validateDraft(ContentDraft draft, boolean isEdit) {
        if (draft == null) {
            return ValidationResult.invalid("Contenu invalide.");
        }

        String title = normalize(draft.titre());
        if (title.length() < 5 || title.length() > 255) {
            return ValidationResult.invalid("Le titre doit contenir entre 5 et 255 caracteres.");
        }

        String type = normalize(draft.type()).toLowerCase(Locale.ROOT);
        if (!Set.of("article", "video", "pdf", "lien").contains(type)) {
            return ValidationResult.invalid("Type de contenu invalide.");
        }

        String description = normalize(draft.description());
        if (description.length() > 500) {
            return ValidationResult.invalid("La description ne peut pas depasser 500 caracteres.");
        }

        String content = normalize(draft.contenu());
        if (content.length() < 5 || content.length() > 20000) {
            return ValidationResult.invalid("Le contenu doit contenir entre 5 et 20000 caracteres.");
        }

        if ("lien".equals(type) && !isValidUrl(content)) {
            return ValidationResult.invalid("Le lien fourni est invalide.");
        }

        return ValidationResult.ok();
    }

    private void updateArticleScore(int contentId, boolean flushIgnored) {
        if (contentId <= 0) {
            return;
        }

        String commentsSql = "SELECT note FROM commentaires WHERE contenu_id = ? AND statut = 'publie' ORDER BY created_at ASC";
        String upsertSql = "INSERT INTO article_scores (contenu_id, score_article, nb_commentaires, updated_at) "
                + "VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE score_article = VALUES(score_article), nb_commentaires = VALUES(nb_commentaires), updated_at = VALUES(updated_at)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement commentsStatement = connection.prepareStatement(commentsSql);
             PreparedStatement upsertStatement = connection.prepareStatement(upsertSql)) {
            commentsStatement.setInt(1, contentId);

            double sum = 0.0;
            int count = 0;

            try (ResultSet rs = commentsStatement.executeQuery()) {
                while (rs.next()) {
                    int noteRaw = rs.getInt("note");
                    if (rs.wasNull()) {
                        continue;
                    }

                    double signedScore = Math.max(-1.0, Math.min(1.0, noteRaw / 100.0));
                    sum += signedScore;
                    count++;
                }
            }

            double score = count > 0 ? (sum / count) : 0.0;

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            upsertStatement.setInt(1, contentId);
            upsertStatement.setDouble(2, score);
            upsertStatement.setInt(3, count);
            upsertStatement.setTimestamp(4, now);
            upsertStatement.executeUpdate();
        } catch (SQLException exception) {
            System.err.println("[ContentCommunityService] updateArticleScore error for contentId=" + contentId + ": " + exception.getMessage());
        }
    }

    private boolean isValidUrl(String rawUrl) {
        String candidate = normalize(rawUrl);
        if (candidate.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(candidate);
            String scheme = safe(uri.getScheme()).toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && !safe(uri.getHost()).isBlank();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void bindParams(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object param = params.get(i);
            int idx = i + 1;
            if (param instanceof Integer value) {
                statement.setInt(idx, value);
            } else {
                statement.setString(idx, param == null ? null : String.valueOf(param));
            }
        }
    }

    private void ensureTables() {
        String[] ddl = new String[] {
                "CREATE TABLE IF NOT EXISTS contenu ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "auteur_id INT NULL,"
                        + "titre VARCHAR(255) NOT NULL,"
                        + "type VARCHAR(50) NOT NULL,"
                        + "description TEXT NULL,"
                        + "contenu LONGTEXT NOT NULL,"
                        + "categorie VARCHAR(100) NULL,"
                        + "tags VARCHAR(255) NULL,"
                        + "statut VARCHAR(20) NOT NULL DEFAULT 'en_attente',"
                        + "date_publication DATETIME NULL,"
                        + "created_at DATETIME NULL,"
                        + "updated_at DATETIME NULL"
                        + ")",
                "CREATE TABLE IF NOT EXISTS commentaires ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "contenu_id INT NOT NULL,"
                        + "user_id INT NOT NULL,"
                        + "parent_id INT NULL,"
                        + "commentaire TEXT NOT NULL,"
                        + "note INT NULL,"
                        + "statut VARCHAR(20) NOT NULL DEFAULT 'publie',"
                        + "created_at DATETIME NULL,"
                        + "updated_at DATETIME NULL"
                        + ")",
                "CREATE TABLE IF NOT EXISTS likes ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "contenu_id INT NOT NULL,"
                        + "user_id INT NOT NULL,"
                        + "created_at DATETIME NULL,"
                        + "UNIQUE KEY uq_like_contenu_user (contenu_id, user_id)"
                        + ")",
                "CREATE TABLE IF NOT EXISTS article_scores ("
                        + "contenu_id INT PRIMARY KEY,"
                        + "score_article DOUBLE NOT NULL DEFAULT 0,"
                        + "nb_commentaires INT NOT NULL DEFAULT 0,"
                        + "updated_at DATETIME NULL"
                        + ")"
        };

        for (String sql : ddl) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.execute();
            } catch (SQLException ignored) {
            }
        }

        String[] alters = new String[] {
                "ALTER TABLE contenu ADD COLUMN IF NOT EXISTS categorie VARCHAR(100) NULL",
                "ALTER TABLE contenu ADD COLUMN IF NOT EXISTS tags VARCHAR(255) NULL",
                "ALTER TABLE contenu ADD COLUMN IF NOT EXISTS statut VARCHAR(20) NOT NULL DEFAULT 'en_attente'",
                "ALTER TABLE contenu ADD COLUMN IF NOT EXISTS date_publication DATETIME NULL",
                "ALTER TABLE commentaires ADD COLUMN IF NOT EXISTS note INT NULL",
                "ALTER TABLE commentaires ADD COLUMN IF NOT EXISTS statut VARCHAR(20) NOT NULL DEFAULT 'publie'"
        };

        for (String alter : alters) {
            try (Connection connection = databaseService.getConnection();
                 PreparedStatement statement = connection.prepareStatement(alter)) {
                statement.execute();
            } catch (SQLException ignored) {
            }
        }
    }

    private List<String> loadForbiddenWords() {
        String raw = safe(System.getenv("CONTENT_FORBIDDEN_WORDS"));
        if (raw.isBlank()) {
            raw = "spam,arnaque,haine,violence,insulte";
        }

        return java.util.Arrays.stream(raw.split(","))
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .toList();
    }

    private boolean isBannedEffective(User user) {
        if (user == null || !Boolean.TRUE.equals(user.getIsBanned())) {
            return false;
        }

        LocalDateTime banUntil = user.getBanUntil();
        if (banUntil == null) {
            return true;
        }

        return banUntil.isAfter(LocalDateTime.now());
    }

    private String mapAuthorDisplay(String nom, String prenom, String email) {
        String full = (safe(nom) + " " + safe(prenom)).trim();
        if (!full.isBlank()) {
            return full;
        }
        if (!safe(email).isBlank()) {
            return safe(email);
        }
        return "Anonyme";
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String nullIfBlank(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String shortText(String value, int max) {
        String text = safe(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "...";
    }

    private boolean blank(String value) {
        return normalize(value).isBlank();
    }

    public record FeedFilter(String type, String status, String query, String category, boolean ownerOnly) {
        public static FeedFilter defaultFilter() {
            return new FeedFilter("", "", "", "", false);
        }
    }

    public record ModerationFilter(String status, String type, String query) {
        public static ModerationFilter defaultFilter() {
            return new ModerationFilter("", "", "");
        }
    }

    public record FeedData(List<ContentSummary> items, List<ContentSummary> recommended) {
        public static FeedData empty() {
            return new FeedData(List.of(), List.of());
        }
    }

    public record ContentDetail(ContentSummary content, List<CommentSummary> comments, boolean likedByCurrentUser) {
    }

    public record ContentSummary(
            int id,
            Integer authorId,
            String title,
            String type,
            String description,
            String content,
            String category,
            String tags,
            String status,
            LocalDateTime publicationDate,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String authorDisplay,
            String authorEffectiveRole,
            int likesCount,
            int commentsCount,
            double scoreArticle,
            int scoreCount
    ) {
        public LocalDateTime publishedAtOrCreated() {
            return publicationDate != null ? publicationDate : createdAt;
        }

        public String scoreLabel() {
            return String.format(Locale.ROOT, "%.2f", scoreArticle);
        }
    }

    public record CommentSummary(
            int id,
            int userId,
            String authorDisplay,
            String message,
            LocalDateTime createdAt,
            double score,
            String sentiment
    ) {
    }

    public record ModerationItem(
            int id,
            String title,
            String type,
            String status,
            String author,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            int likesCount,
            int commentsCount
    ) {
    }

            public record HomeHighlight(
                int id,
                String title,
                String excerpt,
                String category,
                String type
            ) {
            }

    public record ContentDraft(
            String titre,
            String type,
            String description,
            String contenu,
            String categorie,
            String tags
    ) {
    }

    public record ActionResult(boolean success, String message, int numericValue) {
        public static ActionResult success(String message, int numericValue) {
            return new ActionResult(true, message, numericValue);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message, 0);
        }
    }

    public record AccessDecision(boolean allowed, String message) {
        public static AccessDecision allow(String message) {
            return new AccessDecision(true, message);
        }

        public static AccessDecision deny(String message) {
            return new AccessDecision(false, message);
        }
    }

    private record ValidationResult(boolean isValid, String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, "");
        }

        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
    }

    public static String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(value);
    }
}
