package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.common.NotFoundException;
import com.payledger.ledger.LedgerPostingService;
import com.payledger.ledger.LedgerTransaction;
import com.payledger.ledger.LedgerTransactionType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RefundService}: the refund cap, status rule and idempotent replay.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID MERCHANT = UUID.randomUUID();

    @Mock
    PaymentRepository payments;

    @Mock
    RefundRepository refunds;

    @Mock
    LedgerPostingService postings;

    RefundService service;
    Payment payment;

    @BeforeEach
    void setUp() {
        service = new RefundService(payments, refunds, postings, Clock.fixed(NOW, ZoneOffset.UTC));
        payment = new Payment(UUID.randomUUID(), MERCHANT, 10_000, "INR", "cust-1", NOW);
    }

    private void succeed() {
        payment.startProcessing(NOW);
        payment.succeed("B-1", NOW);
    }

    private void stubLockedPayment() {
        when(payments.lockByIdAndMerchantId(payment.getId(), MERCHANT)).thenReturn(Optional.of(payment));
    }

    @Test
    void partialRefundPostsLedgerAndReportsRemaining() {
        succeed();
        stubLockedPayment();
        when(refunds.sumAmountByPaymentId(payment.getId())).thenReturn(3_000L);
        when(postings.recordRefund(payment.getId(), MERCHANT, "cust-1", 2_000))
                .thenReturn(new LedgerTransaction(UUID.randomUUID(), payment.getId(), LedgerTransactionType.REFUND, NOW));
        when(refunds.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefundResult result = service.refund(MERCHANT, payment.getId(), "r1", new CreateRefundRequest(2_000L, " damaged "));

        assertThat(result.replayed()).isFalse();
        assertThat(result.refund().amountPaise()).isEqualTo(2_000);
        assertThat(result.refund().reason()).isEqualTo("damaged");
        assertThat(result.refund().totalRefundedPaise()).isEqualTo(5_000);
        assertThat(result.refund().refundablePaise()).isEqualTo(5_000);
    }

    @Test
    void refundingExactlyTheRemainderIsAllowed() {
        succeed();
        stubLockedPayment();
        when(refunds.sumAmountByPaymentId(payment.getId())).thenReturn(9_999L);
        when(postings.recordRefund(any(), any(), any(), anyLong()))
                .thenReturn(new LedgerTransaction(UUID.randomUUID(), payment.getId(), LedgerTransactionType.REFUND, NOW));
        when(refunds.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefundResult result = service.refund(MERCHANT, payment.getId(), "r1", new CreateRefundRequest(1L, null));
        assertThat(result.refund().refundablePaise()).isZero();
    }

    @Test
    void refundAboveRemainingIsRejectedAndNothingIsPosted() {
        succeed();
        stubLockedPayment();
        when(refunds.sumAmountByPaymentId(payment.getId())).thenReturn(9_999L);

        assertThatThrownBy(() -> service.refund(MERCHANT, payment.getId(), "r1", new CreateRefundRequest(2L, null)))
                .isInstanceOf(RefundExceedsPaymentException.class)
                .hasMessageContaining("refundable amount of 1 paise");
        verify(postings, never()).recordRefund(any(), any(), any(), anyLong());
        verify(refunds, never()).save(any());
    }

    @Test
    void onlySucceededPaymentsCanBeRefunded() {
        stubLockedPayment();  // still CREATED
        assertThatThrownBy(() -> service.refund(MERCHANT, payment.getId(), "r1", new CreateRefundRequest(1L, null)))
                .isInstanceOf(PaymentNotRefundableException.class);
        verify(postings, never()).recordRefund(any(), any(), any(), anyLong());
    }

    @Test
    void sameKeySameRefundIsReplayedNotRefundedTwice() {
        succeed();
        stubLockedPayment();
        Refund previous = new Refund(UUID.randomUUID(), payment.getId(), MERCHANT, 4_000, null, "r1", UUID.randomUUID(), NOW);
        when(refunds.findByMerchantIdAndIdempotencyKey(MERCHANT, "r1")).thenReturn(Optional.of(previous));
        when(refunds.sumAmountByPaymentId(payment.getId())).thenReturn(4_000L);

        RefundResult result = service.refund(MERCHANT, payment.getId(), "r1", new CreateRefundRequest(4_000L, null));

        assertThat(result.replayed()).isTrue();
        assertThat(result.refund().id()).isEqualTo(previous.getId());
        verify(postings, never()).recordRefund(any(), any(), any(), anyLong());
    }

    @Test
    void sameKeyDifferentRefundIs422() {
        succeed();
        stubLockedPayment();
        Refund previous = new Refund(UUID.randomUUID(), payment.getId(), MERCHANT, 4_000, null, "r1", UUID.randomUUID(), NOW);
        when(refunds.findByMerchantIdAndIdempotencyKey(MERCHANT, "r1")).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.refund(MERCHANT, payment.getId(), "r1", new CreateRefundRequest(5_000L, null)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void unknownPaymentIsNotFound() {
        UUID id = UUID.randomUUID();
        when(payments.lockByIdAndMerchantId(id, MERCHANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refund(MERCHANT, id, "r1", new CreateRefundRequest(1L, null)))
                .isInstanceOf(NotFoundException.class);
    }
}
