package com.payledger.payment;

import java.util.UUID;

/**
 * Reversing one payment failed; its transaction was rolled back and nothing changed. Carries the
 * payment id so the job can skip it for the rest of the run and retry it next run.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class ReversalFailedException extends RuntimeException {

    private final UUID paymentId;

    public ReversalFailedException(UUID paymentId, Throwable cause) {
        super("Reversal of payment " + paymentId + " failed: " + cause.getMessage(), cause);
        this.paymentId = paymentId;
    }

    public UUID paymentId() {
        return paymentId;
    }
}
