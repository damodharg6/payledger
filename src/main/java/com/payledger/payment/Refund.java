package com.payledger.payment;

import com.payledger.common.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * A full or partial refund of a succeeded payment. The money movement itself is the REFUND
 * ledger transaction this row points to; this row adds the business details (reason,
 * idempotency key) and makes "how much was refunded already?" a cheap query.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Immutable
@Table(name = "refunds")
public class Refund extends AssignedIdEntity<UUID> {

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "amount_paise", nullable = false, updatable = false)
    private long amountPaise;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "ledger_transaction_id", nullable = false, updatable = false)
    private UUID ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Refund() {
    }

    public Refund(UUID id, UUID paymentId, UUID merchantId, long amountPaise, String reason, String idempotencyKey,
                  UUID ledgerTransactionId, Instant createdAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.amountPaise = amountPaise;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.ledgerTransactionId = ledgerTransactionId;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public long getAmountPaise() {
        return amountPaise;
    }

    public String getReason() {
        return reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getLedgerTransactionId() {
        return ledgerTransactionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
