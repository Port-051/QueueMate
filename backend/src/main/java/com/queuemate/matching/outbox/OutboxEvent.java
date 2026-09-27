package com.queuemate.matching.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * outbox 의 한 줄 — 아직 보내지 않았거나 이미 보낸 앱 간 이벤트 하나 (transactional outbox, docs/11 #18 · #21).
 *
 * <p><b>이 앱이 DB 에 쓰는 유일한 엔티티다.</b> 진행 중인 매칭 상태는 Redis 가 원본이고 여기에 두지 않는다 —
 * 이 표는 "확정된 것"만 안다 (docs/11 #27, CLAUDE.md §3). 표는 이 앱의 Flyway({@code db/migration/V1__matching_outbox.sql})가
 * 만들고, 이 클래스는 그 표의 모양을 그대로 옮긴 것이다. 둘이 어긋나면 테스트의 {@code ddl-auto=create-drop} 이
 * 엔티티대로 다시 만들어 드러난다.
 *
 * <p><b>왜 적어 두나.</b> 확정 순간에 SQS 를 곧장 부르면 "확정했다"와 "알렸다"가 따로 성공·실패한다. 먼저 이 줄을
 * 넣고 배달원이 {@link #sentAt} 이 비어 있는 줄을 꺼내 보내면, 적힌 것은 언젠가 반드시 전달된다. 그 대가로 같은 줄이
 * 두 번 갈 수 있으므로 받는 쪽은 {@link #aggregateId} 로 멱등해야 한다 (docs/11 #21 — "소비자 멱등성 요구는 유지").
 *
 * <p><b>({@link #eventType}, {@link #aggregateId}) 는 유일하다.</b> 같은 제안의 확정이 두 번 적히지 않는다 — INSERT
 * 성공 뒤 앱이 죽어 재시도가 같은 줄을 넣으려 하면 위반이 나고, 부르는 쪽은 그것을 "이미 됐음"으로 읽어야 한다.
 *
 * <p>상태 전이는 {@link #markSent()} / {@link #recordFailure(String)} 둘뿐이다. setter 를 두지 않는다.
 */
@Entity
@Table(name = "matching_outbox",
        uniqueConstraints = @UniqueConstraint(name = "matching_outbox_event_key",
                columnNames = {"event_type", "aggregate_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    /** DB 가 채번한다 ({@code GENERATED ALWAYS AS IDENTITY}). 배달원이 이 순서로 보낸다 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private OutboxEventType eventType;

    /** 이벤트가 가리키는 것의 id. 확정이면 partyId(= proposalId). FIFO 큐면 MessageGroupId 로도 쓴다 */
    @Column(name = "aggregate_id", nullable = false, updatable = false, length = 64)
    private String aggregateId;

    /** 받는 쪽에 그대로 실어 보내는 JSON 문자열. 이 앱은 그 안을 질의하지 않는다 */
    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 배달원이 보낸 시각. null 이면 아직 안 보냈다 — 배달원이 꺼내는 기준이다 */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** 보내기를 시도한 횟수. 실패가 쌓이는지 보는 진단용이다 */
    @Column(nullable = false)
    private int attempts;

    /** 마지막 실패 이유. 보내는 데 성공하면 비운다 */
    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    private OutboxEvent(OutboxEventType eventType, String aggregateId, String payload) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    /** 확정된 제안 한 건. {@code payload} 는 받는 쪽과의 계약대로 만든 JSON 이어야 한다 */
    public static OutboxEvent proposalConfirmed(String partyId, String payload) {
        return new OutboxEvent(OutboxEventType.PROPOSAL_CONFIRMED, partyId, payload);
    }

    public boolean isSent() {
        return sentAt != null;
    }

    /** 배달원이 SQS 에 보내는 데 성공했다. 실패 기록은 지운다 */
    public void markSent() {
        this.sentAt = Instant.now();
        this.lastError = null;
        this.attempts++;
    }

    /** 배달원이 보내는 데 실패했다. 줄은 남겨 다음 회차에 다시 시도한다 */
    public void recordFailure(String reason) {
        this.attempts++;
        this.lastError = reason;
    }
}
