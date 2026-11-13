package com.payledger.payment;

/**
 * Result of a refund call and whether it replays an earlier one with the same key.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record RefundResult(RefundResponse refund, boolean replayed) {
}
