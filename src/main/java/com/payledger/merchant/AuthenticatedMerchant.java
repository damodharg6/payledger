package com.payledger.merchant;

import java.util.UUID;

/**
 * The security principal for a request authenticated with an API key. Controllers receive it
 * with {@code @AuthenticationPrincipal} and must only show this merchant's data.
 *
 * @param merchantId the authenticated merchant
 * @param name       the merchant's name (for logs)
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record AuthenticatedMerchant(UUID merchantId, String name) {
}
