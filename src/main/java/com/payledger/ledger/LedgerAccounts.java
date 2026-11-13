package com.payledger.ledger;

import java.util.UUID;

/**
 * Fixed ledger accounts and constants. The bank clearing account is created by migration V2.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public final class LedgerAccounts {

    /** Holds money taken from a customer whose payment failed, until it is reversed. */
    public static final UUID BANK_CLEARING_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** v1 supports Indian rupees only. */
    public static final String INR = "INR";

    private LedgerAccounts() {
    }
}
