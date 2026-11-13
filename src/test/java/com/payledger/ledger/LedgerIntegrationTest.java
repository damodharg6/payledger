package com.payledger.ledger;

import static com.payledger.ledger.LedgerLine.credit;
import static com.payledger.ledger.LedgerLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.payledger.AbstractIntegrationTest;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import com.payledger.payment.ReversalService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The ledger against a real PostgreSQL: balanced postings, cached balances that match the
 * entries, "charge once" enforced by the database, no deadlocks under concurrent opposite
 * transfers, and the read API with its access rules.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class LedgerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    LedgerService ledger;

    @Autowired
    ReversalService reversalService;

    @Autowired
    LedgerQueryService queries;

    @Autowired
    LedgerAccountRepository accountRepository;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void succeededPaymentMovesMoneyFromCustomerToMerchant() throws Exception {
        TestMerchant m = newMerchant();
        UUID paymentId = succeededPayment(m, 50_000, "cust-a");
        UUID merchantAcc = ledgerAccounts.merchantAccountId(m.id());
        UUID customerAcc = ledgerAccounts.customerAccountId(m.id(), "cust-a");

        mvc.perform(get("/api/v1/ledger/accounts/" + merchantAcc + "/balance").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balancePaise").value(50_000))
                .andExpect(jsonPath("$.balanceFromEntriesPaise").value(50_000))
                .andExpect(jsonPath("$.consistent").value(true));
        mvc.perform(get("/api/v1/ledger/accounts/" + customerAcc + "/balance").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(jsonPath("$.balancePaise").value(-50_000))
                .andExpect(jsonPath("$.ownerType").value("CUSTOMER"));
        mvc.perform(get("/api/v1/ledger/accounts/" + merchantAcc + "/entries").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].transactionType").value("PAYMENT"))
                .andExpect(jsonPath("$.content[0].paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.content[0].amountPaise").value(50_000));

        assertLedgerIsSound();
    }

    @Test
    void failedDebitIsHeldInClearingAndReversalReturnsIt() throws Exception {
        TestMerchant m = newMerchant();
        long clearingBefore = balance(LedgerAccounts.BANK_CLEARING_ID);
        // The mock bank debits this customer but fails the payment: the flow parks the money in clearing.
        UUID paymentId = createPayment(m, 7_500, "sim_debit_fail-r");
        UUID customerAcc = ledgerAccounts.customerAccountId(m.id(), "sim_debit_fail-r");
        assertThat(balance(customerAcc)).isEqualTo(-7_500);
        assertThat(balance(LedgerAccounts.BANK_CLEARING_ID)).isEqualTo(clearingBefore + 7_500);

        reversalService.reversePayment(paymentId);  // what the T+1 auto-reversal job does
        assertThat(balance(customerAcc)).isZero();
        assertThat(balance(LedgerAccounts.BANK_CLEARING_ID)).isEqualTo(clearingBefore);

        assertLedgerIsSound();
    }

    @Test
    void unbalancedPostingWritesNothing() throws Exception {
        TestMerchant m = newMerchant();
        UUID acc = ledgerAccounts.merchantAccountId(m.id());
        long entriesBefore = countEntries();

        assertThatThrownBy(() -> ledger.post(LedgerTransactionType.REFUND, null,
                List.of(debit(acc, 100), credit(LedgerAccounts.BANK_CLEARING_ID, 99))))
                .isInstanceOf(UnbalancedTransactionException.class);

        assertThat(countEntries()).isEqualTo(entriesBefore);
        assertThat(balance(acc)).isZero();
    }

    @Test
    void databaseRefusesToChargeTheSamePaymentTwice() throws Exception {
        TestMerchant m = newMerchant();
        UUID paymentId = succeededPayment(m, 1_000, "cust-twice");
        UUID merchantAcc = ledgerAccounts.merchantAccountId(m.id());

        assertThatThrownBy(() -> ledgerPostings.recordPaymentSucceeded(paymentId, m.id(), "cust-twice", 1_000))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(balance(merchantAcc)).isEqualTo(1_000);

        assertThatThrownBy(() -> ledgerPostings.recordDebitHeldForFailedPayment(paymentId, m.id(), "cust-twice", 1_000))
                .as("a second PAYMENT transaction of any kind is refused")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertLedgerIsSound();
    }

    /**
     * 40 threads move money between the same two accounts in opposite directions at once. Without
     * a fixed lock order, A→B and B→A transfers would deadlock and PostgreSQL would abort some.
     */
    @Test
    void oppositeConcurrentTransfersNeverDeadlock() throws Exception {
        UUID a = ledgerAccounts.merchantAccountId(newMerchant().id());
        UUID b = ledgerAccounts.merchantAccountId(newMerchant().id());
        int perDirection = 20;
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(perDirection * 2)) {
            for (int i = 0; i < perDirection; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return ledger.post(LedgerTransactionType.REFUND, null, List.of(debit(a, 7), credit(b, 7)));
                }));
                futures.add(pool.submit(() -> {
                    go.await();
                    return ledger.post(LedgerTransactionType.REFUND, null, List.of(debit(b, 3), credit(a, 3)));
                }));
            }
            go.countDown();
            for (Future<?> f : futures) {
                f.get();  // rethrows a deadlock or any other failure
            }
        }

        assertThat(balance(a)).isEqualTo(perDirection * (-7L + 3L));
        assertThat(balance(b)).isEqualTo(perDirection * (7L - 3L));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE account_id IN (?, ?)", Long.class, a, b))
                .isEqualTo(perDirection * 4L);
        assertLedgerIsSound();
    }

    @Test
    void merchantsCannotReadOtherLedgerAccounts() throws Exception {
        TestMerchant owner = newMerchant();
        TestMerchant other = newMerchant();
        UUID ownerAcc = ledgerAccounts.merchantAccountId(owner.id());

        mvc.perform(get("/api/v1/ledger/accounts/" + ownerAcc + "/balance").header(ApiKeyAuthenticationFilter.HEADER, other.apiKey()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ledger/accounts/" + LedgerAccounts.BANK_CLEARING_ID + "/balance")
                        .header(ApiKeyAuthenticationFilter.HEADER, owner.apiKey()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ledger/accounts/" + ownerAcc + "/entries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void entriesArePaginatedNewestFirst() throws Exception {
        TestMerchant m = newMerchant();
        succeededPayment(m, 100, "c1");
        succeededPayment(m, 200, "c2");
        succeededPayment(m, 300, "c3");
        UUID acc = ledgerAccounts.merchantAccountId(m.id());

        mvc.perform(get("/api/v1/ledger/accounts/" + acc + "/entries?size=2").header(ApiKeyAuthenticationFilter.HEADER, m.apiKey()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].amountPaise").value(300))
                .andExpect(jsonPath("$.content[1].amountPaise").value(200));
    }

    /** The three invariants of a correct ledger, checked over the whole database. */
    private void assertLedgerIsSound() {
        TrialBalance trial = queries.trialBalance();
        assertThat(trial.debitsPaise()).as("total debits = total credits").isEqualTo(trial.creditsPaise());
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(balance_paise), 0) FROM ledger_accounts", Long.class))
                .as("all cached balances add up to zero").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ledger_accounts a
                WHERE a.balance_paise <> (SELECT COALESCE(SUM(CASE WHEN e.direction = 'C' THEN e.amount_paise
                                                                    ELSE -e.amount_paise END), 0)
                                          FROM ledger_entries e WHERE e.account_id = a.id)""", Long.class))
                .as("every cached balance equals the sum of its entries").isZero();
    }

    private long balance(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalancePaise();
    }

    private long countEntries() {
        return jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class);
    }
}
