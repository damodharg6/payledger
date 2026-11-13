package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AutoReversalJob}: loop, failures, batch limit and metrics.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class AutoReversalJobTest {

    private static final Instant T = Instant.parse("2026-11-13T10:00:00Z");

    @Mock
    ReversalService reversals;

    SimpleMeterRegistry meters;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
    }

    private static Optional<ReversalResult> result(boolean late) {
        return Optional.of(new ReversalResult(UUID.randomUUID(), 100, T, T, late));
    }

    private double counter(String outcome) {
        return meters.counter("payledger.reversals", "outcome", outcome).count();
    }

    @Test
    void reversesUntilNothingIsLeftAndCountsOutcomes() {
        UUID broken = UUID.randomUUID();
        when(reversals.reverseNext(anyCollection()))
                .thenReturn(result(false))
                .thenThrow(new ReversalFailedException(broken, new IllegalStateException("x")))
                .thenReturn(result(true))
                .thenReturn(Optional.empty());

        AutoReversalJob.Summary summary = new AutoReversalJob(reversals, new ReversalProperties(10), meters).run();

        assertThat(summary.reversed()).isEqualTo(2);
        assertThat(summary.late()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(counter("on_time")).isEqualTo(1);
        assertThat(counter("late")).isEqualTo(1);
        assertThat(counter("failed")).isEqualTo(1);
    }

    @Test
    void stopsAtTheBatchSize() {
        when(reversals.reverseNext(anyCollection())).thenReturn(result(false));

        AutoReversalJob.Summary summary = new AutoReversalJob(reversals, new ReversalProperties(3), meters).run();

        assertThat(summary.reversed()).isEqualTo(3);
        verify(reversals, times(3)).reverseNext(anyCollection());
    }

    @Test
    void reportsOverdueReversals() {
        when(reversals.reverseNext(anyCollection())).thenReturn(Optional.empty());
        when(reversals.countOverdue()).thenReturn(4L);

        assertThat(new AutoReversalJob(reversals, new ReversalProperties(10), meters).run().stillOverdue()).isEqualTo(4);
    }

    @Test
    void unexpectedErrorStopsTheRunWithoutThrowing() {
        when(reversals.reverseNext(anyCollection())).thenThrow(new IllegalStateException("database down"));

        AutoReversalJob.Summary summary = new AutoReversalJob(reversals, new ReversalProperties(10), meters).run();

        assertThat(summary.reversed()).isZero();
        verify(reversals, times(1)).reverseNext(anyCollection());
    }
}
