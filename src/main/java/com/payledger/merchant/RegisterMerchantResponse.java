package com.payledger.merchant;

import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /api/v1/merchants}. This is the only time the plain API key leaves
 * the server.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record RegisterMerchantResponse(
        UUID merchantId,
        String name,
        String email,
        String apiKey,
        UUID ledgerAccountId,
        Instant createdAt) {
}
