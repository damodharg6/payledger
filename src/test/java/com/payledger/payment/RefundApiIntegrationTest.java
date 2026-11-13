package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.payledger.AbstractIntegrationTest;
import com.payledger.ledger.LedgerAccountRepository;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Refunds over HTTP against a real PostgreSQL: partial refunds, the cap, replays, status rules,
 * and many refunds of the same payment racing each other.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class RefundApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    LedgerAccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void partialRefundsUpToTheFullAmountThenRejected() throws Exception {
        TestMerchant m = newMerchant();
        UUID paymentId = succeededPayment(m, 10_000, "cust-1");

        refund(m, paymentId, "r1", 3_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalRefundedPaise").value(3_000))
                .andExpect(jsonPath("$.refundablePaise").value(7_000));
        refund(m, paymentId, "r2", 7_000)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refundablePaise").value(0));
        refund(m, paymentId, "r3", 1)
                .andExpect(status().isUnprocessableContent());

        mvc.perform(get("/api/v1/payments/" + paymentId).header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.refundedPaise").value(10_000));
        assertThat(merchantBalance(m)).as("merchant gave everything back").isZero();
        assertThat(customerBalance(m, "cust-1")).as("customer got everything back").isZero();
    }

    @Test
    void retriedRefundIsReplayedNotPaidTwice() throws Exception {
        TestMerchant m = newMerchant();
        UUID paymentId = succeededPayment(m, 10_000, "cust-1");

        String first = refund(m, paymentId, "same", 4_000).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = refund(m, paymentId, "same", 4_000).andExpect(status().isCreated())
                .andExpect(header().string(PaymentController.REPLAYED, "true"))
                .andReturn().getResponse().getContentAsString();

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(merchantBalance(m)).isEqualTo(6_000);

        refund(m, paymentId, "same", 5_000).andExpect(status().isUnprocessableContent());
        assertThat(merchantBalance(m)).isEqualTo(6_000);
    }

    @Test
    void onlySucceededPaymentsCanBeRefunded() throws Exception {
        TestMerchant m = newMerchant();
        UUID created = createPayment(m, 5_000, "cust-1");
        refund(m, created, "r", 1_000).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("only SUCCEEDED")));
    }

    @Test
    void anotherMerchantsPaymentIsNotFound() throws Exception {
        TestMerchant owner = newMerchant();
        TestMerchant other = newMerchant();
        UUID paymentId = succeededPayment(owner, 5_000, "cust-1");
        refund(other, paymentId, "r", 1_000).andExpect(status().isNotFound());
        assertThat(merchantBalance(owner)).isEqualTo(5_000);
    }

    @Test
    void invalidRefundRequestsAre400() throws Exception {
        TestMerchant m = newMerchant();
        UUID paymentId = succeededPayment(m, 5_000, "cust-1");
        refund(m, paymentId, "r", 0).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amountPaise").exists());
        mvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                        .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\":100}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * 10 threads each try to refund 25% of the same payment at the same moment. Without the row
     * lock several could pass the "is there enough left?" check together. With it, exactly 4
     * succeed and the total is exactly 100%.
     */
    @Test
    void concurrentRefundsNeverExceedThePayment() throws Exception {
        TestMerchant m = newMerchant();
        UUID paymentId = succeededPayment(m, 10_000, "cust-race");
        int threads = 10;
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                String key = "race-" + i;
                futures.add(pool.submit(() -> {
                    go.await();
                    return refund(m, paymentId, key, 2_500).andReturn().getResponse().getStatus();
                }));
            }
            go.countDown();
        }

        int ok = 0;
        int rejected = 0;
        for (Future<Integer> f : futures) {
            int status = f.get();
            if (status == 201) {
                ok++;
            } else if (status == 422) {
                rejected++;
            } else {
                throw new AssertionError("Unexpected status " + status);
            }
        }
        System.out.printf("Refund race: %d threads x 25%% -> %d refunded, %d rejected%n", threads, ok, rejected);
        assertThat(ok).isEqualTo(4);
        assertThat(rejected).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT SUM(amount_paise) FROM refunds WHERE payment_id = ?", Long.class, paymentId))
                .isEqualTo(10_000L);
        assertThat(merchantBalance(m)).isZero();
    }

    private ResultActions refund(TestMerchant m, UUID paymentId, String key, long amountPaise) throws Exception {
        return mvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                .header(PaymentController.IDEMPOTENCY_KEY, key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountPaise\":" + amountPaise + "}"));
    }

    private long merchantBalance(TestMerchant m) {
        return accounts.findById(ledgerAccounts.merchantAccountId(m.id())).orElseThrow().getBalancePaise();
    }

    private long customerBalance(TestMerchant m, String customerRef) {
        return accounts.findById(ledgerAccounts.customerAccountId(m.id(), customerRef)).orElseThrow().getBalancePaise();
    }
}
