package com.payledger.common.security;

import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings ({@code payledger.jwt.*}). The secret must be Base64 and decode to at least 32
 * bytes (256 bits, required by HS256). In any shared environment set it from the
 * {@code JWT_SECRET} environment variable, never from a file in git.
 *
 * @param secret Base64 HMAC key
 * @param issuer value of the {@code iss} claim; tokens from anyone else are rejected
 * @param ttl    how long a token stays valid
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ConfigurationProperties("payledger.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("payledger.jwt.secret is required");
        }
        if (Base64.getDecoder().decode(secret).length < 32) {
            throw new IllegalArgumentException("payledger.jwt.secret must decode to at least 32 bytes (256 bits)");
        }
        issuer = issuer == null ? "payledger" : issuer;
        ttl = ttl == null ? Duration.ofHours(1) : ttl;
    }

    public byte[] secretBytes() {
        return Base64.getDecoder().decode(secret);
    }
}
