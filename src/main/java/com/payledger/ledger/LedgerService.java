package com.payledger.ledger;

import com.payledger.common.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Posts double-entry transactions. This is the only class that writes ledger entries or changes
 * cached balances.
 * <p>
 * For every posting, in one database transaction:
 * <ol>
 *   <li><b>Check balance:</b> total debits must equal total credits, or nothing is written.</li>
 *   <li><b>Lock accounts in a fixed order</b> (sorted by id) with {@code SELECT ... FOR UPDATE}.
 *       If thread 1 locks A then B while thread 2 locks B then A, each waits for the other
 *       forever: a deadlock. When everyone locks in the same global order, that cycle cannot
 *       form. Any fixed order works; we use Java's {@code UUID} order.</li>
 *   <li><b>Write</b> the transaction and its append-only entries.</li>
 *   <li><b>Update the cached balances</b> of the locked accounts, in the same transaction, so
 *       the cache can never disagree with the entries.</li>
 * </ol>
 * It joins the caller's transaction ({@code REQUIRED}), so a payment's status change and its
 * ledger posting commit or roll back together.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class LedgerService {

    private final LedgerAccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final LedgerEntryRepository entries;
    private final Clock clock;

    public LedgerService(LedgerAccountRepository accounts, LedgerTransactionRepository transactions,
                         LedgerEntryRepository entries, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional
    public LedgerTransaction post(LedgerTransactionType type, UUID paymentId, List<LedgerLine> lines) {
        requireBalanced(lines);

        List<UUID> lockOrder = lines.stream().map(LedgerLine::accountId).distinct().sorted().toList();
        Map<UUID, LedgerAccount> locked = new HashMap<>();
        for (UUID accountId : lockOrder) {
            locked.put(accountId, accounts.lockById(accountId)
                    .orElseThrow(() -> new NotFoundException("Ledger account " + accountId + " not found")));
        }
        long currencies = locked.values().stream().map(LedgerAccount::getCurrency).distinct().count();
        if (currencies != 1) {
            throw new IllegalArgumentException("All accounts in one ledger transaction must use the same currency");
        }

        Instant now = clock.instant();
        // Flush now: entries use IDENTITY ids, so they are inserted at once and need this row to exist.
        LedgerTransaction txn = transactions.saveAndFlush(new LedgerTransaction(UUID.randomUUID(), paymentId, type, now));
        for (LedgerLine line : lines) {
            entries.save(new LedgerEntry(txn.getId(), line.accountId(), line.direction(), line.amountPaise(), now));
            locked.get(line.accountId()).apply(line.direction(), line.amountPaise());
        }
        return txn;
    }

    /** Throws unless there are at least two lines and debits equal credits (overflow-safe). */
    static void requireBalanced(List<LedgerLine> lines) {
        if (lines == null || lines.size() < 2) {
            throw new IllegalArgumentException("A ledger transaction needs at least two lines");
        }
        long debits = 0;
        long credits = 0;
        for (LedgerLine line : lines) {
            if (line.direction() == EntryDirection.DEBIT) {
                debits = Math.addExact(debits, line.amountPaise());
            } else {
                credits = Math.addExact(credits, line.amountPaise());
            }
        }
        if (debits != credits) {
            throw new UnbalancedTransactionException(debits, credits);
        }
    }
}
