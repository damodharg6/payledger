package com.payledger.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 description for Swagger UI at {@code /swagger-ui.html}. Both ways of signing in are
 * declared, so the "Authorize" button in Swagger UI accepts an API key or a dashboard JWT.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String API_KEY = "apiKey";
    public static final String BEARER = "bearerJwt";

    @Bean
    public OpenAPI payLedgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayLedger API")
                        .version("v1")
                        .description("""
                                Payments and a double-entry ledger for merchants: no double charges \
                                (Idempotency-Key), no lost money (debits = credits), failed debits \
                                auto-reversed within T+1. Amounts are integer paise (₹1 = 100). \
                                Test outcomes with customerRef sim_success, sim_decline, sim_debit_fail, \
                                sim_timeout_success, sim_timeout_pending, sim_bank_down.""")
                        .contact(new Contact().name("Giddaluru Damodhar (MrDamodar), Agentic AI Engineer")))
                .components(new Components()
                        .addSecuritySchemes(API_KEY, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-API-Key")
                                .description("Merchant server key from POST /api/v1/merchants"))
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Dashboard token from POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
