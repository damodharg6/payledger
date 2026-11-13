package com.payledger.common.security;

import com.payledger.merchant.ApiKeyAuthenticationFilter;
import com.payledger.merchant.MerchantRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * HTTP security for the API.
 * <ul>
 *   <li>Stateless: no HTTP session, every request proves who it is with its own header.</li>
 *   <li>CSRF is off because nothing is authenticated by a browser cookie.</li>
 *   <li>Open: merchant registration and health. Everything else needs a valid {@code X-API-Key}.</li>
 * </ul>
 * Phase 6 adds a second way in: JWT for the dashboard.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(HttpSecurity http, MerchantRepository merchants) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/merchants").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint()))
                .addFilterBefore(new ApiKeyAuthenticationFilter(merchants), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
