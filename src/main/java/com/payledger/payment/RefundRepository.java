package com.payledger.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Database access for {@link Refund}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @Query("select coalesce(sum(r.amountPaise), 0) from Refund r where r.paymentId = :paymentId")
    long sumAmountByPaymentId(@Param("paymentId") UUID paymentId);

    Optional<Refund> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);
}
