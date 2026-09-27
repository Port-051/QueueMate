package com.queuemate.matching.outbox;

import org.springframework.data.domain.Limit;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * outbox 표 접근. 쓰는 쪽(확정 시 한 줄 추가)과 배달원(안 보낸 줄 꺼내기 · 보냄 표시)이 쓴다.
 *
 * <p><b>{@code JpaRepository} 를 상속하지 않는다.</b> {@code deleteAll} 같은 것이 딸려오면 안 된다 — outbox 는
 * 지우지 않고 {@code sentAt} 으로 표시만 한다(보낸 기록이 곧 감사 기록이다). 필요한 메서드만 직접 선언한다.
 *
 * <p><b>같은 제안이 두 번 적히면</b> {@link #save} 가 {@code DataIntegrityViolationException} 을 던진다
 * ({@code (event_type, aggregate_id)} UNIQUE). 부르는 쪽은 그것을 "이미 적혔다"로 읽고 실패로 다루지 않는다 —
 * 먼저 {@link #existsByEventTypeAndAggregateId} 로 보고 넣어도 되지만, 두 요청이 동시에 오면 그 확인과 INSERT 사이가
 * 벌어지므로 UNIQUE 위반을 잡는 쪽이 정답이다.
 */
public interface OutboxRepository extends Repository<OutboxEvent, Long> {

    OutboxEvent save(OutboxEvent event);

    Optional<OutboxEvent> findById(Long id);

    boolean existsByEventTypeAndAggregateId(OutboxEventType eventType, String aggregateId);

    /**
     * 아직 보내지 않은 줄을 오래된 것부터. 배달원이 한 회차에 처리할 만큼만 꺼낸다.
     *
     * <p>{@code sent_at IS NULL ORDER BY id} — 마이그레이션이 만든 {@code (sent_at, id)} 인덱스를 탄다.
     */
    List<OutboxEvent> findBySentAtIsNullOrderByIdAsc(Limit limit);
}
