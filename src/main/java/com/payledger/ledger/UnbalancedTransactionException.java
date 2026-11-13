package com.payledger.ledger;

/**
 * A ledger transaction whose total debits do not equal its total credits. This is always a
 * programming error, never bad user input, so it is not mapped to a 4xx status.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public class UnbalancedTransactionException extends IllegalArgumentException {

    public UnbalancedTransactionException(long debitsPaise, long creditsPaise) {
        super("Ledger transaction is unbalanced: debits " + debitsPaise + " != credits " + creditsPaise);
    }
}
