package com.queuemate.matching.concurrency;

import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.KeyConditionType;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.MatchCancelService;
import com.queuemate.matching.service.MatchRequestService;
import com.queuemate.matching.service.ProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VALORANT 경로의 불변식 회귀. {@code PartyJoinConcurrencyTest}(LoL)와 같은 것을 지킨다.
 *
 * <p>왜 게임마다 따로 있어야 하나 — 배정 Lua 를 게임별 디렉터리로 나눈 대가다 (CLAUDE.md §4).
 * 스크립트가 한 벌일 때는 한 벌의 테스트가 전부를 지켰지만, 나뉜 뒤로는 테스트 없는 게임
 * 스크립트가 <b>아무도 실행하지 않는 코드</b>가 된다. 잘못된 수정이 그 게임에서만 조용히
 * 깨진 채 배포되는 것이 나눠서 막으려던 바로 그 일이다.
 *
 * <p>LoL 판과 다른 것은 티어 모드를 함께 본다는 점이다. 발로란트 경쟁전은 정원이 3이라
 * 티어 규칙에 전이(transitive)가 성립하지 않는 구간이 생긴다 — 줄 하나만 보고 받으면
 * 게임에서 큐가 안 잡히는 파티가 만들어진다. 그래서 {@code join-party-tiered.lua} 가
 * 합류할 때마다 파티 범위를 교집합으로 좁히는데, 그게 동시에 들어와도 지켜지는지 본다.
 */
class ValorantPartyJoinConcurrencyTest extends ConcurrencyTestSupport {

    private static final String TIERED_MODE = "COMPETITIVE_TRIO";
    private static final String UNTIERED_MODE = "UNRATED_TRIO";
    private static final int TARGET = 3;

    /** 역할군 4개. 한 파티 안에서 겹치지 않는다 (positionUniqueness=true). */
    private static final List<String> ROLES = List.of("DUELIST", "INITIATOR", "CONTROLLER", "SENTINEL");

    /**
     * 티어 사다리. {@code seed/gameconfig.redis} 의 VALORANT 섹션과 <b>같은 값</b>이어야 한다.
     *
     * <p><b>발로란트는 숫자가 클수록 높다</b> (IRON_1 &lt; IRON_2 &lt; IRON_3). 롤/배그와 반대다.
     */
    private static final List<String> LADDER = List.of(
            "UNRANKED",
            "IRON_1", "IRON_2", "IRON_3",
            "BRONZE_1", "BRONZE_2", "BRONZE_3",
            "SILVER_1", "SILVER_2", "SILVER_3",
            "GOLD_1", "GOLD_2", "GOLD_3",
            "PLATINUM_1", "PLATINUM_2", "PLATINUM_3",
            "DIAMOND_1", "DIAMOND_2", "DIAMOND_3",
            "ASCENDANT_1", "ASCENDANT_2", "ASCENDANT_3",
            "IMMORTAL_1", "IMMORTAL_2", "IMMORTAL_3",
            "RADIANT");

    /**
     * 경쟁전 3인 티어별 허용 범위 표. 시드가 생성한 값을 그대로 옮겼다.
     *
     * <p>손으로 고치지 마라 — 시드와 어긋나면 이 테스트가 통과해도 운영에서는 다른 파티가 생긴다.
     */
    private static final String TRIO_TIER_RANGE =
            "UNRANKED SOLO_ONLY "
            + "IRON_1 IRON_1:SILVER_3 IRON_2 IRON_1:SILVER_3 IRON_3 IRON_1:SILVER_3 "
            + "BRONZE_1 IRON_1:SILVER_3 BRONZE_2 IRON_1:SILVER_3 BRONZE_3 IRON_1:SILVER_3 "
            + "SILVER_1 IRON_1:GOLD_3 SILVER_2 IRON_1:GOLD_3 SILVER_3 IRON_1:GOLD_3 "
            + "GOLD_1 SILVER_1:PLATINUM_3 GOLD_2 SILVER_1:PLATINUM_3 GOLD_3 SILVER_1:PLATINUM_3 "
            + "PLATINUM_1 GOLD_1:DIAMOND_1 PLATINUM_2 GOLD_1:DIAMOND_2 PLATINUM_3 GOLD_1:DIAMOND_3 "
            + "DIAMOND_1 PLATINUM_1:ASCENDANT_1 DIAMOND_2 PLATINUM_2:ASCENDANT_2 DIAMOND_3 PLATINUM_3:ASCENDANT_3 "
            + "ASCENDANT_1 DIAMOND_1:ASCENDANT_3 ASCENDANT_2 DIAMOND_2:ASCENDANT_3 ASCENDANT_3 DIAMOND_3:ASCENDANT_3 "
            + "IMMORTAL_1 SOLO_ONLY IMMORTAL_2 SOLO_ONLY IMMORTAL_3 SOLO_ONLY RADIANT SOLO_ONLY";

