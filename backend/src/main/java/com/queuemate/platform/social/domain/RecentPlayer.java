package com.queuemate.platform.social.domain;

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
 * 최근 함께한 사람 한 줄 — {@code userId} 의 목록에 {@code otherUserId} 가 있다. <b>읽기만 한다</b>({@code @Immutable}).
 *
 * <p><b>채우는 코드가 아직 없다</b> — 파티가 닫힐 때({@code PartyClosed.fifo} 의 소비 — CLAUDE.md §3.4) 채우는데 SQS 배선이 미정이다.
 * 한 사람에 한 줄이다(PK 가 {@code (user_id, other_user_id)}) — 같은 사람과 또 하면 줄이 늘지 않고 마지막 것으로 덮인다.
 */
@Entity
@Immutable
@Table(schema = "social", name = "recent_players")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentPlayer {

    @EmbeddedId
    private Key key;

    /** 마지막으로 같이 한 파티. {@code party.parties} 의 id(bigint)지만 FK 가 없다 */
    @Column(name = "last_party_id", nullable = false)
    private Long lastPartyId;

    @Column(name = "last_played_at", nullable = false)
    private Instant lastPlayedAt;

    public Long getOtherUserId()
    {
        return key.getOtherUserId();
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "other_user_id", nullable = false)
        private Long otherUserId;
    }
}
