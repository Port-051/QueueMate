package com.queuemate.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/** V4__social_identity.sql의 user_identities와 1:1로 대응한다. */
@Entity
@Table(name = "user_identities")
public class UserIdentity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20, updatable = false)
    private OAuthProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 191, updatable = false)
    private String providerUserId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected UserIdentity() {
    }

    private UserIdentity(UUID userId, OAuthProvider provider, String providerUserId, String email) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
    }

    public static UserIdentity link(UUID userId, OAuthProvider provider, String providerUserId, String email) {
        return new UserIdentity(userId, provider, providerUserId, email);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public OAuthProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
