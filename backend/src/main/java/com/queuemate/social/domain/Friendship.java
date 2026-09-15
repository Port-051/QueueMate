package com.queuemate.social.domain;

import com.queuemate.common.social.UuidOrder;
import org.hibernate.annotations.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * (user_low_id, user_high_id) 정규화 쌍. 방향이 없다.
 * 순서는 반드시 UuidOrder를 거친다. Java 기본 UUID 비교와 DB 비교가 다르다.
 */
@Entity
@Table(name = "friendships")
public class Friendship {

    @EmbeddedId
    private FriendshipId id;

    /**
     * DB의 DEFAULT now()가 채운다. insert 직후 그 값을 다시 읽어 오게 한다.
     * 이게 없으면 방금 만든 객체의 이 필드가 null이라 생성 응답에 빈 값이 나간다.
     */
    @Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Friendship() {
    }

    private Friendship(FriendshipId id) {
        this.id = id;
    }

    public static Friendship of(UUID a, UUID b) {
        return new Friendship(FriendshipId.of(a, b));
    }

    public FriendshipId getId() {
        return id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** 요청한 사용자의 상대편을 돌려준다. */
    public UUID counterpartOf(UUID userId) {
        return id.userLowId().equals(userId) ? id.userHighId() : id.userLowId();
    }

    @Embeddable
    public static class FriendshipId implements Serializable {

        @Column(name = "user_low_id", nullable = false, updatable = false)
        private UUID userLowId;

        @Column(name = "user_high_id", nullable = false, updatable = false)
        private UUID userHighId;

        protected FriendshipId() {
        }

        private FriendshipId(UUID userLowId, UUID userHighId) {
            this.userLowId = userLowId;
            this.userHighId = userHighId;
        }

        public static FriendshipId of(UUID a, UUID b) {
            if (a.equals(b)) {
                throw new IllegalArgumentException("자기 자신과 친구가 될 수 없다");
            }
            return new FriendshipId(UuidOrder.lower(a, b), UuidOrder.higher(a, b));
        }

        public UUID userLowId() {
            return userLowId;
        }

        public UUID userHighId() {
            return userHighId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof FriendshipId that)) {
                return false;
            }
            return Objects.equals(userLowId, that.userLowId)
                    && Objects.equals(userHighId, that.userHighId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userLowId, userHighId);
        }
    }
}
