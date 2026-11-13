package com.payledger.ledger;

/**
 * Who a ledger account belongs to.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public enum OwnerType {
    /** A paying customer, keyed by the merchant-supplied customer reference. */
    CUSTOMER,
    /** A merchant's settlement account, keyed by merchant id. */
    MERCHANT,
    /** PayLedger's own accounts, such as bank clearing. */
    SYSTEM
}
