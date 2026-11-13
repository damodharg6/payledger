package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.payledger.bank.BankChargeRequest;
import com.payledger.bank.BankOutcome;
import com.payledger.bank.BankResponse;
import com.payledger.ledger.LedgerPostingService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Every bank outcome mapped to the right status, deadline and ledger posting.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentTransitionsTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID MERCHANT = UUID.randomUUID();

    @Mock
    PaymentRepository payments;

    @Mock
    LedgerPostingService postings;

    PaymentTransitions transitions;
    Payment payment;

    @BeforeEach
    void setUp() {
        transitions = new PaymentTransitions(payments, postings, Clock.fixed(NOW, ZoneOffset.UTC));
        payment = new Payment(UUID.randomUUID(), MERCHANT, 5_000, "INR", "cust-1", NOW);
        when(payments.lockById(payment.getId())).thenReturn(Optional.of(payment));
    }

    private PaymentStatus apply(BankOutcome outcome) {
        return transitions.applyBankResult(payment.getId(), new BankResponse(outcome, "B-1", "msg " + outcome));
    }

    @Test
    void startProcessingMovesCreatedToProcessingAndBuildsTheBankRequest() {
        BankChargeRequest request = transitions.startProcessing(payment.getId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(request).isEqualTo(new BankChargeRequest(payment.getId(), "cust-1", 5_000));

        transitions.startProcessing(payment.getId());  // second call is harmless
        assertThat(payment.getHistory()).hasSize(2);
    }

    @Test
    void successPostsCustomerToMerchant() {
        payment.startProcessing(NOW);
        assertThat(apply(BankOutcome.SUCCESS)).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.getBankReference()).isEqualTo("B-1");
        verify(postings).recordPaymentSucceeded(payment.getId(), MERCHANT, "cust-1", 5_000);
    }

    @ParameterizedTest
    @EnumSource(value = BankOutcome.class, names = {"DECLINED", "NOT_FOUND"})
    void noMoneyMovedMeansFailedWithNothingToReverse(BankOutcome outcome) {
        payment.startProcessing(NOW);
        assertThat(apply(outcome)).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getReverseBy()).isNull();
        assertThat(payment.getFailureReason()).isEqualTo("msg " + outcome);
        verifyNoInteractions(postings);
    }

    @Test
    void debitedButFailedSetsTPlusOneAndParksMoneyInClearing() {
        payment.startProcessing(NOW);
        assertThat(apply(BankOutcome.DEBITED_NOT_CREDITED)).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getReverseBy()).isEqualTo(ReversalDeadline.tPlusOne(NOW));
        assertThat(payment.needsReversal()).isTrue();
        verify(postings).recordDebitHeldForFailedPayment(payment.getId(), MERCHANT, "cust-1", 5_000);
    }

    @ParameterizedTest
    @EnumSource(value = BankOutcome.class, names = {"PENDING", "TIMEOUT"})
    void unknownOutcomeIsNeverTreatedAsFailed(BankOutcome outcome) {
        payment.startProcessing(NOW);
        assertThat(apply(outcome)).isEqualTo(PaymentStatus.PROCESSING);
        verifyNoInteractions(postings);
    }

    @Test
    void lateResultForAnAlreadyResolvedPaymentIsIgnored() {
        payment.startProcessing(NOW);
        apply(BankOutcome.SUCCESS);
        assertThat(apply(BankOutcome.DEBITED_NOT_CREDITED)).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(postings, never()).recordDebitHeldForFailedPayment(any(), any(), any(), anyLong());
    }
}
