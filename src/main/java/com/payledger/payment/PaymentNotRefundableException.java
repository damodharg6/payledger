package com.payledger.payment;

import com.payledger.common.ConflictException;
import java.util.UUID;

/**
 * Only SUCCEEDED payments can be refunded (HTTP 409). A failed payment's money is returned by
 * the automatic reversal instead.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class PaymentNotRefundableException extends ConflictException {

    public PaymentNotRefundableException(UUID paymentId, PaymentStatus status) {
        super("Payment " + paymentId + " is " + status + "; only SUCCEEDED payments can be refunded");
    }
}
