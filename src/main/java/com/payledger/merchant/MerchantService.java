package com.payledger.merchant;

import com.payledger.common.NotFoundException;
import com.payledger.ledger.LedgerAccount;
import com.payledger.ledger.LedgerAccountService;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers merchants and reads their profile.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class MerchantService {

    private final MerchantRepository merchants;
    private final LedgerAccountService ledgerAccounts;
    private final ApiKeys apiKeys;
    private final Clock clock;

    public MerchantService(MerchantRepository merchants, LedgerAccountService ledgerAccounts, ApiKeys apiKeys, Clock clock) {
        this.merchants = merchants;
        this.ledgerAccounts = ledgerAccounts;
        this.apiKeys = apiKeys;
        this.clock = clock;
    }

    /**
     * Creates the merchant and its ledger account in one database transaction, and returns the
     * plain API key exactly once. The quick {@code existsByEmail} check gives a clear error; the
     * UNIQUE constraint on email still protects against two registrations racing each other.
     */
    @Transactional
    public RegisterMerchantResponse register(RegisterMerchantRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (merchants.existsByEmail(email)) {
            throw new DuplicateMerchantException(email);
        }
        String apiKey = apiKeys.generate();
        Merchant merchant = new Merchant(UUID.randomUUID(), request.name().trim(), email,
                ApiKeys.sha256(apiKey), blankToNull(request.webhookUrl()), clock.instant());
        merchants.saveAndFlush(merchant);

        LedgerAccount account = ledgerAccounts.openMerchantAccount(merchant.getId(), merchant.getName());
        return new RegisterMerchantResponse(merchant.getId(), merchant.getName(), merchant.getEmail(),
                apiKey, account.getId(), merchant.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public MerchantResponse get(UUID merchantId) {
        Merchant m = merchants.findById(merchantId)
                .orElseThrow(() -> new NotFoundException("Merchant " + merchantId + " not found"));
        UUID accountId = ledgerAccounts.findMerchantAccount(merchantId).map(LedgerAccount::getId).orElse(null);
        return new MerchantResponse(m.getId(), m.getName(), m.getEmail(), m.getWebhookUrl(), accountId, m.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
