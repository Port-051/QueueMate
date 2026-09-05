package com.queuemate.social.domain;

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

/** 방향이 있는 차단 기록. 매칭 배제는 방향과 무관하게 양쪽 모두에 적용된다 (INV-6). */
@Entity
@Table(name = "blocks")
public class Block {

    @EmbeddedId
    private BlockId id;

    /**
     * DB의 DEFAULT now()가 채운다. insert 직후 그 값을 다시 읽어 오게 한다.
     * 이게 없으면 방금 만든 객체의 이 필드가 null이라 생성 응답에 빈 값이 나간다.
     */
    @Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Block() {
    }

    private Block(BlockId id) {
        this.id = id;
    }

    public static Block of(UUID blockerId, UUID blockedId) {
        return new Block(BlockId.of(blockerId, blockedId));
    }

    public BlockId getId() {
        return id;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Embeddable
    public static class BlockId implements Serializable {

        @Column(name = "blocker_id", nullable = false, updatable = false)
        private UUID blockerId;

        @Column(name = "blocked_id", nullable = false, updatable = false)
        private UUID blockedId;

        protected BlockId() {
        }

        private BlockId(UUID blockerId, UUID blockedId) {
            this.blockerId = blockerId;
            this.blockedId = blockedId;
        }

        public static BlockId of(UUID blockerId, UUID blockedId) {
            if (blockerId.equals(blockedId)) {
                throw new IllegalArgumentException("자기 자신을 차단할 수 없다");
            }
            return new BlockId(blockerId, blockedId);
        }

        public UUID blockerId() {
            return blockerId;
        }

        public UUID blockedId() {
            return blockedId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BlockId that)) {
                return false;
            }
            return Objects.equals(blockerId, that.blockerId) && Objects.equals(blockedId, that.blockedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockerId, blockedId);
        }
    }
}
