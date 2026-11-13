package com.payledger.payment;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Database access for {@link Payment}. Filtered lists use JPA Specifications, so only the filters
 * actually sent end up in the SQL.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID>, JpaSpecificationExecutor<Payment> {

    /** Scoped by merchant: a merchant can never read another merchant's payment. */
    Optional<Payment> findByIdAndMerchantId(UUID id, UUID merchantId);
}
