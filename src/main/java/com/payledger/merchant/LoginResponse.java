package com.payledger.merchant;

import java.util.UUID;

/**
 * A signed access token for the dashboard. The client sends it back as
 * {@code Authorization: Bearer <accessToken>}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UUID merchantId,
        String merchantName,
        String email,
        DashboardUser.Role role) {
}
