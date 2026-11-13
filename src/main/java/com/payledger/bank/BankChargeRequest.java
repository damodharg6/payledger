package com.payledger.bank;

import java.util.UUID;

/**
 * A request to debit a customer. {@code paymentId} doubles as the merchant reference the bank
 * uses to find (and de-duplicate) the charge.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record BankChargeRequest(UUID paymentId, String customerRef, long amountPaise) {
}
