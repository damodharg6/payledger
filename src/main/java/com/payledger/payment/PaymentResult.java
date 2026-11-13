package com.payledger.payment;

/**
 * Result of a create-payment call, with the HTTP status to answer with and whether it is a
 * replay of an earlier request.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record PaymentResult(PaymentResponse payment, int httpStatus, boolean replayed) {
}
