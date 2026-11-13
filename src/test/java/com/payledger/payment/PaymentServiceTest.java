package com.payledger.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Unit tests for {@link PaymentService#create}: claim, create, process, complete, and what is
 * (and is not) released when something fails.
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

    @Mock
    PaymentProcessor processor;

    PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, idempotency, queries, processor, mock(PlatformTransactionManager.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PaymentResponse response(UUID id, PaymentStatus status) {
        return new PaymentResponse(id, MERCHANT, 50_000L, "INR", status, "cust-1", null, null, null, NOW, NOW, 0L, List.of());
    }

    @Test
    void newKeyCreatesProcessesThenCompletesKey() {
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenReturn(IdempotencyService.Claim.newRequest());
        when(processor.process(any())).thenReturn(PaymentStatus.SUCCEEDED);
        when(queries.get(eq(MERCHANT), any())).thenAnswer(inv -> response(inv.getArgument(1), PaymentStatus.SUCCEEDED));

        PaymentResult result = service.create(MERCHANT, "k1", REQUEST);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(saved.capture());
        UUID id = saved.getValue().getId();
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(saved.getValue().getCurrency()).isEqualTo("INR");

        InOrder order = inOrder(idempotency, processor);
        order.verify(idempotency).attachPayment(MERCHANT, "k1", id);
        order.verify(processor).process(id);
        order.verify(idempotency).complete(MERCHANT, "k1", id, 201);

        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.replayed()).isFalse();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    void sameKeyReturnsStoredPaymentWithoutCreatingOrCharging() {
        UUID existing = UUID.randomUUID();
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenReturn(IdempotencyService.Claim.replayOf(existing, 201));
        when(queries.get(MERCHANT, existing)).thenReturn(response(existing, PaymentStatus.SUCCEEDED));

        PaymentResult result = service.create(MERCHANT, "k1", REQUEST);

        assertThat(result.replayed()).isTrue();
        assertThat(result.payment().id()).isEqualTo(existing);
        verify(payments, never()).saveAndFlush(any());
        verify(processor, never()).process(any());
        verify(idempotency, never()).complete(any(), any(), any(), anyInt());
    }

    @Test
    void failureBeforeThePaymentExistsReleasesTheKey() {
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenReturn(IdempotencyService.Claim.newRequest());
        when(payments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.create(MERCHANT, "k1", REQUEST)).isInstanceOf(DataIntegrityViolationException.class);
        verify(idempotency).release(MERCHANT, "k1");
        verify(processor, never()).process(any());
    }

    @Test
    void failureWhileProcessingKeepsTheKeyAndReturnsThePayment() {
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenReturn(IdempotencyService.Claim.newRequest());
        when(processor.process(any())).thenThrow(new IllegalStateException("database blip"));
        when(queries.get(eq(MERCHANT), any())).thenAnswer(inv -> response(inv.getArgument(1), PaymentStatus.PROCESSING));

        PaymentResult result = service.create(MERCHANT, "k1", REQUEST);

        // The payment exists: releasing the key would let a retry create a second one.
        verify(idempotency, never()).release(any(), any());
        verify(idempotency).complete(eq(MERCHANT), eq("k1"), any(), eq(201));
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void rejectedClaimCreatesNothing() {
        when(idempotency.claim(eq(MERCHANT), eq("k1"), anyString())).thenThrow(new IdempotencyKeyReusedException("k1"));

        assertThatThrownBy(() -> service.create(MERCHANT, "k1", REQUEST)).isInstanceOf(IdempotencyKeyReusedException.class);
        verify(payments, never()).saveAndFlush(any());
        verify(idempotency, never()).release(any(), any());
    }
}
