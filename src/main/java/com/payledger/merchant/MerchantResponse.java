package com.payledger.merchant;

import java.time.Instant;
import java.util.UUID;

/**
 * Public view of a merchant. Never contains the API key or its hash.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record MerchantResponse(
        UUID merchantId,
        String name,
        String email,
        String webhookUrl,
        UUID ledgerAccountId,
        Instant createdAt) {
}