    /** 경쟁전에 쓸 티어 표본. 규칙상 서로 되는 쌍과 안 되는 쌍이 섞이도록 골랐다. */
    private static final List<String> TIERS =
            List.of("SILVER_2", "GOLD_1", "GOLD_3", "PLATINUM_2", "DIAMOND_1");

    @Autowired
    private List<CandidateRule> candidateRules;

    @Autowired
    private MatchRequestService matchRequestService;

    @Autowired
    private MatchCancelService matchCancelService;

    @Autowired
    private ProposalService proposalService;

    /**
     * VALORANT gameconfig. 부모의 {@code resetRedis()} 가 DB 15 를 비운 뒤에 돌아야 하므로
     * 여기서 다시 넣는다 (JUnit 은 상위 클래스의 {@code @BeforeEach} 를 먼저 실행한다).
     *
     * <p>앱은 gameconfig 를 밀어넣지 않고 읽기만 한다 (CLAUDE.md §3). 테스트에서만 넣는 것이고,
     * 값은 {@code seed/gameconfig.redis} 와 같아야 한다.
     */
    @BeforeEach
    void seedValorantGameConfig() {
        redis.opsForHash().putAll("qm:gameconfig:VALORANT:" + TIERED_MODE, Map.of(
                "targetPartySize", String.valueOf(TARGET),
                "positionUniqueness", "true",
                "tierRule", "EXIST"));
        redis.opsForHash().putAll("qm:gameconfig:VALORANT:" + UNTIERED_MODE, Map.of(
                "targetPartySize", String.valueOf(TARGET),
                "positionUniqueness", "true",
                "tierRule", "NONE"));

        for (int rank = 0; rank < LADDER.size(); rank++) {
            redis.opsForZSet().add("qm:gameconfig:VALORANT:tier", LADDER.get(rank), rank);
        }

        Map<String, String> table = new LinkedHashMap<>();
        String[] tokens = TRIO_TIER_RANGE.split(" ");
        for (int i = 0; i < tokens.length; i += 2) {
            table.put(tokens[i], tokens[i + 1]);
        }
        assertThat(table).hasSize(LADDER.size());
        redis.opsForHash().putAll("qm:gameconfig:VALORANT:tier-range:" + TIERED_MODE, table);
    }

    /**
     * 컨트롤러와 같은 두 단계를 태운다 (MatchingController#createMatchRequest).
     *
     * <p>claim 을 건너뛰면 배정 Lua 가 맨 앞의 {@code EXISTS qm:user:active-request:{userId}} 에서
     * -2 를 돌려주어 파티가 하나도 안 생긴다. 그러면 "중복 없음" 류의 단언이 빈 목록 위에서
     * 자동 통과한다.
     */
    private String join(String userId, String modeKey, String role, String tier) {
        CreateMatchRequestCommand command =
                command(userId, GameKey.VALORANT, KeyConditionType.ROLE, modeKey, role);
        command.setTier(tier);

        String requestId = matchRequestService.join(command).orElseThrow(
                () -> new IllegalStateException("활성 요청 선점 실패: " + userId));
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow()
                .canJoin(command);
        return requestId;
    }

    // ── INV-3 / INV-8 / INV-2 : 티어를 보지 않는 모드 ─────────────────────────

    @Test
    @DisplayName("INV-3: 일반전 3인 파티에 20명이 동시에 들어와도 어느 파티도 3명을 넘지 않는다")
    void untieredPartyNeverExceedsTarget() throws InterruptedException {
        runConcurrently(20, i -> join("u" + i, UNTIERED_MODE, ROLES.get(i % ROLES.size()), null));

        assertPartySizeWithinTarget();
    }

    @Test
    @DisplayName("INV-8: 일반전 파티에 같은 역할군이 두 명 들어가지 않는다")
    void untieredRoleIsUniqueWithinParty() throws InterruptedException {
        runConcurrently(20, i -> join("u" + i, UNTIERED_MODE, ROLES.get(i % ROLES.size()), null));

        assertRolesUnique();
    }

