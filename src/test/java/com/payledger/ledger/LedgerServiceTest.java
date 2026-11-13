package com.payledger.ledger;

import static com.payledger.ledger.LedgerLine.credit;
import static com.payledger.ledger.LedgerLine.debit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.payledger.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link LedgerService}: the balance rule, lock order and cached balances.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
    // Chosen so that Java's UUID order is LOW < HIGH.
    private static final UUID LOW = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID HIGH = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    @Mock
    LedgerAccountRepository accounts;

    @Mock
    LedgerTransactionRepository transactions;

    @Mock
    LedgerEntryRepository entries;

    LedgerService service;
    LedgerAccount low;
    LedgerAccount high;

    @BeforeEach
    void setUp() {
        service = new LedgerService(accounts, transactions, entries, Clock.fixed(NOW, ZoneOffset.UTC));
        low = new LedgerAccount(LOW, OwnerType.CUSTOMER, "m:c", "Customer", "INR", NOW);
        high = new LedgerAccount(HIGH, OwnerType.MERCHANT, "m", "Merchant", "INR", NOW);
    }

    private void stubAccountsAndTransaction() {
        when(accounts.lockById(LOW)).thenReturn(Optional.of(low));
        when(accounts.lockById(HIGH)).thenReturn(Optional.of(high));
        when(transactions.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void postsBalancedTransactionAndUpdatesCachedBalances() {
        stubAccountsAndTransaction();
        UUID paymentId = UUID.randomUUID();

        LedgerTransaction txn = service.post(LedgerTransactionType.PAYMENT, paymentId,
                List.of(debit(LOW, 50_000), credit(HIGH, 50_000)));

        assertThat(txn.getPaymentId()).isEqualTo(paymentId);
        assertThat(txn.getType()).isEqualTo(LedgerTransactionType.PAYMENT);
        assertThat(low.getBalancePaise()).isEqualTo(-50_000);
        assertThat(high.getBalancePaise()).isEqualTo(50_000);

        ArgumentCaptor<LedgerEntry> saved = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(entries, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(e -> assertThat(e.getTransactionId()).isEqualTo(txn.getId()));
        assertThat(saved.getAllValues()).extracting(LedgerEntry::getDirection)
                .containsExactly(EntryDirection.DEBIT, EntryDirection.CREDIT);
    }

    @Test
    void locksAccountsInIdOrderWhateverTheLineOrder() {
        stubAccountsAndTransaction();

        // HIGH comes first in the lines, but LOW must still be locked first.
        service.post(LedgerTransactionType.REFUND, null, List.of(debit(HIGH, 10), credit(LOW, 10)));

        InOrder order = inOrder(accounts);
        order.verify(accounts).lockById(LOW);
        order.verify(accounts).lockById(HIGH);
    }

    @Test
    void accountUsedTwiceIsLockedOnce() {
        stubAccountsAndTransaction();

        service.post(LedgerTransactionType.PAYMENT, null,
                List.of(debit(LOW, 30), debit(LOW, 20), credit(HIGH, 50)));

        verify(accounts, times(1)).lockById(LOW);
        assertThat(low.getBalancePaise()).isEqualTo(-50);
    }

    @Test
    void unbalancedTransactionIsRejectedBeforeAnythingIsLockedOrWritten() {
        assertThatThrownBy(() -> service.post(LedgerTransactionType.PAYMENT, null,
                List.of(debit(LOW, 50_000), credit(HIGH, 49_999))))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("50000").hasMessageContaining("49999");
        verify(accounts, never()).lockById(any());
        verify(transactions, never()).saveAndFlush(any());
        verify(entries, never()).save(any());
    }

    @Test
    void needsAtLeastTwoLines() {
        assertThatThrownBy(() -> service.post(LedgerTransactionType.PAYMENT, null, List.of(debit(LOW, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.post(LedgerTransactionType.PAYMENT, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lineAmountsMustBePositive() {
        assertThatThrownBy(() -> debit(LOW, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> credit(LOW, -5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowingTotalsAreRejectedNotWrapped() {
        assertThatThrownBy(() -> LedgerService.requireBalanced(List.of(
                debit(LOW, Long.MAX_VALUE), debit(LOW, 1), credit(HIGH, 1))))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void unknownAccountIsNotFound() {
        when(accounts.lockById(LOW)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.post(LedgerTransactionType.PAYMENT, null, List.of(debit(LOW, 1), credit(HIGH, 1))))
                .isInstanceOf(NotFoundException.class);
        verify(entries, never()).save(any());
    }

    @Test
    void mixedCurrenciesAreRejected() {
        LedgerAccount usd = new LedgerAccount(HIGH, OwnerType.MERCHANT, "m", "USD merchant", "USD", NOW);
        when(accounts.lockById(LOW)).thenReturn(Optional.of(low));
        when(accounts.lockById(HIGH)).thenReturn(Optional.of(usd));

        assertThatThrownBy(() -> service.post(LedgerTransactionType.PAYMENT, null, List.of(debit(LOW, 1), credit(HIGH, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currency");
        verify(entries, never()).save(any());
    }
}
