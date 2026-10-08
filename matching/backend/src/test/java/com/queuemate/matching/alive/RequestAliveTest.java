package com.queuemate.matching.alive;

import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.redisKeys.SharedKeys;
import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.HeartBeatService;
import com.queuemate.matching.service.MatchCancelService;
import com.queuemate.matching.service.MatchRequestService;
import com.queuemate.matching.service.ProposalService;
import com.queuemate.matching.service.RequestAliveSweeper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대기 요청의 접속 확인(heartbeat)을 본다 (docs/11 D-43).
 *
 * <p><b>무엇을 지키나.</b> 창을 닫거나 브라우저가 죽은 사람이 파티 자리를 붙들지 않는다 — 유예 동안 신호가 없으면
 * 스위퍼가 그 요청을 취소 경로({@code leave-party.lua})로 뺀다. 반대로 <b>확정된 요청은 건드리지 않는다</b>
 * (확정 뒤 클라이언트는 방으로 넘어가 신호를 끊는 것이 정상이다). 끝난 요청의 member 는 Lua 가 {@code ZREM} 하지
 * 않고 스위퍼가 시한 뒤에 게으르게 지우므로, 그것이 예외 없이 조용히 끝나는지도 본다.
 *
 * <p><b>시간을 실제로 흘린다.</b> 유예를 1.5 초로 줄이고({@code queuemate.alive.grace-ms}) 2 초 잔 뒤
 * {@link RequestAliveSweeper#sweep()} 을 <b>직접 부른다</b>. 스케줄은 이 클래스에서 1 시간으로 밀어 두었다 —
 * 5 초 주기가 살아 있으면 "u2 만 살려 두고 u1 을 뺀다" 같은 경우에서 잠든 사이 스케줄이 둘 다 빼 버린다.
 * ({@code fixedDelay} 라 기동 직후 한 번은 돌지만 그때는 DB 가 비어 있다.)
 *
 * <p>{@link ConcurrencyTestSupport} 를 상속하는 이유는 동시성 때문이 아니라 Redis DB 15 / 테스트마다 flush /
 * LoL gameconfig 시드 / {@code command()} 헬퍼 때문이다 — 전부 단일 스레드 순차 실행이다. 유예를 덮어써서
 * Spring 컨텍스트는 다른 테스트와 따로 뜬다({@code @TestPropertySource} 는 부모 것과 합쳐진다).
 */
@TestPropertySource(properties = {
        "queuemate.alive.grace-ms=" + RequestAliveTest.GRACE_MILLIS,
        "queuemate.alive.sweep-interval-ms=3600000"
})
class RequestAliveTest extends ConcurrencyTestSupport {

    /** {@code ConcurrencyTestSupport} 가 시드하는 값. 2명이면 정원이 찬다 */
    private static final String MODE = "RANKED_SOLO";
    /** 이 클래스가 덮어쓰는 유예(ms). 짧을수록 빨리 끝나지만 접수 · 배정이 이 안에 끝나야 한다 */
    static final long GRACE_MILLIS = 1_500;
    /** 유예가 확실히 지난 뒤 스위퍼를 부르기까지 자는 시간 */
    private static final long PAST_GRACE_MILLIS = GRACE_MILLIS + 500;

    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private MatchCancelService matchCancelService;
    @Autowired
    private ProposalService proposalService;
    @Autowired
    private HeartBeatService heartBeatService;
    @Autowired
    private RequestAliveSweeper requestAliveSweeper;
    @Autowired
    private List<CandidateRule> candidateRules;

    // ── 신호를 넣고 미는 자리 ─────────────────────────────────────────────────

    @Test
    @DisplayName("접수하면 접속 확인 목록에 내가 있고 시한은 queuedAt + 유예다")
    void claimingPutsMeInTheAliveListWithTheFirstDeadline() {
        long before = System.currentTimeMillis();
        enqueue(command("u1", MODE, "TOP"));
        long after = System.currentTimeMillis();

        Double deadline = deadlineOf("u1");
        assertThat(deadline).as("접수가 첫 신호다 — claim-request.lua 가 KEYS[3] 에 ZADD 해야 한다").isNotNull();
        assertThat(deadline.longValue()).isBetween(before + GRACE_MILLIS, after + GRACE_MILLIS);
    }

    @Test
    @DisplayName("heartbeat 는 시한을 지금 + 유예로 민다. 활성 요청이 없는 사람은 false 이고 목록에 넣지 않는다")
    void heartbeatPushesTheDeadlineAndRefusesStrangers() throws InterruptedException {
        enqueue(command("u1", MODE, "TOP"));
        double first = deadlineOf("u1");

        // 같은 밀리초에 돌면 score 가 같아 "밀렸다"를 볼 수 없다
        Thread.sleep(50);
        long before = System.currentTimeMillis();
        assertThat(heartBeatService.update("u1")).isTrue();
        long after = System.currentTimeMillis();

        double pushed = deadlineOf("u1");
        assertThat(pushed).isGreaterThan(first);
        assertThat((long) pushed).isBetween(before + GRACE_MILLIS, after + GRACE_MILLIS);

        // 활성 요청이 없으면 유령 member 를 만들지 않는다 — 취소 뒤 늦게 도착한 신호가 이 경우다
        assertThat(heartBeatService.update("ghost")).isFalse();
        assertThat(deadlineOf("ghost")).isNull();
    }

    // ── 신호가 끊기면 빠진다 ─────────────────────────────────────────────────

    @Test
    @DisplayName("유예 동안 신호가 없으면 스위퍼가 큐에서 뺀다 — 혼자였으니 파티도 색인도 남지 않는다")
    void silentRequestIsSweptOut() throws InterruptedException {
        enqueue(command("u1", MODE, "TOP"));
        String partyId = partyIdOf("u1");
        assertThat(partyId).isNotBlank();

        Thread.sleep(PAST_GRACE_MILLIS);
        requestAliveSweeper.sweep();

        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u1"))).isFalse();
        assertThat(deadlineOf("u1")).isNull();
        assertThat(redis.hasKey(SharedKeys.partyKey(partyId))).isFalse();
        assertThat(partyKeys()).isEmpty();
        assertThat(needsKeysHolding(partyId)).as("색인에 남은 파티는 유령 후보가 된다").isEmpty();
    }

    @Test
    @DisplayName("제안 도중 한 명의 신호가 끊기면 그 사람만 빠지고 파티는 남으며 제안 흔적은 지워진다")
    void silentMemberLeavesThePendingProposal() throws InterruptedException {
        String partyId = fullParty();
        assertThat(field(partyId, "status")).isEqualTo("PENDING");

        // 둘 다 시한이 지나게 둔 뒤 u2 만 다시 신호를 보낸다 — 그러면 u1 만 오래된 member 다
        Thread.sleep(PAST_GRACE_MILLIS);
        assertThat(heartBeatService.update("u2")).isTrue();
        requestAliveSweeper.sweep();

        // u1 은 빠졌다
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u1"))).isFalse();
        assertThat(deadlineOf("u1")).isNull();
        // u2 는 그대로 기다린다
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u2"))).isTrue();
        assertThat(deadlineOf("u2")).isNotNull();
        // 파티는 남았고 u2 혼자다
        assertThat(redis.hasKey(SharedKeys.partyKey(partyId))).isTrue();
        assertThat(memberValues(SharedKeys.partyKey(partyId))).containsExactly("JUNGLE");
        // 제안 흔적은 깨졌다 (INV-5 cancelled) — 다시 차면 새 제안이 열려야 한다
        assertThat(field(partyId, "status")).isNull();
        assertThat(field(partyId, "expiresAt")).isNull();
        assertThat(redis.hasKey(SharedKeys.acceptsKey(partyId))).isFalse();
        assertThat(redis.opsForZSet().score(SharedKeys.PENDING_KEY, partyId)).isNull();
    }

    @Test
    @DisplayName("확정된 요청은 건너뛴다 — 활성 요청(PARTY)도 파티 HASH(CONFIRMED)도 그대로, 목록에서만 빠진다")
    void confirmedRequestsAreLeftAlone() throws InterruptedException {
        String partyId = fullParty();
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);

        Thread.sleep(PAST_GRACE_MILLIS);
        requestAliveSweeper.sweep();

        for (String userId : List.of("u1", "u2")) {
            Map<String, String> active = redis.<String, String>opsForHash().entries(SharedKeys.activeRequestKey(userId));
            assertThat(active).as("확정된 사람의 활성 요청은 스위퍼가 지우면 안 된다").isNotEmpty();
            assertThat(active.get("status")).isEqualTo("PARTY");
            assertThat(deadlineOf(userId)).as("목록에서는 빠진다 — 안 빼면 매 회차 다시 꺼낸다").isNull();
        }
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
        assertThat(memberValues(SharedKeys.partyKey(partyId))).containsExactlyInAnyOrder("TOP", "JUNGLE");
    }

    @Test
    @DisplayName("취소된 요청의 member 는 시한까지 남았다가 스위퍼가 조용히 지운다 (게으른 정리)")
    void cancelledRequestIsCleanedLazily() throws InterruptedException {
        enqueue(command("u1", MODE, "TOP"));
        String requestId = redis.<String, String>opsForHash().get(SharedKeys.activeRequestKey("u1"), "requestId");
        assertThat(matchCancelService.cancel("u1", requestId)).isEqualTo(CancelResult.CANCELLED_AND_PARTY_CLOSED);

        // 취소는 ZREM 하지 않는다 — Lua 어디에도 이 키의 ZREM 이 없다 (SharedKeys#HEARTBEAT_KEY)
        assertThat(deadlineOf("u1")).isNotNull();

        Thread.sleep(PAST_GRACE_MILLIS);
        requestAliveSweeper.sweep();

        assertThat(deadlineOf("u1")).isNull();
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u1"))).isFalse();
    }

    // ── 준비 / 조회 ─────────────────────────────────────────────────────────

    /** u1(TOP) + u2(JUNGLE) 로 정원 2를 채운다. 제안이 열린 partyId 를 돌려준다 */
    private String fullParty() {
        enqueue(command("u1", MODE, "TOP"));
        enqueue(command("u2", MODE, "JUNGLE"));

        String partyId = partyIdOf("u2");
        assertThat(partyId).as("정원이 찬 파티가 만들어지지 않았다").isNotBlank();
        assertThat(partyId).isEqualTo(partyIdOf("u1"));
        return partyId;
    }

    /** 활성 요청을 만들고 배정까지 태운다. 컨트롤러가 하는 두 단계와 같다 */
    private void enqueue(CreateMatchRequestCommand command) {
        matchRequestService.join(command).accepted().orElseThrow(
                () -> new IllegalStateException("활성 요청 선점에 실패했다: " + command.getUserId()));
        candidateRules.stream()
                .filter(rule -> rule.supports(GameKey.LOL))
                .findFirst()
                .orElseThrow()
                .canJoin(command);
    }

    /** 접속 확인 목록의 score. 없으면 null */
    private Double deadlineOf(String userId) {
        return redis.opsForZSet().score(SharedKeys.HEARTBEAT_KEY, userId);
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get(SharedKeys.activeRequestKey(userId), "partyId");
    }

    private String field(String partyId, String field) {
        return redis.<String, String>opsForHash().get(SharedKeys.partyKey(partyId), field);
    }

    /** 그 파티가 아직 올라 있는 needs 색인 키들 */
    private List<String> needsKeysHolding(String partyId) {
        return redis.keys(SharedKeys.PARTY_OPEN_PREFIX + "*").stream()
                .filter(key -> redis.opsForZSet().score(key, partyId) != null)
                .toList();
    }
}