    @Test
    @DisplayName("INV-2 근사: 일반전에서 한 사용자가 두 파티에 동시에 들어가지 않는다")
    void untieredUserBelongsToOnlyOneParty() throws InterruptedException {
        runConcurrently(20, i -> join("u" + i, UNTIERED_MODE, ROLES.get(i % ROLES.size()), null));

        assertUserBelongsToOneParty();
    }

    // ── 같은 것을 티어 모드에서 한 번 더 ─────────────────────────────────────
    //
    // 스크립트가 다르다 (create-or-check-party-tiered / join-party-tiered). 티어 판은
    // 색인이 (역할군 x 티어) 격자라 칸을 빼는 자리가 훨씬 많고, 정원이 찼을 때 파티의
    // tierLo~tierHi 칸을 전부 지워야 한다. 한 칸이라도 남으면 정원을 넘길 수 있다.

    @Test
    @DisplayName("INV-3: 경쟁전 3인 파티에 30명이 동시에 들어와도 어느 파티도 3명을 넘지 않는다")
    void tieredPartyNeverExceedsTarget() throws InterruptedException {
        runConcurrently(30, i -> join("u" + i, TIERED_MODE,
                ROLES.get(i % ROLES.size()), TIERS.get(i % TIERS.size())));

        assertPartySizeWithinTarget();
    }

    @Test
    @DisplayName("INV-8: 경쟁전 파티에 같은 역할군이 두 명 들어가지 않는다")
    void tieredRoleIsUniqueWithinParty() throws InterruptedException {
        runConcurrently(30, i -> join("u" + i, TIERED_MODE,
                ROLES.get(i % ROLES.size()), TIERS.get(i % TIERS.size())));

        assertRolesUnique();
    }

    @Test
    @DisplayName("INV-2 근사: 경쟁전에서 한 사용자가 두 파티에 동시에 들어가지 않는다")
    void tieredUserBelongsToOnlyOneParty() throws InterruptedException {
        runConcurrently(30, i -> join("u" + i, TIERED_MODE,
                ROLES.get(i % ROLES.size()), TIERS.get(i % TIERS.size())));

        assertUserBelongsToOneParty();
    }

    // ── 티어 모드 특유 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("INV-8: 동시에 들어와도 '파티 최고 티어 <= 한계(파티 최저 티어)' 가 깨지지 않는다")
    void tieredPartyObeysRiotTierRule() throws InterruptedException {
        runConcurrently(30, i -> join("u" + i, TIERED_MODE,
                ROLES.get(i % ROLES.size()), TIERS.get(i % TIERS.size())));

        List<String> keys = valorantPartyKeys();
        assertThat(keys).isNotEmpty();

        boolean sawMultiMemberParty = false;
        for (String key : keys) {
            Map<Object, Object> party = redis.<Object, Object>opsForHash().entries(key);

            List<Integer> ranks = party.entrySet().stream()
                    .filter(e -> ((String) e.getKey()).startsWith("tier:"))
                    .map(e -> LADDER.indexOf((String) e.getValue()))
                    .toList();

            assertThat(ranks).doesNotContain(-1);          // 사다리에 없는 티어가 섞이면 안 된다
            assertThat(ranks).hasSize(memberValues(key).size());  // 멤버마다 tier: 필드가 하나씩

            if (ranks.size() > 1) {
                sawMultiMemberParty = true;
            }

            int min = ranks.stream().mapToInt(Integer::intValue).min().orElseThrow();
            int max = ranks.stream().mapToInt(Integer::intValue).max().orElseThrow();

            // 라이엇 공식 규칙 그대로. 표를 거치지 않고 규칙식으로 다시 확인한다 —
            // 표가 틀렸는데 스크립트가 표를 잘 따라 파티를 만든 경우도 여기서 잡힌다.
            assertThat(max)
                    .as("파티 %s: 최저 %s 의 한계는 %s 인데 최고가 %s 다",
                            key, LADDER.get(min), LADDER.get(limitOf(min)), LADDER.get(max))
                    .isLessThanOrEqualTo(limitOf(min));

            // 색인 칸(tierLo~tierHi) 밖의 사람이 들어와 있으면 안 된다.
            int tierLo = Integer.parseInt((String) party.get("tierLo"));
            int tierHi = Integer.parseInt((String) party.get("tierHi"));
            assertThat(min).isGreaterThanOrEqualTo(tierLo);
            assertThat(max).isLessThanOrEqualTo(tierHi);

            // minTier/maxTier 는 '아직 자리가 남은' 파티에서만 실제 최저/최고와 같다.
            //
            // 정원이 찬 파티는 예외다 — join-party-tiered.lua 는 size >= target 분기에서
            // 색인 칸을 전부 지우고 바로 반환하므로, 마지막 한 명은 minTier/maxTier 에
            // 반영되지 않는다. 버그가 아니다: 찬 파티는 어느 색인에도 없어 이 두 값을
            // 읽을 주체가 없고, 누가 나가면 leave-party.lua 가 남은 사람들의 tier: 필드로
            // 처음부터 다시 계산해 덮어쓴다. 여기서 찬 파티까지 단언하면 그 설계를
            // 깨는 방향으로 운영 코드를 고치게 된다.
            if (ranks.size() < TARGET) {
                assertThat(Integer.parseInt((String) party.get("minTier"))).isEqualTo(min);
                assertThat(Integer.parseInt((String) party.get("maxTier"))).isEqualTo(max);
            }
        }

        // 전원이 1인 파티로 흩어졌다면 위 단언은 전부 공짜로 통과한다.
        assertThat(sawMultiMemberParty)
                .as("2명 이상인 파티가 하나도 없다 — 배정이 아예 안 된 것이라 단언이 무의미하다")
                .isTrue();
    }

