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

    @Column(name = "rank_updated_at")
    private OffsetDateTime rankUpdatedAt;

    @Column(name = "rank_synced_at")
    private OffsetDateTime rankSyncedAt;

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

    public OffsetDateTime getRankUpdatedAt() {
        return rankUpdatedAt;
    }

    public OffsetDateTime getRankSyncedAt() {
        return rankSyncedAt;
    }

    /**
     * 외부 API에서 읽어 온 티어를 반영한다.
     *
     * <p>rankCode가 null이면 언랭이거나 아직 배치가 안 끝난 계정이다. 그것도 조회 결과이므로
     * 시도한 시각은 남긴다. 남기지 않으면 랭크 없는 계정을 매 조회마다 다시 물어보게 된다.
     *
     * <p>verified_at은 건드리지 않는다. Riot ID가 존재한다는 것과 그 계정이 이 사용자의
     * 것이라는 것은 다른 이야기다. 소유권은 RSO를 붙이기 전까지 확인할 수 없다.
     */
    public void applyRank(String rankCode, OffsetDateTime syncedAt) {
        this.rankSyncedAt = syncedAt;
        if (rankCode != null) {
            this.rankCode = rankCode;
            this.rankUpdatedAt = syncedAt;
        }
    }
}
