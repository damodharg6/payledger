package com.payledger.payment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/payments/{id}/refunds}.
 *
 * @param amountPaise amount to refund in paise; partial refunds are allowed
 * @param reason      optional note shown on the dashboard
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record CreateRefundRequest(
        @NotNull @Positive Long amountPaise,
        @Size(max = 255) String reason) {
}