    // ── INV-5 ④ : 제안 도중 '취소' 로 빠진 자리 ──────────────────────────────

    @Test
    @DisplayName("INV-5: 제안 도중 취소로 빠져도 파티가 다시 차면 새 제안이 열리고 옛 수락이 남지 않는다")
    void cancellingDuringProposalClearsItAndAllowsANewOne() {
        // 단일 스레드다. 경합이 아니라 취소 스크립트가 제안 흔적을 지우는지를 본다.
        String r1 = join("u1", UNTIERED_MODE, "DUELIST", null);
        join("u2", UNTIERED_MODE, "INITIATOR", null);
        String r3 = join("u3", UNTIERED_MODE, "CONTROLLER", null);

        String partyId = partyIdOf("u1");
        assertThat(partyId).as("3인 파티가 만들어지지 않았다").isNotBlank();
        assertThat(partyIdOf("u3")).isEqualTo(partyId);
        assertThat(field(partyId, "status")).isEqualTo("PENDING");

        String firstExpiresAt = field(partyId, "expiresAt");
        assertThat(firstExpiresAt).isNotBlank();

        // u1 이 먼저 수락해 둔다. 이 수락이 다음 제안까지 살아남으면 INV-5 ④ 가 깨진다 —
        // 새로 들어온 사람은 u1 이 수락한 그 제안을 본 적이 없다.
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(acceptCount(partyId)).isEqualTo(1);

        // 거절이 아니라 '취소' 로 빠진다. decline-proposal.lua 가 아니라 leave-party.lua 를 탄다.
        matchCancelService.cancel("u3", r3);

        assertThat(field(partyId, "status"))
                .as("취소가 status 를 남기면 파티가 다시 차도 HSETNX 가 막혀 새 제안이 안 열린다")
                .isNull();
        assertThat(field(partyId, "expiresAt")).isNull();
        assertThat(acceptCount(partyId))
                .as("옛 수락이 남으면 새 멤버 한 명의 수락으로 SCARD 가 target 에 닿아 확정된다")
                .isZero();

        // 빈 자리를 새 사람이 채운다. 같은 파티에 들어가야 이 테스트가 의미가 있다.
        join("u4", UNTIERED_MODE, "CONTROLLER", null);
        assertThat(partyIdOf("u4"))
                .as("취소로 비워진 자리에 새 사람이 같은 파티로 들어가지 않았다")
                .isEqualTo(partyId);

        // 새 제안이 열렸는가. expiresAt 도 이번 제안 기준으로 다시 찍혀야 한다.
        assertThat(field(partyId, "status")).isEqualTo("PENDING");
        String secondExpiresAt = field(partyId, "expiresAt");
        assertThat(secondExpiresAt).isNotBlank();
        assertThat(Long.parseLong(secondExpiresAt))
                .as("옛 expiresAt 이 그대로면 새 제안이 만료된 시한을 물려받는다")
                .isGreaterThanOrEqualTo(Long.parseLong(firstExpiresAt));

        // u1 의 옛 수락이 지워졌으므로, 새 제안은 u1 이 다시 눌러야 한 표가 된다.
        assertThat(acceptCount(partyId)).isZero();
        assertThat(proposalService.accept(partyId, "u4")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(field(partyId, "status"))
                .as("한 명만 수락했는데 확정됐다 — 옛 수락이 살아 있었다는 뜻이다")
                .isEqualTo("PENDING");

        // r1 은 u1 이 여전히 큐에 남아 있다는 것을 보이는 데만 쓴다.
        assertThat(r1).isNotBlank();
        assertThat(partyIdOf("u1")).isEqualTo(partyId);
    }

    // ── 공통 단언 ────────────────────────────────────────────────────────────

    /**
     * 파티 HASH 키만. 부모의 {@code partyKeys()} 를 그대로 쓸 수 없다 —
     * 발로란트 스크립트는 {@code qm:party:needs-roles:{partyId}} SET 을 곁딸린 키로 두는데,
     * 그것도 {@code qm:party:*} 에 걸려 HASH 로 읽으면 WRONGTYPE 이 난다.
     */
    private List<String> valorantPartyKeys() {
        return partyKeys().stream()
                .filter(key -> !key.startsWith("qm:party:needs-roles:"))
                .toList();
    }

    private void assertPartySizeWithinTarget() {
        List<String> keys = valorantPartyKeys();
        assertThat(keys).isNotEmpty();

        for (String key : keys) {
            Map<Object, Object> party = redis.<Object, Object>opsForHash().entries(key);
            long members = party.keySet().stream()
                    .filter(field -> ((String) field).startsWith("member:"))
                    .count();

            assertThat(members).isLessThanOrEqualTo((long) TARGET);
            // 인원 수를 따로 저장하지 않는다 — member: 필드를 세는 것이 인원이다.
            // 카운터(HINCRBY)는 재시도 때 두 번 더해져 실제 멤버 수와 어긋난다.
            assertThat(party).doesNotContainKey("size");
        }
    }

    private void assertRolesUnique() {
        List<String> keys = valorantPartyKeys();
        assertThat(keys).isNotEmpty();

        for (String key : keys) {
            List<String> taken = memberValues(key);
            assertThat(taken).doesNotHaveDuplicates();
            assertThat(ROLES).containsAll(taken);
        }
    }

    private void assertUserBelongsToOneParty() {
        List<String> keys = valorantPartyKeys();
        assertThat(keys).isNotEmpty();

        List<String> allMembers = new ArrayList<>(keys.stream()
                .flatMap(key -> redis.<Object, Object>opsForHash().keys(key).stream())
                .map(field -> (String) field)
                .filter(field -> field.startsWith("member:"))
                .collect(Collectors.toList()));

        assertThat(allMembers).doesNotHaveDuplicates();
        assertThat(Set.copyOf(allMembers)).hasSize(allMembers.size());
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get("qm:user:active-request:" + userId, "partyId");
    }

    private String field(String partyId, String field) {
        return redis.<String, String>opsForHash().get("qm:party:" + partyId, field);
    }

    private long acceptCount(String partyId) {
        Long size = redis.opsForSet().size("qm:proposal:accepts:" + partyId);
        return size == null ? 0 : size;
    }

    /**
     * 라이엇 공식 경쟁전 규칙의 '한계'. 파티 최저 티어가 t 일 때 받을 수 있는 최고 티어 순번.
     *
     * <p>아이언/브론즈는 SILVER_3, 실버는 GOLD_3, 골드는 PLATINUM_3 으로 천장이 고정이고,
     * 플래티넘 이상부터 '자기 +3' 이다 (상한 RADIANT).
     */
    private int limitOf(int rank) {
        String name = LADDER.get(rank);
        String tier = name.substring(0, name.indexOf('_') < 0 ? name.length() : name.indexOf('_'));

        Map<String, String> ceiling = new HashMap<>();
        ceiling.put("IRON", "SILVER_3");
        ceiling.put("BRONZE", "SILVER_3");
        ceiling.put("SILVER", "GOLD_3");
        ceiling.put("GOLD", "PLATINUM_3");

        String fixed = ceiling.get(tier);
        if (fixed != null) {
            return LADDER.indexOf(fixed);
        }
        return Math.min(rank + 3, LADDER.size() - 1);
    }
}
