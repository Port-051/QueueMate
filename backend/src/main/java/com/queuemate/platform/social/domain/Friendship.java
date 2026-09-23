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
 * 친구 한 줄. <b>방향이 없다</b> — 두 사용자 번호를 (작은 쪽, 큰 쪽)으로 정규화한 한 줄이고 PK 가 중복을 막는다. 누가 요청했는지는 여기 없다.
 *
 * <p><b>읽기만 한다</b> — 넣는 것은 {@code FriendshipRepository#insertIfAbsent} 의 {@code INSERT … ON CONFLICT DO NOTHING} 이다
 * (같은 수락이 겹치거나 양방향 요청이 따로 수락돼도 위반 없이 한 줄만 남게). 그래서 {@code @Immutable} 이다.
 *
 * <p><b>"작은 쪽"은 {@link Key#of} 한 곳에서만 정한다</b> — 숫자 비교다. DB 의 {@code friendships_ordered} CHECK 도 같은 숫자 비교라 어긋날 수 없다
 * (문자열이던 때의 {@code COLLATE "C"} 는 필요 없어졌다 — V6).
 */
@Entity
@Immutable
@Table(schema = "social", name = "friendships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @EmbeddedId
    private Key key;

    /** 친구가 된 시각 — 응답의 {@code since} 다 */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 이 줄에서 {@code me} 가 아닌 쪽 */
    public Long otherThan(Long me)
    {
        return key.getUserLowId().equals(me) ? key.getUserHighId() : key.getUserLowId();
    }

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class Key implements Serializable {

        @Column(name = "user_low_id", nullable = false)
        private Long userLowId;

        @Column(name = "user_high_id", nullable = false)
        private Long userHighId;

        private Key(Long userLowId, Long userHighId)
        {
            this.userLowId = userLowId;
            this.userHighId = userHighId;
        }

        /** 두 사람의 순서를 가리지 않고 같은 키를 만든다. 같은 사람 둘을 넘기지 마라 — DB 의 CHECK 가 거절한다 */
        public static Key of(Long a, Long b)
        {
            return (a < b) ? new Key(a, b) : new Key(b, a);
        }
    }
}
