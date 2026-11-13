package com.payledger.payment;

import com.payledger.common.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One payment from a customer to a merchant. The amount is a {@code long} in paise, never a
 * floating-point number.
 * <p>
 * {@link Version} turns on optimistic locking: every UPDATE adds {@code WHERE version = ?}, so
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

    /** For JPA only. */
    protected Payment() {
    }

    public Payment(UUID id, UUID merchantId, long amountPaise, String currency, String customerRef, Instant now) {
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive");
        }
        this.id = id;
        this.merchantId = merchantId;
        this.amountPaise = amountPaise;
        this.currency = currency;
        this.customerRef = customerRef;
        this.status = PaymentStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
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
}
