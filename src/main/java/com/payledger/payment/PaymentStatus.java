package com.payledger.payment;

/**
 * Lifecycle of a payment: CREATED → PROCESSING → SUCCEEDED or FAILED; FAILED → REVERSED.
 * Phase 2 adds the rules that allow only these moves.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public enum PaymentStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REVERSED
}
