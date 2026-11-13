package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the payment state machine on the entity itself.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class PaymentTest {

    private static final Instant T0 = Instant.parse("2026-11-13T10:00:00Z");

    private Payment newPayment() {
        return new Payment(UUID.randomUUID(), UUID.randomUUID(), 50_075L, "INR", "cust-1", T0);
    }

    @Test
    void newPaymentIsCreatedWithOneTimelineEntry() {
        Payment p = newPayment();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(p.getHistory()).singleElement().satisfies(h -> {
            assertThat(h.getFromStatus()).isNull();
            assertThat(h.getToStatus()).isEqualTo(PaymentStatus.CREATED);
        });
    }

    @Test
    void happyPathRecordsEveryStep() {
        Payment p = newPayment();
        p.startProcessing(T0.plusSeconds(1));
        p.succeed("BANK-123", T0.plusSeconds(2));

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(p.getBankReference()).isEqualTo("BANK-123");
        assertThat(p.getUpdatedAt()).isEqualTo(T0.plusSeconds(2));
        assertThat(p.getHistory()).extracting(PaymentStatusChange::getToStatus)
                .containsExactly(PaymentStatus.CREATED, PaymentStatus.PROCESSING, PaymentStatus.SUCCEEDED);
    }

    @Test
    void failedAfterDebitCanBeReversed() {
        Payment p = newPayment();
        p.startProcessing(T0);
        Instant deadline = T0.plus(Duration.ofDays(1));
        p.fail("Bank debited but could not credit merchant", "BANK-9", deadline, T0);

        assertThat(p.needsReversal()).isTrue();
        assertThat(p.getReverseBy()).isEqualTo(deadline);
        p.reverse(T0.plusSeconds(60));
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.REVERSED);
        assertThat(p.needsReversal()).isFalse();
    }

    @Test
    void failedWithoutDebitCannotBeReversed() {
        Payment p = newPayment();
        p.startProcessing(T0);
        p.fail("Declined by bank", null, null, T0);

        assertThat(p.needsReversal()).isFalse();
        assertThatThrownBy(() -> p.reverse(T0)).isInstanceOf(IllegalStateException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void cannotSkipProcessing() {
        Payment p = newPayment();
        assertThatThrownBy(() -> p.succeed("X", T0))
                .isInstanceOf(InvalidPaymentTransitionException.class)
                .hasMessageContaining("CREATED to SUCCEEDED");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(p.getHistory()).hasSize(1);
    }

    @Test
    void succeededPaymentCannotFailLater() {
        Payment p = newPayment();
        p.startProcessing(T0);
        p.succeed("X", T0);
        assertThatThrownBy(() -> p.fail("late failure", null, null, T0))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void cannotProcessTwice() {
        Payment p = newPayment();
        p.startProcessing(T0);
        assertThatThrownBy(() -> p.startProcessing(T0)).isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void amountMustBePositive() {
        assertThatThrownBy(() -> new Payment(UUID.randomUUID(), UUID.randomUUID(), 0, "INR", "c", T0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
