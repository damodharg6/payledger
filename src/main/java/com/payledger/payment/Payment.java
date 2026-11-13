package com.payledger.payment;

import com.payledger.common.AssignedIdEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One payment from a customer to a merchant. The amount is a {@code long} in paise, never a
 * floating-point number.
 * <p>
 * <b>State machine:</b> the status has no setter. It changes only through {@link #startProcessing},
 * {@link #succeed}, {@link #fail} and {@link #reverse}, which all go through one guarded method
 * that checks {@link PaymentStatus#canTransitionTo} and records the step in the timeline.
 * <p>
 * <b>Optimistic locking:</b> {@link Version} adds {@code WHERE version = ?} to every UPDATE, so
 * if two threads change the same payment, the second one fails instead of silently
 * overwriting the first.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Table(name = "payments")
public class Payment extends AssignedIdEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "customer_ref", nullable = false, length = 100, updatable = false)
    private String customerRef;

    @Column(name = "bank_reference", length = 100)
    private String bankReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    /** RBI T+1 deadline: set only when the customer was debited but the payment failed. */
    @Column(name = "reverse_by")
    private Instant reverseBy;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** The timeline. Part of this aggregate: new steps are saved together with the payment. */
    @OneToMany(cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    @OrderBy("id")
    private List<PaymentStatusChange> history = new ArrayList<>();

    /** For JPA only. */
    protected Payment() {
    }

    public Payment(UUID id, UUID merchantId, long amountPaise, String currency, String customerRef, Instant now) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive");
        }
        this.id = Objects.requireNonNull(id);
        this.merchantId = Objects.requireNonNull(merchantId);
        this.amountPaise = amountPaise;
        this.currency = Objects.requireNonNull(currency);
        this.customerRef = Objects.requireNonNull(customerRef);
        this.status = PaymentStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
        this.history.add(new PaymentStatusChange(null, PaymentStatus.CREATED, "Payment created", now));
    }

    /** CREATED → PROCESSING: we are about to ask the bank. */
    public void startProcessing(Instant now) {
        transitionTo(PaymentStatus.PROCESSING, "Sent to bank", now);
    }

    /** PROCESSING → SUCCEEDED: the bank confirmed the debit. */
    public void succeed(String bankReference, Instant now) {
        transitionTo(PaymentStatus.SUCCEEDED, "Bank confirmed payment", now);
        this.bankReference = bankReference;
    }

    /**
     * PROCESSING → FAILED. If the customer's money was taken anyway, {@code reverseBy} is the
     * RBI T+1 deadline for giving it back; otherwise it is null and nothing needs reversing.
     */
    public void fail(String reason, String bankReference, Instant reverseBy, Instant now) {
        transitionTo(PaymentStatus.FAILED, reason, now);
        this.failureReason = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
        this.bankReference = bankReference;
        this.reverseBy = reverseBy;
    }

    /** FAILED → REVERSED: the customer's money has been given back. */
    public void reverse(Instant now) {
        if (reverseBy == null) {
            throw new IllegalStateException("Payment " + id + " was never debited, nothing to reverse");
        }
        transitionTo(PaymentStatus.REVERSED, "Customer debit reversed", now);
    }

    /** The single place where the status changes. */
    private void transitionTo(PaymentStatus next, String reason, Instant now) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidPaymentTransitionException(id, status, next);
        }
        history.add(new PaymentStatusChange(status, next, reason, now));
        this.status = next;
        this.updatedAt = now;
    }

    /** True when the customer was debited and the money must be (or was) returned. */
    public boolean needsReversal() {
        return status == PaymentStatus.FAILED && reverseBy != null;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public String getBankReference() {
        return bankReference;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getReverseBy() {
        return reverseBy;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<PaymentStatusChange> getHistory() {
        return Collections.unmodifiableList(history);
    }
}
