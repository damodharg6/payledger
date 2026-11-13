package com.payledger.payment;

import com.payledger.common.BadRequestException;
import com.payledger.common.NotFoundException;
import com.payledger.common.PageResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of payments: one payment with its timeline, and a filtered, paginated list.
 * Read-only transactions let Hibernate skip dirty checking and let PostgreSQL know nothing will
 * be written.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class PaymentQueryService {

    public static final int MAX_PAGE_SIZE = 100;

    private final PaymentRepository payments;
    private final RefundRepository refunds;

    public PaymentQueryService(PaymentRepository payments, RefundRepository refunds) {
        this.payments = payments;
        this.refunds = refunds;
    }

    /** Returns 404 (not 403) for another merchant's payment, so ids of other merchants leak nothing. */
    @Transactional(readOnly = true)
    public PaymentResponse get(UUID merchantId, UUID paymentId) {
        return payments.findByIdAndMerchantId(paymentId, merchantId)
                .map(p -> PaymentResponse.from(p, refunds.sumAmountByPaymentId(p.getId())))
                .orElseThrow(() -> new NotFoundException("Payment " + paymentId + " not found"));
    }

    /**
     * Newest first. {@code from} is inclusive, {@code to} is exclusive. Uses the index
     * {@code idx_payments_merchant_created (merchant_id, created_at DESC)}.
     */
    @Transactional(readOnly = true)
    public PageResponse<PaymentSummary> list(UUID merchantId, PaymentStatus status, Instant from, Instant to,
                                             int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new BadRequestException("'from' must be before 'to'");
        }

        List<Specification<Payment>> filters = new ArrayList<>();
        filters.add((root, query, cb) -> cb.equal(root.get("merchantId"), merchantId));
        if (status != null) {
            filters.add((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            filters.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            filters.add((root, query, cb) -> cb.lessThan(root.get("createdAt"), to));
        }

        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(payments.findAll(Specification.allOf(filters), pageable), PaymentSummary::from);
    }
}
