package com.payledger.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/v1/payments}.
 *
 * @param amountPaise amount in paise (₹500.75 = 50075); at most ₹1 crore per payment
 * @param currency    optional, only {@code INR} in v1
 * @param customerRef the merchant's id for the paying customer (one ledger account per customer)
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
public record CreatePaymentRequest(
        @NotNull @Positive @Max(MAX_AMOUNT_PAISE) Long amountPaise,
        @Pattern(regexp = "INR", message = "only INR is supported") String currency,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:@-]+", message = "letters, digits and . _ : @ - only")
        String customerRef) {

    /** ₹1,00,00,000 (1 crore) in paise. */
    public static final long MAX_AMOUNT_PAISE = 1_000_000_000L;

    public String currencyOrDefault() {
        return currency == null ? "INR" : currency;
    }
}
