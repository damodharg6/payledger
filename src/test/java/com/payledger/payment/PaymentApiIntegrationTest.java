package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.payledger.AbstractIntegrationTest;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end tests of the payment API over HTTP against a real PostgreSQL: creation, idempotent
 * replay, 422 and 409, merchant isolation, and the filtered list.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class PaymentApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    PaymentRepository payments;

    @Autowired
    IdempotencyKeyRepository keys;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createReturns201WithTimeline() throws Exception {
        TestMerchant m = newMerchant();
        postPayment(m, "key-1", 50_075, "cust-1")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/payments/")))
                .andExpect(header().doesNotExist(PaymentController.REPLAYED))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amountPaise").value(50_075))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.timeline[0].to").value("CREATED"));
    }

    @Test
    void sameKeySameBodyReplaysTheSamePayment() throws Exception {
        TestMerchant m = newMerchant();
        String first = postPayment(m, "retry-me", 10_000, "cust-1")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

        String second = postPayment(m, "retry-me", 10_000, "cust-1")
                .andExpect(status().isCreated())
                .andExpect(header().string(PaymentController.REPLAYED, "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(countPayments(m)).isEqualTo(1);
    }

    @Test
    void sameKeyDifferentBodyIs422() throws Exception {
        TestMerchant m = newMerchant();
        postPayment(m, "k", 10_000, "cust-1").andExpect(status().isCreated());

        postPayment(m, "k", 99_999, "cust-1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(containsString("different request body")));
        assertThat(countPayments(m)).isEqualTo(1);
    }

    @Test
    void duplicateWhileFirstStillRunningIs409() throws Exception {
        TestMerchant m = newMerchant();
        // Simulate a first request that has claimed the key but not finished yet.
        String hash = IdempotencyService.requestHash(new CreatePaymentRequest(10_000L, null, "cust-1"));
        tx.executeWithoutResult(s -> keys.insertIfAbsent(m.id(), "busy", hash, Instant.now()));

        postPayment(m, "busy", 10_000, "cust-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("still in progress")));
        assertThat(countPayments(m)).isZero();
    }

    @Test
    void sameKeyIsIndependentPerMerchant() throws Exception {
        TestMerchant a = newMerchant();
        TestMerchant b = newMerchant();
        postPayment(a, "shared-key", 100, "c").andExpect(status().isCreated())
                .andExpect(header().doesNotExist(PaymentController.REPLAYED));
        postPayment(b, "shared-key", 100, "c").andExpect(status().isCreated())
                .andExpect(header().doesNotExist(PaymentController.REPLAYED));
    }

    @Test
    void missingIdempotencyKeyIs400() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(post("/api/v1/payments").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body(100, "c")))
                .andExpect(status().isBadRequest());
        assertThat(countPayments(m)).isZero();
    }

    @Test
    void tooLongIdempotencyKeyIs400WithHeaderNameInErrors() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(post("/api/v1/payments").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                        .header(PaymentController.IDEMPOTENCY_KEY, "x".repeat(101))
                        .contentType(MediaType.APPLICATION_JSON).content(body(100, "c")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['Idempotency-Key']").exists());
        assertThat(countPayments(m)).isZero();
    }

    @Test
    void invalidBodyIs400AndDoesNotBurnTheKey() throws Exception {
        TestMerchant m = newMerchant();
        mvc.perform(post("/api/v1/payments").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                        .header(PaymentController.IDEMPOTENCY_KEY, "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountPaise\":-5,\"currency\":\"USD\",\"customerRef\":\"bad ref!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountPaise").exists())
                .andExpect(jsonPath("$.errors.currency").exists())
                .andExpect(jsonPath("$.errors.customerRef").exists());
        // Validation runs before the key is claimed, so the same key still works afterwards.
        postPayment(m, "k", 100, "c").andExpect(status().isCreated())
                .andExpect(header().doesNotExist(PaymentController.REPLAYED));
    }

    @Test
    void paymentsRequireAnApiKey() throws Exception {
        mvc.perform(post("/api/v1/payments").header(PaymentController.IDEMPOTENCY_KEY, "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body(100, "c")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void merchantCannotSeeAnotherMerchantsPayment() throws Exception {
        TestMerchant owner = newMerchant();
        TestMerchant other = newMerchant();
        String id = JsonPath.read(postPayment(owner, "k", 100, "c").andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/payments/" + id).header(ApiKeyAuthenticationFilter.HEADER, owner.apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/v1/payments/" + id).header(ApiKeyAuthenticationFilter.HEADER, other.apiKey()))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFiltersByStatusAndDateAndPaginates() throws Exception {
        TestMerchant m = newMerchant();
        for (int i = 0; i < 3; i++) {
            postPayment(m, "list-" + i, 100 + i, "c").andExpect(status().isCreated());
        }
        // Move one payment to PROCESSING through the state machine, so the status filter has something to find.
        UUID first = jdbc.queryForObject("SELECT id FROM payments WHERE merchant_id = ? LIMIT 1", UUID.class, m.id());
        tx.executeWithoutResult(s -> payments.findById(first).orElseThrow().startProcessing(Instant.now()));

        list(m, "?size=2&page=0")
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
        list(m, "?size=2&page=1").andExpect(jsonPath("$.content.length()").value(1));
        list(m, "?status=PROCESSING")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first.toString()));
        list(m, "?status=SUCCEEDED").andExpect(jsonPath("$.totalElements").value(0));

        Instant hourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        Instant inAnHour = Instant.now().plus(1, ChronoUnit.HOURS);
        list(m, "?from=" + hourAgo + "&to=" + inAnHour).andExpect(jsonPath("$.totalElements").value(3));
        list(m, "?from=" + inAnHour).andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/v1/payments?size=500").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/payments?status=NOPE").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statusTimelineIsPersisted() throws Exception {
        TestMerchant m = newMerchant();
        String id = JsonPath.read(postPayment(m, "t", 100, "c").andReturn().getResponse().getContentAsString(), "$.id");
        tx.executeWithoutResult(s -> payments.findById(UUID.fromString(id)).orElseThrow().startProcessing(Instant.now()));

        mvc.perform(get("/api/v1/payments/" + id).header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andExpect(jsonPath("$.timeline[1].from").value("CREATED"))
                .andExpect(jsonPath("$.timeline[1].to").value("PROCESSING"));
    }

    private ResultActions postPayment(TestMerchant m, String key, long amountPaise, String customerRef) throws Exception {
        return mvc.perform(post("/api/v1/payments")
                .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                .header(PaymentController.IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(amountPaise, customerRef)));
    }

    private ResultActions list(TestMerchant m, String query) throws Exception {
        return mvc.perform(get("/api/v1/payments" + query).header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(status().isOk());
    }

    private long countPayments(TestMerchant m) {
        return jdbc.queryForObject("SELECT count(*) FROM payments WHERE merchant_id = ?", Long.class, m.id());
    }

    private static String body(long amountPaise, String customerRef) {
        return """
                {"amountPaise":%d,"customerRef":"%s"}""".formatted(amountPaise, customerRef);
    }
}
