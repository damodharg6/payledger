package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.payledger.AbstractIntegrationTest;
import com.payledger.bank.BankOutcome;
import com.payledger.bank.BankProperties;
import com.payledger.bank.MockBankClient;
import com.payledger.ledger.LedgerAccounts;
import com.payledger.ledger.LedgerQueryService;
import com.payledger.ledger.TrialBalance;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;

/**
 * <b>Proof 2: no rupee is lost, and every failed debit is reversed.</b>
 * <p>
 * 1,000 payments from 25 concurrent clients across 5 merchants and 100 customers, against a
 * hostile mock bank: 70% success, 10% declined, 10% "debited but failed" (20% failures) and 10%
 * timeouts, with 20% of inquiries answering "still pending". Every 10th payment is sent twice
 * (a client retry). 100 succeeded payments are then half-refunded. Finally the inquiry job and
 * the T+1 auto-reversal job run until they have nothing left.
 * <p>
 * Afterwards the whole ledger is checked: debits = credits, all balances sum to zero, every
 * cached balance equals its entries, nobody was charged twice, every failed debit was reversed,
 * and the clearing account is empty.
 * <p>
 * Runs in its own Spring context (different bank settings), so it gets its own fresh PostgreSQL
 * container and the whole-ledger numbers are exact.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@TestPropertySource(properties = {
        "payledger.bank.success-rate=0.70",
        "payledger.bank.decline-rate=0.10",
        "payledger.bank.debited-failure-rate=0.10",
        "payledger.bank.timeout-rate=0.10",
        "payledger.bank.inquiry-pending-rate=0.20",
        "payledger.bank.min-latency=0ms",
        "payledger.bank.max-latency=3ms",
        "payledger.bank.timeout=200ms",
        "payledger.bank.slow-response-delay=350ms",
        "payledger.bank.seed=2026",
        "payledger.payments.inquiry-batch-size=200"
})
class ChaosLedgerProofIntegrationTest extends AbstractIntegrationTest {

    private static final int PAYMENTS = 1_000;
    private static final int CLIENTS = 25;
    private static final int MERCHANTS = 5;
    private static final int CUSTOMERS = 100;
    private static final int REFUNDS = 100;

    @Autowired
    BankProperties bankProperties;

    @Autowired
    MockBankClient bank;

    @Autowired
    UnresolvedPaymentJob unresolvedJob;

    @Autowired
    AutoReversalJob reversalJob;

    @Autowired
    LedgerQueryService ledgerQueries;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void thousandPaymentsAgainstAHostileBankLoseNothing() throws Exception {
        assertThat(bankProperties.timeoutRate()).as("chaos bank settings are active").isEqualTo(0.10);
        assertThat(bankProperties.successRate()).isEqualTo(0.70);

        List<TestMerchant> merchants = new ArrayList<>();
        for (int i = 0; i < MERCHANTS; i++) {
            merchants.add(newMerchant());
        }

        // 1. 1,000 payments from 25 concurrent clients; every 10th is retried with the same key.
        AtomicInteger retriesReplayed = new AtomicInteger();
        AtomicInteger non201 = new AtomicInteger();
        long start = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(CLIENTS)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < PAYMENTS; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    TestMerchant m = merchants.get(n % MERCHANTS);
                    String key = "chaos-" + n;
                    String body = "{\"amountPaise\":%d,\"customerRef\":\"cust-%d\"}".formatted(amountFor(n), n % CUSTOMERS);
                    if (send(m, key, body).getStatus() != 201) {
                        non201.incrementAndGet();
                    }
                    if (n % 10 == 0) {
                        MockHttpServletResponse retry = send(m, key, body);
                        if (retry.getStatus() == 201 && "true".equals(retry.getHeader(PaymentController.REPLAYED))) {
                            retriesReplayed.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }
        long paymentMillis = (System.nanoTime() - start) / 1_000_000;
        Map<String, Long> afterRequests = statusCounts();

        // 2. Finish every payment whose outcome was still unknown (timeouts + "pending" inquiries).
        int inquiryRuns = 0;
        int resolvedByInquiry = 0;
        while (unresolved() > 0 && inquiryRuns < 50) {
            resolvedByInquiry += unresolvedJob.run();
            inquiryRuns++;
        }

        // 3. Merchants refund half of 100 succeeded payments.
        List<Map<String, Object>> toRefund = jdbc.queryForList(
                "SELECT id, merchant_id, amount_paise FROM payments WHERE status = 'SUCCEEDED' ORDER BY id LIMIT ?", REFUNDS);
        int refunded = 0;
        for (Map<String, Object> row : toRefund) {
            TestMerchant m = merchants.stream().filter(x -> x.id().equals(row.get("merchant_id"))).findFirst().orElseThrow();
            long half = ((Number) row.get("amount_paise")).longValue() / 2;
            int status = mvc.perform(post("/api/v1/payments/" + row.get("id") + "/refunds")
                            .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                            .header(PaymentController.IDEMPOTENCY_KEY, "refund-" + row.get("id"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"amountPaise\":" + half + "}"))
                    .andReturn().getResponse().getStatus();
            if (status == 201) {
                refunded++;
            }
        }

        // 4. T+1 auto-reversal until nothing is left.
        long clearingBeforeReversal = clearingBalance();
        int reversed = 0;
        int late = 0;
        AutoReversalJob.Summary run;
        do {
            run = reversalJob.run();
            reversed += run.reversed();
            late += run.late();
        } while (run.reversed() > 0);
        long totalMillis = (System.nanoTime() - start) / 1_000_000;

        // ---- The numbers ----
        Map<String, Long> finalStatus = statusCounts();
        MockBankClient.Stats bankStats = bank.stats();
        TrialBalance trial = ledgerQueries.trialBalance();
        long maxChargesPerPayment = jdbc.queryForList("SELECT id FROM payments", UUID.class).stream()
                .mapToLong(bank::chargeRequestCount).max().orElse(0);
        long debitedFailures = bankStats.outcomes().getOrDefault(BankOutcome.DEBITED_NOT_CREDITED, 0L);

        System.out.printf("""
                PROOF chaos ledger: %d payments, %d concurrent clients, %d merchants, %d customers
                  bank: %d charge requests for %d payments, %d timed out (%.1f%%), outcomes %s
                  after the HTTP calls: %s  (%d ms, %.0f payments/s)
                  inquiry job: %d runs resolved %d payments; client retries replayed: %d/%d
                  refunds: %d; reversals: %d (late: %d); clearing before reversal: %d paise
                  final statuses: %s  (total %d ms)
                  trial balance: debits=%d credits=%d; max bank charges per payment: %d
                """,
                PAYMENTS, CLIENTS, MERCHANTS, CUSTOMERS,
                bankStats.chargeRequests(), bankStats.distinctPayments(), bankStats.slowAnswers(),
                100.0 * bankStats.slowAnswers() / PAYMENTS, bankStats.outcomes(),
                afterRequests, paymentMillis, PAYMENTS * 1000.0 / paymentMillis,
                inquiryRuns, resolvedByInquiry, retriesReplayed.get(), PAYMENTS / 10,
                refunded, reversed, late, clearingBeforeReversal,
                finalStatus, totalMillis, trial.debitsPaise(), trial.creditsPaise(), maxChargesPerPayment);

        // ---- The proof ----
        assertThat(non201.get()).as("every request got 201").isZero();
        assertThat(retriesReplayed.get()).as("every retry was a replay").isEqualTo(PAYMENTS / 10);
        assertThat(count("SELECT count(*) FROM payments")).as("retries created no extra payments").isEqualTo(PAYMENTS);
        assertThat(bankStats.chargeRequests()).as("the bank saw exactly one charge per payment").isEqualTo(PAYMENTS);
        assertThat(maxChargesPerPayment).as("no payment charged twice at the bank").isEqualTo(1);
        assertThat(count("""
                SELECT count(*) FROM (SELECT payment_id FROM ledger_transactions WHERE type IN ('PAYMENT','REVERSAL')
                                      GROUP BY payment_id, type HAVING count(*) > 1) d"""))
                .as("no payment posted or reversed twice in the ledger").isZero();

        assertThat(trial.debitsPaise()).as("total debits = total credits").isEqualTo(trial.creditsPaise());
        assertThat(count("SELECT COALESCE(SUM(balance_paise), 0) FROM ledger_accounts")).as("all balances sum to 0").isZero();
        assertThat(count("""
                SELECT count(*) FROM ledger_accounts a WHERE a.balance_paise <>
                  (SELECT COALESCE(SUM(CASE WHEN e.direction = 'C' THEN e.amount_paise ELSE -e.amount_paise END), 0)
                   FROM ledger_entries e WHERE e.account_id = a.id)"""))
                .as("every cached balance equals its entries").isZero();

        assertThat(unresolved()).as("no payment left CREATED or PROCESSING").isZero();
        assertThat(count("SELECT count(*) FROM payments WHERE status = 'FAILED' AND reverse_by IS NOT NULL"))
                .as("every failed debit was reversed").isZero();
        assertThat(finalStatus.getOrDefault("REVERSED", 0L)).as("reversed = bank's debited-but-failed")
                .isEqualTo(debitedFailures);
        assertThat(clearingBalance()).as("clearing account empty").isZero();
        assertThat(late).as("no reversal missed T+1").isZero();
        assertThat(refunded).isEqualTo(REFUNDS);

        assertThat(count("""
                SELECT count(*) FROM ledger_accounts a WHERE a.owner_type = 'MERCHANT' AND a.balance_paise <>
                  (SELECT COALESCE(SUM(p.amount_paise), 0) FROM payments p
                   WHERE p.merchant_id::text = a.owner_ref AND p.status = 'SUCCEEDED')
                - (SELECT COALESCE(SUM(r.amount_paise), 0) FROM refunds r WHERE r.merchant_id::text = a.owner_ref)"""))
                .as("each merchant holds exactly its successful payments minus refunds").isZero();
    }

    private MockHttpServletResponse send(TestMerchant m, String key, String body) throws Exception {
        return mvc.perform(post("/api/v1/payments")
                        .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                        .header(PaymentController.IDEMPOTENCY_KEY, key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    /** ₹1 to ₹9,970, varied but repeatable. */
    private static long amountFor(int n) {
        return 100L * (1 + (n * 7919L) % 997);
    }

    private long unresolved() {
        return count("SELECT count(*) FROM payments WHERE status IN ('CREATED', 'PROCESSING')");
    }

    private long clearingBalance() {
        return count("SELECT balance_paise FROM ledger_accounts WHERE id = '" + LedgerAccounts.BANK_CLEARING_ID + "'");
    }

    private Map<String, Long> statusCounts() {
        Map<String, Long> counts = new java.util.TreeMap<>();
        jdbc.query("SELECT status, count(*) FROM payments GROUP BY status",
                rs -> {
                    counts.put(rs.getString(1), rs.getLong(2));
                });
        return counts;
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
