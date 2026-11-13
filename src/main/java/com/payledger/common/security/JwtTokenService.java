package com.payledger.common.security;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues dashboard access tokens. Claims:
 * <ul>
 *   <li>{@code sub}: dashboard user id</li>
 *   <li>{@code merchant_id}, {@code merchant_name}: which merchant's data the user may see</li>
 *   <li>{@code role}: ADMIN or VIEWER</li>
 *   <li>{@code iss}, {@code iat}, {@code exp}: issuer, issued-at and expiry</li>
 * </ul>
 * A JWT is signed, not encrypted: anyone can read the claims, so nothing secret goes in it.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class JwtTokenService {

    public static final String MERCHANT_ID = "merchant_id";
    public static final String MERCHANT_NAME = "merchant_name";
    public static final String ROLE = "role";

    private final JwtEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties props, Clock clock) {
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    public String issue(UUID userId, UUID merchantId, String merchantName, String email, String role) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(props.ttl()))
                .claim("email", email)
                .claim(MERCHANT_ID, merchantId.toString())
                .claim(MERCHANT_NAME, merchantName)
                .claim(ROLE, role)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long ttlSeconds() {
        return props.ttl().toSeconds();
    }
}
