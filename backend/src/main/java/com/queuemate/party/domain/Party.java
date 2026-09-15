package com.queuemate.party.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 확정된 proposal 하나에서 나온 파티. 사용자가 직접 만들 수 없다.
 * proposal_id에 unique 제약이 걸려 있어 한 proposal은 파티 하나만 만든다 (INV-4).
 */
@Entity
@Table(name = "parties")
public class Party {

    @Id
    private UUID id;

    @Column(name = "proposal_id", nullable = false, updatable = false)
    private UUID proposalId;

    @Column(name = "game_key", nullable = false, updatable = false)
    private String gameKey;

    @Column(name = "mode_key", nullable = false, updatable = false)
    private String modeKey;

    @Column(name = "target_size", nullable = false, updatable = false)
    private int targetSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PartyStatus status;

    @Column(name = "scheduled_start", updatable = false)
    private OffsetDateTime scheduledStart;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    /** 전원 준비가 된 시각. 준비가 풀리면 다시 비운다. 게임 시작 판정의 기준이다. */
    @Column(name = "ready_at")
    private OffsetDateTime readyAt;

    /** 게임에 들어간 시각. CLOSED가 된 뒤에도 남아 실제로 플레이했는지를 증명한다. */
    @Column(name = "played_at")
    private OffsetDateTime playedAt;

    protected Party() {
    }

    private Party(UUID id, UUID proposalId, String gameKey, String modeKey,
                  int targetSize, OffsetDateTime scheduledStart) {
        this.id = id;
        this.proposalId = proposalId;
        this.gameKey = gameKey;
        this.modeKey = modeKey;
        this.targetSize = targetSize;
        this.scheduledStart = scheduledStart;
        this.status = PartyStatus.OPEN;
    }

    public static Party of(UUID proposalId, String gameKey, String modeKey,
                           int targetSize, OffsetDateTime scheduledStart) {
        // id를 애플리케이션이 만든다. DB default를 쓰면 insert 전에 id를 알 수 없어
        // 같은 트랜잭션에서 party_members를 채울 때 한 번 더 조회해야 한다.
        return new Party(UUID.randomUUID(), proposalId, gameKey, modeKey, targetSize, scheduledStart);
    }

    /**
     * 전원 준비면 READY, 아니면 OPEN. 진행/종료 상태는 여기서 건드리지 않는다.
     *
     * READY로 들어온 시각을 함께 남긴다. 이미 READY였다면 갱신하지 않는다.
     * 준비 상태가 얼마나 유지됐는지가 게임 시작 판정의 근거인데, 사람이 들고 나며
     * 계산이 다시 돌 때마다 시각을 밀면 그 파티는 영원히 게임에 못 들어간다.
     */
    public void refreshReadiness(boolean allReady, OffsetDateTime at) {
        if (status == PartyStatus.PLAYING || status == PartyStatus.CLOSED) {
            return;
        }
        status = allReady ? PartyStatus.READY : PartyStatus.OPEN;
        if (status == PartyStatus.READY) {
            if (readyAt == null) {
                readyAt = at;
            }
        } else {
            readyAt = null;
        }
    }

    /**
     * 게임에 들어갔다고 본다. READY에서만 넘어간다.
     *
     * 서버는 게임을 관측할 수 없다. 이 전이는 관측이 아니라 추정이고, 근거는
     * 전원 준비 상태가 충분히 유지됐다는 것뿐이다. 그래서 되돌리지 않는다.
     * 되돌릴 수 있게 만들면 게임 중에 준비를 풀어 유예를 짧게 만드는 길이 생긴다.
     */
    public boolean startPlaying(OffsetDateTime at) {
        if (status != PartyStatus.READY) {
            return false;
        }
        status = PartyStatus.PLAYING;
        playedAt = at;
        return true;
    }

    /** 게임에 들어간 적이 있는가. 닫힌 뒤에도 답이 유지된다. */
    public boolean hasPlayed() {
        return playedAt != null;
    }

    /**
     * 파티를 끝낸다. 스키마의 parties_closed_at_check가 CLOSED와 closed_at을 함께 요구한다.
     * played_at은 지우지 않는다. 닫힌 뒤에도 실제로 플레이했는지를 구분해야 한다.
     */
    public boolean close(OffsetDateTime at) {
        if (status == PartyStatus.CLOSED) {
            return false;
        }
        status = PartyStatus.CLOSED;
        closedAt = at;
        return true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProposalId() {
        return proposalId;
    }

    public String getGameKey() {
        return gameKey;
    }

    public String getModeKey() {
        return modeKey;
    }

    public int getTargetSize() {
        return targetSize;
    }

    public PartyStatus getStatus() {
        return status;
    }

    public OffsetDateTime getScheduledStart() {
        return scheduledStart;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public OffsetDateTime getReadyAt() {
        return readyAt;
    }

    public OffsetDateTime getPlayedAt() {
        return playedAt;
    }
}
