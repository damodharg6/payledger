package com.payledger.payment;

import com.payledger.bank.BankChargeRequest;
import com.payledger.bank.BankResponse;
import com.payledger.common.NotFoundException;
import com.payledger.ledger.LedgerPostingService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The short database transactions around a bank call. Each method locks the payment row, checks
 * the current status, changes it through the state machine and posts to the ledger, all in one
 * transaction. No method here talks to the bank: holding a database transaction (and row locks)
 * open while waiting seconds for a bank would block everyone else.
 * <p>
 * {@link #applyBankResult} is <b>idempotent</b>: the request thread and the inquiry job may both
 * learn the outcome of the same payment. Whoever comes second sees the status is no longer
 * PROCESSING and does nothing. (The database's "charge once" index is a second guard.)
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class PaymentTransitions {

    private static final Logger log = LoggerFactory.getLogger(PaymentTransitions.class);

    private final PaymentRepository payments;
    private final LedgerPostingService postings;
    private final Clock clock;

    public PaymentTransitions(PaymentRepository payments, LedgerPostingService postings, Clock clock) {
        this.payments = payments;
        this.postings = postings;
        this.clock = clock;
    }

    /** CREATED → PROCESSING, committed before the bank is called. Returns what to send to the bank. */
    @Transactional
    public BankChargeRequest startProcessing(UUID paymentId) {
        Payment payment = lock(paymentId);
        if (payment.getStatus() == PaymentStatus.CREATED) {
            payment.startProcessing(clock.instant());
        }
        return new BankChargeRequest(payment.getId(), payment.getCustomerRef(), payment.getAmountPaise());
    }

    /**
     * Applies a bank answer to a PROCESSING payment and returns the resulting status.
     * <ul>
     *   <li>SUCCESS → SUCCEEDED + ledger: D customer, C merchant</li>
     *   <li>DECLINED / NOT_FOUND → FAILED, no money moved, nothing to reverse</li>
     *   <li>DEBITED_NOT_CREDITED → FAILED with reverse_by = T+1 + ledger: D customer, C clearing</li>
     *   <li>PENDING / TIMEOUT → stays PROCESSING; the inquiry job asks again later</li>
     * </ul>
     */
    @Transactional
    public PaymentStatus applyBankResult(UUID paymentId, BankResponse result) {
        Payment payment = lock(paymentId);
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            log.debug("Payment {} already {}; ignoring bank result {}", paymentId, payment.getStatus(), result.outcome());
            return payment.getStatus();
        }
        Instant now = clock.instant();
        switch (result.outcome()) {
            case SUCCESS -> {
                payment.succeed(result.bankReference(), now);
                postings.recordPaymentSucceeded(paymentId, payment.getMerchantId(), payment.getCustomerRef(),
                        payment.getAmountPaise());
            }
            case DECLINED, NOT_FOUND -> payment.fail(result.message(), result.bankReference(), null, now);
            case DEBITED_NOT_CREDITED -> {
                Instant deadline = ReversalDeadline.tPlusOne(now);
                payment.fail(result.message(), result.bankReference(), deadline, now);
                postings.recordDebitHeldForFailedPayment(paymentId, payment.getMerchantId(), payment.getCustomerRef(),
                        payment.getAmountPaise());
                log.warn("Payment {}: customer debited but payment failed; must reverse before {}", paymentId, deadline);
            }
            case PENDING, TIMEOUT -> log.info("Payment {} outcome still unknown ({}); stays PROCESSING",
                    paymentId, result.message());
        }
        return payment.getStatus();
    }

    /** CREATED or PROCESSING payments not touched since {@code cutoff}: candidates for an inquiry. */
    @Transactional(readOnly = true)
    public List<UUID> findUnresolved(Instant cutoff, int limit) {
        return payments.findUnresolvedIds(List.of(PaymentStatus.CREATED, PaymentStatus.PROCESSING), cutoff, Limit.of(limit));
    }

    private Payment lock(UUID paymentId) {
        return payments.lockById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment " + paymentId + " not found"));
    }
}
