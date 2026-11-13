package com.payledger.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * One refund, plus how much of the payment has been refunded in total and how much is left.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record RefundResponse(
        UUID id,
        UUID paymentId,
        long amountPaise,
        String reason,
        Instant createdAt,
        long totalRefundedPaise,
        long refundablePaise) {

    static RefundResponse of(Refund refund, long paymentAmountPaise, long totalRefundedPaise) {
        return new RefundResponse(refund.getId(), refund.getPaymentId(), refund.getAmountPaise(), refund.getReason(),
                refund.getCreatedAt(), totalRefundedPaise, paymentAmountPaise - totalRefundedPaise);
    }
}
