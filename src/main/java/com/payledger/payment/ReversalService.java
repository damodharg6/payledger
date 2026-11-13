package com.payledger.payment;

import com.payledger.common.NotFoundException;
import com.payledger.ledger.LedgerPostingService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gives customers their money back when they were debited but the payment failed.
 * <p>
 * Each reversal is one database transaction: lock the payment, post the REVERSAL to the ledger
 * (D bank clearing, C customer), move the payment FAILED → REVERSED, commit. Either all of it
 * happens or none of it. One transaction per payment also means one bad payment cannot block
 * the others.
 * <p>
 * Three guards stop a double reversal: the {@code SKIP LOCKED} row lock, the state machine
 * (only FAILED can become REVERSED), and the database's "reverse once" unique index.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class ReversalService {

    private static final Logger log = LoggerFactory.getLogger(ReversalService.class);
    /** NOT IN () is invalid SQL, so an empty exclusion list is replaced by an id that never exists. */
    private static final List<UUID> NOTHING_EXCLUDED = List.of(new UUID(0, 0));

    private final PaymentRepository payments;
    private final LedgerPostingService postings;
    private final Clock clock;

    public ReversalService(PaymentRepository payments, LedgerPostingService postings, Clock clock) {
        this.payments = payments;
        this.postings = postings;
        this.clock = clock;
    }

    /**
     * Claims and reverses the most urgent pending reversal that no other worker holds.
     *
     * @return the reversal, or empty when nothing is left to do
     * @throws ReversalFailedException if this payment could not be reversed (rolled back)
     */
    @Transactional
    public Optional<ReversalResult> reverseNext(Collection<UUID> excluded) {
        return payments.lockNextForReversal(excluded.isEmpty() ? NOTHING_EXCLUDED : excluded)
                .map(this::reverse);
    }

    /** Reverses one specific payment now (used by tests, and later by an admin "reverse now" action). */
    @Transactional
    public ReversalResult reversePayment(UUID paymentId) {
        Payment payment = payments.lockById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment " + paymentId + " not found"));
        if (!payment.needsReversal()) {
            throw new IllegalStateException("Payment " + paymentId + " is " + payment.getStatus() + " and needs no reversal");
        }
        return reverse(payment);
    }

    /** Overdue reversals: debited, failed, deadline passed, still not reversed. */
    @Transactional(readOnly = true)
    public long countOverdue() {
        return payments.countByStatusAndReverseByBefore(PaymentStatus.FAILED, clock.instant());
    }

    private ReversalResult reverse(Payment payment) {
        try {
            Instant now = clock.instant();
            postings.recordReversal(payment.getId(), payment.getMerchantId(), payment.getCustomerRef(), payment.getAmountPaise());
            payment.reverse(now);
            boolean late = !now.isBefore(payment.getReverseBy());
            if (late) {
                log.warn("T+1 DEADLINE MISSED: reversed payment {} ({} paise) at {}, deadline was {}",
                        payment.getId(), payment.getAmountPaise(), now, payment.getReverseBy());
            } else {
                log.info("Reversed payment {} ({} paise) back to customer {}, before deadline {}",
                        payment.getId(), payment.getAmountPaise(), payment.getCustomerRef(), payment.getReverseBy());
            }
            return new ReversalResult(payment.getId(), payment.getAmountPaise(), payment.getReverseBy(), now, late);
        } catch (RuntimeException e) {
            throw new ReversalFailedException(payment.getId(), e);
        }
    }
}
