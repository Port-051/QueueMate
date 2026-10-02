package com.queuemate.matching.concurrency;

import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.condition.KeyConditionType;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.MatchRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-6 — 차단 관계의 사용자는 같은 파티가 될 수 없다. <b>합류 Lua 가 Redis 집합을 읽어 지킨다</b> (docs/11 D-57, 2026-10-02).
 *
 * <p>차단 관계는 {@code app:platform} 이 SET {@code qm:user:block-rel:{userId}} 에 쓴다 — A 가 B 를 차단하면 A 의 집합에 B,
 * B 의 집합에 A 를 같이 넣는다(대칭, platform P-52). 이 앱은 세 게임의 {@code join-party*.lua} 가 <b>들어오려는 사람의</b>
 * 집합을 KEYS 의 마지막으로 받아 파티원마다 {@code SISMEMBER} 하고, 걸리면 아무것도 쓰지 않고 {@code -3} 을 돌려준다.
 * 자바({@code *Assigner})는 그 파티를 건너뛰고 다음 후보를 보며, 후보가 없으면 새 파티를 만든다.
 * 그 전에는 {@code *CandidateRule#canJoin} 이 DB 의 {@code blocks} 를 읽어 자바에서 걸렀다(D-41 의 선필터 한 겹).
 *
 * <p>테스트가 집합을 직접 {@code SADD} 한다 — platform 이 쓰는 자리를 흉내 낸다. 키 문자열은 {@code SharedKeys.BLOCK_REL_PREFIX} 를
 * 쓰지 않고 리터럴로 적는다 — 자바 상수가 platform 과 어긋나게 바뀌면 여기서 드러나게.
 *
 * <p>여섯 합류 스크립트(LoL · PUBG · VALORANT × 티어 유/무)를 전부 한 번씩 태운다. 게임마다 "차단이면 다른 파티" 와 짝으로
 * "차단이 없으면 같은 파티" 를 두어, 다른 파티가 된 이유가 차단이라고 말할 수 있게 한다. 마지막 하나는 동시에 들어오는 경우다.
 *
 * <p>{@link ConcurrencyTestSupport} 의 Redis DB 15 · 테스트마다 flush · LoL gameconfig 시드 · {@code command()} 헬퍼를 쓴다.
 * 동시성 테스트 하나를 빼면 단일 스레드 순차 실행이다 — 배정은 원래 {@code @Async} 지만 규칙을 직접 불러 순서를 고정한다.
 */
class BlockRelationTest extends ConcurrencyTestSupport {

    /** LoL 2인 · 포지션 중복 금지 — {@link ConcurrencyTestSupport} 가 시드한다. 티어 모드로도 쓴다(아래 시드) */
    private static final String LOL_DUO = "RANKED_SOLO";
    /** LoL 5인 · 포지션 없음(NONE) — {@link ConcurrencyTestSupport} 가 시드한다 */
    private static final String LOL_ARAM = "ARAM_5";

    private static final String PUBG_NORMAL = "NORMAL_DUO_TPP";
    private static final String PUBG_RANKED = "RANKED_DUO_TPP";
    private static final String VALORANT_UNRATED = "UNRATED_DUO";
    private static final String VALORANT_COMPETITIVE = "COMPETITIVE_DUO";

    /** 티어 모드용 — 세 게임 모두 같은 이름의 작은 사다리를 심는다. 값의 뜻은 이 테스트에 상관없다 */
    private static final List<String> LADDER = List.of("SILVER_1", "GOLD_4", "GOLD_3");
    private static final String TIER = "GOLD_4";

    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private List<CandidateRule> candidateRules;

    /**
     * 부모의 {@code resetRedis()} 가 DB 15 를 비운 뒤에 돈다(JUnit 은 상위 클래스의 {@code @BeforeEach} 를 먼저 실행한다).
     * 티어 모드 셋은 사다리(ZSET)와 tier-range 표(HASH)가 있어야 찾기 스크립트가 파티를 만든다 — {@code GOLD_4} 하나만
     * "SILVER_1 ~ GOLD_3" 을 받게 한다. 둘이 같은 티어로 들어오므로 표가 범위를 좁히거나 갈라놓지 않는다.
     */
    @BeforeEach
    void seedOtherGames() {
        for (String game : List.of("LOL", "PUBG", "VALORANT")) {
            for (int rank = 0; rank < LADDER.size(); rank++) {
                redis.opsForZSet().add("qm:gameconfig:" + game + ":tier", LADDER.get(rank), rank);
            }
        }
        redis.opsForHash().put("qm:gameconfig:LOL:tier-range:" + LOL_DUO, TIER, "SILVER_1:GOLD_3");

        redis.opsForHash().putAll("qm:gameconfig:PUBG:" + PUBG_NORMAL, Map.of(
                "targetPartySize", "2", "tierRule", "NONE"));
        redis.opsForHash().putAll("qm:gameconfig:PUBG:" + PUBG_RANKED, Map.of(
                "targetPartySize", "2", "tierRule", "EXIST"));
        redis.opsForHash().put("qm:gameconfig:PUBG:tier-range:" + PUBG_RANKED, TIER, "SILVER_1:GOLD_3");

        redis.opsForHash().putAll("qm:gameconfig:VALORANT:" + VALORANT_UNRATED, Map.of(
                "targetPartySize", "2", "positionUniqueness", "true", "tierRule", "NONE"));
        redis.opsForHash().putAll("qm:gameconfig:VALORANT:" + VALORANT_COMPETITIVE, Map.of(
                "targetPartySize", "2", "positionUniqueness", "true", "tierRule", "EXIST"));
        redis.opsForHash().put("qm:gameconfig:VALORANT:tier-range:" + VALORANT_COMPETITIVE, TIER, "SILVER_1:GOLD_3");
    }

    // ── LoL · 티어 없음 (join-party.lua) ─────────────────────────────────────

    @Test
    @DisplayName("양쪽 집합에 서로가 있으면 같은 조건으로 들어와도 같은 파티가 되지 않는다 — 둘 다 각자 파티는 생긴다")
    void blockedBothWaysNeverShareAParty() {
        blockBothWays("u1", "u2");

        enqueue(lol("u1", LOL_DUO, "TOP", null));
        enqueue(lol("u2", LOL_DUO, "JUNGLE", null));

        assertSeparated("u1", "u2");
    }

    @Test
    @DisplayName("들어오는 사람의 집합에만 있어도 막힌다 — 이 앱은 들어오는 사람 쪽 키만 본다")
    void blockedWhenOnlyJoinersSetHasIt() {
        redis.opsForSet().add(blockRelKey("u2"), "u1");   // u2 가 나중에 들어온다

        enqueue(lol("u1", LOL_DUO, "TOP", null));
        enqueue(lol("u2", LOL_DUO, "JUNGLE", null));

        assertSeparated("u1", "u2");
    }

    @Test
    @DisplayName("기존 파티원 쪽 집합에만 있으면 막지 않는다 — 이 앱은 들어오는 사람 쪽 키만 보고, 양쪽에 쓰는 것은 platform 의 몫이다(P-52)")
    void notBlockedWhenOnlyExistingMembersSetHasIt() {
        redis.opsForSet().add(blockRelKey("u1"), "u2");   // u1 이 먼저 파티를 만든다

        enqueue(lol("u1", LOL_DUO, "TOP", null));
        enqueue(lol("u2", LOL_DUO, "JUNGLE", null));

        assertTogether("u1", "u2");
    }

    @Test
    @DisplayName("차단 집합이 비어 있으면(키가 없으면) 같은 파티가 된다")
    void noRelationMeansSameParty() {
        enqueue(lol("u1", LOL_DUO, "TOP", null));
        enqueue(lol("u2", LOL_DUO, "JUNGLE", null));

        assertTogether("u1", "u2");
    }

    @Test
    @DisplayName("상관없는 사람이 차단 집합에 있어도 같은 파티가 된다 — 파티원만 본다")
    void unrelatedEntriesDoNotBlock() {
        redis.opsForSet().add(blockRelKey("u2"), "u7", "u8");

        enqueue(lol("u1", LOL_DUO, "TOP", null));
        enqueue(lol("u2", LOL_DUO, "JUNGLE", null));

        assertTogether("u1", "u2");
    }

    @Test
    @DisplayName("방장이 아닌 파티원과의 차단도 본다 — 파티원 누구와든")
    void blockedWithAnyMemberNotJustTheCreator() {
        enqueue(lol("u1", LOL_ARAM, "NONE", null));
        enqueue(lol("u2", LOL_ARAM, "NONE", null));
        assertTogether("u1", "u2");

        blockBothWays("u3", "u2");
        enqueue(lol("u3", LOL_ARAM, "NONE", null));

        assertThat(partyIdOf("u3")).isNotBlank().isNotEqualTo(partyIdOf("u1"));
        assertThat(membersOf(partyIdOf("u1"))).containsExactlyInAnyOrder("u1", "u2");
    }

    @Test
    @DisplayName("차단 상대의 파티는 건너뛰고 다음 후보 파티에 들어간다 — 거절된 파티에는 아무것도 쓰이지 않는다")
    void skipsBlockedPartyAndJoinsTheNextCandidate() throws InterruptedException {
        enqueue(lol("u1", LOL_ARAM, "NONE", null));                // 파티 A
        Thread.sleep(5);   // 색인 점수(createdAt)를 갈라 A 가 B 보다 앞에 오게 한다 — 같은 ms 면 partyId 순이라 순서를 장담 못 한다
        redis.opsForSet().add(blockRelKey("u2"), "u1");
        enqueue(lol("u2", LOL_ARAM, "NONE", null));                // A 를 거절당하고 새 파티 B
        String partyA = partyIdOf("u1");
        String partyB = partyIdOf("u2");
        assertThat(partyB).isNotBlank().isNotEqualTo(partyA);

        redis.opsForSet().add(blockRelKey("u3"), "u1");
        enqueue(lol("u3", LOL_ARAM, "NONE", null));                // A(첫 후보)를 거절당하고 B(다음 후보)로

        assertThat(partyIdOf("u3")).isEqualTo(partyB);
        assertThat(membersOf(partyA)).containsExactly("u1");
        assertThat(membersOf(partyB)).containsExactlyInAnyOrder("u2", "u3");
    }

    // ── LoL · 티어 (join-party-tiered.lua) ───────────────────────────────────

    @Test
    @DisplayName("LoL 티어 모드 — 차단이면 다른 파티")
    void lolTieredBlocked() {
        blockBothWays("u1", "u2");

        enqueue(lol("u1", LOL_DUO, "TOP", TIER));
        enqueue(lol("u2", LOL_DUO, "JUNGLE", TIER));

        assertSeparated("u1", "u2");
    }

    @Test
    @DisplayName("LoL 티어 모드 — 차단이 없으면 같은 파티 (대조)")
    void lolTieredNotBlocked() {
        enqueue(lol("u1", LOL_DUO, "TOP", TIER));
        enqueue(lol("u2", LOL_DUO, "JUNGLE", TIER));

        assertTogether("u1", "u2");
    }

    // ── PUBG (join-party.lua · join-party-tiered.lua) ───────────────────────

    @Test
    @DisplayName("PUBG 일반전 — 차단이면 다른 파티")
    void pubgUntieredBlocked() {
        blockBothWays("u1", "u2");

        enqueue(pubg("u1", PUBG_NORMAL, null));
        enqueue(pubg("u2", PUBG_NORMAL, null));

        assertSeparated("u1", "u2");
    }

    @Test
    @DisplayName("PUBG 일반전 — 차단이 없으면 같은 파티 (대조)")
    void pubgUntieredNotBlocked() {
        enqueue(pubg("u1", PUBG_NORMAL, null));
        enqueue(pubg("u2", PUBG_NORMAL, null));

        assertTogether("u1", "u2");
    }

    @Test
    @DisplayName("PUBG 랭크(티어 모드) — 차단이면 다른 파티, 없으면 같은 파티")
    void pubgTiered() {
        blockBothWays("u1", "u2");
        enqueue(pubg("u1", PUBG_RANKED, TIER));
        enqueue(pubg("u2", PUBG_RANKED, TIER));
        assertSeparated("u1", "u2");

        // u1 의 파티는 아직 한 자리가 비어 있다 — 차단이 없는 u3 는 그리로 들어간다
        enqueue(pubg("u3", PUBG_RANKED, TIER));
        assertThat(partyIdOf("u3")).isIn(partyIdOf("u1"), partyIdOf("u2"));
        assertThat(membersOf(partyIdOf("u3"))).hasSize(2);
    }

    // ── VALORANT (join-party.lua · join-party-tiered.lua) ───────────────────

    @Test
    @DisplayName("VALORANT 일반전 — 차단이면 다른 파티, 없으면 같은 파티")
    void valorantUntiered() {
        blockBothWays("u1", "u2");
        enqueue(valorant("u1", VALORANT_UNRATED, "DUELIST", null));
        enqueue(valorant("u2", VALORANT_UNRATED, "INITIATOR", null));
        assertSeparated("u1", "u2");

        enqueue(valorant("u3", VALORANT_UNRATED, "SENTINEL", null));
        assertThat(partyIdOf("u3")).isIn(partyIdOf("u1"), partyIdOf("u2"));
        assertThat(membersOf(partyIdOf("u3"))).hasSize(2);
    }

    @Test
    @DisplayName("VALORANT 경쟁전(티어 모드 — 합류마다 범위를 좁히는 스크립트) — 차단이면 다른 파티")
    void valorantTieredBlocked() {
        blockBothWays("u1", "u2");

        enqueue(valorant("u1", VALORANT_COMPETITIVE, "DUELIST", TIER));
        enqueue(valorant("u2", VALORANT_COMPETITIVE, "INITIATOR", TIER));

        assertSeparated("u1", "u2");
        // 거절된 합류는 범위 좁히기 · 빈 역할군 목록도 건드리지 않았다
        assertThat(redis.opsForSet().members("qm:party:needs-roles:" + partyIdOf("u1"))).contains("INITIATOR");
    }

    @Test
    @DisplayName("VALORANT 경쟁전 — 차단이 없으면 같은 파티 (대조)")
    void valorantTieredNotBlocked() {
        enqueue(valorant("u1", VALORANT_COMPETITIVE, "DUELIST", TIER));
        enqueue(valorant("u2", VALORANT_COMPETITIVE, "INITIATOR", TIER));

        assertTogether("u1", "u2");
    }

    // ── 동시에 들어올 때 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("INV-6: 20명이 동시에 들어와도 차단 쌍은 한 파티에 없다 (칼바람 5인 · 열 쌍이 서로 차단)")
    void concurrentJoinsNeverPutABlockedPairTogether() throws InterruptedException {
        for (int i = 0; i < 20; i += 2) {
            blockBothWays("u" + i, "u" + (i + 1));
        }

        runConcurrently(20, i -> enqueue(lol("u" + i, LOL_ARAM, "NONE", null)));

        for (int i = 0; i < 20; i++) {
            assertThat(partyIdOf("u" + i)).as("u%d 가 파티에 들어가지 못했다", i).isNotBlank();
        }
        for (int i = 0; i < 20; i += 2) {
            assertThat(partyIdOf("u" + i)).as("차단 쌍 u%d · u%d", i, i + 1).isNotEqualTo(partyIdOf("u" + (i + 1)));
        }
        // 빈 단언이 아니다 — 실제로 여럿이 한 파티에 모였다
        Set<String> parties = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> partyIdOf("u" + i)).collect(Collectors.toSet());
        assertThat(parties.size()).isLessThan(20);
        parties.forEach(partyId -> assertThat(membersOf(partyId)).hasSizeLessThanOrEqualTo(5));
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    /** platform 이 차단 때 하는 것과 같다 — 양쪽 집합에 서로를 넣는다 */
    private void blockBothWays(String a, String b) {
        redis.opsForSet().add(blockRelKey(a), b);
        redis.opsForSet().add(blockRelKey(b), a);
    }

    /** {@code SharedKeys.blockRelKey} 와 같은 문자열 — 자바 상수가 바뀌면 여기서 드러나게 리터럴로 적는다 */
    private static String blockRelKey(String userId) {
        return "qm:user:block-rel:" + userId;
    }

    private CreateMatchRequestCommand lol(String userId, String mode, String position, String tier) {
        CreateMatchRequestCommand command = command(userId, GameKey.LOL, KeyConditionType.POSITION, mode, position);
        command.setTier(tier);
        return command;
    }

    private CreateMatchRequestCommand pubg(String userId, String mode, String tier) {
        CreateMatchRequestCommand command = command(userId, GameKey.PUBG, KeyConditionType.PLATFORM, mode, "STEAM");
        command.setTier(tier);
        return command;
    }

    private CreateMatchRequestCommand valorant(String userId, String mode, String role, String tier) {
        CreateMatchRequestCommand command = command(userId, GameKey.VALORANT, KeyConditionType.ROLE, mode, role);
        command.setTier(tier);
        return command;
    }

    /** 컨트롤러와 같은 두 단계(MatchingController#createMatchRequest) — 선점 뒤 배정 */
    private void enqueue(CreateMatchRequestCommand command) {
        matchRequestService.join(command).accepted().orElseThrow(
                () -> new IllegalStateException("활성 요청 선점에 실패했다: " + command.getUserId()));
        candidateRules.stream()
                .filter(rule -> rule.supports(command.getGame()))
                .findFirst()
                .orElseThrow()
                .canJoin(command);
    }

    /** 둘 다 파티가 있고 서로 다르며, 각자 혼자다 — 거절된 합류가 상대 파티에 아무것도 쓰지 않았다 */
    private void assertSeparated(String first, String second) {
        String firstParty = partyIdOf(first);
        String secondParty = partyIdOf(second);
        assertThat(firstParty).as("%s 의 파티가 없다", first).isNotBlank();
        assertThat(secondParty).as("%s 의 파티가 없다 — 거절된 뒤 새 파티를 만들어야 한다", second).isNotBlank();
        assertThat(secondParty).isNotEqualTo(firstParty);
        assertThat(membersOf(firstParty)).containsExactly(first);
        assertThat(membersOf(secondParty)).containsExactly(second);
        assertThat(redis.<String, String>opsForHash().get("qm:party:" + firstParty, "status")).isNull();
    }

    /** 둘이 한 파티로 정원(2)을 채웠다 */
    private void assertTogether(String first, String second) {
        String partyId = partyIdOf(first);
        assertThat(partyId).as("%s 의 파티가 없다", first).isNotBlank();
        assertThat(partyIdOf(second)).isEqualTo(partyId);
        assertThat(membersOf(partyId)).contains(first, second);
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get("qm:user:active-request:" + userId, "partyId");
    }

    /** 파티 HASH 의 {@code member:{userId}} 필드에서 userId 만 */
    private Set<String> membersOf(String partyId) {
        return redis.<String, String>opsForHash().keys("qm:party:" + partyId).stream()
                .filter(field -> field.startsWith("member:"))
                .map(field -> field.substring("member:".length()))
                .collect(Collectors.toSet());
    }
}
