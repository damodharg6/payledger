package com.payledger.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BankGateway}: a slow or broken bank never becomes an exception or a
 * false "failed".
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
class BankGatewayTest {

    private static final BankChargeRequest REQUEST = new BankChargeRequest(UUID.randomUUID(), "c", 100);

    private BankClient client;
    private BankGateway gateway;

    @BeforeEach
    void setUp() {
        client = mock(BankClient.class);
        BankProperties props = new BankProperties(1, 0, 0, 0, Duration.ZERO, Duration.ZERO,
                Duration.ofMillis(150), Duration.ofMillis(300), 0, null);
        gateway = new BankGateway(client, props);
    }

    @AfterEach
    void tearDown() {
        gateway.shutdown();
    }

    @Test
    void fastAnswerPassesThrough() {
        BankResponse ok = new BankResponse(BankOutcome.SUCCESS, "B1", "Approved");
        when(client.charge(REQUEST)).thenReturn(ok);
        assertThat(gateway.charge(REQUEST)).isEqualTo(ok);
    }

    @Test
    void slowBankBecomesTimeoutAfterTheConfiguredWait() {
        when(client.charge(REQUEST)).thenAnswer(inv -> {
            Thread.sleep(5_000);
            return new BankResponse(BankOutcome.SUCCESS, "late", "Approved");
        });
        long start = System.nanoTime();
        BankResponse result = gateway.charge(REQUEST);
        Duration took = Duration.ofNanos(System.nanoTime() - start);

        assertThat(result.outcome()).isEqualTo(BankOutcome.TIMEOUT);
        assertThat(result.outcome().isKnown()).isFalse();
        assertThat(took).isBetween(Duration.ofMillis(140), Duration.ofMillis(1_000));
    }

    @Test
    void bankErrorBecomesPendingNotFailed() {
        when(client.charge(any())).thenThrow(new BankUnavailableException("connection refused"));
        BankResponse result = gateway.charge(REQUEST);
        assertThat(result.outcome()).isEqualTo(BankOutcome.PENDING);
        assertThat(result.message()).contains("connection refused");
    }

    @Test
    void slowInquiryIsPending() {
        when(client.inquire(any())).thenAnswer(inv -> {
            Thread.sleep(5_000);
            return BankResponse.notFound();
        });
        assertThat(gateway.inquire(UUID.randomUUID()).outcome()).isEqualTo(BankOutcome.PENDING);
    }
}
