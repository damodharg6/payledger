package com.payledger.payment;

import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates and processes payments, exactly once per Idempotency-Key.
 * <ol>
 *   <li><b>Claim</b> the key (own committed transaction) or replay / reject.</li>
 *   <li><b>Create</b> the payment and attach it to the key (one transaction). If this fails,
 *       nothing exists, so the key is released for a retry.</li>
 *   <li><b>Process</b> it with the bank ({@link PaymentProcessor}). If this throws, the payment
 *       already exists, so the key is <i>not</i> released: a retry must never create a second
 *       payment. The inquiry job will finish it.</li>
 *   <li><b>Complete</b> the key, so later duplicates are replayed instead of getting 409.</li>
 * </ol>
 * This is deliberately not one big {@code @Transactional}: the key claim must be visible to
 * duplicates before the work starts, and no database transaction may stay open during the bank
 * call. A {@link TransactionTemplate} is used instead of calling a {@code @Transactional} method on
 * {@code this}, because Spring's proxy does not see self-calls.
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
    private final PaymentProcessor processor;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, IdempotencyService idempotency, PaymentQueryService queries,
                          PaymentProcessor processor, PlatformTransactionManager txManager, Clock clock) {
        this.payments = payments;
        this.idempotency = idempotency;
        this.queries = queries;
        this.processor = processor;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    public PaymentResult create(UUID merchantId, String idempotencyKey, CreatePaymentRequest request) {
        IdempotencyService.Claim claim = idempotency.claim(merchantId, idempotencyKey, IdempotencyService.requestHash(request));
        if (claim.replay()) {
            log.info("Replaying payment {} for idempotency key {}", claim.paymentId(), idempotencyKey);
            return new PaymentResult(queries.get(merchantId, claim.paymentId()), claim.responseCode(), true);
        }

        UUID paymentId;
        try {
            paymentId = tx.execute(status -> {
                Payment payment = new Payment(UUID.randomUUID(), merchantId, request.amountPaise(),
                        request.currencyOrDefault(), request.customerRef().trim(), clock.instant());
                payments.saveAndFlush(payment);
                idempotency.attachPayment(merchantId, idempotencyKey, payment.getId());
                return payment.getId();
            });
        } catch (RuntimeException e) {
            idempotency.release(merchantId, idempotencyKey);  // nothing was created: the client may retry
            throw e;
        }

        try {
            PaymentStatus status = processor.process(paymentId);
            log.info("Payment {} ({} paise) for merchant {} is {}", paymentId, request.amountPaise(), merchantId, status);
        } catch (RuntimeException e) {
            log.error("Processing payment {} failed; it stays unresolved for the inquiry job", paymentId, e);
        }
        idempotency.complete(merchantId, idempotencyKey, paymentId, CREATED);
        return new PaymentResult(queries.get(merchantId, paymentId), CREATED, false);
    }
}
