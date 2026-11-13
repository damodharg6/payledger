package com.payledger.ledger;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens and finds ledger accounts. Posting money (debits and credits) comes in Phase 3.
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

    private static String truncate(String s) {
        return s.length() <= MAX_NAME_LENGTH ? s : s.substring(0, MAX_NAME_LENGTH);
    }
}
