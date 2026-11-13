package com.payledger.ledger;

/**
 * Total debits and credits of the whole ledger. In a correct ledger they are always equal.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record TrialBalance(long debitsPaise, long creditsPaise) {

    public boolean balanced() {
        return debitsPaise == creditsPaise;
    }
}
