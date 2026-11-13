package com.payledger.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link LedgerEntry}. Phase 3 adds the ledger queries.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
}
