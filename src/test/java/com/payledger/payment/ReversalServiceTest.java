package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.ledger.LedgerPostingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for {@link ReversalService}: posting, status change, late detection and failures.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class ReversalServiceTest {

    private static final Instant DEBITED_AT = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID MERCHANT = UUID.randomUUID();

    @Mock
    PaymentRepository payments;

    @Mock
    LedgerPostingService postings;

    private ReversalService serviceAt(Instant now) {
        return new ReversalService(payments, postings, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Payment debitedButFailed() {
        Payment p = new Payment(UUID.randomUUID(), MERCHANT, 8_000, "INR", "cust-1", DEBITED_AT);
        p.startProcessing(DEBITED_AT);
        p.fail("debited but failed", "B-1", ReversalDeadline.tPlusOne(DEBITED_AT), DEBITED_AT);
        return p;
    }

    @Test
    void reversesOnTimeAndMovesToReversed() {
        Payment p = debitedButFailed();
        when(payments.lockNextForReversal(anyCollection())).thenReturn(Optional.of(p));
        Instant now = DEBITED_AT.plus(Duration.ofMinutes(1));

        ReversalResult result = serviceAt(now).reverseNext(Set.of()).orElseThrow();

        verify(postings).recordReversal(p.getId(), MERCHANT, "cust-1", 8_000);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(result.late()).isFalse();
        assertThat(result.reversedAt()).isEqualTo(now);
        assertThat(result.amountPaise()).isEqualTo(8_000);
    }

    @Test
    void reversalAtOrAfterTheDeadlineIsMarkedLate() {
        Payment p = debitedButFailed();
        when(payments.lockNextForReversal(anyCollection())).thenReturn(Optional.of(p));

        ReversalResult result = serviceAt(p.getReverseBy()).reverseNext(Set.of()).orElseThrow();

        assertThat(result.late()).isTrue();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REVERSED);
    }

    @Test
    void nothingToDoReturnsEmpty() {
        when(payments.lockNextForReversal(anyCollection())).thenReturn(Optional.empty());
        assertThat(serviceAt(DEBITED_AT).reverseNext(Set.of())).isEmpty();
    }

    @Test
    void emptyExclusionListIsNeverSentToTheDatabase() {
        when(payments.lockNextForReversal(anyCollection())).thenReturn(Optional.empty());
        serviceAt(DEBITED_AT).reverseNext(Set.of());
        verify(payments).lockNextForReversal(List.of(new UUID(0, 0)));
    }

    @Test
    void ledgerFailureBecomesReversalFailedWithThePaymentId() {
        Payment p = debitedButFailed();
        when(payments.lockNextForReversal(anyCollection())).thenReturn(Optional.of(p));
        when(postings.recordReversal(any(), any(), any(), anyLong()))
                .thenThrow(new DataIntegrityViolationException("already reversed"));

        assertThatThrownBy(() -> serviceAt(DEBITED_AT).reverseNext(Set.of()))
                .isInstanceOf(ReversalFailedException.class)
                .satisfies(e -> assertThat(((ReversalFailedException) e).paymentId()).isEqualTo(p.getId()));
    }

    @Test
    void paymentThatWasNeverDebitedCannotBeReversed() {
        Payment declined = new Payment(UUID.randomUUID(), MERCHANT, 8_000, "INR", "cust-1", DEBITED_AT);
        declined.startProcessing(DEBITED_AT);
        declined.fail("declined", null, null, DEBITED_AT);
        when(payments.lockById(declined.getId())).thenReturn(Optional.of(declined));

        assertThatThrownBy(() -> serviceAt(DEBITED_AT).reversePayment(declined.getId()))
                .isInstanceOf(IllegalStateException.class);
        verify(postings, never()).recordReversal(any(), any(), any(), anyLong());
    }
}
