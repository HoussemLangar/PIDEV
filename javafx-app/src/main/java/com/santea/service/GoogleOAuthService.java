package com.santea.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleOAuthService {
    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String CALLBACK_PATH = "/oauth2/callback";
    private static final int DEFAULT_CALLBACK_PORT = 53682;
    private static final Duration TOKEN_TIMEOUT = Duration.ofSeconds(20);
    private static final String[] CLIENT_ID_KEYS = {
            "GOOGLE_OAUTH_CLIENT_ID",
            "GOOGLE_CLIENT_ID",
            "OAUTH_GOOGLE_CLIENT_ID",
            "GOOGLE_AUTH_CLIENT_ID"
    };
    private static final String[] CLIENT_SECRET_KEYS = {
            "GOOGLE_OAUTH_CLIENT_SECRET",
            "GOOGLE_CLIENT_SECRET",
            "OAUTH_GOOGLE_CLIENT_SECRET",
            "GOOGLE_AUTH_CLIENT_SECRET"
    };

    public AuthResult authenticate() {
        String clientId = resolveConfig(CLIENT_ID_KEYS);
        String clientSecret = resolveConfig(CLIENT_SECRET_KEYS);
        String configuredRedirect = resolveConfig("GOOGLE_OAUTH_REDIRECT_URI", "GOOGLE_REDIRECT_URI");

        if (clientId.isBlank() || clientSecret.isBlank()) {
            return AuthResult.failure("Configuration Google OAuth manquante (essayez GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET). ");
        }

        RedirectConfig redirectConfig = buildRedirectConfig(configuredRedirect);
        if (!redirectConfig.valid()) {
            return AuthResult.failure("URI de callback Google invalide. Utilisez par exemple: http://localhost:" + DEFAULT_CALLBACK_PORT + CALLBACK_PATH);
        }

        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(redirectConfig.bindHost(), redirectConfig.port()), 0);
        } catch (IOException e) {
            return AuthResult.failure("Impossible de demarrer le callback OAuth local: " + safeMessage(e));
        }

        String state = randomToken(24);
        String redirectUri = redirectConfig.redirectUri();

        CompletableFuture<Map<String, String>> callbackFuture = new CompletableFuture<>();
        server.createContext(redirectConfig.path(), exchange -> handleCallback(exchange, callbackFuture));
        server.start();

        try {
            String authUrl = GOOGLE_AUTH_ENDPOINT
                    + "?response_type=code"
                    + "&client_id=" + enc(clientId)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&scope=" + enc("openid email profile " +
                        "https://www.googleapis.com/auth/fitness.activity.read " +
                        "https://www.googleapis.com/auth/fitness.sleep.read " +
                        "https://www.googleapis.com/auth/fitness.heart_rate.read " +
                        "https://www.googleapis.com/auth/fitness.body.read")
                    + "&access_type=offline"
                    + "&prompt=select_account"
                    + "&state=" + enc(state);

            if (!openExternalBrowser(authUrl)) {
                return AuthResult.failure("Impossible d'ouvrir le navigateur pour Google Auth. Lien: " + authUrl);
            }

            Map<String, String> params = callbackFuture.get(180, TimeUnit.SECONDS);
            if (!state.equals(params.getOrDefault("state", ""))) {
                return AuthResult.failure("Etat OAuth invalide. Reessayez la connexion Google.");
            }

            String error = params.getOrDefault("error", "");
            if (!error.isBlank()) {
                return AuthResult.failure("Google OAuth annule/echoue: " + error);
            }

            String code = params.getOrDefault("code", "");
            if (code.isBlank()) {
                return AuthResult.failure("Code OAuth Google manquant.");
            }

            String tokenResponse = exchangeCodeForToken(clientId, clientSecret, code, redirectUri);
            if (tokenResponse.startsWith("__ERROR__")) {
                return AuthResult.failure(tokenResponse.substring("__ERROR__".length()));
            }

            String idToken = jsonString(tokenResponse, "id_token");
            String accessToken = jsonString(tokenResponse, "access_token");
            String refreshToken = jsonString(tokenResponse, "refresh_token");
            long expiresIn = parseLongOrDefault(jsonString(tokenResponse, "expires_in"), 3600L);
            LocalDateTime tokenExpiration = LocalDateTime.now().plusSeconds(Math.max(60L, expiresIn));
            if (idToken.isBlank()) {
                return AuthResult.failure("id_token Google absent dans la reponse OAuth.");
            }

            String payloadJson = decodeJwtPayload(idToken);
            String email = jsonString(payloadJson, "email");
            String givenName = jsonString(payloadJson, "given_name");
            String familyName = jsonString(payloadJson, "family_name");
            String fullName = jsonString(payloadJson, "name");

            if (email.isBlank()) {
                return AuthResult.failure("Google n'a pas fourni d'email.");
            }

            String nom = familyName.isBlank() ? fullName : familyName;
            String prenom = givenName;
            if (prenom.isBlank() && !fullName.isBlank()) {
                prenom = fullName;
            }

            String googleAccountId = jsonString(payloadJson, "sub");
            return AuthResult.success(
                    new GoogleProfile(email, nom, prenom, fullName),
                    accessToken,
                    refreshToken,
                    tokenExpiration,
                    googleAccountId
            );
        } catch (TimeoutException e) {
            return AuthResult.failure("Google n'a pas redirige vers l'application. Verifiez dans Google Cloud Console l'URI autorisee exacte: " + redirectUri);
        } catch (Exception e) {
            return AuthResult.failure("Echec Google OAuth: " + safeMessage(e));
        } finally {
            server.stop(0);
        }
    }

    private RedirectConfig buildRedirectConfig(String configuredRedirect) {
        if (configuredRedirect == null || configuredRedirect.isBlank()) {
            String uri = "http://localhost:" + DEFAULT_CALLBACK_PORT + CALLBACK_PATH;
            return new RedirectConfig(true, "localhost", DEFAULT_CALLBACK_PORT, CALLBACK_PATH, uri);
        }

        try {
            URI uri = URI.create(configuredRedirect.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? "" : uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();

            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return RedirectConfig.invalid();
            }
            if (host.isBlank()) {
                return RedirectConfig.invalid();
            }
            if (port <= 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            if (path == null || path.isBlank()) {
                path = CALLBACK_PATH;
            }

            String normalized = scheme + "://" + host + (("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80) ? "" : ":" + port) + path;
            return new RedirectConfig(true, host, port, path, normalized);
        } catch (Exception ignored) {
            return RedirectConfig.invalid();
        }
    }

    private void handleCallback(HttpExchange exchange, CompletableFuture<Map<String, String>> future) throws IOException {
        String query = exchange.getRequestURI() == null ? "" : exchange.getRequestURI().getRawQuery();
        Map<String, String> params = parseQuery(query);

        String html = "<html><head><meta charset=\"utf-8\"/></head><body>"
                + "<h3>Connexion Google terminee.</h3>"
                + "<p>Vous pouvez fermer cet onglet et revenir a l'application SANTEA.</p>"
                + "</body></html>";

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }

        if (!future.isDone()) {
            future.complete(params);
        }
    }

    private String exchangeCodeForToken(String clientId, String clientSecret, String code, String redirectUri) {
        try {
            String body = "code=" + enc(code)
                    + "&client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret)
                    + "&redirect_uri=" + enc(redirectUri)
                    + "&grant_type=authorization_code";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GOOGLE_TOKEN_ENDPOINT))
                    .timeout(TOKEN_TIMEOUT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "__ERROR__Google token endpoint a refuse la requete (HTTP " + response.statusCode() + "): " + truncate(response.body(), 240);
            }

            return response.body() == null ? "" : response.body();
        } catch (Exception e) {
            return "__ERROR__Erreur appel token Google: " + safeMessage(e);
        }
    }

    private String decodeJwtPayload(String jwt) {
        String[] parts = jwt == null ? new String[0] : jwt.split("\\.");
        if (parts.length < 2) {
            return "";
        }
        String payload = parts[1];
        int padding = (4 - (payload.length() % 4)) % 4;
        payload = payload + "=".repeat(padding);
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private String jsonString(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return "";
        }
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return "";
        }
        return unescapeJson(matcher.group(1));
    }

    private String unescapeJson(String value) {
        return value == null ? "" : value.replace("\\/", "/").replace("\\\"", "\"");
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isBlank()) {
            return map;
        }
        for (String pair : query.split("&")) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, idx));
            String val = decode(pair.substring(idx + 1));
            map.put(key, val);
        }
        return map;
    }

    private String resolveConfig(String... keys) {
        if (keys == null || keys.length == 0) {
            return "";
        }

        for (String key : keys) {
            String fromEnv = System.getenv(key);
            if (fromEnv != null && !fromEnv.isBlank()) {
                return fromEnv.trim();
            }
        }

        Path[] envPaths = {
                Path.of("..", "symfony-app", ".env.local"),
                Path.of("..", "symfony-app", ".env.dev.local"),
                Path.of("..", "symfony-app", ".env")
        };

        for (Path envPath : envPaths) {
            String value = readValueFromEnvFile(envPath, keys);
            if (!value.isBlank()) {
                return value;
            }
        }

        return "";
    }

    private String readValueFromEnvFile(Path envPath, String... keys) {
        if (envPath == null || !Files.exists(envPath)) {
            return "";
        }

        try {
            for (String line : Files.readAllLines(envPath, StandardCharsets.UTF_8)) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }

                int i = trimmed.indexOf('=');
                String k = trimmed.substring(0, i).trim();
                String v = trimmed.substring(i + 1).trim();

                for (String wanted : keys) {
                    if (k.equals(wanted)) {
                        return stripQuotes(v);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    private String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String randomToken(int bytes) {
        byte[] random = new byte[Math.max(8, bytes)];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private boolean openExternalBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
            String os = System.getProperty("os.name", "").toLowerCase();
            Process process;
            if (os.contains("linux")) {
                process = new ProcessBuilder("xdg-open", url).start();
            } else if (os.contains("mac")) {
                process = new ProcessBuilder("open", url).start();
            } else if (os.contains("win")) {
                process = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else {
                return false;
            }
            return process.isAlive() || process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String decode(String value) {
        if (value == null) {
            return "";
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private long parseLongOrDefault(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String safeMessage(Exception e) {
        return e == null || e.getMessage() == null || e.getMessage().isBlank()
                ? "erreur inconnue"
                : e.getMessage();
    }

    public record GoogleProfile(String email, String nom, String prenom, String fullName) {
    }

    public record AuthResult(
            boolean success,
            GoogleProfile profile,
            String message,
            String accessToken,
            String refreshToken,
            LocalDateTime tokenExpiration,
            String googleAccountId
    ) {
        public static AuthResult success(GoogleProfile profile) {
            return new AuthResult(true, profile, "Connexion Google reussie.", "", "", null, "");
        }

        public static AuthResult success(
                GoogleProfile profile,
                String accessToken,
                String refreshToken,
                LocalDateTime tokenExpiration,
                String googleAccountId
        ) {
            return new AuthResult(
                    true,
                    profile,
                    "Connexion Google reussie.",
                    accessToken == null ? "" : accessToken,
                    refreshToken == null ? "" : refreshToken,
                    tokenExpiration,
                    googleAccountId == null ? "" : googleAccountId
            );
        }

        public static AuthResult failure(String message) {
            return new AuthResult(false, null, message == null ? "Erreur OAuth" : message, "", "", null, "");
        }
    }

    private record RedirectConfig(boolean valid, String bindHost, int port, String path, String redirectUri) {
        private static RedirectConfig invalid() {
            return new RedirectConfig(false, "", 0, "", "");
        }
    }
}
