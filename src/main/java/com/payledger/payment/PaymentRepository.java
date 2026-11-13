package com.payledger.payment;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for {@link Payment}. Filtered lists use JPA Specifications, so only the filters
 * actually sent end up in the SQL.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    /** Scoped by merchant: a merchant can never read another merchant's payment. */
    Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId);

    /** {@code SELECT ... FOR UPDATE}: status changes from the request thread and the jobs never overlap. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> lockById(@Param("id") UUID id);

    /** Ids of payments in the given states, untouched since {@code cutoff}, oldest first. */
    @Query("select p.id from Payment p where p.status in :statuses and p.updatedAt < :cutoff order by p.updatedAt")
    List<UUID> findUnresolvedIds(@Param("statuses") Collection<PaymentStatus> statuses, @Param("cutoff") Instant cutoff,
                                 Limit limit);

    /**
     * Claims the next payment that needs its customer debit reversed, most urgent deadline first.
     * <p>
     * {@code FOR UPDATE} locks the row until our transaction ends. {@code SKIP LOCKED} makes other
     * workers <i>skip</i> rows that are already locked instead of waiting for them, so two app
     * instances running this job at the same moment pick different payments: no double reversal,
     * and no worker sits idle behind another one's lock. (Uses idx_payments_status_reverse_by.)
     *
     * @param excluded ids to skip in this run (payments whose reversal already failed); never empty
     */
    @Query(value = """
            SELECT * FROM payments
            WHERE status = 'FAILED' AND reverse_by IS NOT NULL AND id NOT IN (:excluded)
            ORDER BY reverse_by
            LIMIT 1
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    Optional<Payment> lockNextForReversal(@Param("excluded") Collection<UUID> excluded);

    /** Debited-but-failed payments whose T+1 deadline has already passed without a reversal. */
    long countByStatusAndReverseByBefore(PaymentStatus status, Instant now);

    /** {@code SELECT ... FOR UPDATE} on one payment, for operations that must not overlap (refunds). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.merchantId = :merchantId")
    Optional<Payment> lockByIdAndMerchantId(@Param("id") UUID id, @Param("merchantId") UUID merchantId);
}
