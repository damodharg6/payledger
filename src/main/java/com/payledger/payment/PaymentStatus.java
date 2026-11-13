package com.payledger.payment;

/**
 * Lifecycle of a payment, as a state machine. The only allowed moves are:
 * <pre>
 *   CREATED ──► PROCESSING ──► SUCCEEDED
 *                    │
 *                    └──────► FAILED ──► REVERSED
 * </pre>
 * SUCCEEDED and REVERSED are final. Refunds do not change the status; they are tracked in the
 * ledger.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public enum PaymentStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REVERSED;

    /** True only for the moves drawn above. A switch makes the compiler check every state. */
    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case CREATED -> next == PROCESSING;
            case PROCESSING -> next == SUCCEEDED || next == FAILED;
            case FAILED -> next == REVERSED;
            case SUCCEEDED, REVERSED -> false;
        };
    }

    public boolean isFinal() {
        return this == SUCCEEDED || this == REVERSED;
    }
}
