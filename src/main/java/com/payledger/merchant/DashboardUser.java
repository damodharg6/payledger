package com.payledger.merchant;

import com.payledger.common.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A person who signs in to the dashboard for one merchant. The password is stored only as a
 * BCrypt hash: slow on purpose and salted, so a leaked table cannot be cracked quickly and two
 * equal passwords never produce the same hash.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Table(name = "dashboard_users")
public class DashboardUser extends AssignedIdEntity<UUID> {

    /** ADMIN may refund; VIEWER may only look. */
    public enum Role {
        ADMIN,
        VIEWER
    }

    @Id
    private UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected DashboardUser() {
    }

    public DashboardUser(UUID id, UUID merchantId, String email, String passwordHash, Role role, Instant createdAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
