package com.payledger.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.payledger.AbstractIntegrationTest;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import com.payledger.merchant.DashboardUser;
import com.payledger.merchant.DashboardUserRepository;
import com.payledger.payment.PaymentController;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Dashboard JWT login next to merchant API keys, role rules, CORS and Swagger, over HTTP with a
 * real PostgreSQL.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "correct horse battery";

    @Autowired
    DashboardUserRepository dashboardUsers;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    /** A merchant with both an API key and a dashboard ADMIN login. */
    private record Shop(UUID id, String apiKey, String email) {
    }

    private Shop newShop() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.com";
        String body = mvc.perform(post("/api/v1/merchants").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Chai Point","email":"%s","dashboardPassword":"%s"}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new Shop(UUID.fromString(JsonPath.read(body, "$.merchantId")), JsonPath.read(body, "$.apiKey"), email);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private String token(String email) throws Exception {
        return JsonPath.read(login(email, PASSWORD).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    private String createPaymentWithApiKey(Shop shop) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/v1/payments")
                        .header(ApiKeyAuthenticationFilter.HEADER, shop.apiKey())
                        .header(PaymentController.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\":5000,\"customerRef\":\"c1\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void loginReturnsJwtThatOpensTheDashboardEndpoints() throws Exception {
        Shop shop = newShop();
        String paymentId = createPaymentWithApiKey(shop);

        login(shop.email(), PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.merchantId").value(shop.id().toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        String jwt = token(shop.email());

        mvc.perform(get("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, bearer(jwt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/payments/" + paymentId).header(HttpHeaders.AUTHORIZATION, bearer(jwt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(get("/api/v1/merchants/me").header(HttpHeaders.AUTHORIZATION, bearer(jwt)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.merchantId").value(shop.id().toString()));
    }

    @Test
    void dashboardAdminCanRefundButCannotCreatePayments() throws Exception {
        Shop shop = newShop();
        String paymentId = createPaymentWithApiKey(shop);
        String jwt = token(shop.email());

        mvc.perform(post("/api/v1/payments/" + paymentId + "/refunds").header(HttpHeaders.AUTHORIZATION, bearer(jwt))
                        .header(PaymentController.IDEMPOTENCY_KEY, "dash-refund-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\":1000,\"reason\":\"damaged\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundablePaise").value(4000));

        mvc.perform(post("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, bearer(jwt))
                        .header(PaymentController.IDEMPOTENCY_KEY, "dash-pay")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\":100,\"customerRef\":\"c\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void viewerCanLookButCannotRefund() throws Exception {
        Shop shop = newShop();
        String paymentId = createPaymentWithApiKey(shop);
        String viewerEmail = "viewer-" + UUID.randomUUID() + "@example.com";
        dashboardUsers.save(new DashboardUser(UUID.randomUUID(), shop.id(), viewerEmail,
                passwordEncoder.encode(PASSWORD), DashboardUser.Role.VIEWER, Instant.now()));
        String jwt = token(viewerEmail);

        mvc.perform(get("/api/v1/payments/" + paymentId).header(HttpHeaders.AUTHORIZATION, bearer(jwt)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/payments/" + paymentId + "/refunds").header(HttpHeaders.AUTHORIZATION, bearer(jwt))
                        .header(PaymentController.IDEMPOTENCY_KEY, "viewer-refund")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\":1000}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenOfOneMerchantCannotSeeAnothersPayment() throws Exception {
        Shop a = newShop();
        Shop b = newShop();
        String paymentOfB = createPaymentWithApiKey(b);
        mvc.perform(get("/api/v1/payments/" + paymentOfB).header(HttpHeaders.AUTHORIZATION, bearer(token(a.email()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        Shop shop = newShop();
        String wrongPassword = login(shop.email(), "not the password").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody-" + UUID.randomUUID() + "@example.com", "whatever")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(wrongPassword).isEqualTo(unknownEmail);
    }

    @Test
    void forgedOrGarbageTokensAre401() throws Exception {
        mvc.perform(get("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, bearer("not.a.jwt")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
        String token = token(newShop().email());
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mvc.perform(get("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordsAreStoredAsBcryptOnly() throws Exception {
        Shop shop = newShop();
        String hash = jdbc.queryForObject("SELECT password_hash FROM dashboard_users WHERE email = ?", String.class, shop.email());
        assertThat(hash).startsWith("$2a$10$").doesNotContain(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
    }

    @Test
    void tooShortDashboardPasswordIs400() throws Exception {
        mvc.perform(post("/api/v1/merchants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"S\",\"email\":\"s-%s@example.com\",\"dashboardPassword\":\"short\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dashboardPassword").exists());
    }

    @Test
    void corsAllowsOnlyTheDashboardOrigin() throws Exception {
        mvc.perform(options("/api/v1/payments")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        mvc.perform(options("/api/v1/payments")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void swaggerUiAndOpenApiDocsArePublicAndDescribeBothAuthSchemes() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("PayLedger API"))
                .andExpect(jsonPath("$.components.securitySchemes.apiKey.name").value("X-API-Key"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerJwt.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/payments'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/payments/{id}/refunds'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post").exists());
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Swagger UI")));
    }
}
