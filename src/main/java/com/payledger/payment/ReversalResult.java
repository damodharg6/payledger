package com.payledger.payment;

import java.time.Instant;
import java.util.UUID;

/**
 * One completed reversal.
 *
 * @param paymentId   the payment whose debit was returned
 * @param amountPaise the amount given back to the customer
 * @param reverseBy   the T+1 deadline
 * @param reversedAt  when the reversal was posted
 * @param late        true if the deadline had already passed (an RBI breach to investigate)
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record ReversalResult(UUID paymentId, long amountPaise, Instant reverseBy, Instant reversedAt, boolean late) {
}
