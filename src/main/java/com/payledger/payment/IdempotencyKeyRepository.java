package com.payledger.payment;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for {@link IdempotencyKey}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, IdempotencyKeyId> {
}
