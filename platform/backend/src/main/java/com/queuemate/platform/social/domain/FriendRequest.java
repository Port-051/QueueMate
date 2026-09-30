package com.queuemate.platform.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 친구 요청 한 줄. <b>방향이 있다</b> — {@code requesterId} 가 {@code receiverId} 에게 보냈다.
 *
 * <p><b>상태를 바꾸는 메서드가 없다</b> — 수락 · 거절 · 거두기는 엔티티를 읽어 고치지 않고 <b>조건부 UPDATE</b>
 * ({@code … WHERE id = ? AND receiver_id = ? AND status = 'PENDING'})로 한다({@code FriendRequestRepository}). 읽고 → 판단하고 → 저장하면
 * 그 사이에 같은 요청이 끼어든다(CLAUDE.md §5).
 *
 * <p>사용자 번호 둘은 {@code users.id} 로 FK 가 걸려 있다 — 엔티티 연관은 두지 않고 숫자로만 든다.
 * id 가 {@code null} 인 새 엔티티라 {@code save()} 가 {@code persist} 로 간다 — 반드시 INSERT 가 나가고 중복은 partial unique index 가 막는다.
 */
@Entity
@Table(name = "friend_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 보낸 사람 */
    @Column(name = "requester_id", nullable = false, updatable = false)
    private Long requesterId;

    /** 받은 사람 */
    @Column(name = "receiver_id", nullable = false, updatable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private FriendRequestStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 대기 중이 아니게 된 시각 — 수락 · 거절 · 거두기 어느 쪽이든. 대기 중이면 {@code null} 이다(DB 의 CHECK 가 둘을 같이 묶는다) */
    @Column(name = "responded_at")
    private Instant respondedAt;

    public FriendRequest(Long requesterId, Long receiverId, Instant now)
    {
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.status = FriendRequestStatus.PENDING;
        this.createdAt = now;
    }
}
