package com.payledger.merchant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Creates and hashes merchant API keys.
 * <p>
 * A key is {@code plk_} + 32 random bytes from {@link SecureRandom} (256 bits), Base64URL encoded.
 * <p>
 * Why SHA-256 and not BCrypt? BCrypt is slow on purpose to protect weak, human-chosen
 * passwords. A 256-bit random key cannot be guessed, so a fast hash is safe, and because SHA-256
 * gives the same output every time we can find the merchant by an indexed lookup on the hash.
 * Dashboard passwords (Phase 6) still use BCrypt.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Component
public class ApiKeys {

    static final String PREFIX = "plk_";
    private static final int KEY_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** Returns a new random API key. Show it to the merchant once; store only {@link #sha256}. */
    public String generate() {
        byte[] bytes = new byte[KEY_BYTES];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lower-case hex SHA-256 of the key: always 64 characters. */
    public static String sha256(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }
}
