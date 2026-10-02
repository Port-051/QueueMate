package com.queuemate.matching.proposal;

import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.domain.condition.KeyConditionType;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.MatchCancelService;
import com.queuemate.matching.service.MatchRequestService;
import com.queuemate.matching.service.ProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 거절한 상대와 한동안 다시 매칭되지 않는지 본다.
 *
 * <p>{@code ProposalService#decline} 은 제안을 실제로 깬 한 번에 <b>양쪽의</b>
 * {@code qm:user:declined:{userId}} ZSET 에 적는다 — 내 키에 상대들, 상대 키마다 나.
 * score 는 풀리는 시각(now + {@code queuemate.proposal.decline-avoid-seconds}, 기본 600초)이고
 * 키 TTL 도 그 값으로 매번 다시 건다. 배정 선필터({@code *CandidateRule#canJoin})가 락 밖에서
 * {@code ZRANGEBYSCORE declined:{me} now +inf} 를 한 번 읽어 차단 목록에 합치므로, 그 사람이 든
 * 후보 파티는 {@code ScriptSupport#blockedWith} 에서 걸러진다.
 *
 * <p><b>양방향을 둘 다 본다.</b> 선필터는 "내가 들어가려는 파티에 내 집합의 사람이 있나"만 보므로
 * 한쪽만 적으면 누가 먼저 큐에 들어오느냐에 따라 절반은 못 막는다 — 거절한 사람이 다시 들어오는 경우(1번)와
 * 거절당한 사람이 거절한 사람의 새 파티에 들어가는 경우(2번)를 각각 만든다.
 *
 * <p>세 게임 모두 2인 모드를 쓴다 — 두 명이면 정원이 차서 제안이 열리고, 거절 뒤에는 남은 한 명의
 * 빈 자리가 그대로 색인에 올라와 "맞는 값으로 다시 들어오면 원래는 그 파티에 들어간다" 가 성립한다.
 * 그래야 "다른 파티" 라는 단언이 거절 기록 때문이라고 말할 수 있다(3 · 4번이 그 대조다).
 *
 * <p>{@link ConcurrencyTestSupport} 를 상속하는 이유는 {@code ProposalIdempotencyTest} 와 같다 —
 * Redis DB 15 / 테스트마다 flush / gameconfig 시드 / {@code command()} 헬퍼. 전부 단일 스레드 순차 실행이다.
 */
class DeclinedPartnerTest extends ConcurrencyTestSupport {

    /** {@code application.yaml} 의 {@code queuemate.proposal.decline-avoid-seconds} 기본값(초) */
    private static final long AVOID_SECONDS = 600;

    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private MatchCancelService matchCancelService;
    @Autowired
    private ProposalService proposalService;
    @Autowired
    private List<CandidateRule> candidateRules;

    // ── LoL ─────────────────────────────────────────────────────────────────

    /**
     * {@code RANKED_SOLO} — {@link ConcurrencyTestSupport} 가 시드하는 2인 모드다(tierRule 없이 시드되어 티어를 안 본다).
     * 포지션 중복 금지라 u1 은 TOP, u2 는 JUNGLE 로 채운다. 거절 뒤 u2 파티의 빈 자리는 TOP 이다.
     */
    @Nested
    @DisplayName("LoL (RANKED_SOLO, 2인)")
    class Lol {

        private static final String MODE = "RANKED_SOLO";

        private String join(String userId, String position) {
            return enqueue(command(userId, MODE, position));
        }

        private String fullParty() {
            join("u1", "TOP");
            join("u2", "JUNGLE");
            return assertFilledTogether("u1", "u2");
        }

        @Test
        @DisplayName("거절한 사람은 다시 큐에 들어와도 방금 거절한 상대의 파티에 들어가지 않는다")
        void declinerDoesNotRejoin() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);

            // u2 파티는 TOP 자리가 비어 색인에 다시 올라와 있다. 기록이 없으면 u1 은 그리로 들어간다
            join("u1", "TOP");

            assertThat(partyIdOf("u1")).isNotBlank().isNotEqualTo(partyIdOf("u2"));
        }

        @Test
        @DisplayName("거절당한 사람도 거절한 사람이 새로 만든 파티에 들어가지 않는다 — 기록이 양쪽에 있다")
        void declinedDoesNotJoinDecliner() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);
            cancel("u2");

            join("u1", "TOP");     // 새 파티. JUNGLE 자리가 빈다
            join("u2", "JUNGLE");  // 기록이 u1 쪽에만 있었다면 u1 의 파티에 들어갔을 것이다

            assertThat(partyIdOf("u2")).isNotBlank().isNotEqualTo(partyIdOf("u1"));
        }

        @Test
        @DisplayName("풀리는 시각이 지난 기록은 거르지 않는다 — 다시 같은 파티가 된다")
        void expiredRecordAllowsRejoin() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);

            // score 가 풀리는 시각이다. 과거로 돌려 "10분이 지났다" 를 만든다(키 TTL 은 아직 남아 있다 —
            // 읽는 쪽이 score 로 거르는지를 보는 것이다)
            redis.opsForZSet().add(declinedKey("u1"), "u2", 1);

            join("u1", "TOP");

            assertThat(partyIdOf("u1")).isEqualTo(partyIdOf("u2"));
        }

        @Test
        @DisplayName("거절과 무관한 사람은 거절당한 사람의 파티에 그대로 들어간다")
        void unrelatedUserStillJoins() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);

            join("u3", "TOP");

            assertThat(partyIdOf("u3")).isEqualTo(partyIdOf("u2")).isEqualTo(partyId);
        }

        @Test
        @DisplayName("거절은 양쪽 키에 풀리는 시각(now+600초)과 TTL 을 적고, 거절 재시도(NOT_FOUND)는 아무것도 더 적지 않는다")
        void declineWritesBothWaysOnce() {
            String partyId = fullParty();

            long before = System.currentTimeMillis();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);
            long expected = before + AVOID_SECONDS * 1000;

            Double mine = redis.opsForZSet().score(declinedKey("u1"), "u2");
            Double theirs = redis.opsForZSet().score(declinedKey("u2"), "u1");
            assertThat(mine).isNotNull().isCloseTo((double) expected, within(5_000.0));
            assertThat(theirs).isNotNull().isCloseTo((double) expected, within(5_000.0));
            assertThat(redis.opsForZSet().range(declinedKey("u1"), 0, -1)).containsExactly("u2");
            assertThat(redis.opsForZSet().range(declinedKey("u2"), 0, -1)).containsExactly("u1");
            assertThat(redis.getExpire(declinedKey("u1"))).isGreaterThan(590).isLessThanOrEqualTo(AVOID_SECONDS);
            assertThat(redis.getExpire(declinedKey("u2"))).isGreaterThan(590).isLessThanOrEqualTo(AVOID_SECONDS);

            // 같은 거절의 재시도는 status 가 이미 지워져 NOT_FOUND 다. 제안을 깬 것이 아니므로 적지 않는다 —
            // 적으면 score 가 밀려 풀리는 시각이 재시도마다 늦춰진다
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.NOT_FOUND);

            assertThat(redis.opsForZSet().score(declinedKey("u1"), "u2")).isEqualTo(mine);
            assertThat(redis.opsForZSet().score(declinedKey("u2"), "u1")).isEqualTo(theirs);
            assertThat(redis.opsForZSet().range(declinedKey("u1"), 0, -1)).containsExactly("u2");
            assertThat(redis.opsForZSet().range(declinedKey("u2"), 0, -1)).containsExactly("u1");
            assertThat(redis.keys("qm:user:declined:*")).containsExactlyInAnyOrder(declinedKey("u1"), declinedKey("u2"));
        }
    }

    // ── VALORANT ────────────────────────────────────────────────────────────

    /**
     * {@code UNRATED_DUO} — 2인 · 역할군 중복 금지 · 티어를 안 본다({@code seed/gameconfig.redis} 와 같은 값).
     * 부모는 LoL 만 시드하므로 여기서 넣는다. 역할군이 달라야 한 파티가 되므로 u1 DUELIST, u2 INITIATOR.
     * 티어 모드({@code COMPETITIVE_DUO})를 쓰지 않은 이유 — 거절 기록은 스크립트 밖(자바 선필터)에서 걸러지므로
     * 티어 유무와 무관하고, 티어 모드는 사다리와 tier-range 표를 통째로 시드해야 해 이 테스트의 요점을 흐린다.
     */
    @Nested
    @DisplayName("VALORANT (UNRATED_DUO, 2인)")
    class Valorant {

        private static final String MODE = "UNRATED_DUO";

        @BeforeEach
        void seedValorantDuo() {
            redis.opsForHash().putAll("qm:gameconfig:VALORANT:" + MODE, Map.of(
                    "targetPartySize", "2",
                    "positionUniqueness", "true",
                    "tierRule", "NONE"));
        }

        private String join(String userId, String role) {
            return enqueue(command(userId, GameKey.VALORANT, KeyConditionType.ROLE, MODE, role));
        }

        private String fullParty() {
            join("u1", "DUELIST");
            join("u2", "INITIATOR");
            return assertFilledTogether("u1", "u2");
        }

        @Test
        @DisplayName("거절한 사람은 다시 큐에 들어와도 방금 거절한 상대의 파티에 들어가지 않는다")
        void declinerDoesNotRejoin() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);

            join("u1", "DUELIST");

            assertThat(partyIdOf("u1")).isNotBlank().isNotEqualTo(partyIdOf("u2"));
        }

        @Test
        @DisplayName("거절당한 사람도 거절한 사람이 새로 만든 파티에 들어가지 않는다 — 기록이 양쪽에 있다")
        void declinedDoesNotJoinDecliner() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);
            cancel("u2");

            join("u1", "DUELIST");
            join("u2", "INITIATOR");

            assertThat(partyIdOf("u2")).isNotBlank().isNotEqualTo(partyIdOf("u1"));
        }

        @Test
        @DisplayName("거절과 무관한 사람은 거절당한 사람의 파티에 그대로 들어간다")
        void unrelatedUserStillJoins() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);

            join("u3", "DUELIST");

            assertThat(partyIdOf("u3")).isEqualTo(partyIdOf("u2")).isEqualTo(partyId);
        }
    }

    // ── PUBG ────────────────────────────────────────────────────────────────

    /**
     * {@code NORMAL_DUO_TPP} — 2인 · 티어를 안 본다({@code seed/gameconfig.redis} 와 같은 값, 중복 금지 필드는 없다).
     * 부모는 LoL 만 시드하므로 여기서 넣는다. 스팀과 카카오는 색인이 갈리므로 전원 STEAM 이다.
     */
    @Nested
    @DisplayName("PUBG (NORMAL_DUO_TPP, 2인)")
    class Pubg {

        private static final String MODE = "NORMAL_DUO_TPP";
        private static final String STEAM = "STEAM";

        @BeforeEach
        void seedPubgDuo() {
            redis.opsForHash().putAll("qm:gameconfig:PUBG:" + MODE, Map.of(
                    "targetPartySize", "2",
                    "tierRule", "NONE"));
        }

        private String join(String userId) {
            return enqueue(command(userId, GameKey.PUBG, KeyConditionType.PLATFORM, MODE, STEAM));
        }

        private String fullParty() {
            join("u1");
            join("u2");
            return assertFilledTogether("u1", "u2");
        }

        @Test
        @DisplayName("거절한 사람은 다시 큐에 들어와도 방금 거절한 상대의 파티에 들어가지 않는다")
        void declinerDoesNotRejoin() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);

            join("u1");

            assertThat(partyIdOf("u1")).isNotBlank().isNotEqualTo(partyIdOf("u2"));
        }

        @Test
        @DisplayName("거절당한 사람도 거절한 사람이 새로 만든 파티에 들어가지 않는다 — 기록이 양쪽에 있다")
        void declinedDoesNotJoinDecliner() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);
            cancel("u2");

            join("u1");
            join("u2");

            assertThat(partyIdOf("u2")).isNotBlank().isNotEqualTo(partyIdOf("u1"));
        }

        @Test
        @DisplayName("거절과 무관한 사람은 거절당한 사람의 파티에 그대로 들어간다")
        void unrelatedUserStillJoins() {
            String partyId = fullParty();
            assertThat(proposalService.decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);

            join("u3");

            assertThat(partyIdOf("u3")).isEqualTo(partyIdOf("u2")).isEqualTo(partyId);
        }
    }

    // ── 공통 헬퍼 ────────────────────────────────────────────────────────────

    /**
     * 컨트롤러와 같은 두 단계를 태운다 (MatchingController#createMatchRequest) — 선점 뒤 배정.
     * 배정은 원래 {@code @Async} 지만 여기서는 규칙을 직접 불러 순서를 고정한다. requestId 를 돌려준다.
     */
    private String enqueue(CreateMatchRequestCommand command) {
        String requestId = matchRequestService.join(command).accepted().orElseThrow(
                () -> new IllegalStateException("활성 요청 선점에 실패했다: " + command.getUserId())).requestId();
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow()
                .canJoin(command);
        return requestId;
    }

    /** 두 사람이 한 파티로 정원을 채워 제안이 열렸는지 확인하고 partyId 를 돌려준다 */
    private String assertFilledTogether(String a, String b) {
        String partyId = partyIdOf(b);
        assertThat(partyId).as("정원이 찬 파티가 만들어지지 않았다").isNotBlank();
        assertThat(partyId).isEqualTo(partyIdOf(a));
        assertThat(redis.<String, String>opsForHash().get("qm:party:" + partyId, "status")).isEqualTo("PENDING");
        return partyId;
    }

    /** 취소 API 와 같은 경로. requestId 는 활성 요청 HASH 에서 읽는다 */
    private void cancel(String userId) {
        String requestId = redis.<String, String>opsForHash().get("qm:user:active-request:" + userId, "requestId");
        assertThat(requestId).as("활성 요청이 없다: " + userId).isNotBlank();
        assertThat(matchCancelService.cancel(userId, requestId))
                .isIn(CancelResult.CANCELLED, CancelResult.CANCELLED_AND_PARTY_CLOSED);
        assertThat(partyIdOf(userId)).isNull();
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get("qm:user:active-request:" + userId, "partyId");
    }

    /** {@code SharedKeys.declinedKey} 와 같은 문자열 — 자바 상수가 바뀌면 여기서 드러나게 리터럴로 적는다 */
    private static String declinedKey(String userId) {
        return "qm:user:declined:" + userId;
    }
}
