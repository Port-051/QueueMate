package com.queuemate.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 지표 이름과 태그를 한곳에 모은다.
 *
 * MeterRegistry를 서비스마다 직접 주입하면 이름이 코드 곳곳에 문자열로 흩어진다.
 * 한 글자만 달라도 다른 지표가 되고, 그 사실을 대시보드를 만들 때에야 안다.
 * 도메인 코드는 무엇이 일어났는지만 말하고 그것을 어떤 이름으로 셀지는 여기서 정한다.
 *
 * 태그에 userId나 partyId를 넣지 않는다. 값의 가짓수가 무한해서 시계열이 그만큼 늘어나고,
 * 그건 지표 저장소를 죽이는 가장 흔한 방법이다. 개별 식별자는 로그의 MDC에 있다.
 */
@Component
public class QueueMateMetrics {

    private final MeterRegistry registry;

    public QueueMateMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 불변식 방어가 실제로 발동했다.
     *
     * 평범한 사용자 충돌과 섞지 않는다. 이미 나간 파티에서 준비를 바꾸려는 것은 충돌이지
     * 불변식 위반이 아니다. docs/09가 invariant violation을 Sev-1 후보로 취급하라고
     * 하는데, 정상적인 충돌이 섞이면 그 규칙이 아무 의미가 없어진다.
     *
     * 이 구분은 던지는 자리만 안다. 그래서 예외 처리기에서 일괄로 세지 않고
     * 각 자리에서 명시적으로 부른다.
     */
    public void invariantViolated(Invariant invariant) {
        Counter.builder("queuemate.invariant.violation")
                .tag("invariant", invariant.name())
                .description("불변식 방어가 발동한 횟수. Sev-1 후보다")
                .register(registry)
                .increment();
    }

    /** 파티가 게임 시작으로 넘어갔다. 서버가 관측한 것이 아니라 시간으로 추정한 전이다. */
    public void partyStartedPlaying() {
        counter("queuemate.party.playing", "게임 시작으로 판정한 파티 수").increment();
    }

    public void partyClosed(String reason) {
        Counter.builder("queuemate.party.closed")
                .tag("reason", reason)
                .description("종료된 파티 수")
                .register(registry)
                .increment();
    }

    /**
     * 이탈 유예를 어느 단계로 줬는가.
     *
     * 유예를 파티 상태별로 나눈 판단이 맞는지 보는 유일한 창이다.
     * PLAYING 단계가 거의 안 잡히면 게임 시작 판정이 늦다는 뜻이고,
     * 대부분 PLAYING이면 판정이 이르다는 뜻이다.
     */
    public void departureGraceChosen(String partyStatus) {
        Counter.builder("queuemate.departure.grace")
                .tag("status", partyStatus)
                .description("이탈 유예를 준 횟수. 파티 상태별")
                .register(registry)
                .increment();
    }

    public void departureEvicted() {
        counter("queuemate.departure.evicted", "유예가 지나 파티에서 내보낸 횟수").increment();
    }

    /**
     * 대조가 빠진 예약을 찾았다.
     *
     * 이 값이 0이 아니라는 것 자체가 정상 경로의 실패를 뜻한다.
     * 수리 작업이 조용히 고치기만 하면 원인은 계속 남는다.
     */
    public void reconcileFound() {
        counter("queuemate.presence.reconcile.found", "대조로 찾아낸 빠진 이탈 예약 수").increment();
    }

    /** 한도에 걸려 거절했다. 정상 사용자가 걸리는지 보는 유일한 창이다. */
    public void rateLimitRejected(String scope) {
        Counter.builder("queuemate.ratelimit.rejected")
                .tag("scope", scope)
                .description("속도 제한에 걸린 횟수")
                .register(registry)
                .increment();
    }

    /** Redis를 못 읽어 정책대로 처리했다. 장애가 어느 기능에 어떻게 닿았는지 남긴다. */
    public void rateLimitUnavailable(String scope, String policy) {
        Counter.builder("queuemate.ratelimit.unavailable")
                .tag("scope", scope)
                .tag("policy", policy)
                .description("Redis 장애로 한도를 확인하지 못한 횟수")
                .register(registry)
                .increment();
    }

    /** 이벤트를 몇 개의 session에 보냈는가. origin이 LOCAL이면 같은 노드, REMOTE면 다른 노드다. */
    public void eventDelivered(String origin, int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("queuemate.event.delivered")
                .tag("origin", origin)
                .description("session으로 보낸 이벤트 수")
                .register(registry)
                .increment(count);
    }

    private Counter counter(String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }

    /** 불변식 방어가 걸리는 자리. CLAUDE.md의 INV 번호와 대응한다. */
    public enum Invariant {
        /** INV-3: 파티 인원은 mode의 target party size를 넘지 않는다. */
        PARTY_SIZE_MISMATCH,
        /** INV-4: 모든 참가자가 accept하기 전에는 party를 확정하지 않는다. */
        PROPOSAL_NOT_FULLY_ACCEPTED,
        /** INV-4: proposal 하나에서 party는 하나만 나온다. 동시 확정에서 걸린다. */
        PARTY_DUPLICATE_CREATION,
        /** INV-5: expired/declined/cancelled proposal은 다시 confirm될 수 없다. */
        PROPOSAL_NOT_CONFIRMED,
        /** INV-6: 차단 관계 사용자는 같은 party에 들어갈 수 없다. */
        BLOCKED_MEMBERS,
        /** INV-4: 호출자가 아는 명단과 수락 기록이 어긋났다. 매칭 쪽 버그 신호다. */
        PROPOSAL_MEMBER_MISMATCH
    }
}
