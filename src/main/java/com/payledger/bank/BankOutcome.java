package com.payledger.bank;

/**
 * What the bank says happened to a charge.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public enum BankOutcome {
    /** Customer debited and merchant credited. */
    SUCCESS(true),
    /** Rejected before any money moved (e.g. insufficient funds). Nothing to reverse. */
    DECLINED(true),
    /** The classic "money debited, payment failed": the customer lost money and must get it back by T+1. */
    DEBITED_NOT_CREDITED(true),
    /** (Inquiry only) the bank never received this charge, so no money moved. */
    NOT_FOUND(true),
    /** The bank does not know yet. Never treat this as failed; ask again later. */
    PENDING(false),
    /** We gave up waiting. The charge may or may not have happened; we must ask (inquire). */
    TIMEOUT(false);

    private final boolean known;

    BankOutcome(boolean known) {
        this.known = known;
    }

    /** True when we know for sure whether money moved. */
    public boolean isKnown() {
        return known;
    }
}
