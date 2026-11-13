package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.payledger.AbstractIntegrationTest;
import com.payledger.ledger.LedgerAccountRepository;
import com.payledger.ledger.LedgerAccounts;
import com.payledger.ledger.LedgerQueryService;
import com.payledger.merchant.ApiKeyAuthenticationFilter;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The whole payment flow with the mock bank, end to end over HTTP and a real PostgreSQL, one
 * test per bank behaviour (chosen with {@code sim_*} customer refs). The test bank's timeout is
 * 400 ms and a "slow" answer takes 700 ms.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class PaymentFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    LedgerAccountRepository accounts;

    @Autowired
    LedgerQueryService ledgerQueries;

    @Autowired
    UnresolvedPaymentJob unresolvedJob;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void successChargesOnceAndCreditsTheMerchant() throws Exception {
        TestMerchant m = newMerchant();
        Payment p = pay(m, 25_000, "sim_success");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(merchantBalance(m)).isEqualTo(25_000);
        assertThat(customerBalance(m, "sim_success")).isEqualTo(-25_000);
        assertThat(ledgerTransactions(p.getId())).isEqualTo(1);
        assertThat(ledgerQueries.trialBalance().balanced()).isTrue();
    }

    @Test
    void declineFailsWithoutMovingMoney() throws Exception {
        TestMerchant m = newMerchant();
        Payment p = pay(m, 25_000, "sim_decline");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getReverseBy()).isNull();
        assertThat(ledgerTransactions(p.getId())).isZero();
        assertThat(merchantBalance(m)).isZero();
    }

    @Test
    void debitedButFailedGetsTPlusOneDeadlineAndMoneyWaitsInClearing() throws Exception {
        TestMerchant m = newMerchant();
        long clearingBefore = balance(LedgerAccounts.BANK_CLEARING_ID);

        Payment p = pay(m, 9_900, "sim_debit_fail");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getReverseBy()).isEqualTo(ReversalDeadline.tPlusOne(p.getUpdatedAt()));
        assertThat(p.getReverseBy()).isAfter(Instant.now().plus(Duration.ofHours(24)));
        assertThat(customerBalance(m, "sim_debit_fail")).isEqualTo(-9_900);
        assertThat(balance(LedgerAccounts.BANK_CLEARING_ID)).isEqualTo(clearingBefore + 9_900);
        assertThat(merchantBalance(m)).as("merchant never received it").isZero();
    }

    @Test
    void timeoutIsResolvedByInquiryNotByGuessing() throws Exception {
        TestMerchant m = newMerchant();
        long start = System.nanoTime();
        Payment p = pay(m, 4_000, "sim_timeout_success");
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(took).as("we waited for the bank timeout").isGreaterThanOrEqualTo(Duration.ofMillis(400));
        assertThat(p.getStatus()).as("inquiry found the charge went through").isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(merchantBalance(m)).isEqualTo(4_000);
    }

    @Test
    void timeoutThatReallyDebitedIsFailedWithDeadline() throws Exception {
        TestMerchant m = newMerchant();
        Payment p = pay(m, 4_000, "sim_timeout_debit_fail");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.needsReversal()).isTrue();
    }

    @Test
    void stillUnknownStaysProcessingUntilTheJobResolvesIt() throws Exception {
        TestMerchant m = newMerchant();
        Payment p = pay(m, 6_000, "sim_timeout_pending");

        assertThat(p.getStatus()).as("unknown is never treated as failed").isEqualTo(PaymentStatus.PROCESSING);
        assertThat(merchantBalance(m)).isZero();

        unresolvedJob.run();
        assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(merchantBalance(m)).isEqualTo(6_000);

        unresolvedJob.run();  // running again changes nothing
        assertThat(merchantBalance(m)).isEqualTo(6_000);
        assertThat(ledgerTransactions(p.getId())).isEqualTo(1);
    }

    @Test
    void unreachableBankWithNoRecordFailsSafely() throws Exception {
        TestMerchant m = newMerchant();
        Payment p = pay(m, 1_500, "sim_bank_down");

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getFailureReason()).contains("no record");
        assertThat(p.getReverseBy()).isNull();
        assertThat(ledgerTransactions(p.getId())).isZero();
    }

    @Test
    void paymentStuckInCreatedAfterACrashIsFinishedByTheJob() throws Exception {
        TestMerchant m = newMerchant();
        UUID id = UUID.randomUUID();
        // As if the server died right after saving the payment, before calling the bank.
        paymentRepository.saveAndFlush(new Payment(id, m.id(), 700, "INR", "cust-crash", Instant.now().minusSeconds(5)));

        unresolvedJob.run();

        Payment p = paymentRepository.findById(id).orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(p.getReverseBy()).as("the bank never saw it, nothing to reverse").isNull();
    }

    @Test
    void duplicateWhileTheBankIsStillThinkingIs409ThenReplays() throws Exception {
        TestMerchant m = newMerchant();
        String body = "{\"amountPaise\":3000,\"customerRef\":\"sim_timeout_success\"}";

        CompletableFuture<MockHttpServletResponse> first = CompletableFuture.supplyAsync(() -> post(m, "slow-key", body));
        Thread.sleep(150);  // first request is now waiting on the slow bank
        MockHttpServletResponse duplicate = post(m, "slow-key", body);
        MockHttpServletResponse original = first.get(10, TimeUnit.SECONDS);

        assertThat(duplicate.getStatus()).isEqualTo(409);
        assertThat(original.getStatus()).isEqualTo(201);
        assertThat((String) JsonPath.read(original.getContentAsString(), "$.status")).isEqualTo("SUCCEEDED");

        MockHttpServletResponse retry = post(m, "slow-key", body);
        assertThat(retry.getStatus()).isEqualTo(201);
        assertThat(retry.getHeader(PaymentController.REPLAYED)).isEqualTo("true");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE merchant_id = ?", Long.class, m.id())).isEqualTo(1L);
        assertThat(merchantBalance(m)).as("charged exactly once").isEqualTo(3_000);
    }

    private Payment pay(TestMerchant m, long amountPaise, String customerRef) throws Exception {
        return paymentRepository.findById(createPayment(m, amountPaise, customerRef)).orElseThrow();
    }

    private MockHttpServletResponse post(TestMerchant m, String key, String body) {
        try {
            return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/payments")
                    .header(ApiKeyAuthenticationFilter.HEADER, m.apiKey())
                    .header(PaymentController.IDEMPOTENCY_KEY, key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)).andReturn().getResponse();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Payment reload(Payment p) {
        return paymentRepository.findById(p.getId()).orElseThrow();
    }

    private long ledgerTransactions(UUID paymentId) {
        return jdbc.queryForObject("SELECT count(*) FROM ledger_transactions WHERE payment_id = ?", Long.class, paymentId);
    }

    private long balance(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().getBalancePaise();
    }

    private long merchantBalance(TestMerchant m) {
        return balance(ledgerAccounts.merchantAccountId(m.id()));
    }

    private long customerBalance(TestMerchant m, String customerRef) {
        return balance(ledgerAccounts.customerAccountId(m.id(), customerRef));
    }
}
