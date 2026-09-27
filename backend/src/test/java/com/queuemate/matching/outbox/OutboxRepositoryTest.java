package com.queuemate.matching.outbox;

import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * outbox 표와 엔티티의 약속을 본다 — 채번 · UNIQUE · "안 보낸 줄" 질의 · 상태 전이.
 *
 * <p>{@link ConcurrencyTestSupport} 를 상속하는 이유는 Redis 가 아니라 Spring 컨텍스트 때문이다 (Redisson 이 기동 시
 * Redis 에 붙으므로 어차피 Redis 가 필요하다). 표는 H2 에 Flyway 가 만든 뒤 {@code ddl-auto=create-drop} 이
 * 엔티티대로 다시 만든다 — 엔티티와 마이그레이션이 어긋나면 여기서 드러난다.
 */
class OutboxRepositoryTest extends ConcurrencyTestSupport {

    @Autowired
    private OutboxRepository outbox;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearOutbox() {
        jdbc.update("delete from matching_outbox");
    }

    @Test
    @DisplayName("저장하면 DB 가 id 를 채번하고 createdAt 이 찍히며 아직 보내지 않은 상태다")
    void saveAssignsIdAndStartsUnsent() {
        OutboxEvent saved = outbox.save(OutboxEvent.proposalConfirmed("party-1", "{\"partyId\":\"party-1\"}"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isSent()).isFalse();
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getEventType()).isEqualTo(OutboxEventType.PROPOSAL_CONFIRMED);
        assertThat(outbox.existsByEventTypeAndAggregateId(OutboxEventType.PROPOSAL_CONFIRMED, "party-1")).isTrue();
    }

    @Test
    @DisplayName("같은 제안의 확정을 두 번 적으면 UNIQUE 위반이다 — 재시도가 줄을 두 배로 만들지 않는다")
    void duplicateEventForSameAggregateIsRejected() {
        outbox.save(OutboxEvent.proposalConfirmed("party-1", "{}"));

        assertThatThrownBy(() -> outbox.save(OutboxEvent.proposalConfirmed("party-1", "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject("select count(*) from matching_outbox", Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("안 보낸 줄만 id 순서로 꺼내고, 상한만큼만 꺼낸다")
    void unsentRowsComeBackInIdOrderUpToTheLimit() {
        OutboxEvent first = outbox.save(OutboxEvent.proposalConfirmed("party-1", "{}"));
        OutboxEvent second = outbox.save(OutboxEvent.proposalConfirmed("party-2", "{}"));
        OutboxEvent third = outbox.save(OutboxEvent.proposalConfirmed("party-3", "{}"));

        second.markSent();
        outbox.save(second);

        List<OutboxEvent> unsent = outbox.findBySentAtIsNullOrderByIdAsc(Limit.of(10));
        assertThat(unsent).extracting(OutboxEvent::getId).containsExactly(first.getId(), third.getId());

        assertThat(outbox.findBySentAtIsNullOrderByIdAsc(Limit.of(1)))
                .extracting(OutboxEvent::getId).containsExactly(first.getId());
    }

    @Test
    @DisplayName("실패는 횟수와 이유를 남기고, 성공하면 이유를 지우고 보낸 시각을 찍는다")
    void failureThenSuccessTransitions() {
        OutboxEvent event = outbox.save(OutboxEvent.proposalConfirmed("party-1", "{}"));

        event.recordFailure("SQS timeout");
        outbox.save(event);
        OutboxEvent afterFailure = outbox.findById(event.getId()).orElseThrow();
        assertThat(afterFailure.getAttempts()).isEqualTo(1);
        assertThat(afterFailure.getLastError()).isEqualTo("SQS timeout");
        assertThat(afterFailure.isSent()).isFalse();

        afterFailure.markSent();
        outbox.save(afterFailure);
        OutboxEvent afterSuccess = outbox.findById(event.getId()).orElseThrow();
        assertThat(afterSuccess.isSent()).isTrue();
        assertThat(afterSuccess.getAttempts()).isEqualTo(2);
        assertThat(afterSuccess.getLastError()).isNull();
        assertThat(outbox.findBySentAtIsNullOrderByIdAsc(Limit.of(10))).isEmpty();
    }
}
