package com.queuemate.matching.concurrency;

import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.MatchCancelService;
import com.queuemate.matching.service.MatchRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-3: 파티 인원은 정원을 넘지 않는다.
 *
 * 마지막 한 자리를 여럿이 동시에 노릴 때가 위험하다.
 * 인원 확인과 삽입이 Lua 한 덩어리 안에 있어야 정원이 지켜진다.
 */
class PartyJoinConcurrencyTest extends ConcurrencyTestSupport {

    @Autowired
    private List<CandidateRule> candidateRules;

    @Autowired
    private MatchRequestService matchRequestService;

    @Autowired
    private MatchCancelService matchCancelService;

    /** 테스트 시드의 조건 조합. {@code ConcurrencyTestSupport#command()} 가 REQUIRED / RANK_UP 을 고정한다. */
    private static final List<String> POSITIONS = List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");

    /**
     * 컨트롤러와 같은 두 단계를 태운다 (MatchingController#createMatchRequest).
     *
     * <p>claim 을 건너뛰고 배정만 부르면 배정 Lua 가 맨 앞에서
     * {@code EXISTS qm:user:active-request:{userId}} 를 보고 -2 를 돌려준다
     * (create-or-check-party-untiered.lua). 그러면 파티가 하나도 안 생겨
     * "중복 없음" 류의 단언이 빈 목록 위에서 자동 통과한다.
     *
     * @return requestId. 취소({@code MatchCancelService#cancel})에 넘긴다
     */
    private String join(String userId, String modeKey, String keyValue) {
        var command = command(userId, modeKey, keyValue);
        String requestId = matchRequestService.join(command).accepted().orElseThrow(
                () -> new IllegalStateException("활성 요청 선점 실패: " + userId)).requestId();
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow()
                .canJoin(command);
        return requestId;
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get("qm:user:active-request:" + userId, "partyId");
    }

    /**
     * 티어를 보지 않는 모드의 needs 한 줄. 테스트 시드는 {@code tierRule} 이 없어 untiered 경로라
     * 티어 접미사가 붙지 않는다 (LolPartyKeys / SharedKeys#needsKey 와 같은 모양).
     */
    private static String needsKey(String modeKey, String position) {
        return "qm:party:open:LOL:" + modeKey + ":REQUIRED:RANK_UP:needs:" + position;
    }

    /** 그 파티가 그 포지션 줄에 올라가 있는가 (ZSET 멤버 여부) */
    private boolean indexed(String modeKey, String position, String partyId) {
        return redis.opsForZSet().score(needsKey(modeKey, position), partyId) != null;
    }

