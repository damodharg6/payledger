package com.payledger.payment;

import com.payledger.common.UnprocessableEntityException;

/**
 * The refund would make the total refunded larger than the payment (HTTP 422).
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class RefundExceedsPaymentException extends UnprocessableEntityException {

    public RefundExceedsPaymentException(long requestedPaise, long refundablePaise) {
        super("Refund of " + requestedPaise + " paise exceeds the refundable amount of " + refundablePaise + " paise");
    }
}
