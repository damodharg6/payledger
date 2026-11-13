package com.payledger.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/**
 * Composite primary key of {@link IdempotencyKey}: (merchant, key). Two merchants may use the
 * same key string; one merchant may not use it twice.
 *
 * @param merchantId     the merchant that sent the request
 * @param idempotencyKey the value of the {@code Idempotency-Key} header
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Embeddable
public record IdempotencyKeyId(
        @Column(name = "merchant_id", nullable = false) UUID merchantId,
        @Column(name = "idempotency_key", nullable = false, length = 100) String idempotencyKey) {
}