    /** 남은 사람이 맡지 않은 줄에는 있고, 맡은 줄에는 없어야 한다 */
    private void assertNeedsLinesAre(String modeKey, String partyId, Set<String> heldByRemaining) {
        for (String position : POSITIONS) {
            boolean expected = !heldByRemaining.contains(position);
            assertThat(indexed(modeKey, position, partyId))
                    .as("needs:%s 에 파티가 %s", position, expected ? "있어야 한다" : "없어야 한다")
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("INV-3: 정원 5명 파티에 20명이 동시에 들어와도 어느 파티도 5명을 넘지 않는다")
    void partyNeverExceedsTarget() throws InterruptedException {
        List<String> positions = List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");

        runConcurrently(20, i -> join("u" + i, "RANKED_FLEX_5", positions.get(i % 5)));

        List<Map<Object, Object>> parties = partyKeys().stream()
                .map(key -> redis.<Object, Object>opsForHash().entries(key))
                .toList();

        assertThat(parties).isNotEmpty();
        for (Map<Object, Object> party : parties) {
            long members = party.keySet().stream()
                    .filter(field -> ((String) field).startsWith("member:"))
                    .count();

            assertThat(members).isLessThanOrEqualTo(5L);
            // 인원 수를 따로 저장하지 않는다. member: 필드를 세는 것이 인원이라
            // 카운터와 실제 멤버 수가 어긋나는 상태 자체가 없다 (HINCRBY 는 재시도 때
            // 두 번 더해져 어긋났다). 그 카운터가 되살아나면 여기서 잡는다
            assertThat(party).doesNotContainKey("size");
        }
    }

    @Test
    @DisplayName("INV-3: 정원 5명 파티에서 같은 포지션이 두 명 들어가지 않는다")
    void positionIsUniqueWithinParty() throws InterruptedException {
        List<String> positions = List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");

        runConcurrently(20, i -> join("u" + i, "RANKED_FLEX_5", positions.get(i % 5)));

        for (String key : partyKeys()) {
            List<String> taken = redis.<Object, Object>opsForHash().entries(key).entrySet().stream()
                    .filter(e -> ((String) e.getKey()).startsWith("member:"))
                    .map(e -> (String) e.getValue())
                    .toList();

            assertThat(taken).doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("한 사용자가 두 파티에 동시에 들어가지 않는다")
    void userBelongsToOnlyOneParty() throws InterruptedException {
        List<String> positions = List.of("TOP", "JUNGLE", "MID", "ADC", "SUPPORT");

        runConcurrently(20, i -> join("u" + i, "RANKED_FLEX_5", positions.get(i % 5)));

        List<String> allMembers = partyKeys().stream()
                .flatMap(key -> redis.<Object, Object>opsForHash().keys(key).stream())
                .map(field -> (String) field)
                .filter(field -> field.startsWith("member:"))
                .collect(Collectors.toList());

        assertThat(allMembers).doesNotHaveDuplicates();
        assertThat(Set.copyOf(allMembers)).hasSize(allMembers.size());
    }

    // ── 취소 뒤 색인 되돌리기 — 남은 사람이 맡지 않은 줄 전부 ─────────────────────
    //
    // 단일 스레드다. 경합이 아니라 leave-party.lua 6번이 "내가 비운 줄 하나"가 아니라
    // "남은 사람이 맡지 않은 줄 전부"를 되돌리는지를 본다. 정원이 차면 join-party.lua 가
    // 모든 줄에서 파티를 내리므로, 한 명이 빠진 뒤 그 사람 줄만 올리면 아무도 안 맡았던
    // 포지션의 사람은 이 파티를 영영 못 본다.

    @Test
    @DisplayName("정원이 찼다 한 명이 빠진 파티는 남은 사람이 맡지 않은 포지션 줄 전부에 되돌아온다")
    void fullPartyReturnsToEveryUnheldLineAfterOneLeaves() {
        // RANKED_SOLO 는 정원 2 · 포지션 중복 금지 (ConcurrencyTestSupport 시드)
        join("u1", "RANKED_SOLO", "TOP");
        String r2 = join("u2", "RANKED_SOLO", "MID");

        String partyId = partyIdOf("u1");
        assertThat(partyId).as("2인 파티가 만들어지지 않았다").isNotBlank();
        assertThat(partyIdOf("u2")).isEqualTo(partyId);
        assertThat(redis.<String, String>opsForHash().get("qm:party:" + partyId, "status"))
                .as("정원이 찼는데 제안이 열리지 않았다 — 이 테스트의 전제(색인에서 전부 내려간 상태)가 아니다")
                .isEqualTo("PENDING");
        for (String position : POSITIONS) {
            assertThat(indexed("RANKED_SOLO", position, partyId))
                    .as("찬 파티가 needs:%s 에 남아 있다", position)
                    .isFalse();
        }

        // MID 가 빠진다. 남은 사람은 TOP 하나 — TOP 을 뺀 네 줄이 전부 돌아와야 한다
        matchCancelService.cancel("u2", r2);
        assertNeedsLinesAre("RANKED_SOLO", partyId, Set.of("TOP"));

        // 아무도 맡은 적 없던 JUNGLE 이 이 파티를 볼 수 있어야 한다
        join("u3", "RANKED_SOLO", "JUNGLE");
        assertThat(partyIdOf("u3"))
                .as("옛 규칙(내가 비운 MID 줄만 되돌림)이면 JUNGLE 은 새 파티를 만든다")
                .isEqualTo(partyId);
        assertThat(memberValues("qm:party:" + partyId)).containsExactlyInAnyOrder("TOP", "JUNGLE");
    }

    @Test
    @DisplayName("정원이 안 찼던 파티에서 한 명이 빠져도 색인은 남은 사람이 맡지 않은 줄에만 있다")
    void notFullPartyKeepsCorrectLinesAfterOneLeaves() {
        // RANKED_FLEX_5 는 정원 5 — 두 명으로는 차지 않아 색인에서 내려간 적이 없다.
        // 새 규칙이 정원이 안 찼던 경우를 깨뜨리지 않는지(TOP 줄을 열어 버리지 않는지) 본다
        join("u1", "RANKED_FLEX_5", "TOP");
        String r2 = join("u2", "RANKED_FLEX_5", "MID");

        String partyId = partyIdOf("u1");
        assertThat(partyId).isNotBlank();
        assertThat(partyIdOf("u2")).isEqualTo(partyId);
        assertNeedsLinesAre("RANKED_FLEX_5", partyId, Set.of("TOP", "MID"));

        matchCancelService.cancel("u2", r2);
        assertNeedsLinesAre("RANKED_FLEX_5", partyId, Set.of("TOP"));
        assertThat(memberValues("qm:party:" + partyId)).containsExactly("TOP");
    }
}
