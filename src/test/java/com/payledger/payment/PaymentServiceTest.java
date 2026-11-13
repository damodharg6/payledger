package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit tests for {@link PaymentService#create}: new request, replay, and clean-up on failure.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID MERCHANT = UUID.randomUUID();
    private static final CreatePaymentRequest REQUEST = new CreatePaymentRequest(50_000L, null, "cust-1");

    @Mock
    PaymentRepository payments;

    @Mock
    IdempotencyService idempotency;

    @Mock
    PaymentQueryService queries;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, idempotency, queries, mock(PlatformTransactionManager.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void newKeyCreatesPaymentAndCompletesKey() {
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenReturn(IdempotencyService.Claim.newRequest());

        PaymentResult result = service.create(MERCHANT, "k1", REQUEST);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(saved.capture());
        Payment p = saved.getValue();
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(p.getAmountPaise()).isEqualTo(50_000L);
        assertThat(p.getCurrency()).isEqualTo("INR");
        verify(idempotency).complete(MERCHANT, "k1", p.getId(), 201);

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.replayed()).isFalse();
        assertThat(result.payment().id()).isEqualTo(p.getId());
    }

    @Test
    void sameKeyReturnsStoredPaymentWithoutCreatingAnother() {
        UUID existing = UUID.randomUUID();
        PaymentResponse stored = new PaymentResponse(existing, MERCHANT, 50_000L, "INR", PaymentStatus.CREATED,
                "cust-1", null, null, null, NOW, NOW, 0L, List.of());
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenReturn(IdempotencyService.Claim.replayOf(existing, 201));
        when(queries.get(MERCHANT, existing)).thenReturn(stored);

        PaymentResult result = service.create(MERCHANT, "k1", REQUEST);

        assertThat(result.replayed()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.payment()).isSameAs(stored);
        verify(payments, never()).saveAndFlush(any());
        verify(idempotency, never()).complete(any(), any(), any(), anyInt());
    }

    @Test
    void failureWhileCreatingReleasesTheKey() {
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenReturn(IdempotencyService.Claim.newRequest());
        when(payments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.create(MERCHANT, "k1", REQUEST)).isInstanceOf(DataIntegrityViolationException.class);
        verify(idempotency).release(MERCHANT, "k1");
    }

    @Test
    void rejectedClaimCreatesNothing() {
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenThrow(new IdempotencyKeyReusedException("k1"));

        assertThatThrownBy(() -> service.create(MERCHANT, "k1", REQUEST)).isInstanceOf(IdempotencyKeyReusedException.class);
        verify(payments, never()).saveAndFlush(any());
        verify(idempotency, never()).release(any(), any());
    }
}
