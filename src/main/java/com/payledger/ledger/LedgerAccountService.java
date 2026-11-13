package com.payledger.ledger;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens and finds ledger accounts: one per merchant, one per (merchant, customer), and the
 * SYSTEM bank clearing account from migration V2.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class LedgerAccountService {

    private static final int MAX_NAME_LENGTH = 120;

    private final LedgerAccountRepository accounts;
    private final Clock clock;

    public LedgerAccountService(LedgerAccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /**
     * Opens the INR settlement account of a new merchant. Joins the caller's transaction, so a
     * merchant is never saved without its account (or the other way round).
     */
    @Transactional
    public LedgerAccount openMerchantAccount(UUID merchantId, String merchantName) {
        String name = truncate("Merchant: " + merchantName);
        LedgerAccount account = new LedgerAccount(
                UUID.randomUUID(), OwnerType.MERCHANT, merchantId.toString(), name, LedgerAccounts.INR, clock.instant());
        return accounts.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<LedgerAccount> findMerchantAccount(UUID merchantId) {
        return accounts.findByOwnerTypeAndOwnerRefAndCurrency(OwnerType.MERCHANT, merchantId.toString(), LedgerAccounts.INR);
    }

    /** The merchant's settlement account id. Every registered merchant has one. */
    @Transactional(readOnly = true)
    public UUID merchantAccountId(UUID merchantId) {
        return accounts.findIdByOwner(OwnerType.MERCHANT, merchantId.toString(), LedgerAccounts.INR)
                .orElseThrow(() -> new IllegalStateException("Merchant " + merchantId + " has no ledger account"));
    }

    /**
     * The customer's account id, created on first use. {@code INSERT ... ON CONFLICT DO NOTHING}
     * then SELECT is safe when two payments for a brand-new customer arrive together: the UNIQUE
     * (owner_type, owner_ref, currency) constraint lets only one INSERT win.
     */
    @Transactional
    public UUID customerAccountId(UUID merchantId, String customerRef) {
        String ownerRef = customerOwnerRef(merchantId, customerRef);
        accounts.insertCustomerIfAbsent(UUID.randomUUID(), ownerRef, truncate("Customer: " + customerRef),
                LedgerAccounts.INR, clock.instant());
        return accounts.findIdByOwner(OwnerType.CUSTOMER, ownerRef, LedgerAccounts.INR)
                .orElseThrow(() -> new IllegalStateException("Customer account " + ownerRef + " missing after insert"));
    }

    /** Customer accounts are scoped per merchant: {@code <merchantId>:<customerRef>}. */
    public static String customerOwnerRef(UUID merchantId, String customerRef) {
        return merchantId + ":" + customerRef;
    }

    private static String truncate(String s) {
        return s.length() <= MAX_NAME_LENGTH ? s : s.substring(0, MAX_NAME_LENGTH);
    }
}
