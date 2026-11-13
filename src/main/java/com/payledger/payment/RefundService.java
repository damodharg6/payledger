package com.payledger.payment;

import com.payledger.common.NotFoundException;
import com.payledger.ledger.LedgerPostingService;
import com.payledger.ledger.LedgerTransaction;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refunds a succeeded payment, fully or in parts, never more than the original amount.
 * <p>
 * <b>Why lock the payment row?</b> Two refunds of 60% arriving together would each read
 * "0% refunded so far", each pass the check, and together refund 120%. Taking
 * {@code SELECT ... FOR UPDATE} on the payment first makes refunds of the same payment run one
 * after another, so the second one sees the first. (This is a pessimistic lock: we expect
 * conflicts and wait, instead of retrying after a failed version check.)
 * <p>
 * Refunds also take an Idempotency-Key, checked while holding the same lock, so a retried
 * refund is replayed instead of refunding twice.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final LedgerPostingService postings;
    private final Clock clock;

    public RefundService(PaymentRepository payments, RefundRepository refunds, LedgerPostingService postings, Clock clock) {
        this.payments = payments;
        this.refunds = refunds;
        this.postings = postings;
        this.clock = clock;
    }

    @Transactional
    public RefundResult refund(UUID merchantId, UUID paymentId, String idempotencyKey, CreateRefundRequest request) {
        Payment payment = payments.lockByIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new NotFoundException("Payment " + paymentId + " not found"));
        long amount = request.amountPaise();

        var existing = refunds.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey);
        if (existing.isPresent()) {
            Refund previous = existing.get();
            boolean sameRequest = previous.getPaymentId().equals(paymentId) && previous.getAmountPaise() == amount
                    && Objects.equals(previous.getReason(), blankToNull(request.reason()));
            if (!sameRequest) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            long total = refunds.sumAmountByPaymentId(paymentId);
            return new RefundResult(RefundResponse.of(previous, payment.getAmountPaise(), total), true);
        }

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new PaymentNotRefundableException(paymentId, payment.getStatus());
        }
        long alreadyRefunded = refunds.sumAmountByPaymentId(paymentId);
        long refundable = payment.getAmountPaise() - alreadyRefunded;
        if (amount > refundable) {
            throw new RefundExceedsPaymentException(amount, refundable);
        }

        LedgerTransaction txn = postings.recordRefund(paymentId, merchantId, payment.getCustomerRef(), amount);
        Refund refund = refunds.save(new Refund(UUID.randomUUID(), paymentId, merchantId, amount,
                blankToNull(request.reason()), idempotencyKey, txn.getId(), clock.instant()));
        log.info("Refunded {} paise of payment {} ({} of {} now refunded)", amount, paymentId,
                alreadyRefunded + amount, payment.getAmountPaise());
        return new RefundResult(RefundResponse.of(refund, payment.getAmountPaise(), alreadyRefunded + amount), false);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
