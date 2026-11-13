package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.payledger.AbstractIntegrationTest;
import com.payledger.bank.MockBankClient;
import com.payledger.ledger.LedgerAccountRepository;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * <b>Proof 1: no customer is ever charged twice.</b>
 * <p>
 * 50 threads send the same payment with the same Idempotency-Key at the same instant, like a
 * client whose retry logic went wild. Afterwards there must be exactly one payment, the bank
 * must have received exactly one charge request, the ledger must hold exactly one PAYMENT
 * transaction, and the merchant must have been credited exactly once.
 * <p>
 * Run twice: with a fast bank, and with a slow bank that times out (so 49 duplicates arrive while
 * the first request is still waiting on the bank).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class DoubleChargeProofIntegrationTest extends AbstractIntegrationTest {

    private static final int THREADS = 50;
    private static final long AMOUNT = 49_900;

    @Autowired
    MockBankClient bank;

    @Autowired
    LedgerAccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest(name = "customerRef={0}")
    @ValueSource(strings = {"cust-race", "sim_timeout_success-race"})
    void fiftyConcurrentDuplicatesChargeExactlyOnce(String customerRef) throws Exception {
        TestMerchant m = newMerchant();
        String body = "{\"amountPaise\":" + AMOUNT + ",\"customerRef\":\"" + customerRef + "\"}";
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MockHttpServletResponse>> futures = new ArrayList<>();

        long start = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mvc.perform(post("/api/v1/payments")
                                    .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                                    .header(PaymentController.IDEMPOTENCY_KEY, "order-1001")
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse();
                }));
            }
            ready.await();
            go.countDown();
        }
        long millis = (System.nanoTime() - start) / 1_000_000;

        int created = 0;
        int replayed = 0;
        int inProgress = 0;
        Set<String> ids = new HashSet<>();
        for (Future<MockHttpServletResponse> f : futures) {
            MockHttpServletResponse r = f.get();
            if (r.getStatus() == 201) {
                ids.add(JsonPath.read(r.getContentAsString(), "$.id"));
                if ("true".equals(r.getHeader(PaymentController.REPLAYED))) {
                    replayed++;
                } else {
                    created++;
                }
            } else if (r.getStatus() == 409) {
                inProgress++;
            } else {
                throw new AssertionError("Unexpected " + r.getStatus() + ": " + r.getContentAsString());
            }
        }
        UUID paymentId = UUID.fromString(ids.iterator().next());

        long payments = jdbc.queryForObject("SELECT count(*) FROM payments WHERE merchant_id = ?", Long.class, m.id());
        long paymentPostings = jdbc.queryForObject(
                "SELECT count(*) FROM ledger_transactions WHERE payment_id = ? AND type = 'PAYMENT'", Long.class, paymentId);
        long merchantBalance = accounts.findById(ledgerAccounts.merchantAccountId(m.id())).orElseThrow().getBalancePaise();
        int bankCharges = bank.chargeRequestCount(paymentId);

        System.out.printf("PROOF double-charge [%s]: %d threads in %d ms -> %d created, %d replayed, %d got 409 | "
                        + "payments=%d, bank charge requests=%d, PAYMENT postings=%d, merchant credited=%d paise%n",
                customerRef, THREADS, millis, created, replayed, inProgress, payments, bankCharges, paymentPostings,
                merchantBalance);

        assertThat(created).as("exactly one request did the work").isEqualTo(1);
        assertThat(created + replayed + inProgress).isEqualTo(THREADS);
        assertThat(ids).as("every 201 is the same payment").hasSize(1);
        assertThat(payments).as("ONE payment").isEqualTo(1);
        assertThat(bankCharges).as("ONE charge at the bank").isEqualTo(1);
        assertThat(paymentPostings).as("ONE ledger posting").isEqualTo(1);
        assertThat(merchantBalance).as("merchant credited once").isEqualTo(AMOUNT);
    }
}
