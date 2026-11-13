package com.payledger.bank;

import java.util.UUID;

/**
 * The bank / UPI network as PayLedger sees it. v1 ships only {@link MockBankClient}; a real
 * integration would implement this same interface.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public interface BankClient {

    /** Debits the customer. May be slow, may throw. Charging the same payment twice is a no-op at the bank. */
    BankResponse charge(BankChargeRequest request);

    /** Asks what really happened to a charge. Used after a timeout instead of guessing. */
    BankResponse inquire(UUID paymentId);
}
