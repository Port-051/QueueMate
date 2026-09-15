package com.queuemate.matching.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * 제안 참가자 한 명과 그 응답 (docs/03 §3).
 * PK가 (proposal_id, user_id)라 같은 제안에 같은 사람이 두 번 들어갈 수 없다.
 */
@Entity
@Table(name = "proposal_members")
public class ProposalMember {

    @EmbeddedId
    private ProposalMemberId id;

    /**
     * 실시간이면 match_request, 예약이면 reservation의 id다.
     * 가리키는 테이블이 둘이라 FK가 없고, 정합성은 서비스가 지킨다.
     */
    @Column(name = "source_request_id", nullable = false, updatable = false)
    private UUID sourceRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance", nullable = false)
    private Acceptance acceptance;

    protected ProposalMember() {
    }

    private ProposalMember(ProposalMemberId id, UUID sourceRequestId) {
        this.id = id;
        this.sourceRequestId = sourceRequestId;
        this.acceptance = Acceptance.PENDING;
    }

    public static ProposalMember pending(UUID proposalId, UUID userId, UUID sourceRequestId) {
        return new ProposalMember(ProposalMemberId.of(proposalId, userId), sourceRequestId);
    }

    /**
     * 응답을 기록한다. 이미 응답한 사람의 재요청은 무시한다.
     *
     * @return 이번 호출로 상태가 바뀌었으면 true
     */
    public boolean respond(Acceptance response) {
        if (response == Acceptance.PENDING) {
            throw new IllegalArgumentException("PENDING으로 되돌릴 수 없다");
        }
        if (acceptance != Acceptance.PENDING) {
            return false;
        }
        this.acceptance = response;
        return true;
    }

    public ProposalMemberId getId() {
        return id;
    }

    public UUID getProposalId() {
        return id.proposalId();
    }

    public UUID getUserId() {
        return id.userId();
    }

    public UUID getSourceRequestId() {
        return sourceRequestId;
    }

    public Acceptance getAcceptance() {
        return acceptance;
    }

    @Embeddable
    public static class ProposalMemberId implements Serializable {

        @Column(name = "proposal_id", nullable = false, updatable = false)
        private UUID proposalId;

        @Column(name = "user_id", nullable = false, updatable = false)
        private UUID userId;

        protected ProposalMemberId() {
        }

        private ProposalMemberId(UUID proposalId, UUID userId) {
            this.proposalId = proposalId;
            this.userId = userId;
        }

        public static ProposalMemberId of(UUID proposalId, UUID userId) {
            if (proposalId == null || userId == null) {
                throw new IllegalArgumentException("proposalId와 userId는 필수다");
            }
            return new ProposalMemberId(proposalId, userId);
        }

        public UUID proposalId() {
            return proposalId;
        }

        public UUID userId() {
            return userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProposalMemberId that)) {
                return false;
            }
            return Objects.equals(proposalId, that.proposalId) && Objects.equals(userId, that.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(proposalId, userId);
        }
    }
}
