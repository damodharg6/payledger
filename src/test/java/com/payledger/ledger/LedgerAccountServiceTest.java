package com.payledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LedgerAccountService}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class LedgerAccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");

    @Mock
    LedgerAccountRepository accounts;

    LedgerAccountService service;

    @BeforeEach
    void setUp() {
        service = new LedgerAccountService(accounts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void opensInrMerchantAccountWithZeroBalance() {
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID merchantId = UUID.randomUUID();

        LedgerAccount account = service.openMerchantAccount(merchantId, "Chai Point");

        assertThat(account.getOwnerType()).isEqualTo(OwnerType.MERCHANT);
        assertThat(account.getOwnerRef()).isEqualTo(merchantId.toString());
        assertThat(account.getCurrency()).isEqualTo("INR");
        assertThat(account.getBalancePaise()).isZero();
        assertThat(account.getName()).isEqualTo("Merchant: Chai Point");
        assertThat(account.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void longMerchantNamesAreTruncatedToColumnSize() {
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerAccount account = service.openMerchantAccount(UUID.randomUUID(), "x".repeat(120));

        assertThat(account.getName()).hasSize(120).startsWith("Merchant: ");
    }
}
