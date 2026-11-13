package com.payledger.payment;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates payments, exactly once per Idempotency-Key.
 * <p>
 * This method is deliberately <b>not</b> one big {@code @Transactional}. The key claim must be
 * committed <i>before</i> the work starts so that duplicates can see it (and get 409), and in
 * Phase 4 the bank call must happen with no database transaction open. So the work is split into
 * short transactions: claim (in {@link IdempotencyService}), then create + complete (in a
 * {@link TransactionTemplate}). A template is used instead of calling another
 * {@code @Transactional} method on {@code this}, because Spring's proxy does not see self-calls.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    static final int CREATED = 201;

    private final PaymentRepository payments;
    private final IdempotencyService idempotency;
    private final PaymentQueryService queries;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, IdempotencyService idempotency, PaymentQueryService queries,
                          PlatformTransactionManager txManager, Clock clock) {
        this.payments = payments;
        this.idempotency = idempotency;
        this.queries = queries;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public PaymentResult create(UUID merchantId, String idempotencyKey, CreatePaymentRequest request) {
        String requestHash = IdempotencyService.requestHash(request);
        IdempotencyService.Claim claim = idempotency.claim(merchantId, idempotencyKey, requestHash);
        if (claim.replay()) {
            log.info("Replaying payment {} for idempotency key {}", claim.paymentId(), idempotencyKey);
            return new PaymentResult(queries.get(merchantId, claim.paymentId()), claim.responseCode(), true);
        }

        try {
            PaymentResponse created = tx.execute(status -> {
                Payment payment = new Payment(UUID.randomUUID(), merchantId, request.amountPaise(),
                        request.currencyOrDefault(), request.customerRef().trim(), clock.instant());
                payments.saveAndFlush(payment);
                idempotency.complete(merchantId, idempotencyKey, payment.getId(), CREATED);
                return PaymentResponse.from(payment);
            });
            log.info("Created payment {} ({} paise) for merchant {}", created.id(), created.amountPaise(), merchantId);
            return new PaymentResult(created, CREATED, false);
        } catch (RuntimeException e) {
            // Nothing was created (the transaction rolled back), so free the key for a retry.
            idempotency.release(merchantId, idempotencyKey);
            throw e;
        }
    }
}
