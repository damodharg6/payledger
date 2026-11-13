package com.payledger.merchant;

import com.payledger.common.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A shop that takes payments through PayLedger. Only the SHA-256 hash of its API key is stored,
 * so a database leak does not give anyone a working key.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@Entity
@Table(name = "merchants")
public class Merchant extends AssignedIdEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 160, unique = true)
    private String email;

    @Column(name = "api_key_hash", nullable = false, length = 100)
    private String apiKeyHash;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected Merchant() {
    }

    public Merchant(UUID id, String name, String email, String apiKeyHash, String webhookUrl, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.apiKeyHash = apiKeyHash;
        this.webhookUrl = webhookUrl;
        this.createdAt = createdAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
