package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The inquiry job resolves what it can and keeps going when one payment fails.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class UnresolvedPaymentJobTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Mock
    PaymentTransitions transitions;

    @Mock
    PaymentProcessor processor;

    @Test
    void resolvesEachUnresolvedPaymentAndSurvivesErrors() {
        var job = new UnresolvedPaymentJob(transitions, processor,
                new PaymentProcessingProperties(Duration.ofSeconds(30), 10), Clock.fixed(NOW, ZoneOffset.UTC));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        when(transitions.findUnresolved(NOW.minusSeconds(30), 10)).thenReturn(List.of(a, b, c, d));
        when(processor.resolve(a)).thenReturn(PaymentStatus.SUCCEEDED);
        when(processor.resolve(b)).thenReturn(PaymentStatus.PROCESSING);
        when(processor.resolve(c)).thenThrow(new IllegalStateException("db blip"));
        when(processor.resolve(d)).thenReturn(PaymentStatus.FAILED);

        assertThat(job.run()).isEqualTo(2);
        verify(processor).resolve(d);  // the error on c did not stop the run
    }
}
