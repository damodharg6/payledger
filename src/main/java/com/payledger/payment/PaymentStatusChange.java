package com.payledger.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

/**
 * One step in a payment's timeline. Written only by {@link Payment} when its status changes,
 * never updated afterwards.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Immutable
@Table(name = "payment_status_history")
public class PaymentStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for the first entry (the payment was just created). */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private PaymentStatus toStatus;

    @Column(length = 255, updatable = false)
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    /** For JPA only. */
    protected PaymentStatusChange() {
    }

    PaymentStatusChange(PaymentStatus fromStatus, PaymentStatus toStatus, String reason, Instant changedAt) {
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason == null || reason.length() <= 255 ? reason : reason.substring(0, 255);
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public PaymentStatus getFromStatus() {
        return fromStatus;
    }

    public PaymentStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
