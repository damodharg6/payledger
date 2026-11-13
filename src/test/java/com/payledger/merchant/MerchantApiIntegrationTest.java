package com.payledger.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.payledger.AbstractIntegrationTest;
import com.payledger.ledger.LedgerAccountRepository;
import com.payledger.ledger.OwnerType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * End-to-end tests of merchant registration and API key authentication over HTTP, against a
 * real PostgreSQL.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class MerchantApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MerchantRepository merchants;

    @Autowired
    LedgerAccountRepository accounts;

    @Test
    void registerReturnsKeyOnceStoresOnlyHashAndOpensLedgerAccount() throws Exception {
        String email = uniqueEmail();
        String body = mvc.perform(post("/api/v1/merchants").contentType(MediaType.APPLICATION_JSON)
                        .content(json("Chai Point", email.toUpperCase())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.apiKey", startsWith("plk_")))
                .andExpect(jsonPath("$.email").value(email))
                .andReturn().getResponse().getContentAsString();

        UUID merchantId = UUID.fromString(JsonPath.read(body, "$.merchantId"));
        String apiKey = JsonPath.read(body, "$.apiKey");
        UUID accountId = UUID.fromString(JsonPath.read(body, "$.ledgerAccountId"));

        Merchant saved = merchants.findById(merchantId).orElseThrow();
        assertThat(saved.getApiKeyHash()).isEqualTo(ApiKeys.sha256(apiKey)).isNotEqualTo(apiKey);

        var account = accounts.findById(accountId).orElseThrow();
        assertThat(account.getOwnerType()).isEqualTo(OwnerType.MERCHANT);
        assertThat(account.getOwnerRef()).isEqualTo(merchantId.toString());
        assertThat(account.getBalancePaise()).isZero();
    }

    @Test
    void apiKeyAuthenticatesTheMerchant() throws Exception {
        String email = uniqueEmail();
        String apiKey = register("Dosa Corner", email);

        mvc.perform(get("/api/v1/merchants/me").header(ApiKeyAuthenticationFilter.HEADER, apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void missingOrWrongApiKeyIs401() throws Exception {
        mvc.perform(get("/api/v1/merchants/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Type", startsWith("application/problem+json")));
        mvc.perform(get("/api/v1/merchants/me").header(ApiKeyAuthenticationFilter.HEADER, "plk_not-a-real-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        String email = uniqueEmail();
        register("First", email);
        mvc.perform(post("/api/v1/merchants").contentType(MediaType.APPLICATION_JSON).content(json("Second", email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/merchants").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"webhookUrl\":\"ftp://x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.webhookUrl").exists());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private String register(String name, String email) throws Exception {
        String body = mvc.perform(post("/api/v1/merchants").contentType(MediaType.APPLICATION_JSON).content(json(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.apiKey");
    }

    private static String json(String name, String email) {
        return """
                {"name":"%s","email":"%s","webhookUrl":"https://shop.example/hooks"}""".formatted(name, email);
    }

    private static String uniqueEmail() {
        return "m-" + UUID.randomUUID() + "@example.com";
    }
}
