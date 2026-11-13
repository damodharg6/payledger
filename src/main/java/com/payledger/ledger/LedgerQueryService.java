package com.payledger.ledger;

import com.payledger.common.BadRequestException;
import com.payledger.common.NotFoundException;
import com.payledger.common.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the ledger: balances, entries and the trial balance.
 * <p>
 * A merchant may read its own settlement account and its own customers' accounts. Anything
 * else (another merchant's accounts, SYSTEM accounts) is reported as 404 so nothing leaks.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class LedgerQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final LedgerAccountRepository accounts;
    private final LedgerEntryRepository entries;

    public LedgerQueryService(LedgerAccountRepository accounts, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public AccountBalance balance(UUID merchantId, UUID accountId) {
        LedgerAccount account = visibleAccount(merchantId, accountId);
        long fromEntries = entries.balanceFromEntries(accountId);
        return new AccountBalance(account.getId(), account.getOwnerType(), account.getName(), account.getCurrency(),
                account.getBalancePaise(), fromEntries, account.getBalancePaise() == fromEntries);
    }

    @Transactional(readOnly = true)
    public PageResponse<LedgerEntryView> entries(UUID merchantId, UUID accountId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be >= 0 and size between 1 and " + MAX_PAGE_SIZE);
        }
        visibleAccount(merchantId, accountId);
        return PageResponse.of(entries.findViewsByAccountId(accountId, PageRequest.of(page, size)), v -> v);
    }

    /** Whole-ledger check used by tests and (later) monitoring: debits must equal credits. */
    @Transactional(readOnly = true)
    public TrialBalance trialBalance() {
        LedgerEntryRepository.Totals totals = entries.totals();
        return new TrialBalance(totals.getDebits(), totals.getCredits());
    }

    private LedgerAccount visibleAccount(UUID merchantId, UUID accountId) {
        return accounts.findById(accountId)
                .filter(a -> isVisibleTo(a, merchantId))
                .orElseThrow(() -> new NotFoundException("Ledger account " + accountId + " not found"));
    }

    static boolean isVisibleTo(LedgerAccount account, UUID merchantId) {
        return switch (account.getOwnerType()) {
            case MERCHANT -> account.getOwnerRef().equals(merchantId.toString());
            case CUSTOMER -> account.getOwnerRef().startsWith(merchantId + ":");
            case SYSTEM -> false;
        };
    }
}
