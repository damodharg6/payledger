package com.payledger.ledger;

import java.util.UUID;

/**
 * Response of {@code GET /api/v1/ledger/accounts/{id}/balance}. It returns both the cached
 * balance and the balance recomputed from the entries; {@code consistent} says whether they agree.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record AccountBalance(
        UUID accountId,
        OwnerType ownerType,
        String name,
        String currency,
        long balancePaise,
        long balanceFromEntriesPaise,
        boolean consistent) {
}
