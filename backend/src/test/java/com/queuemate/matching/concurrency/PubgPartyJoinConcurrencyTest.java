package com.queuemate.matching.concurrency;

import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.condition.KeyConditionType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PUBG 경로의 불변식 회귀. {@code PartyJoinConcurrencyTest}(LoL) / {@code ValorantPartyJoinConcurrencyTest}
 * 와 같은 것을 지킨다.
 *
 * <p>왜 게임마다 따로 있어야 하나 — 배정 Lua 를 게임별 디렉터리로 나눈 대가다 (CLAUDE.md §4).
 * 스크립트가 한 벌일 때는 한 벌의 테스트가 전부를 지켰지만, 나뉜 뒤로는 테스트 없는 게임
 * 스크립트가 <b>아무도 실행하지 않는 코드</b>가 된다. 이 파일이 생기기 전까지
 * {@code redis/pubg/*.lua} 다섯 개를 도는 테스트는 하나도 없었다.
 *
 * <p><b>PUBG 가 다른 두 게임과 다른 점.</b>
 * <ul>
 *   <li>핵심 조건(keyValue)이 플랫폼(STEAM / KAKAO)이고 <b>중복 금지가 없다</b>. 한 파티에
 *       같은 플랫폼이 여럿인 것이 정상이라 gameconfig 에 {@code positionUniqueness} 필드
 *       자체가 없다. 그래서 색인이 (핵심조건 x 티어) 격자가 아니라 <b>티어 한 줄</b>이다.</li>
 *   <li>참가자 필드 값이 keyValue 가 아니라 자리 채움 {@code 'EXIST'} 다. 플랫폼은
 *       색인 키 이름에 이미 들어 있어 파티 HASH 에서 다시 읽을 일이 없다.</li>
 *   <li>멤버 티어를 파티 HASH 에 적지 않는다(발로란트의 {@code tier:{userId}} 가 없다).
 *       그래서 티어 규칙 단언은 <b>테스트가 누구를 어떤 티어로 넣었는지 스스로 기억해서</b>
 *       검증한다.</li>
 *   <li>파티가 받아들일 티어 범위는 <b>만든 사람 기준으로 한 번</b> 정해지고 바뀌지 않는다
 *       (발로란트처럼 합류할 때마다 좁히지 않는다).</li>
 * </ul>
 */
class PubgPartyJoinConcurrencyTest extends ConcurrencyTestSupport {

    private static final String TIERED_MODE = "RANKED_SQUAD_TPP";
    private static final String UNTIERED_MODE = "NORMAL_SQUAD_TPP";
    private static final int TARGET = 4;

    /** 스쿼드 표가 ±5 라 파티가 받아들이는 폭이 최대 10 이다 (seed/gameconfig.redis 의 근거 주석). */
    private static final int MAX_SQUAD_SPREAD = 10;

    private static final String STEAM = "STEAM";
    private static final String KAKAO = "KAKAO";

    /**
     * 티어 사다리. {@code seed/gameconfig.redis} 의 PUBG 섹션과 <b>같은 값</b>이어야 한다.
     *
     * <p>롤과 같은 표기다 — 단(division)은 숫자가 클수록 낮다(브론즈4 → 브론즈1 → 실버4).
     * 사다리 순번(index)은 반대로 <b>클수록 높은 티어</b>다.
     */
    private static final List<String> LADDER = List.of(
            "UNRANKED",
            "BRONZE_4", "BRONZE_3", "BRONZE_2", "BRONZE_1",
            "SILVER_4", "SILVER_3", "SILVER_2", "SILVER_1",
            "GOLD_4", "GOLD_3", "GOLD_2", "GOLD_1",
            "PLATINUM_4", "PLATINUM_3", "PLATINUM_2", "PLATINUM_1",
            "CRYSTAL_4", "CRYSTAL_3", "CRYSTAL_2", "CRYSTAL_1",
            "DIAMOND_4", "DIAMOND_3", "DIAMOND_2", "DIAMOND_1",
            "MASTER", "SURVIVOR");

    /**
     * 랭크 스쿼드(4인)의 티어별 허용 범위 표. 시드가 생성한 값을 그대로 옮겼다.
     *
     * <p>손으로 고치지 마라 — 시드와 어긋나면 이 테스트가 통과해도 운영에서는 다른 파티가 생긴다.
     */
    private static final String SQUAD_TIER_RANGE =
            "UNRANKED SOLO_ONLY "
            + "BRONZE_4 BRONZE_4:SILVER_3 BRONZE_3 BRONZE_4:SILVER_2 "
            + "BRONZE_2 BRONZE_4:SILVER_1 BRONZE_1 BRONZE_4:GOLD_4 "
            + "SILVER_4 BRONZE_4:GOLD_3 SILVER_3 BRONZE_4:GOLD_2 "
            + "SILVER_2 BRONZE_3:GOLD_1 SILVER_1 BRONZE_2:PLATINUM_4 "
            + "GOLD_4 BRONZE_1:PLATINUM_3 GOLD_3 SILVER_4:PLATINUM_2 "
            + "GOLD_2 SILVER_3:PLATINUM_1 GOLD_1 SILVER_2:CRYSTAL_4 "
            + "PLATINUM_4 SILVER_1:CRYSTAL_3 PLATINUM_3 GOLD_4:CRYSTAL_2 "
            + "PLATINUM_2 GOLD_3:CRYSTAL_1 PLATINUM_1 GOLD_2:DIAMOND_4 "
            + "CRYSTAL_4 GOLD_1:DIAMOND_3 CRYSTAL_3 PLATINUM_4:DIAMOND_2 "
            + "CRYSTAL_2 PLATINUM_3:DIAMOND_1 CRYSTAL_1 PLATINUM_2:MASTER "
            + "DIAMOND_4 PLATINUM_1:SURVIVOR DIAMOND_3 CRYSTAL_4:SURVIVOR "
            + "DIAMOND_2 CRYSTAL_3:SURVIVOR DIAMOND_1 CRYSTAL_2:SURVIVOR "
            + "MASTER CRYSTAL_1:SURVIVOR SURVIVOR DIAMOND_4:SURVIVOR";

    /**
     * 랭크에 쓸 티어 표본.
     *
     * <p>아무 데나 고르면 안 된다. 서로 되는 쌍과 안 되는 쌍이 섞여야 색인 칸을 빼는 코드가
     * 실제로 일을 한다. 순번으로 7 / 9 / 12 / 14 / 17 이라 양 끝(SILVER_2 ~ CRYSTAL_4)은
     * 정확히 10 단계 차이다 — GOLD_1(12) 이 만든 파티의 범위 [7,17] 양끝에 딱 걸린다.
     * 반대로 SILVER_2 가 만든 파티(범위 [2,12])에는 CRYSTAL_4 가 들어올 수 없다.
     */
    private static final List<String> TIERS =
            List.of("SILVER_2", "GOLD_4", "GOLD_1", "PLATINUM_3", "CRYSTAL_4");

    @Autowired
    private List<CandidateRule> candidateRules;

    @Autowired
    private MatchRequestService matchRequestService;

    @Autowired
    private MatchCancelService matchCancelService;

    @Autowired
    private ProposalService proposalService;

    /**
     * 누구를 어떤 티어 / 플랫폼으로 넣었는지. PUBG 는 둘 다 파티 HASH 에 남기지 않으므로
     * (참가자 값은 {@code 'EXIST'} 자리 채움이다) 테스트가 기억하는 수밖에 없다.
     */
    private final Map<String, String> tierOf = new ConcurrentHashMap<>();
    private final Map<String, String> platformOf = new ConcurrentHashMap<>();

    /**
     * PUBG gameconfig. 부모의 {@code resetRedis()} 가 DB 15 를 비운 뒤에 돌아야 하므로
     * 여기서 다시 넣는다 (JUnit 은 상위 클래스의 {@code @BeforeEach} 를 먼저 실행한다).
     *
     * <p>앱은 gameconfig 를 밀어넣지 않고 읽기만 한다 (CLAUDE.md §3). 테스트에서만 넣는 것이고,
     * 값은 {@code seed/gameconfig.redis} 와 같아야 한다.
     *
     * <p>{@code positionUniqueness} 를 <b>일부러 넣지 않는다.</b> 시드에도 없다 —
     * 배그에는 중복을 금지할 대상 자체가 없다.
     *
     * <p>{@code tierLadder} 도 넣지 않는다 — 시드에는 있지만(랭크 모드의 사다리 키, docs/11 D-48)
     * 읽는 쪽은 app:platform · frontend 이고 이 앱은 읽지 않는다.
     */
    @BeforeEach
    void seedPubgGameConfig() {
        tierOf.clear();
        platformOf.clear();

        redis.opsForHash().putAll("qm:gameconfig:PUBG:" + TIERED_MODE, Map.of(
                "targetPartySize", String.valueOf(TARGET),
                "tierRule", "EXIST"));
        redis.opsForHash().putAll("qm:gameconfig:PUBG:" + UNTIERED_MODE, Map.of(
                "targetPartySize", String.valueOf(TARGET),
                "tierRule", "NONE"));

        for (int rank = 0; rank < LADDER.size(); rank++) {
            redis.opsForZSet().add("qm:gameconfig:PUBG:tier", LADDER.get(rank), rank);
        }

        Map<String, String> table = new LinkedHashMap<>();
        String[] tokens = SQUAD_TIER_RANGE.split(" ");
        for (int i = 0; i < tokens.length; i += 2) {
            table.put(tokens[i], tokens[i + 1]);
        }
        assertThat(table).hasSize(LADDER.size());
        redis.opsForHash().putAll("qm:gameconfig:PUBG:tier-range:" + TIERED_MODE, table);
    }

    /**
     * 컨트롤러와 같은 두 단계를 태운다 (MatchingController#createMatchRequest).
     *
     * <p>claim 을 건너뛰면 배정 Lua 가 맨 앞의 {@code EXISTS qm:user:active-request:{userId}} 에서
     * -2 를 돌려주어 파티가 하나도 안 생긴다. 그러면 "중복 없음" 류의 단언이 빈 목록 위에서
     * 자동 통과한다.
     */
    private String join(String userId, String modeKey, String platform, String tier) {
        platformOf.put(userId, platform);
        if (tier != null) {
            tierOf.put(userId, tier);
        }

        CreateMatchRequestCommand command =
                command(userId, GameKey.PUBG, KeyConditionType.PLATFORM, modeKey, platform);
        command.setTier(tier);

        String requestId = matchRequestService.join(command).accepted().orElseThrow(
                () -> new IllegalStateException("활성 요청 선점 실패: " + userId)).requestId();
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow()
                .canJoin(command);
        return requestId;
    }

    // ── INV-3 / INV-2 : 티어를 보지 않는 모드 ─────────────────────────────────

    @Test
    @DisplayName("INV-3: 일반전 4인 파티에 20명이 동시에 들어와도 어느 파티도 4명을 넘지 않는다")
    void untieredPartyNeverExceedsTarget() throws InterruptedException {
        runConcurrently(20, i -> join("u" + i, UNTIERED_MODE, platform(i), null));

        assertPartySizeWithinTarget();
    }

    @Test
    @DisplayName("INV-2 근사: 일반전에서 한 사용자가 두 파티에 동시에 들어가지 않는다")
    void untieredUserBelongsToOnlyOneParty() throws InterruptedException {
        runConcurrently(20, i -> join("u" + i, UNTIERED_MODE, platform(i), null));

        assertUserBelongsToOneParty();
    }

    // ── 같은 것을 티어 모드에서 한 번 더 ─────────────────────────────────────
    //
    // 스크립트가 다르다 (create-or-check-party-tiered / join-party-tiered). 티어 판은
    // 파티 하나가 자기 범위의 티어 칸 전부에 올라가 있어, 정원이 찼을 때 그 칸을 모두
    // 지워야 한다. 한 칸이라도 남으면 정원을 넘길 수 있다.

    @Test
    @DisplayName("INV-3: 랭크 4인 파티에 30명이 동시에 들어와도 어느 파티도 4명을 넘지 않는다")
    void tieredPartyNeverExceedsTarget() throws InterruptedException {
        runConcurrently(30, i -> join("u" + i, TIERED_MODE, platform(i), TIERS.get(i % TIERS.size())));

        assertPartySizeWithinTarget();
    }

    @Test
    @DisplayName("INV-2 근사: 랭크에서 한 사용자가 두 파티에 동시에 들어가지 않는다")
    void tieredUserBelongsToOnlyOneParty() throws InterruptedException {
        runConcurrently(30, i -> join("u" + i, TIERED_MODE, platform(i), TIERS.get(i % TIERS.size())));

        assertUserBelongsToOneParty();
    }

    // ── PUBG 고유 : 핵심 조건에 중복 금지가 없다 ─────────────────────────────

    @Test
    @DisplayName("PUBG 고유: 같은 플랫폼(STEAM)만으로도 파티가 정원까지 찬다 — 중복 금지가 없다")
    void sameKeyValueFillsPartyToTarget() throws InterruptedException {
        // LoL 이라면 여덟 명이 전부 같은 포지션일 때 파티가 1인짜리 여덟 개로 흩어진다.
        // 배그의 keyValue 는 플랫폼이고, 스팀 유저 넷이 한 파티인 것이 정상이다.
        runConcurrently(8, i -> join("u" + i, UNTIERED_MODE, STEAM, null));

        List<String> keys = partyKeys();
        assertThat(keys).isNotEmpty();

        assertThat(totalMembers(keys))
                .as("여덟 명이 전부 배정돼야 한다")
                .isEqualTo(8);

        assertThat(keys.stream().anyMatch(key -> memberIdsOf(key).size() == TARGET))
                .as("같은 플랫폼만으로는 정원이 안 찼다 — 배그에 없는 중복 금지가 끼어든 것이다")
                .isTrue();

        for (String key : keys) {
            // 참가자 값은 keyValue 가 아니라 자리 채움이다. 플랫폼을 여기에 적으면
            // 색인 키 이름과 두 곳에 같은 사실이 적히게 된다.
            assertThat(memberValues(key)).containsOnly("EXIST");
        }
    }

    @Test
    @DisplayName("INV-8 구조적 분리: 스팀과 카카오가 한 파티에 섞이지 않는다")
    void platformsNeverShareAParty() throws InterruptedException {
        runConcurrently(20, i -> join("u" + i, UNTIERED_MODE, platform(i), null));

        List<String> keys = partyKeys();
        assertThat(keys).isNotEmpty();

        for (String key : keys) {
            Set<String> platforms = memberIdsOf(key).stream()
                    .map(platformOf::get)
                    .collect(Collectors.toSet());

            assertThat(platforms)
                    .as("파티 %s 에 두 플랫폼이 섞였다 — 서버가 갈려 같이 게임에 못 들어간다", key)
                    .hasSize(1);
        }
    }

    // ── 티어 모드 특유 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("INV-8: 동시에 들어와도 파티 폭(최고-최저)이 10 을 넘지 않고, 전원이 tierLo~tierHi 안이다")
    void tieredPartyObeysSquadTierRule() throws InterruptedException {
        runConcurrently(30, i -> join("u" + i, TIERED_MODE, platform(i), TIERS.get(i % TIERS.size())));

        List<String> keys = partyKeys();
        assertThat(keys).isNotEmpty();

        boolean sawMultiMemberParty = false;
        for (String key : keys) {
            Map<Object, Object> party = redis.<Object, Object>opsForHash().entries(key);

            List<Integer> ranks = memberIdsOf(key).stream()
                    .map(tierOf::get)
                    .map(LADDER::indexOf)
                    .toList();

            assertThat(ranks)
                    .as("파티 %s 에 티어를 모르는 멤버가 있다", key)
                    .doesNotContain(-1);
            assertThat(ranks).isNotEmpty();

            if (ranks.size() > 1) {
                sawMultiMemberParty = true;
            }

            int min = ranks.stream().mapToInt(Integer::intValue).min().orElseThrow();
            int max = ranks.stream().mapToInt(Integer::intValue).max().orElseThrow();

            // 게임 공식 규칙은 "파티 최고-최저 <= 12 단계"다. 우리 표는 ±5 라 구조적으로
            // 10 을 넘을 수 없다 — 그 구조가 동시에 들어와도 지켜지는지를 본다.
            assertThat(max - min)
                    .as("파티 %s: 최저 %s 최고 %s 로 폭이 %d 다",
                            key, LADDER.get(min), LADDER.get(max), max - min)
                    .isLessThanOrEqualTo(MAX_SQUAD_SPREAD);

            // 색인 칸(tierLo~tierHi) 밖의 사람이 들어와 있으면 안 된다.
            // 범위는 파티를 만든 사람 기준으로 한 번 정해지고 바뀌지 않는다.
            int tierLo = Integer.parseInt((String) party.get("tierLo"));
            int tierHi = Integer.parseInt((String) party.get("tierHi"));
            assertThat(min)
                    .as("파티 %s: 범위 밖(아래) 멤버가 들어왔다", key)
                    .isGreaterThanOrEqualTo(tierLo);
            assertThat(max)
                    .as("파티 %s: 범위 밖(위) 멤버가 들어왔다", key)
                    .isLessThanOrEqualTo(tierHi);
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
        String r1 = join("u1", UNTIERED_MODE, STEAM, null);
        join("u2", UNTIERED_MODE, STEAM, null);
        join("u3", UNTIERED_MODE, STEAM, null);
        String r4 = join("u4", UNTIERED_MODE, STEAM, null);

        String partyId = partyIdOf("u1");
        assertThat(partyId).as("4인 파티가 만들어지지 않았다").isNotBlank();
        assertThat(partyIdOf("u4")).isEqualTo(partyId);
        assertThat(field(partyId, "status")).isEqualTo("PENDING");

        String firstExpiresAt = field(partyId, "expiresAt");
        assertThat(firstExpiresAt).isNotBlank();
        assertThat(pendingScore(partyId))
                .as("정원이 찰 때 qm:proposal:pending 에 올라가지 않았다 — 스위퍼가 이 제안을 영영 못 본다")
                .isNotNull();

        // u1 이 먼저 수락해 둔다. 이 수락이 다음 제안까지 살아남으면 INV-5 ④ 가 깨진다 —
        // 새로 들어온 사람은 u1 이 수락한 그 제안을 본 적이 없다.
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(acceptCount(partyId)).isEqualTo(1);

        // 거절이 아니라 '취소' 로 빠진다. decline-proposal.lua 가 아니라 leave-party.lua 를 탄다.
        matchCancelService.cancel("u4", r4);

        assertThat(field(partyId, "status"))
                .as("취소가 status 를 남기면 파티가 다시 차도 HSETNX 가 막혀 새 제안이 안 열린다")
                .isNull();
        assertThat(field(partyId, "expiresAt")).isNull();
        assertThat(acceptCount(partyId))
                .as("옛 수락이 남으면 새 멤버 한 명의 수락으로 SCARD 가 target 에 닿아 확정된다")
                .isZero();
        assertThat(pendingScore(partyId))
                .as("끝난 제안이 pending 에 남으면 스위퍼가 주기마다 영원히 다시 꺼낸다")
                .isNull();

        // 빈 자리를 새 사람이 채운다. 같은 파티에 들어가야 이 테스트가 의미가 있다.
        join("u5", UNTIERED_MODE, STEAM, null);
        assertThat(partyIdOf("u5"))
                .as("취소로 비워진 자리에 새 사람이 같은 파티로 들어가지 않았다 — 색인이 되돌아오지 않은 것이다")
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
        assertThat(proposalService.accept(partyId, "u5")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(field(partyId, "status"))
                .as("한 명만 수락했는데 확정됐다 — 옛 수락이 살아 있었다는 뜻이다")
                .isEqualTo("PENDING");

        // r1 은 u1 이 여전히 큐에 남아 있다는 것을 보이는 데만 쓴다.
        assertThat(r1).isNotBlank();
        assertThat(partyIdOf("u1")).isEqualTo(partyId);
    }

    // ── 취소 뒤 색인 복구 (티어 모드) ────────────────────────────────────────

    @Test
    @DisplayName("취소: 정원이 차서 색인에서 빠진 랭크 파티가 한 명 취소로 원래 티어 칸 전부에 되돌아온다")
    void cancelRestoresPartyToEveryTierCell() {
        // 넷 다 같은 티어라 첫 사람이 만든 파티의 범위 안에 전부 들어간다.
        String creatorTier = "GOLD_1";                       // 순번 12, 범위 [7,17]
        join("p1", TIERED_MODE, STEAM, creatorTier);
        join("p2", TIERED_MODE, STEAM, creatorTier);
        join("p3", TIERED_MODE, STEAM, creatorTier);
        String r4 = join("p4", TIERED_MODE, STEAM, creatorTier);

        String partyId = partyIdOf("p1");
        assertThat(partyId).isNotBlank();
        assertThat(partyIdOf("p4")).isEqualTo(partyId);

        int tierLo = Integer.parseInt(field(partyId, "tierLo"));
        int tierHi = Integer.parseInt(field(partyId, "tierHi"));
        assertThat(LADDER.get(tierLo)).isEqualTo("SILVER_2");
        assertThat(LADDER.get(tierHi)).isEqualTo("CRYSTAL_4");

        // 정원이 찼으니 범위의 모든 칸에서 빠져 있어야 한다. 한 칸이라도 남으면
        // 그 칸으로 들어온 사람이 이미 찬 파티에 합류해 INV-3 이 깨진다.
        for (int rank = tierLo; rank <= tierHi; rank++) {
            assertThat(cellScore(STEAM, LADDER.get(rank), partyId))
                    .as("정원이 찼는데 %s 칸에 파티가 남아 있다", LADDER.get(rank))
                    .isNull();
        }

        matchCancelService.cancel("p4", r4);

        // 자리가 비었으니 범위의 모든 칸에 되돌아와야 한다. 되돌아오지 않으면
        // 그 파티는 세 명인 채로 영영 안 채워진다.
        for (int rank = tierLo; rank <= tierHi; rank++) {
            assertThat(cellScore(STEAM, LADDER.get(rank), partyId))
                    .as("취소했는데 %s 칸에 파티가 되돌아오지 않았다", LADDER.get(rank))
                    .isNotNull();
        }

        // 범위 밖 칸까지 올리면 규칙을 어기는 사람이 들어온다.
        assertThat(cellScore(STEAM, LADDER.get(tierLo - 1), partyId))
                .as("범위 아래 칸(%s)까지 되돌아왔다", LADDER.get(tierLo - 1))
                .isNull();
        assertThat(cellScore(STEAM, LADDER.get(tierHi + 1), partyId))
                .as("범위 위 칸(%s)까지 되돌아왔다", LADDER.get(tierHi + 1))
                .isNull();

        // 색인이 되돌아온 것으로 끝이 아니다. 실제로 새 사람이 그 자리를 채울 수 있어야 한다.
        join("p5", TIERED_MODE, STEAM, creatorTier);
        assertThat(partyIdOf("p5")).isEqualTo(partyId);
        assertThat(memberIdsOf("qm:party:" + partyId)).hasSize(TARGET);
    }

    // ── 공통 단언 ────────────────────────────────────────────────────────────

    /**
     * 짝수는 스팀, 홀수는 카카오. 플랫폼이 다르면 색인 키부터 갈리므로 파티가 섞이지 않는다.
     */
    private String platform(int i) {
        return i % 2 == 0 ? STEAM : KAKAO;
    }

    private void assertPartySizeWithinTarget() {
        List<String> keys = partyKeys();
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

    private void assertUserBelongsToOneParty() {
        List<String> keys = partyKeys();
        assertThat(keys).isNotEmpty();

        List<String> allMembers = new ArrayList<>(keys.stream()
                .flatMap(key -> memberIdsOf(key).stream())
                .toList());

        assertThat(allMembers).doesNotHaveDuplicates();
        assertThat(Set.copyOf(allMembers)).hasSize(allMembers.size());
    }

    /** 파티 HASH 의 {@code member:{userId}} 필드에서 userId 만 뽑는다. */
    private List<String> memberIdsOf(String partyKey) {
        return redis.<Object, Object>opsForHash().keys(partyKey).stream()
                .map(field -> (String) field)
                .filter(field -> field.startsWith("member:"))
                .map(field -> field.substring("member:".length()))
                .toList();
    }

    private int totalMembers(List<String> keys) {
        return keys.stream().mapToInt(key -> memberIdsOf(key).size()).sum();
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

    private Double pendingScore(String partyId) {
        return redis.opsForZSet().score("qm:proposal:pending", partyId);
    }

    /**
     * needs 색인의 티어 칸 하나. 자바 쪽 {@code PubgPartyKeys#needsKey} 와 같은 문자열이어야 한다 —
     * 배정 Lua 는 접미사 없는 키를 받아 {@code ':' .. 티어이름} 을 스스로 붙인다.
     */
    private Double cellScore(String platform, String tier, String partyId) {
        String cell = "qm:party:open:PUBG:" + TIERED_MODE + ":REQUIRED:RANK_UP:needs:" + platform
                + ":" + tier;
        return redis.opsForZSet().score(cell, partyId);
    }
}
