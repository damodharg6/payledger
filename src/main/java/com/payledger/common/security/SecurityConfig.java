package com.payledger.common.security;

import com.payledger.merchant.ApiKeyAuthenticationFilter;
import com.payledger.merchant.MerchantRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * HTTP security. Two ways in, one principal:
 * <ul>
 *   <li><b>Merchant servers</b>: {@code X-API-Key} header → {@code ROLE_MERCHANT}</li>
 *   <li><b>Dashboard users</b>: {@code Authorization: Bearer <JWT>} → {@code ROLE_DASHBOARD}
 *       + {@code ROLE_DASHBOARD_ADMIN} or {@code ROLE_DASHBOARD_VIEWER}</li>
 * </ul>
 * Rules (first match wins):
 * <ul>
 *   <li>Public: register merchant, dashboard login, health, Swagger UI and API docs</li>
 *   <li>Create payment: merchant servers only (a browser session should never charge customers)</li>
 *   <li>Refund: merchant servers or dashboard ADMIN (not VIEWER)</li>
 *   <li>Everything else: any authenticated caller, always scoped to its own merchant</li>
 * </ul>
 * Stateless (no session, no cookies), so CSRF protection is not needed. CORS allows only the
 * dashboard's origin(s).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, MerchantRepository merchants) throws Exception {
        var entryPoint = new ProblemDetailAuthenticationEntryPoint();
        var accessDenied = new ProblemDetailAccessDeniedHandler();
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchants", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments").hasRole("MERCHANT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/*/refunds").hasAnyRole("MERCHANT", "DASHBOARD_ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtMerchantAuthenticationConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied))
                .addFilterBefore(new ApiKeyAuthenticationFilter(merchants), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Lets the React dashboard (another origin, e.g. http://localhost:5173) call the API from the browser. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${payledger.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        cors.setExposedHeaders(List.of("Location", "Idempotent-Replayed"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
