package com.queuemate.social.domain;

import com.queuemate.common.error.ConflictException;
import org.hibernate.annotations.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "friend_requests")
public class FriendRequest {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "receiver_id", nullable = false, updatable = false)
    private UUID receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FriendRequestStatus status = FriendRequestStatus.PENDING;

    /**
     * DB의 DEFAULT now()가 채운다. insert 직후 그 값을 다시 읽어 오게 한다.
     * 이게 없으면 방금 만든 객체의 이 필드가 null이라 생성 응답에 빈 값이 나간다.
     */
    @Generated(event = org.hibernate.generator.EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    protected FriendRequest() {
    }

    private FriendRequest(UUID id, UUID senderId, UUID receiverId) {
        this.id = id;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.status = FriendRequestStatus.PENDING;
    }

    public static FriendRequest create(UUID senderId, UUID receiverId) {
        if (senderId.equals(receiverId)) {
            throw new ConflictException("SELF_FRIEND_REQUEST", "자기 자신에게 친구 요청할 수 없다");
        }
        return new FriendRequest(UUID.randomUUID(), senderId, receiverId);
    }

    /**
     * PENDING이 아닌 요청은 어떤 방향으로도 다시 바뀌지 않는다.
     * friend_requests_responded_at_check가 status와 responded_at 정합성도 함께 본다.
     */
    public void transitionTo(FriendRequestStatus next) {
        if (status != FriendRequestStatus.PENDING) {
            throw new ConflictException("FRIEND_REQUEST_NOT_PENDING", "대기 중인 요청이 아니다");
        }
        this.status = next;
        this.respondedAt = OffsetDateTime.now();
    }

    public boolean isPending() {
        return status == FriendRequestStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public UUID getReceiverId() {
        return receiverId;
    }

    public FriendRequestStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getRespondedAt() {
        return respondedAt;
    }
}
