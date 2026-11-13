package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Checks every one of the 25 (from, to) pairs against the allowed transitions.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class PaymentStatusTest {

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            PaymentStatus.CREATED, EnumSet.of(PaymentStatus.PROCESSING),
            PaymentStatus.PROCESSING, EnumSet.of(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED),
            PaymentStatus.FAILED, EnumSet.of(PaymentStatus.REVERSED),
            PaymentStatus.SUCCEEDED, EnumSet.noneOf(PaymentStatus.class),
            PaymentStatus.REVERSED, EnumSet.noneOf(PaymentStatus.class));

    @ParameterizedTest
    @EnumSource(PaymentStatus.class)
    void onlyDocumentedTransitionsAreAllowed(PaymentStatus from) {
        for (PaymentStatus to : PaymentStatus.values()) {
            assertThat(from.canTransitionTo(to))
                    .as("%s -> %s", from, to)
                    .isEqualTo(ALLOWED.get(from).contains(to));
        }
    }

    @Test
    void noStatusCanMoveToItself() {
        for (PaymentStatus s : PaymentStatus.values()) {
            assertThat(s.canTransitionTo(s)).as(s.name()).isFalse();
        }
    }

    @Test
    void onlySucceededAndReversedAreFinal() {
        assertThat(EnumSet.allOf(PaymentStatus.class).stream().filter(PaymentStatus::isFinal))
                .containsExactlyInAnyOrder(PaymentStatus.SUCCEEDED, PaymentStatus.REVERSED);
    }
}
