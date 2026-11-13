package com.payledger.payment;

import com.payledger.bank.BankChargeRequest;
import com.payledger.bank.BankGateway;
import com.payledger.bank.BankResponse;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Drives one payment through the bank:
 * <pre>
 *   [tx] CREATED → PROCESSING      (committed)
 *        call bank with timeout    (no transaction open)
 *        timeout / error?  → ask the bank with a status inquiry, never guess
 *   [tx] apply the answer          (SUCCEEDED, FAILED, or still PROCESSING)
 * </pre>
 * Deliberately not {@code @Transactional}: see {@link PaymentTransitions}.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class PaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessor.class);

    private final PaymentTransitions transitions;
    private final BankGateway bank;

    public PaymentProcessor(PaymentTransitions transitions, BankGateway bank) {
        this.transitions = transitions;
        this.bank = bank;
    }

    public PaymentStatus process(UUID paymentId) {
        BankChargeRequest request = transitions.startProcessing(paymentId);
        BankResponse result = bank.charge(request);
        if (!result.outcome().isKnown()) {
            log.info("Payment {}: bank said {}, sending status inquiry", paymentId, result.outcome());
            result = bank.inquire(paymentId);
        }
        return transitions.applyBankResult(paymentId, result);
    }

    /** Used by the inquiry job for a payment whose outcome is still unknown. */
    public PaymentStatus resolve(UUID paymentId) {
        transitions.startProcessing(paymentId);  // a payment stuck in CREATED was never sent; this makes the inquiry valid
        return transitions.applyBankResult(paymentId, bank.inquire(paymentId));
    }
}
