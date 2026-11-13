package com.payledger.bank;

/**
 * The bank's answer to a charge or a status inquiry.
 *
 * @param outcome       what happened
 * @param bankReference the bank's id for the charge (null if the bank never saw it)
 * @param message       human-readable detail, stored as the failure reason when the payment fails
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record BankResponse(BankOutcome outcome, String bankReference, String message) {

    public static BankResponse timeout() {
        return new BankResponse(BankOutcome.TIMEOUT, null, "No answer from bank in time");
    }

    public static BankResponse pending(String message) {
        return new BankResponse(BankOutcome.PENDING, null, message);
    }

    public static BankResponse notFound() {
        return new BankResponse(BankOutcome.NOT_FOUND, null, "Bank has no record of this charge");
    }
}
