package com.payledger.payment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full view of one payment, including its status timeline and the total refunded so far.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record PaymentResponse(
        UUID id,
        UUID merchantId,
        long amountPaise,
        String currency,
        PaymentStatus status,
        String customerRef,
        String bankReference,
        String failureReason,
        Instant reverseBy,
        Instant createdAt,
        Instant updatedAt,
        long refundedPaise,
        List<TimelineEntry> timeline) {

    /**
     * One step in the timeline.
     *
     * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
     */
    public record TimelineEntry(PaymentStatus from, PaymentStatus to, String reason, Instant at) {
    }

    /** For a payment with no refunds. Must be called inside a transaction (lazy timeline). */
    public static PaymentResponse from(Payment p) {
        return from(p, 0L);
    }

    /** Must be called inside a transaction: the timeline is loaded lazily. */
    public static PaymentResponse from(Payment p, long refundedPaise) {
        List<TimelineEntry> timeline = p.getHistory().stream()
                .map(h -> new TimelineEntry(h.getFromStatus(), h.getToStatus(), h.getReason(), h.getChangedAt()))
                .toList();
        return new PaymentResponse(p.getId(), p.getMerchantId(), p.getAmountPaise(), p.getCurrency(), p.getStatus(),
                p.getCustomerRef(), p.getBankReference(), p.getFailureReason(), p.getReverseBy(),
                p.getCreatedAt(), p.getUpdatedAt(), refundedPaise, timeline);
    }
}
