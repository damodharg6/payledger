package com.payledger.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Body of {@code POST /api/v1/merchants}.
 *
 * @param name       shop name shown on the dashboard
 * @param email      contact email; one merchant per email
 * @param webhookUrl optional URL for payment notifications (used in v2)
 * @param dashboardPassword optional; if given, a dashboard ADMIN login is created for this email.
 *                          12-72 characters (BCrypt only uses the first 72 bytes)
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record RegisterMerchantRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 500) @URL(protocol = "https", message = "must be an https URL") String webhookUrl,
        @Size(min = 12, max = 72) String dashboardPassword) {

    /** For callers that do not want a dashboard login. */
    public RegisterMerchantRequest(String name, String email, String webhookUrl) {
        this(name, email, webhookUrl, null);
    }
}
