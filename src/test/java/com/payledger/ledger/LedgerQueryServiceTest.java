package com.payledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.payledger.common.BadRequestException;
import com.payledger.common.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LedgerQueryService}: visibility rules and the cache-vs-entries check.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class LedgerQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    private static final UUID MERCHANT = UUID.randomUUID();

    @Mock
    LedgerAccountRepository accounts;

    @Mock
    LedgerEntryRepository entries;

    @InjectMocks
    LedgerQueryService service;

    @Test
    void merchantSeesOwnAccountsOnly() {
        UUID other = UUID.randomUUID();
        assertThat(LedgerQueryService.isVisibleTo(account(OwnerType.MERCHANT, MERCHANT.toString()), MERCHANT)).isTrue();
        assertThat(LedgerQueryService.isVisibleTo(account(OwnerType.CUSTOMER, MERCHANT + ":cust-1"), MERCHANT)).isTrue();
        assertThat(LedgerQueryService.isVisibleTo(account(OwnerType.MERCHANT, other.toString()), MERCHANT)).isFalse();
        assertThat(LedgerQueryService.isVisibleTo(account(OwnerType.CUSTOMER, other + ":cust-1"), MERCHANT)).isFalse();
        assertThat(LedgerQueryService.isVisibleTo(account(OwnerType.SYSTEM, "BANK_CLEARING"), MERCHANT)).isFalse();
    }

    @Test
    void balanceReportsWhetherCacheMatchesEntries() {
        LedgerAccount acc = account(OwnerType.MERCHANT, MERCHANT.toString());
        when(accounts.findById(acc.getId())).thenReturn(Optional.of(acc));
        when(entries.balanceFromEntries(acc.getId())).thenReturn(0L);

        AccountBalance balance = service.balance(MERCHANT, acc.getId());

        assertThat(balance.balancePaise()).isZero();
        assertThat(balance.consistent()).isTrue();

        when(entries.balanceFromEntries(acc.getId())).thenReturn(7L);
        assertThat(service.balance(MERCHANT, acc.getId()).consistent()).isFalse();
    }

    @Test
    void systemAccountIsNotFoundForMerchants() {
        LedgerAccount clearing = account(OwnerType.SYSTEM, "BANK_CLEARING");
        when(accounts.findById(clearing.getId())).thenReturn(Optional.of(clearing));
        assertThatThrownBy(() -> service.balance(MERCHANT, clearing.getId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void entriesRejectsBadPaging() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.entries(MERCHANT, id, -1, 10)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.entries(MERCHANT, id, 0, 101)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void trialBalanceComparesDebitsAndCredits() {
        when(entries.totals()).thenReturn(totals(100, 100));
        assertThat(service.trialBalance().balanced()).isTrue();
        when(entries.totals()).thenReturn(totals(100, 99));
        assertThat(service.trialBalance().balanced()).isFalse();
    }

    private static LedgerAccount account(OwnerType type, String ownerRef) {
        return new LedgerAccount(UUID.randomUUID(), type, ownerRef, "x", "INR", NOW);
    }

    private static LedgerEntryRepository.Totals totals(long debits, long credits) {
        return new LedgerEntryRepository.Totals() {
            @Override
            public Long getDebits() {
                return debits;
            }

            @Override
            public Long getCredits() {
                return credits;
            }
        };
    }
}
