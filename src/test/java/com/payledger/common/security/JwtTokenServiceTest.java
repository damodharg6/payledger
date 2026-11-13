package com.payledger.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Real HS256 signing and verification: claims round-trip; expired, foreign or tampered tokens
 * are rejected.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class JwtTokenServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final UUID USER = UUID.randomUUID();
    private static final UUID MERCHANT = UUID.randomUUID();

    private final JwtConfig config = new JwtConfig();

    private String issue(JwtProperties props, Instant at) {
        return new JwtTokenService(config.jwtEncoder(props), props, Clock.fixed(at, ZoneOffset.UTC))
                .issue(USER, MERCHANT, "Chai Point", "owner@chai.in", "ADMIN");
    }

    private static JwtProperties props(String issuer) {
        return new JwtProperties(SECRET, issuer, Duration.ofHours(1));
    }

    @Test
    void issuedTokenCarriesTheMerchantAndRole() {
        JwtProperties props = props("payledger");
        Jwt jwt = config.jwtDecoder(props).decode(issue(props, Instant.now()));

        assertThat(jwt.getSubject()).isEqualTo(USER.toString());
        assertThat(jwt.getClaimAsString(JwtTokenService.MERCHANT_ID)).isEqualTo(MERCHANT.toString());
        assertThat(jwt.getClaimAsString(JwtTokenService.ROLE)).isEqualTo("ADMIN");
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));

        var auth = new JwtMerchantAuthenticationConverter().convert(jwt);
        assertThat(auth.getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_DASHBOARD", "ROLE_DASHBOARD_ADMIN");
    }

    @Test
    void expiredTokenIsRejected() {
        JwtProperties props = props("payledger");
        String old = issue(props, Instant.now().minus(Duration.ofHours(3)));
        assertThatThrownBy(() -> config.jwtDecoder(props).decode(old)).isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = issue(props("someone-else"), Instant.now());
        assertThatThrownBy(() -> config.jwtDecoder(props("payledger")).decode(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        JwtProperties props = props("payledger");
        String token = issue(props, Instant.now());
        String[] parts = token.split("\\.");
        // Swap in a payload claiming a different merchant, keep the old signature.
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"x\",\"merchant_id\":\"" + UUID.randomUUID() + "\",\"iss\":\"payledger\"}").getBytes())
                + "." + parts[2];
        assertThatThrownBy(() -> config.jwtDecoder(props).decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        String weak = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThatThrownBy(() -> new JwtProperties(weak, null, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }
}
