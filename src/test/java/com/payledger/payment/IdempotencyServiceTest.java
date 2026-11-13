package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
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
 * Unit tests for every branch of {@link IdempotencyService#claim}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID MERCHANT = UUID.randomUUID();
    private static final String KEY = "order-42";
    private static final IdempotencyKeyId ID = new IdempotencyKeyId(MERCHANT, KEY);

    @Mock
    IdempotencyKeyRepository keys;

    IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(keys, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));
    }

    @Test
    void firstRequestWinsTheClaim() {
        when(keys.insertIfAbsent(MERCHANT, KEY, "h1", NOW)).thenReturn(1);
        assertThat(service.claim(MERCHANT, KEY, "h1").replay()).isFalse();
        verify(keys, never()).findById(any());
    }

    @Test
    void completedDuplicateIsReplayed() {
        UUID paymentId = UUID.randomUUID();
        when(keys.insertIfAbsent(MERCHANT, KEY, "h1", NOW)).thenReturn(0);
        when(keys.findById(ID)).thenReturn(Optional.of(new IdempotencyKey(ID, "h1", paymentId, 201, NOW)));

        IdempotencyService.Claim claim = service.claim(MERCHANT, KEY, "h1");

        assertThat(claim.replay()).isTrue();
        assertThat(claim.paymentId()).isEqualTo(paymentId);
        assertThat(claim.responseCode()).isEqualTo(201);
    }

    @Test
    void sameKeyDifferentBodyIs422() {
        when(keys.insertIfAbsent(MERCHANT, KEY, "h2", NOW)).thenReturn(0);
        when(keys.findById(ID)).thenReturn(Optional.of(new IdempotencyKey(ID, "h1", UUID.randomUUID(), 201, NOW)));

        assertThatThrownBy(() -> service.claim(MERCHANT, KEY, "h2")).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void duplicateWhileFirstIsRunningIs409() {
        when(keys.insertIfAbsent(MERCHANT, KEY, "h1", NOW)).thenReturn(0);
        when(keys.findById(ID)).thenReturn(Optional.of(new IdempotencyKey(ID, "h1", NOW.minusSeconds(2))));

        assertThatThrownBy(() -> service.claim(MERCHANT, KEY, "h1"))
                .isInstanceOf(IdempotencyRequestInProgressException.class);
        verify(keys, never()).deleteStale(any(), any(), any());
    }

    @Test
    void keyReleasedBetweenInsertAndReadIs409SoClientRetries() {
        when(keys.insertIfAbsent(MERCHANT, KEY, "h1", NOW)).thenReturn(0);
        when(keys.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim(MERCHANT, KEY, "h1"))
                .isInstanceOf(IdempotencyRequestInProgressException.class);
    }

    @Test
    void staleClaimFromCrashedRequestIsTakenOver() {
        Instant cutoff = NOW.minus(Duration.ofMinutes(5));
        when(keys.insertIfAbsent(MERCHANT, KEY, "h1", NOW)).thenReturn(0, 1);
        when(keys.findById(ID)).thenReturn(Optional.of(new IdempotencyKey(ID, "h1", NOW.minus(Duration.ofMinutes(10)))));
        when(keys.deleteStale(MERCHANT, KEY, cutoff)).thenReturn(1);

        assertThat(service.claim(MERCHANT, KEY, "h1").replay()).isFalse();
    }

    @Test
    void staleTakeoverLostToAnotherRequestIs409() {
        when(keys.insertIfAbsent(MERCHANT, KEY, "h1", NOW)).thenReturn(0);
        when(keys.findById(ID)).thenReturn(Optional.of(new IdempotencyKey(ID, "h1", NOW.minus(Duration.ofMinutes(10)))));
        when(keys.deleteStale(eq(MERCHANT), eq(KEY), any())).thenReturn(0);

        assertThatThrownBy(() -> service.claim(MERCHANT, KEY, "h1"))
                .isInstanceOf(IdempotencyRequestInProgressException.class);
    }

    @Test
    void completeFailsLoudlyIfKeyWasNeverClaimed() {
        UUID paymentId = UUID.randomUUID();
        when(keys.complete(MERCHANT, KEY, paymentId, 201)).thenReturn(0);
        assertThatThrownBy(() -> service.complete(MERCHANT, KEY, paymentId, 201)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requestHashIgnoresFormattingButNotValues() {
        String a = IdempotencyService.requestHash(new CreatePaymentRequest(50_000L, null, "cust-1"));
        String b = IdempotencyService.requestHash(new CreatePaymentRequest(50_000L, "INR", " cust-1 "));
        String c = IdempotencyService.requestHash(new CreatePaymentRequest(50_001L, "INR", "cust-1"));
        assertThat(a).hasSize(64).isEqualTo(b).isNotEqualTo(c);
    }
}
