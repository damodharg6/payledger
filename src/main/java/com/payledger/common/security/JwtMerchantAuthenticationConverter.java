package com.payledger.common.security;

import com.payledger.merchant.AuthenticatedMerchant;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a verified dashboard JWT into the same {@link AuthenticatedMerchant} principal that an
 * API key produces, so every controller works unchanged for both kinds of caller.
 * Authorities: {@code ROLE_DASHBOARD} plus {@code ROLE_DASHBOARD_ADMIN} or {@code ROLE_DASHBOARD_VIEWER}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class JwtMerchantAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID merchantId = UUID.fromString(jwt.getClaimAsString(JwtTokenService.MERCHANT_ID));
        var principal = new AuthenticatedMerchant(merchantId, jwt.getClaimAsString(JwtTokenService.MERCHANT_NAME));
        String role = jwt.getClaimAsString(JwtTokenService.ROLE);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_DASHBOARD"),
                new SimpleGrantedAuthority("ROLE_DASHBOARD_" + role));
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
    }
}
