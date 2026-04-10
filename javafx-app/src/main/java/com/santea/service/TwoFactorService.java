package com.santea.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

public class TwoFactorService {
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int OTP_DIGITS = 6;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    public String generateSecret() {
        byte[] random = new byte[20];
        new SecureRandom().nextBytes(random);
        return base32Encode(random);
    }

    public String provisioningUri(String issuer, String accountName, String secret) {
        String normalizedIssuer = sanitize(issuer);
        String normalizedAccount = sanitize(accountName);
        String normalizedSecret = sanitize(secret);
        return "otpauth://totp/" + normalizedIssuer + ":" + normalizedAccount
                + "?secret=" + normalizedSecret
                + "&issuer=" + normalizedIssuer
                + "&algorithm=SHA1&digits=" + OTP_DIGITS
                + "&period=" + TIME_STEP_SECONDS;
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
            return false;
        }

        String trimmedCode = code.trim();
        if (!trimmedCode.matches("\\d{6}")) {
            return false;
        }

        byte[] key = decodeSecret(secret);
        if (key.length == 0) {
            return false;
        }

        long currentWindow = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            String expected = generateTotp(key, currentWindow + offset);
            if (trimmedCode.equals(expected)) {
                return true;
            }
        }

        return false;
    }

    public String currentCodeForDebug(String secret) {
        byte[] key = decodeSecret(secret);
        if (key.length == 0) {
            return "";
        }
        long window = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        return generateTotp(key, window);
    }

    private String generateTotp(byte[] key, long counter) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, OTP_DIGITS);
            return String.format("%0" + OTP_DIGITS + "d", otp);
        } catch (Exception exception) {
            return "";
        }
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "SANTEA";
        }
        return value.trim().replace(" ", "%20");
    }

    private byte[] decodeSecret(String secret) {
        byte[] base32 = base32Decode(secret);
        if (base32.length > 0) {
            return base32;
        }
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ignored) {
            return new byte[0];
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                out.append(BASE32_ALPHABET.charAt(index));
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            out.append(BASE32_ALPHABET.charAt(index));
        }

        return out.toString();
    }

    private byte[] base32Decode(String secret) {
        if (secret == null || secret.isBlank()) {
            return new byte[0];
        }

        String normalized = secret.trim().replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer buffer = ByteBuffer.allocate((normalized.length() * 5 + 7) / 8);
        int current = 0;
        int bits = 0;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                return new byte[0];
            }

            current = (current << 5) | value;
            bits += 5;

            if (bits >= 8) {
                bits -= 8;
                buffer.put((byte) ((current >> bits) & 0xFF));
            }
        }

        byte[] out = new byte[buffer.position()];
        buffer.flip();
        buffer.get(out);
        return out;
    }
}
