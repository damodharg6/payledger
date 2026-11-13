package com.payledger.ledger;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link LedgerTransaction}. Phase 3 adds the ledger queries.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
}
