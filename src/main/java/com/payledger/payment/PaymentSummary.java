package com.payledger.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * One row in the payments list (no timeline, so listing never runs one query per row).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record PaymentSummary(
        UUID id,
        long amountPaise,
        String currency,
        PaymentStatus status,
        String customerRef,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentSummary from(Payment p) {
        return new PaymentSummary(p.getId(), p.getAmountPaise(), p.getCurrency(), p.getStatus(),
                p.getCustomerRef(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
