package com.queuemate.party.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** (party_id, user_id)가 PK다. 같은 사용자가 한 파티에 두 번 들어갈 수 없다 (INV-7). */
@Entity
@Table(name = "party_members")
public class PartyMember {

    @EmbeddedId
    private PartyMemberId id;

    @Column(nullable = false)
    private boolean ready;

    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    protected PartyMember() {
    }

    private PartyMember(PartyMemberId id) {
        this.id = id;
        this.ready = false;
    }

    public static PartyMember of(UUID partyId, UUID userId) {
        return new PartyMember(new PartyMemberId(partyId, userId));
    }

    public void changeReady(boolean ready) {
        this.ready = ready;
    }

    /** 이미 나간 사람을 다시 나가게 하지 않는다. 처음 나간 시각을 유지한다. */
    public boolean markLeft(OffsetDateTime at) {
        if (leftAt != null) {
            return false;
        }
        leftAt = at;
        // 나간 사람의 준비 상태는 의미가 없다. 남겨 두면 조회 화면에서 오해를 부른다.
        ready = false;
        return true;
    }

    /** 나간 사람은 준비 판정에서 빠진다. */
    public boolean countsForReadiness() {
        return leftAt == null;
    }

    public PartyMemberId getId() {
        return id;
    }

    public UUID getUserId() {
        return id.userId();
    }

    public boolean isReady() {
        return ready;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }

    @Embeddable
    public static class PartyMemberId implements Serializable {

        @Column(name = "party_id", nullable = false, updatable = false)
        private UUID partyId;

        @Column(name = "user_id", nullable = false, updatable = false)
        private UUID userId;

        protected PartyMemberId() {
        }

        public PartyMemberId(UUID partyId, UUID userId) {
            this.partyId = partyId;
            this.userId = userId;
        }

        public UUID partyId() {
            return partyId;
        }

        public UUID userId() {
            return userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PartyMemberId that)) {
                return false;
            }
            return Objects.equals(partyId, that.partyId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(partyId, userId);
        }
    }
}
