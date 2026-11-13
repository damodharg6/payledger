package com.payledger.ledger;

import java.util.Objects;
import java.util.UUID;

/**
 * One line of a ledger transaction before it is posted: "debit (or credit) this account by this
 * many paise".
 *
 * @param accountId   the ledger account
 * @param direction   debit or credit
 * @param amountPaise always positive; the direction says which way the money moves
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record LedgerLine(UUID accountId, EntryDirection direction, long amountPaise) {

    public LedgerLine {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(direction, "direction");
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive, was " + amountPaise);
        }
    }

    public static LedgerLine debit(UUID accountId, long amountPaise) {
        return new LedgerLine(accountId, EntryDirection.DEBIT, amountPaise);
    }

    public static LedgerLine credit(UUID accountId, long amountPaise) {
        return new LedgerLine(accountId, EntryDirection.CREDIT, amountPaise);
    }
}
