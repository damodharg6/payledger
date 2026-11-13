package com.payledger.merchant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads the {@code X-API-Key} header, hashes it and looks the merchant up by that hash. On a
 * match the request is authenticated as an {@link AuthenticatedMerchant}; otherwise it continues
 * unauthenticated and Spring Security answers 401 for protected URLs.
 * <p>
 * Not a Spring {@code @Component} on purpose: Spring Boot would also register a component filter
 * in the plain servlet chain, so it would run outside Spring Security as well.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private static final List<SimpleGrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"));

    private final MerchantRepository merchants;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    public ApiKeyAuthenticationFilter(MerchantRepository merchants) {
        this.merchants = merchants;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);
        if (apiKey != null && !apiKey.isBlank() && contextHolder.getContext().getAuthentication() == null) {
            merchants.findByApiKeyHash(ApiKeys.sha256(apiKey.trim())).ifPresent(merchant -> {
                var principal = new AuthenticatedMerchant(merchant.getId(), merchant.getName());
                SecurityContext context = contextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, AUTHORITIES));
                contextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
