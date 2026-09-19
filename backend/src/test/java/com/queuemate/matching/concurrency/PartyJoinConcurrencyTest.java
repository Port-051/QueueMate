package com.queuemate.matching.concurrency;

import com.queuemate.matching.rule.CandidateRule;
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

    /**
     * 컨트롤러와 같은 두 단계를 태운다 (MatchingController#createMatchRequest).
     *
     * <p>claim 을 건너뛰고 배정만 부르면 배정 Lua 가 맨 앞에서
     * {@code EXISTS qm:user:active-request:{userId}} 를 보고 -2 를 돌려준다
     * (create-or-check-party-untiered.lua). 그러면 파티가 하나도 안 생겨
     * "중복 없음" 류의 단언이 빈 목록 위에서 자동 통과한다.
     */
    private void join(String userId, String modeKey, String keyValue) {
        var command = command(userId, modeKey, keyValue);
        matchRequestService.join(command).accepted().orElseThrow(
                () -> new IllegalStateException("활성 요청 선점 실패: " + userId));
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow()
                .canJoin(command);
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
}
