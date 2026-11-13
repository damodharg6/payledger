package com.payledger.ledger;

import java.time.Instant;
import java.util.UUID;

/**
 * One ledger entry as shown by {@code GET /api/v1/ledger/accounts/{id}/entries}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record LedgerEntryView(
        Long id,
        UUID transactionId,
        LedgerTransactionType transactionType,
        UUID paymentId,
        EntryDirection direction,
        long amountPaise,
        Instant createdAt) {
}
