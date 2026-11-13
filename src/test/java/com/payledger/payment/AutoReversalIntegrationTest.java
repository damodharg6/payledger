package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.payledger.AbstractIntegrationTest;
import com.payledger.ledger.LedgerAccountRepository;
import com.payledger.ledger.LedgerAccounts;
import com.payledger.ledger.LedgerQueryService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The T+1 auto-reversal against a real PostgreSQL: money comes back, the job is safe to repeat,
 * late reversals are flagged, and several workers in parallel never reverse a payment twice.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class AutoReversalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    AutoReversalJob job;

    @Autowired
    ReversalService reversals;

    @Autowired
    LedgerAccountRepository accounts;

    @Autowired
    LedgerQueryService ledgerQueries;

    @Autowired
    MeterRegistry meters;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void debitedButFailedPaymentIsReversedAndTheCustomerIsWhole() throws Exception {
        TestMerchant m = newMerchant();
        UUID id = createPayment(m, 12_345, "sim_debit_fail-whole");
        assertThat(customerBalance(m, "sim_debit_fail-whole")).isEqualTo(-12_345);
        assertThat(balance(LedgerAccounts.BANK_CLEARING_ID)).isGreaterThanOrEqualTo(12_345);

        job.run();  // reverses every pending payment in the database, not only this one

        Payment p = paymentRepository.findById(id).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(customerBalance(m, "sim_debit_fail-whole")).as("customer got the money back").isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM payments WHERE status = 'FAILED' AND reverse_by IS NOT NULL", Long.class))
                .as("nothing left to reverse").isZero();
        assertThat(balance(LedgerAccounts.BANK_CLEARING_ID)).as("clearing is empty once all reversals ran").isZero();
        assertThat(reversalTransactions(id)).isEqualTo(1);
        assertThat(timelineEnd(id)).isEqualTo("REVERSED");

        job.run();  // a second run finds nothing to do for this payment
        assertThat(reversalTransactions(id)).isEqualTo(1);
        assertThat(ledgerQueries.trialBalance().balanced()).isTrue();
    }

    @Test
    void declinedPaymentIsNeverReversed() throws Exception {
        TestMerchant m = newMerchant();
        UUID id = createPayment(m, 5_000, "sim_decline");

        job.run();

        assertThat(paymentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(reversalTransactions(id)).isZero();
    }

    @Test
    void reversalAfterTheDeadlineIsFlaggedLate() throws Exception {
        TestMerchant m = newMerchant();
        UUID id = createPayment(m, 2_000, "sim_debit_fail-late");
        // Simulate the job having been down for two days.
        jdbc.update("UPDATE payments SET reverse_by = ? WHERE id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)), id);
        assertThat(reversals.countOverdue()).isGreaterThanOrEqualTo(1);
        double lateBefore = meters.counter("payledger.reversals", "outcome", "late").count();

        AutoReversalJob.Summary summary = job.run();

        assertThat(summary.late()).isGreaterThanOrEqualTo(1);
        assertThat(meters.counter("payledger.reversals", "outcome", "late").count()).isGreaterThan(lateBefore);
        assertThat(paymentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(reversals.countOverdue()).isZero();
    }

    /**
     * Four "app instances" drain 20 pending reversals at the same time. SKIP LOCKED lets them
     * share the work without waiting on each other, and every payment is reversed exactly once.
     */
    @Test
    void parallelWorkersNeverReverseTheSamePaymentTwice() throws Exception {
        TestMerchant m = newMerchant();
        List<UUID> ours = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            ours.add(createPayment(m, 1_000 + i, "sim_debit_fail-p" + i));
        }

        int workers = 4;
        Map<UUID, Integer> reversedBy = new ConcurrentHashMap<>();
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            for (int w = 0; w < workers; w++) {
                int worker = w;
                futures.add(pool.submit(() -> {
                    go.await();
                    int done = 0;
                    Set<UUID> skip = new HashSet<>();
                    while (true) {
                        try {
                            Optional<ReversalResult> r = reversals.reverseNext(skip);
                            if (r.isEmpty()) {
                                return done;
                            }
                            reversedBy.merge(r.get().paymentId(), 1, Integer::sum);
                            done++;
                        } catch (ReversalFailedException e) {
                            failures.incrementAndGet();
                            skip.add(e.paymentId());
                        }
                    }
                }));
            }
            go.countDown();
        }
        List<Integer> perWorker = new ArrayList<>();
        for (Future<Integer> f : futures) {
            perWorker.add(f.get());
        }
        System.out.printf("Parallel reversal: %d workers reversed %s payments%n", workers, perWorker);

        assertThat(failures.get()).as("no worker ever hit an already-reversed payment").isZero();
        for (UUID id : ours) {
            assertThat(reversedBy.get(id)).as("payment %s reversed exactly once", id).isEqualTo(1);
            assertThat(reversalTransactions(id)).isEqualTo(1);
            assertThat(paymentRepository.findById(id).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REVERSED);
        }
        assertThat(ledgerQueries.trialBalance().balanced()).isTrue();
    }

    private long reversalTransactions(UUID paymentId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM ledger_transactions WHERE payment_id = ? AND type = 'REVERSAL'", Long.class, paymentId);
    }

    private String timelineEnd(UUID paymentId) {
        return jdbc.queryForObject(
                "SELECT to_status FROM payment_status_history WHERE payment_id = ? ORDER BY id DESC LIMIT 1", String.class, paymentId);
    }

    private long balance(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().getBalancePaise();
    }

    private long customerBalance(TestMerchant m, String customerRef) {
        return balance(ledgerAccounts.customerAccountId(m.id(), customerRef));
    }
}
