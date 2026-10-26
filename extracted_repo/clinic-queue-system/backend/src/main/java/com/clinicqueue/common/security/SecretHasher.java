package com.clinicqueue.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Generates high-entropy public secrets and stores only SHA-256 hashes.
 * Public secrets are bearer credentials: never logged, never returned in
 * listings, and revocable by rotation (see docs/LLD.md section 8).
 */
public final class SecretHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SECRET_BYTES = 32; // 256 bits

    private SecretHasher() {
    }

    /** Returns a 64-char hex secret. Callers must show it to the user exactly once. */
    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /** Lowercase hex SHA-256 of the provided secret. */
    public static String hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
