package com.payledger.ledger;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for {@link LedgerEntry}, plus the sums that prove the ledger is correct.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /** Total debits and credits of the whole ledger. */
    interface Totals {
        Long getDebits();

        Long getCredits();
    }

    /** The trial balance: in a correct ledger, debits always equal credits. */
    @Query(value = """
            SELECT CAST(COALESCE(SUM(amount_paise) FILTER (WHERE direction = 'D'), 0) AS BIGINT) AS debits,
                   CAST(COALESCE(SUM(amount_paise) FILTER (WHERE direction = 'C'), 0) AS BIGINT) AS credits
            FROM ledger_entries""", nativeQuery = true)
    Totals totals();

    /** An account's balance recomputed from its entries (credits − debits): the source of truth. */
    @Query(value = """
            SELECT CAST(COALESCE(SUM(CASE WHEN direction = 'C' THEN amount_paise ELSE -amount_paise END), 0) AS BIGINT)
            FROM ledger_entries WHERE account_id = :accountId""", nativeQuery = true)
    long balanceFromEntries(@Param("accountId") UUID accountId);

    /** Newest first, joined with the transaction so each row says what it was for. */
    @Query(value = """
            select new com.payledger.ledger.LedgerEntryView(
                e.id, e.transactionId, t.type, t.paymentId, e.direction, e.amountPaise, e.createdAt)
            from LedgerEntry e join LedgerTransaction t on t.id = e.transactionId
            where e.accountId = :accountId
            order by e.id desc""",
            countQuery = "select count(e) from LedgerEntry e where e.accountId = :accountId")
    Page<LedgerEntryView> findViewsByAccountId(@Param("accountId") UUID accountId, Pageable pageable);
}
