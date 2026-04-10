package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

public class StripeCheckoutService {
    private final DatabaseService databaseService;
    private final SubscriptionService subscriptionService;
    private final InvoiceService invoiceService;

    public StripeCheckoutService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
        this.subscriptionService = new SubscriptionService();
        this.invoiceService = new InvoiceService();
    }

    public CheckoutResult createCheckoutSession(User user, String planType, BigDecimal amount, String currency) {
        if (user == null || user.getId() == null) {
            return CheckoutResult.failure("Session utilisateur invalide.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CheckoutResult.failure("Montant de paiement invalide.");
        }

        if (!databaseService.canConnect()) {
            return CheckoutResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        ensurePaymentsTable();

        String paymentIntent = "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String sessionId = "cs_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);

        String sql = "INSERT INTO stripe_payments (payment_intent_id, session_id, user_id, plan_type, amount, currency, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setString(1, paymentIntent);
            statement.setString(2, sessionId);
            statement.setInt(3, user.getId());
            statement.setString(4, planType);
            statement.setBigDecimal(5, amount);
            statement.setString(6, safeCurrency(currency));
            statement.setTimestamp(7, now);
            statement.setTimestamp(8, now);
            statement.executeUpdate();
            return CheckoutResult.success(sessionId, paymentIntent, "Session Stripe creee.");
        } catch (SQLException exception) {
            return CheckoutResult.failure("Creation session Stripe impossible: " + exception.getMessage());
        }
    }

    public WebhookResult processWebhook(String sessionId, String eventType, String signature) {
        if (sessionId == null || sessionId.isBlank()) {
            return WebhookResult.failure("Session Stripe manquante.");
        }

        if (!databaseService.canConnect()) {
            return WebhookResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        ensurePaymentsTable();
        PaymentRow row = loadPayment(sessionId);
        if (row == null) {
            return WebhookResult.failure("Paiement introuvable pour la session fournie.");
        }

        if (!isSignatureValid(sessionId, eventType, signature)) {
            return WebhookResult.failure("Signature webhook Stripe invalide.");
        }

        if ("checkout.session.completed".equalsIgnoreCase(eventType) || "payment_intent.succeeded".equalsIgnoreCase(eventType)) {
            updatePaymentStatus(sessionId, "succeeded");

            SubscriptionService.ActionResult action = subscriptionService.activatePaidSubscription(
                    row.user,
                    row.planType,
                    row.sessionId,
                    row.amount,
                    row.currency
            );

            if (!action.success()) {
                return WebhookResult.failure("Paiement confirme, mais activation abonnement echouee: " + action.message());
            }

            InvoiceService.InvoiceResult invoiceResult = invoiceService.createInvoice(
                    row.user.getId(),
                    action.abonnementId(),
                    row.planType,
                    row.amount,
                    row.currency
            );

            if (!invoiceResult.success()) {
                return WebhookResult.failure("Paiement confirme, abonnement actif, mais facture echouee: " + invoiceResult.message());
            }

            return WebhookResult.success("Webhook traite: abonnement active et facture generee.", invoiceResult.pdfPath());
        }

        if ("payment_intent.payment_failed".equalsIgnoreCase(eventType)) {
            updatePaymentStatus(sessionId, "failed");
            return WebhookResult.success("Paiement marque en echec.", "");
        }

        return WebhookResult.success("Evenement webhook ignore (non bloquant).", "");
    }

    public String signPayloadForLocalWebhook(String sessionId, String eventType) {
        String payload = safe(sessionId) + ":" + safe(eventType);
        String secret = webhookSecret();
        if (secret.isBlank()) {
            return "dev-signature";
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            return "";
        }
    }

    private void ensurePaymentsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS stripe_payments ("
                + "id INT AUTO_INCREMENT PRIMARY KEY,"
                + "payment_intent_id VARCHAR(120) NOT NULL,"
                + "session_id VARCHAR(120) NOT NULL,"
                + "user_id INT NOT NULL,"
                + "plan_type VARCHAR(64) NOT NULL,"
                + "amount NUMERIC(10,2) NOT NULL,"
                + "currency VARCHAR(8) NOT NULL,"
                + "status VARCHAR(24) NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "updated_at DATETIME NOT NULL"
                + ")";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException ignored) {
        }
    }

    private PaymentRow loadPayment(String sessionId) {
        String sql = "SELECT sp.user_id, sp.session_id, sp.plan_type, sp.amount, sp.currency, u.role, u.subscription_status, u.subscription_type "
                + "FROM stripe_payments sp JOIN users u ON u.id = sp.user_id WHERE sp.session_id = ? LIMIT 1";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                User user = new User();
                user.setId(rs.getInt("user_id"));
                user.setRole(rs.getString("role"));
                user.setSubscriptionStatus(rs.getString("subscription_status"));
                user.setSubscriptionType(rs.getString("subscription_type"));

                return new PaymentRow(
                        user,
                        rs.getString("session_id"),
                        rs.getString("plan_type"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency")
                );
            }
        } catch (SQLException exception) {
            return null;
        }
    }

    private void updatePaymentStatus(String sessionId, String status) {
        String sql = "UPDATE stripe_payments SET status = ?, updated_at = ? WHERE session_id = ?";
        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(3, sessionId);
            statement.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private boolean isSignatureValid(String sessionId, String eventType, String signature) {
        String expected = signPayloadForLocalWebhook(sessionId, eventType);
        if (expected.isBlank()) {
            return false;
        }
        return expected.equals(signature);
    }

    private String safeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "EUR";
        }
        return currency.trim().toUpperCase();
    }

    private String webhookSecret() {
        String value = System.getenv("STRIPE_WEBHOOK_SECRET");
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record PaymentRow(User user, String sessionId, String planType, BigDecimal amount, String currency) {
    }

    public record CheckoutResult(boolean success, String sessionId, String paymentIntentId, String message) {
        public static CheckoutResult success(String sessionId, String paymentIntentId, String message) {
            return new CheckoutResult(true, sessionId, paymentIntentId, message);
        }

        public static CheckoutResult failure(String message) {
            return new CheckoutResult(false, "", "", message);
        }
    }

    public record WebhookResult(boolean success, String message, String invoicePdfPath) {
        public static WebhookResult success(String message, String invoicePdfPath) {
            return new WebhookResult(true, message, invoicePdfPath == null ? "" : invoicePdfPath);
        }

        public static WebhookResult failure(String message) {
            return new WebhookResult(false, message, "");
        }
    }
}
