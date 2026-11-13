package com.payledger.payment;

import com.payledger.common.ConflictException;
import java.util.UUID;

/**
 * Someone tried a status change the state machine does not allow (HTTP 409), for example
 * SUCCEEDED → FAILED or CREATED → SUCCEEDED.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class InvalidPaymentTransitionException extends ConflictException {

    private final PaymentStatus from;
    private final PaymentStatus to;

    public InvalidPaymentTransitionException(UUID paymentId, PaymentStatus from, PaymentStatus to) {
        super("Payment " + paymentId + " cannot move from " + from + " to " + to);
        this.from = from;
        this.to = to;
    }

    public PaymentStatus getFrom() {
        return from;
    }

    public PaymentStatus getTo() {
        return to;
    }
}
