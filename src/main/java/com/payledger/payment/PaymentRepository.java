package com.payledger.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link Payment}. Phase 2 adds the filtered, paginated queries.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
