package com.payledger.payment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
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

    /** {@code SELECT ... FOR UPDATE} on one payment, for operations that must not overlap (refunds). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id and p.merchantId = :merchantId")
    Optional<Payment> lockByIdAndMerchantId(@Param("id") UUID id, @Param("merchantId") UUID merchantId);
}
