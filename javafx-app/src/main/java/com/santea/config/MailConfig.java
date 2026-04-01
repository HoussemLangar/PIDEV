package com.santea.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MailConfig {
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String fromName;
    private final boolean auth;
    private final boolean startTls;
    private final boolean ssl;
    private final String appBaseUrl;

    public MailConfig(
            boolean enabled,
            String host,
            int port,
            String username,
            String password,
            String fromAddress,
            String fromName,
            boolean auth,
            boolean startTls,
            boolean ssl,
            String appBaseUrl
    ) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.auth = auth;
        this.startTls = startTls;
        this.ssl = ssl;
        this.appBaseUrl = appBaseUrl;
    }

    public static MailConfig fromEnvironment() {
        Map<String, String> symfonyEnv = loadSymfonyEnvDefaults();
        String dsn = getOrDefault("MAILER_DSN", "");
        if (dsn.isBlank()) {
            dsn = symfonyEnv.getOrDefault("MAILER_DSN", "");
        }

        ParsedDsn parsedDsn = parseMailerDsn(dsn);

        String host = getEnvOrSymfonyOrDefault("MAIL_HOST", symfonyEnv, parsedDsn.host);
        int port = parsePort(getEnvOrSymfonyOrDefault("MAIL_PORT", symfonyEnv, parsedDsn.port));
        String username = getEnvOrSymfonyOrDefault("MAIL_USERNAME", symfonyEnv, parsedDsn.username);
        String password = getEnvOrSymfonyOrDefault("MAIL_PASSWORD", symfonyEnv, parsedDsn.password);
        String fromAddress = getEnvOrSymfonyOrDefault("MAIL_FROM", symfonyEnv, username);
        String fromName = getEnvOrSymfonyOrDefault("MAIL_FROM_NAME", symfonyEnv, "SANTEA");

        boolean auth = parseBoolean(
            getEnvOrSymfonyOrDefault("MAIL_AUTH", symfonyEnv, String.valueOf(parsedDsn.auth)),
            parsedDsn.auth
        );
        boolean startTls = parseBoolean(
            getEnvOrSymfonyOrDefault("MAIL_STARTTLS", symfonyEnv, String.valueOf(parsedDsn.startTls)),
            parsedDsn.startTls
        );
        boolean ssl = parseBoolean(
            getEnvOrSymfonyOrDefault("MAIL_SSL", symfonyEnv, String.valueOf(parsedDsn.ssl)),
            parsedDsn.ssl
        );

        boolean enabledByEnv = parseBoolean(getEnvOrSymfonyOrDefault("MAIL_ENABLED", symfonyEnv, ""), false);
        String appBaseUrl = getEnvOrSymfonyOrDefault("APP_BASE_URL", symfonyEnv, "https://santea.tn:8443/");

        boolean autoEnabled = !host.isBlank() && !fromAddress.isBlank();
        boolean enabled = enabledByEnv || autoEnabled;

        return new MailConfig(enabled, host, port, username, password, fromAddress, fromName, auth, startTls, ssl, appBaseUrl);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String fromAddress() {
        return fromAddress;
    }

    public String fromName() {
        return fromName;
    }

    public boolean auth() {
        return auth;
    }

    public boolean startTls() {
        return startTls;
    }

    public boolean ssl() {
        return ssl;
    }

    public String appBaseUrl() {
        return appBaseUrl;
    }

    public String debugSummary() {
        String hiddenUser = username == null || username.isBlank() ? "<vide>" : username;
        return "enabled=" + enabled
                + ", host=" + host
                + ", port=" + port
                + ", auth=" + auth
                + ", startTls=" + startTls
                + ", ssl=" + ssl
                + ", from=" + fromAddress
                + ", user=" + hiddenUser;
    }

    private static String getOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String getEnvOrSymfonyOrDefault(String key, Map<String, String> symfonyEnv, String fallback) {
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String symfonyValue = symfonyEnv.get(key);
        if (symfonyValue != null && !symfonyValue.isBlank()) {
            return symfonyValue.trim();
        }

        return fallback;
    }

    private static int parsePort(String rawPort) {
        try {
            return Integer.parseInt(rawPort == null ? "587" : rawPort.trim());
        } catch (NumberFormatException exception) {
            return 587;
        }
    }

    private static boolean parseBoolean(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String normalized = raw.trim().toLowerCase();
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("yes") || normalized.equals("on");
    }

    private static ParsedDsn parseMailerDsn(String dsn) {
        ParsedDsn defaults = ParsedDsn.defaults();

        if (dsn == null || dsn.isBlank()) {
            return defaults;
        }

        if (dsn.startsWith("null://")) {
            return defaults;
        }

        try {
            URI uri = URI.create(dsn.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            String host = uri.getHost() == null ? defaults.host : uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : parsePort(defaults.port);

            String userInfo = uri.getUserInfo();
            String username = "";
            String password = "";

            if (userInfo != null && !userInfo.isBlank()) {
                String[] chunks = userInfo.split(":", 2);
                username = decode(chunks[0]);
                if (chunks.length > 1) {
                    password = decode(chunks[1]);
                }
            } else {
                // Symfony DSN often contains raw email in username (user@gmail.com) which URI can fail to expose as userInfo.
                String rawAuthority = uri.getRawAuthority();
                if (rawAuthority != null && rawAuthority.contains("@")) {
                    int atIndex = rawAuthority.lastIndexOf('@');
                    if (atIndex > 0) {
                        String credentials = rawAuthority.substring(0, atIndex);
                        String[] chunks = credentials.split(":", 2);
                        username = decode(chunks[0]);
                        if (chunks.length > 1) {
                            password = decode(chunks[1]);
                        }
                    }
                }
            }

            Map<String, String> query = parseQuery(uri.getRawQuery());

            boolean ssl = scheme.equals("smtps") || scheme.endsWith("+smtps");
            boolean startTls = !ssl;
            boolean auth = true;

            if (query.containsKey("encryption")) {
                String encryption = query.get("encryption").toLowerCase();
                if (encryption.equals("ssl") || encryption.equals("smtps")) {
                    ssl = true;
                    startTls = false;
                } else if (encryption.equals("tls") || encryption.equals("starttls")) {
                    ssl = false;
                    startTls = true;
                }
            }

            if (query.containsKey("auth_mode") && query.get("auth_mode").equalsIgnoreCase("none")) {
                auth = false;
            }

            if (scheme.startsWith("gmail")) {
                host = "smtp.gmail.com";
                if (scheme.equals("gmail+smtps")) {
                    port = 465;
                    ssl = true;
                    startTls = false;
                } else {
                    port = 587;
                    ssl = false;
                    startTls = true;
                }

                if ((uri.getHost() == null || "default".equalsIgnoreCase(uri.getHost())) && username.contains("%40")) {
                    username = decode(username);
                }
            }

            if (host.isBlank()) {
                host = defaults.host;
            }

            return new ParsedDsn(host, String.valueOf(port), username, password, auth, startTls, ssl);
        } catch (Exception exception) {
            return defaults;
        }
    }

    private static String decode(String value) {
        if (value == null) {
            return "";
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> map = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return map;
        }

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            String[] keyValue = pair.split("=", 2);
            String key = decode(keyValue[0]);
            String value = keyValue.length > 1 ? decode(keyValue[1]) : "";
            map.put(key, value);
        }
        return map;
    }

    private static Map<String, String> loadSymfonyEnvDefaults() {
        Map<String, String> values = new HashMap<>();

        Path symfonyRoot = Path.of("..", "symfony-app").normalize();
        List<String> files = List.of(".env", ".env.local", ".env.dev", ".env.dev.local");

        for (String fileName : files) {
            Path filePath = symfonyRoot.resolve(fileName);
            if (!Files.exists(filePath)) {
                continue;
            }

            try {
                List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
                for (String line : lines) {
                    String trimmed = line == null ? "" : line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }

                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) {
                        continue;
                    }

                    String key = trimmed.substring(0, separator).trim();
                    String rawValue = trimmed.substring(separator + 1).trim();
                    if (key.isEmpty()) {
                        continue;
                    }

                    String value = stripWrappingQuotes(rawValue);
                    values.put(key, value);
                }
            } catch (Exception exception) {
                // Ignore parsing errors and keep best-effort values from other files.
            }
        }

        return values;
    }

    private static String stripWrappingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value == null ? "" : value;
        }

        boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
        boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
        if (singleQuoted || doubleQuoted) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }

    private record ParsedDsn(String host, String port, String username, String password, boolean auth, boolean startTls,
                             boolean ssl) {
        private static ParsedDsn defaults() {
            return new ParsedDsn("", "587", "", "", true, true, false);
        }
    }
}
