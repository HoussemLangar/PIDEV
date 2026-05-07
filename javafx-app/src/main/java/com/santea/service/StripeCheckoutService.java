package com.santea.service;

import com.santea.config.DatabaseConfig;
import com.santea.model.User;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class StripeCheckoutService {
    private static final String STRIPE_API_BASE = "https://api.stripe.com/v1";
    private static final Set<String> STRIPE_SUPPORTED_CURRENCIES = new HashSet<>(Set.of(
            "usd", "eur", "gbp", "cad", "aud", "chf", "sek", "nok", "dkk", "sgd",
            "jpy", "hkd", "inr", "mxn", "brl", "pln", "ron", "czk", "zar", "aed"
    ));

    private final DatabaseService databaseService;
    private final SubscriptionService subscriptionService;
    private final InvoiceService invoiceService;
    private final EmailService emailService;

    public StripeCheckoutService() {
        this.databaseService = new DatabaseService(DatabaseConfig.fromEnvironment());
        this.subscriptionService = new SubscriptionService();
        this.invoiceService = new InvoiceService();
        this.emailService = new EmailService();
    }

    public CheckoutResult createCheckoutSession(User user, String planType, BigDecimal amount, String currency) {
        return createCheckoutSession(user, planType, amount, currency, "", "", "", "");
    }

    public HostedCheckoutResult createHostedCheckoutSession(User user, String planType, BigDecimal amount, String currency) {
        if (user == null || user.getId() == null) {
            return HostedCheckoutResult.failure("Session utilisateur invalide.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return HostedCheckoutResult.failure("Montant de paiement invalide.");
        }

        if (!databaseService.canConnect()) {
            return HostedCheckoutResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        if (stripeSecretKey().isBlank()) {
            return HostedCheckoutResult.failure("STRIPE_SECRET_KEY manquante. Configurez Stripe avant paiement.");
        }

        ensurePaymentsTable();

        String localCurrency = safeCurrency(currency);
        String stripeCurrency = resolveStripeCurrency(localCurrency);
        BigDecimal stripeAmount = convertAmountForStripe(amount, localCurrency, stripeCurrency);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("mode", "payment");
        params.put("customer_email", safe(user.getEmail()));
        params.put("success_url", "https://santea.local/stripe/success?session_id={CHECKOUT_SESSION_ID}");
        params.put("cancel_url", "https://santea.local/stripe/cancel");
        params.put("metadata[user_id]", String.valueOf(user.getId()));
        params.put("metadata[plan_type]", safe(planType));
        params.put("metadata[display_currency]", localCurrency);
        params.put("metadata[display_amount]", amount.toPlainString());
        params.put("line_items[0][quantity]", "1");
        params.put("line_items[0][price_data][currency]", stripeCurrency.toLowerCase());
        params.put("line_items[0][price_data][unit_amount]", toStripeCents(stripeAmount));
        params.put("line_items[0][price_data][product_data][name]", "Abonnement SANTEA - " + safe(planType));
        params.put("line_items[0][price_data][product_data][description]", "Paiement securise Stripe Checkout (Affichage local: " + amount + " " + localCurrency + ")");

        StripeHttpResult result = stripePost("/checkout/sessions", params);
        if (!result.success()) {
            return HostedCheckoutResult.failure("Stripe: " + result.message());
        }

        String sessionId = extractJsonString(result.body(), "id");
        String checkoutUrl = extractJsonString(result.body(), "url");
        if (sessionId.isBlank() || checkoutUrl.isBlank()) {
            return HostedCheckoutResult.failure("Reponse Stripe invalide (session/url absente).");
        }

        String sql = "INSERT INTO stripe_payments (payment_intent_id, session_id, user_id, plan_type, amount, currency, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setString(1, sessionId);
            statement.setString(2, sessionId);
            statement.setInt(3, user.getId());
            statement.setString(4, planType);
            statement.setBigDecimal(5, amount);
            statement.setString(6, safeCurrency(currency));
            statement.setTimestamp(7, now);
            statement.setTimestamp(8, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            return HostedCheckoutResult.failure("Creation session Stripe impossible: " + exception.getMessage());
        }

        return HostedCheckoutResult.success(sessionId, checkoutUrl, "Session Checkout Stripe creee.");
    }

    public CheckoutResult createCheckoutSession(
            User user,
            String planType,
            BigDecimal amount,
            String currency,
            String cardNumber,
            String expMonth,
            String expYear,
            String cvc
    ) {
        if (user == null || user.getId() == null) {
            return CheckoutResult.failure("Session utilisateur invalide.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CheckoutResult.failure("Montant de paiement invalide.");
        }

        if (!databaseService.canConnect()) {
            return CheckoutResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        if (stripeSecretKey().isBlank()) {
            return CheckoutResult.failure("STRIPE_SECRET_KEY manquante. Configurez Stripe avant paiement.");
        }

        String sanitizedCard = normalizeCardNumber(cardNumber);
        if (sanitizedCard.length() < 13 || expMonth.isBlank() || expYear.isBlank() || cvc.isBlank()) {
            return CheckoutResult.failure("Informations carte invalides pour paiement Stripe.");
        }

        ensurePaymentsTable();

        String localCurrency = safeCurrency(currency);
        String stripeCurrency = resolveStripeCurrency(localCurrency);
        BigDecimal stripeAmount = convertAmountForStripe(amount, localCurrency, stripeCurrency);

        String tokenId = createStripeCardToken(sanitizedCard, expMonth.trim(), expYear.trim(), cvc.trim());
        if (tokenId.isBlank()) {
            return CheckoutResult.failure("Stripe: creation token carte echouee.");
        }

        StripeChargeResult charge = createStripeCharge(user, planType, stripeAmount, stripeCurrency, tokenId);
        if (!charge.success()) {
            return CheckoutResult.failure("Stripe: " + charge.message());
        }

        String paymentIntent = charge.chargeId();
        String sessionId = charge.chargeId();

        String sql = "INSERT INTO stripe_payments (payment_intent_id, session_id, user_id, plan_type, amount, currency, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = databaseService.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            statement.setString(1, paymentIntent);
            statement.setString(2, sessionId);
            statement.setInt(3, user.getId());
            statement.setString(4, planType);
            statement.setBigDecimal(5, amount);
            statement.setString(6, localCurrency);
            statement.setString(7, charge.status());
            statement.setTimestamp(8, now);
            statement.setTimestamp(9, now);
            statement.executeUpdate();
            return CheckoutResult.success(sessionId, paymentIntent, "Paiement Stripe confirme.");
        } catch (SQLException exception) {
            return CheckoutResult.failure("Creation session Stripe impossible: " + exception.getMessage());
        }
    }

    public WebhookResult finalizePaidSession(String sessionId, String invoiceOutputDirectory) {
        if (sessionId == null || sessionId.isBlank()) {
            return WebhookResult.failure("Session Stripe manquante.");
        }
        if (invoiceOutputDirectory == null || invoiceOutputDirectory.isBlank()) {
            return WebhookResult.failure("Emplacement facture manquant. Veuillez choisir un dossier de sauvegarde.");
        }

        if (!databaseService.canConnect()) {
            return WebhookResult.failure("Connexion base impossible: " + databaseService.getLastConnectionError());
        }

        ensurePaymentsTable();
        PaymentRow row = loadPayment(sessionId);
        if (row == null) {
            return WebhookResult.failure("Paiement introuvable pour la session fournie.");
        }

        if (!"succeeded".equalsIgnoreCase(row.status())) {
            if (isCheckoutSessionPaid(sessionId)) {
                updatePaymentStatus(sessionId, "succeeded");
            } else {
                return WebhookResult.failure("Paiement Stripe non confirme. Statut actuel: " + row.status());
            }
        }

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
            row.currency,
            invoiceOutputDirectory
        );

        if (!invoiceResult.success()) {
            return WebhookResult.failure("Paiement confirme, abonnement actif, mais facture echouee: " + invoiceResult.message());
        }

        boolean sent = emailService.sendInvoice(
                row.user.getEmail(),
                resolveDisplayName(row.user),
                invoiceResult.invoiceNumber(),
                invoiceResult.pdfPath(),
                row.planType,
                row.amount,
                row.currency
        );

        String suffix = sent
                ? " Facture envoyee par email."
                : " Facture generee, mais envoi email echoue: " + emailService.getLastError();

        return WebhookResult.success("Paiement confirme, abonnement active." + suffix, invoiceResult.pdfPath());
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

        if (!signature.isBlank() && !isSignatureValid(sessionId, eventType, signature)) {
            return WebhookResult.failure("Signature webhook Stripe invalide.");
        }

        if ("checkout.session.completed".equalsIgnoreCase(eventType) || "payment_intent.succeeded".equalsIgnoreCase(eventType)) {
            updatePaymentStatus(sessionId, "succeeded");
            return finalizePaidSession(sessionId, "");
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
        String sql = "SELECT sp.user_id, sp.session_id, sp.plan_type, sp.amount, sp.currency, sp.status, "
            + "u.email, u.nom, u.prenom, u.role, u.subscription_status, u.subscription_type "
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
                user.setEmail(rs.getString("email"));
                user.setNom(rs.getString("nom"));
                user.setPrenom(rs.getString("prenom"));
                user.setRole(rs.getString("role"));
                user.setSubscriptionStatus(rs.getString("subscription_status"));
                user.setSubscriptionType(rs.getString("subscription_type"));

                return new PaymentRow(
                        user,
                        rs.getString("session_id"),
                        rs.getString("plan_type"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("status")
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

    private String createStripeCardToken(String cardNumber, String expMonth, String expYear, String cvc) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("card[number]", cardNumber);
        params.put("card[exp_month]", expMonth);
        params.put("card[exp_year]", expYear);
        params.put("card[cvc]", cvc);

        StripeHttpResult result = stripePost("/tokens", params);
        if (!result.success()) {
            return "";
        }
        return extractJsonString(result.body(), "id");
    }

    private StripeChargeResult createStripeCharge(User user, String planType, BigDecimal amount, String currency, String tokenId) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("amount", toStripeCents(amount));
        params.put("currency", safeCurrency(currency).toLowerCase());
        params.put("source", tokenId);
        params.put("description", "Abonnement SANTEA - " + planType);
        params.put("receipt_email", safe(user.getEmail()));
        params.put("metadata[user_id]", String.valueOf(user.getId()));
        params.put("metadata[plan_type]", safe(planType));

        StripeHttpResult result = stripePost("/charges", params);
        if (!result.success()) {
            return StripeChargeResult.failure(result.message());
        }

        String chargeId = extractJsonString(result.body(), "id");
        String status = extractJsonString(result.body(), "status");
        if (chargeId.isBlank()) {
            return StripeChargeResult.failure("Reponse Stripe invalide (charge id absent).");
        }
        if (!"succeeded".equalsIgnoreCase(status)) {
            return StripeChargeResult.failure("Paiement refuse par Stripe. Statut: " + (status.isBlank() ? "inconnu" : status));
        }

        return StripeChargeResult.success(chargeId, status);
    }

    private StripeHttpResult stripePost(String path, Map<String, String> params) {
        try {
            String secret = stripeSecretKey();
            if (secret.isBlank()) {
                return StripeHttpResult.failure("STRIPE_SECRET_KEY manquante.");
            }

            String body = encodeForm(params);
            HttpURLConnection connection = (HttpURLConnection) URI.create(STRIPE_API_BASE + path).toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Authorization", "Bearer " + secret);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setDoOutput(true);

            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(payload);

            int statusCode = connection.getResponseCode();
            String responseBody;
            try (InputStream stream = statusCode >= 200 && statusCode < 300 ? connection.getInputStream() : connection.getErrorStream()) {
                responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (statusCode >= 200 && statusCode < 300) {
                return StripeHttpResult.success(responseBody);
            }

            String error = extractNestedErrorMessage(responseBody);
            String message = error.isBlank() ? "HTTP " + statusCode : error;
            return StripeHttpResult.failure(message);
        } catch (Exception exception) {
            return StripeHttpResult.failure("Erreur appel Stripe: " + exception.getMessage());
        }
    }

    private boolean isCheckoutSessionPaid(String sessionId) {
        StripeHttpResult result = stripeGet("/checkout/sessions/" + sessionId);
        if (!result.success()) {
            return false;
        }

        String paymentStatus = extractJsonString(result.body(), "payment_status");
        return "paid".equalsIgnoreCase(paymentStatus);
    }

    private StripeHttpResult stripeGet(String path) {
        try {
            String secret = stripeSecretKey();
            if (secret.isBlank()) {
                return StripeHttpResult.failure("STRIPE_SECRET_KEY manquante.");
            }

            HttpURLConnection connection = (HttpURLConnection) URI.create(STRIPE_API_BASE + path).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(20000);
            connection.setRequestProperty("Authorization", "Bearer " + secret);

            int statusCode = connection.getResponseCode();
            String responseBody;
            try (InputStream stream = statusCode >= 200 && statusCode < 300 ? connection.getInputStream() : connection.getErrorStream()) {
                responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }

            if (statusCode >= 200 && statusCode < 300) {
                return StripeHttpResult.success(responseBody);
            }

            String error = extractNestedErrorMessage(responseBody);
            String message = error.isBlank() ? "HTTP " + statusCode : error;
            return StripeHttpResult.failure(message);
        } catch (Exception exception) {
            return StripeHttpResult.failure("Erreur appel Stripe: " + exception.getMessage());
        }
    }

    private String encodeForm(Map<String, String> params) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            pairs.add(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(), StandardCharsets.UTF_8));
        }
        return String.join("&", pairs);
    }

    private String toStripeCents(BigDecimal amount) {
        BigDecimal cents = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP);
        return cents.toPlainString();
    }

    private String normalizeCardNumber(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private String extractJsonString(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractNestedErrorMessage(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile("\\\"error\\\"\\s*:\\s*\\{[\\s\\S]*?\\\"message\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String resolveDisplayName(User user) {
        if (user == null) {
            return "Utilisateur SANTEA";
        }
        String full = (safe(user.getNom()) + " " + safe(user.getPrenom())).trim();
        return full.isBlank() ? "Utilisateur SANTEA" : full;
    }

    private String stripeSecretKey() {
        String value = System.getenv("STRIPE_SECRET_KEY");
        if (value != null && !value.trim().isBlank()) {
            return value.trim();
        }

        return loadStripeSecretFromSymfonyEnv();
    }

    private String resolveStripeCurrency(String localCurrency) {
        String normalized = safeCurrency(localCurrency).toLowerCase();
        if (STRIPE_SUPPORTED_CURRENCIES.contains(normalized)) {
            return normalized.toUpperCase();
        }

        String configured = System.getenv("STRIPE_CURRENCY");
        if (configured != null && !configured.isBlank()) {
            String cfg = configured.trim().toLowerCase();
            if (STRIPE_SUPPORTED_CURRENCIES.contains(cfg)) {
                return cfg.toUpperCase();
            }
        }

        if ("tnd".equalsIgnoreCase(normalized)) {
            return "USD";
        }

        return "USD";
    }

    private BigDecimal convertAmountForStripe(BigDecimal localAmount, String localCurrency, String stripeCurrency) {
        if (localAmount == null) {
            return BigDecimal.ZERO;
        }

        String local = safeCurrency(localCurrency);
        String stripe = safeCurrency(stripeCurrency);
        if (local.equalsIgnoreCase(stripe)) {
            return localAmount.setScale(2, RoundingMode.HALF_UP);
        }

        if ("TND".equalsIgnoreCase(local) && "USD".equalsIgnoreCase(stripe)) {
            String rateRaw = System.getenv("STRIPE_TND_TO_USD_RATE");
            BigDecimal rate;
            try {
                rate = (rateRaw == null || rateRaw.isBlank()) ? new BigDecimal("0.36") : new BigDecimal(rateRaw.trim());
            } catch (Exception ignored) {
                rate = new BigDecimal("0.36");
            }
            return localAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }

        return localAmount.setScale(2, RoundingMode.HALF_UP);
    }

    private String loadStripeSecretFromSymfonyEnv() {
        Path symfonyRoot = Path.of("..", "symfony-app").normalize();
        List<String> candidates = List.of(".env.local", ".env", ".env.dev", ".env.dev.local");

        for (String fileName : candidates) {
            Path file = symfonyRoot.resolve(fileName);
            if (!Files.exists(file)) {
                continue;
            }

            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String trimmed = line == null ? "" : line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }

                    int idx = trimmed.indexOf('=');
                    String key = trimmed.substring(0, idx).trim();
                    if (!"STRIPE_SECRET_KEY".equals(key)) {
                        continue;
                    }

                    String rawValue = trimmed.substring(idx + 1).trim();
                    if ((rawValue.startsWith("\"") && rawValue.endsWith("\"")) || (rawValue.startsWith("'") && rawValue.endsWith("'"))) {
                        rawValue = rawValue.substring(1, rawValue.length() - 1);
                    }

                    if (!rawValue.isBlank()) {
                        return rawValue;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return "";
    }

    private record PaymentRow(User user, String sessionId, String planType, BigDecimal amount, String currency, String status) {
    }

    private record StripeHttpResult(boolean success, String body, String message) {
        static StripeHttpResult success(String body) {
            return new StripeHttpResult(true, body == null ? "" : body, "");
        }

        static StripeHttpResult failure(String message) {
            return new StripeHttpResult(false, "", message == null ? "Erreur Stripe" : message);
        }
    }

    private record StripeChargeResult(boolean success, String chargeId, String status, String message) {
        static StripeChargeResult success(String chargeId, String status) {
            return new StripeChargeResult(true, chargeId, status, "");
        }

        static StripeChargeResult failure(String message) {
            return new StripeChargeResult(false, "", "", message == null ? "Charge Stripe echouee" : message);
        }
    }

    public record CheckoutResult(boolean success, String sessionId, String paymentIntentId, String message) {
        public static CheckoutResult success(String sessionId, String paymentIntentId, String message) {
            return new CheckoutResult(true, sessionId, paymentIntentId, message);
        }

        public static CheckoutResult failure(String message) {
            return new CheckoutResult(false, "", "", message);
        }
    }

    public record HostedCheckoutResult(boolean success, String sessionId, String checkoutUrl, String message) {
        public static HostedCheckoutResult success(String sessionId, String checkoutUrl, String message) {
            return new HostedCheckoutResult(true, sessionId, checkoutUrl, message);
        }

        public static HostedCheckoutResult failure(String message) {
            return new HostedCheckoutResult(false, "", "", message);
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
