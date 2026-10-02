package com.queuemate.platform.party.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.time.Instant;

/**
 * 파티원 한 줄. <b>읽기만 한다</b> — 넣는 것은 {@code PartyMemberRepository#insertIfAbsent} 의 {@code INSERT … ON CONFLICT DO NOTHING} 이다
 * (같은 확정이 두 길로 와도 한 벌만 남게). 그래서 생성자도 세터도 없고 {@code @Immutable} 이다.
 *
 * <p>{@code userId} 는 사용자 번호({@code users.id})이고 FK 가 걸려 있다(사용자를 지우면 딸려 지워진다). 엔티티 연관은 두지 않고 숫자로만 든다.
 * {@code partyId} 는 {@code parties.id}(bigint identity)다 — 글의 id 가 아니다. 글에서 파티를 찾으려면 {@code parties.post_id} 로 간다.
 */
@Entity
@Immutable
@Table(name = "party_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyMember {

    @EmbeddedId
    private Key key;

    /** 글을 쓴 사람인가. {@code room} 의 방장 키의 값이 아니라 글의 {@code hostId} 로 정한다 — 확정한 방은 방장이 바뀔 수 있다(D-23) */
    @Column(name = "is_host", nullable = false)
    private boolean host;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public Long getUserId()
    {
        return key.getUserId();
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Column(name = "party_id", nullable = false)
        private Long partyId;

        @Column(name = "user_id", nullable = false)
        private Long userId;
    }
}
