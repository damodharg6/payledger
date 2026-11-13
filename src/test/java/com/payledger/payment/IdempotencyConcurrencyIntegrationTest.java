package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.payledger.AbstractIntegrationTest;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Many threads send the same payment with the same Idempotency-Key at the same instant. However
 * the race plays out, exactly one payment must exist afterwards. (Phase 7 repeats this with 50
 * threads and real bank charges.)
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class IdempotencyConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int THREADS = 20;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void concurrentDuplicatesCreateExactlyOnePayment() throws Exception {
        TestMerchant m = newMerchant();
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();  // all threads fire together
                    return mvc.perform(post("/api/v1/payments")
                            .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                            .header(PaymentController.IDEMPOTENCY_KEY, "same-key")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"amountPaise\":12345,\"customerRef\":\"cust-race\"}"))
                            .andReturn().getResponse();
                }));
            }
            ready.await();
            go.countDown();
        }

        int originals = 0;
        int replays = 0;
        int inProgress = 0;
        Set<String> paymentIds = new HashSet<>();
        for (Future<MockHttpServletResponse> f : futures) {
            MockHttpServletResponse r = f.get();
            switch (r.getStatus()) {
                case 201 -> {
                    paymentIds.add(JsonPath.read(r.getContentAsString(), "$.id"));
                    if ("true".equals(r.getHeader(PaymentController.REPLAYED))) {
                        replays++;
                    } else {
                        originals++;
                    }
                }
                case 409 -> inProgress++;
                default -> throw new AssertionError("Unexpected status " + r.getStatus() + ": " + r.getContentAsString());
            }
        }

        System.out.printf("Idempotency race: %d threads -> %d created, %d replayed, %d got 409%n",
                THREADS, originals, replays, inProgress);
        assertThat(originals).as("exactly one request creates the payment").isEqualTo(1);
        assertThat(originals + replays + inProgress).isEqualTo(THREADS);
        assertThat(paymentIds).as("every 201 refers to the same payment").hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE merchant_id = ?", Long.class, m.id()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_keys WHERE merchant_id = ?", Long.class, m.id()))
                .isEqualTo(1L);
    }
}
