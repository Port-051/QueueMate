package com.queuemate.user.domain;

import com.queuemate.common.domain.GameKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * V1__init_schema.sql의 game_accounts 테이블과 1:1로 대응한다.
 * 외부 게임 ID는 필요한 범위만 저장한다 (docs/13 PII).
 */
@Entity
@Table(name = "game_accounts")
public class GameAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_game", nullable = false, length = 20)
    private GameKey providerGame;

    @Column(name = "external_game_id", nullable = false, length = 128)
    private String externalGameId;

    @Column(name = "region", length = 20)
    private String region;

    @Column(name = "rank_code", length = 40)
    private String rankCode;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected GameAccount() {
    }

    private GameAccount(UUID id, UUID userId, GameKey providerGame, String externalGameId, String region) {
        this.id = id;
        this.userId = userId;
        this.providerGame = providerGame;
        this.externalGameId = externalGameId;
        this.region = region;
    }

    public static GameAccount create(UUID userId, GameKey providerGame, String externalGameId, String region) {
        return new GameAccount(UUID.randomUUID(), userId, providerGame, externalGameId, region);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public GameKey getProviderGame() {
        return providerGame;
    }

    public String getExternalGameId() {
        return externalGameId;
    }

    public String getRegion() {
        return region;
    }

    public String getRankCode() {
        return rankCode;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }
}
