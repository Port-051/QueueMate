package com.queuemate.matching.proposal;

import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.MatchRequestService;
import com.queuemate.matching.service.ProposalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * INV-6 의 <b>확정 직전 최종 검증</b> (docs/11 D-1) — 파티원 사이에 차단이 있으면 수락이 제안을 깬다.
 *
 * <p><b>왜 선필터 테스트로 모자란가.</b> 배정 경로의 선필터는 "내가 들어갈 때" 상대와의 차단만 본다. 그래서
 * 여기서는 차단 행을 <b>파티가 찬 뒤에</b> 넣는다 — 선필터가 볼 수 없었던 차단이고, 정원 2 인 {@code RANKED_SOLO}
 * 는 첫 사람이 빈 파티를 만들 때 거를 상대도 없었다. 이 차단을 잡는 자리는 수락 경로의
 * {@code PartyBlockCheck} 하나다.
 *
 * <p><b>사용자 id 가 숫자다.</b> 다른 테스트의 {@code "u1"} 같은 id 는 차단 테이블에 있을 수 없어 검증이 WARN 하고
 * 건너뛴다({@code BlockedUsers} 와 같은 정책). 실제로 DB 를 묻게 하려면 {@code "101"} 처럼 사용자 번호여야 한다.
 *
 * <p><b>차단 행은 JDBC 로 넣는다.</b> {@code Block} 은 {@code @Immutable} 이고 저장소도 읽기 전용이라
 * ({@code Repository} 상속 — save 가 없다) JPA 로는 넣을 수 없다. 테이블은 main 의 {@code schema.sql} 이 만들고
 * {@code ConcurrencyTestSupport} 의 {@code create-drop} 이 엔티티대로 다시 만든다. 컨텍스트가 테스트 클래스 사이에
 * 재사용되므로 매 테스트 전에 비운다.
 *
 * <p>{@link ConcurrencyTestSupport} 를 상속하는 이유는 {@code ProposalIdempotencyTest} 와 같다 — 컨텍스트 /
 * Redis DB 15 / flush / LoL gameconfig 시드 / {@code command()}. 전부 단일 스레드다.
 */
class ProposalBlockCheckTest extends ConcurrencyTestSupport {

    /** 정원 2. 선필터가 첫 사람을 거를 상대가 없는 모드다 */
    private static final String SOLO = "RANKED_SOLO";
    /** 정원 5. 차단 쌍만 빠지고 나머지 셋은 남는 것을 본다 */
    private static final String FLEX = "RANKED_FLEX_5";

    private static final String NEEDS_PREFIX = "qm:party:open:LOL:";
    private static final String NEEDS_SUFFIX = ":REQUIRED:RANK_UP:needs:";

    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private ProposalService proposalService;
    @Autowired
    private List<CandidateRule> candidateRules;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearBlocks() {
        jdbc.update("delete from blocks");
    }

    // ── 정원 2: 둘 다 빠지고 파티가 사라진다 ─────────────────────────────────

    @Test
    @DisplayName("파티가 찬 뒤 생긴 차단 — 수락은 NOT_FOUND 이고 둘 다 큐에서 빠져 파티가 사라진다")
    void blockInsertedAfterFillBreaksTheProposalAndEmptiesTheParty() {
        String partyId = soloParty("101", "102");
        block(1, 101, 102);

        assertThat(proposalService.accept(partyId, "101")).isEqualTo(ProposalResult.NOT_FOUND);

        // 제안 흔적이 전부 없다
        assertThat(field(partyId, "status")).isNull();
        assertThat(acceptCount(partyId)).isZero();
        assertThat(pendingContains(partyId)).isFalse();

        // 둘 다 큐에서 빠졌다. 마지막 한 명이 나가며 파티 HASH 도 지워진다 (leave-party.lua 5번)
        assertThat(activeRequestExists("101")).isFalse();
        assertThat(activeRequestExists("102")).isFalse();
        assertThat(redis.hasKey(partyKey(partyId))).isFalse();
        // 빈 파티가 색인에 남아 자리를 먹지 않는다
        assertThat(needsContains(SOLO, "TOP", partyId)).isFalse();
        assertThat(needsContains(SOLO, "JUNGLE", partyId)).isFalse();

        // 깨진 뒤의 재시도도 같은 답이다 — 제안이 없다
        assertThat(proposalService.accept(partyId, "102")).isEqualTo(ProposalResult.NOT_FOUND);
    }

    @Test
    @DisplayName("차단 쌍에 없는 사람이 수락해도 제안은 깨진다 — 그 파티는 어차피 성립할 수 없다")
    void acceptByEitherSideBreaksIt() {
        String partyId = soloParty("101", "102");
        block(1, 101, 102);

        // 차단당한 쪽이 눌러도 같다. 누가 눌렀는지는 판정과 무관하다
        assertThat(proposalService.accept(partyId, "102")).isEqualTo(ProposalResult.NOT_FOUND);
        assertThat(redis.hasKey(partyKey(partyId))).isFalse();
    }

    // ── 정원 5: 차단 쌍만 빠진다 ─────────────────────────────────────────────

    @Test
    @DisplayName("5인 파티에서 차단 쌍 둘만 빠지고 나머지 셋은 파티에 남아 다시 기다린다")
    void onlyTheBlockedPairLeavesAFivePartyAndTheirSlotsReopen() {
        Map<String, String> positions = Map.of(
                "201", "TOP", "202", "JUNGLE", "203", "MID", "204", "ADC", "205", "SUPPORT");
        List<String> order = List.of("201", "202", "203", "204", "205");
        order.forEach(userId -> enqueue(command(userId, FLEX, positions.get(userId))));

        String partyId = partyIdOf("205");
        assertThat(partyId).as("정원이 찬 파티가 만들어지지 않았다").isNotBlank();
        order.forEach(userId -> assertThat(partyIdOf(userId)).isEqualTo(partyId));
        assertThat(field(partyId, "status")).isEqualTo("PENDING");

        // 먼저 한 명이 수락해 둔 상태에서 차단이 발견돼도 그 수락은 남지 않아야 한다
        assertThat(proposalService.accept(partyId, "201")).isEqualTo(ProposalResult.ACCEPTED);

        block(1, 203, 205);

        assertThat(proposalService.accept(partyId, "202")).isEqualTo(ProposalResult.NOT_FOUND);

        // 차단 쌍만 빠졌다
        assertThat(memberIds(partyId)).containsExactlyInAnyOrder("201", "202", "204");
        assertThat(activeRequestExists("203")).isFalse();
        assertThat(activeRequestExists("205")).isFalse();
        for (String remaining : List.of("201", "202", "204")) {
            assertThat(activeRequestExists(remaining)).as("%s 의 활성 요청", remaining).isTrue();
            assertThat(partyIdOf(remaining)).isEqualTo(partyId);
        }

        // 제안 흔적은 없고, 201 의 옛 수락도 지워졌다 — 남겨 두면 새 멤버가 채워진 뒤 SCARD 가 부풀린다 (INV-4)
        assertThat(field(partyId, "status")).isNull();
        assertThat(field(partyId, "expiresAt")).isNull();
        assertThat(acceptCount(partyId)).isZero();
        assertThat(pendingContains(partyId)).isFalse();

        // 비운 두 자리만 색인에 되돌아왔다. 아직 찬 자리는 아니다 (중복 금지 모드 — leave-party.lua 6번)
        assertThat(needsContains(FLEX, "MID", partyId)).isTrue();
        assertThat(needsContains(FLEX, "SUPPORT", partyId)).isTrue();
        assertThat(needsContains(FLEX, "TOP", partyId)).isFalse();
    }

    // ── 방향 · 무해성 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("차단 방향은 무관하다 — B 가 A 를 차단했어도 A 의 수락이 제안을 깬다")
    void directionDoesNotMatter() {
        String partyId = soloParty("101", "102");
        // 102 → 101. 수락하는 101 은 차단당한 쪽이다
        block(1, 102, 101);

        assertThat(proposalService.accept(partyId, "101")).isEqualTo(ProposalResult.NOT_FOUND);
        assertThat(activeRequestExists("101")).isFalse();
        assertThat(activeRequestExists("102")).isFalse();
    }

    @Test
    @DisplayName("차단이 없으면 검증은 아무것도 바꾸지 않는다 — ACCEPTED 다음 CONFIRMED")
    void noBlockLeavesAcceptUntouched() {
        String partyId = soloParty("101", "102");
        // 이 파티와 무관한 차단은 잡히지 않아야 한다 (양쪽 모두 파티원이어야 쌍이다)
        block(1, 101, 999);
        block(2, 888, 102);

        assertThat(proposalService.accept(partyId, "101")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(proposalService.accept(partyId, "102")).isEqualTo(ProposalResult.CONFIRMED);
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
        assertThat(acceptCount(partyId)).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 확정된 제안은 그 뒤에 생긴 차단으로 깨지지 않는다 (INV-5) — 재전송은 ALREADY_RESPONDED")
    void aConfirmedProposalIsNotBrokenByALaterBlock() {
        String partyId = soloParty("101", "102");
        proposalService.accept(partyId, "101");
        assertThat(proposalService.accept(partyId, "102")).isEqualTo(ProposalResult.CONFIRMED);

        block(1, 101, 102);

        // PENDING 이 아니면 검증을 건너뛴다. 확정을 되돌리는 것은 이 앱의 일이 아니다
        assertThat(proposalService.accept(partyId, "101")).isEqualTo(ProposalResult.ALREADY_RESPONDED);
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
        assertThat(memberIds(partyId)).containsExactlyInAnyOrder("101", "102");
    }

    // ── 준비 / 조회 ─────────────────────────────────────────────────────────

    /** a(TOP) + b(JUNGLE) 로 RANKED_SOLO 정원 2 를 채운다. 제안이 열린 partyId 를 돌려준다 */
    private String soloParty(String a, String b) {
        enqueue(command(a, SOLO, "TOP"));
        enqueue(command(b, SOLO, "JUNGLE"));

        String partyId = partyIdOf(b);
        assertThat(partyId).as("정원이 찬 파티가 만들어지지 않았다").isNotBlank();
        assertThat(partyId).isEqualTo(partyIdOf(a));
        assertThat(field(partyId, "status")).isEqualTo("PENDING");
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

    /** 차단 한 건. 채번은 저쪽(app:platform) 몫이라 여기서 id 를 직접 준다 */
    private void block(long id, long blockerId, long blockedId) {
        jdbc.update("insert into blocks(id, blocker_id, blocked_id) values (?, ?, ?)", id, blockerId, blockedId);
    }

    private String partyKey(String partyId) {
        return "qm:party:" + partyId;
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get("qm:user:active-request:" + userId, "partyId");
    }

    private boolean activeRequestExists(String userId) {
        return Boolean.TRUE.equals(redis.hasKey("qm:user:active-request:" + userId));
    }

    private String field(String partyId, String field) {
        return redis.<String, String>opsForHash().get(partyKey(partyId), field);
    }

    private Set<String> memberIds(String partyId) {
        return redis.<String, String>opsForHash().keys(partyKey(partyId)).stream()
                .filter(f -> f.startsWith("member:"))
                .map(f -> f.substring("member:".length()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private long acceptCount(String partyId) {
        Long size = redis.opsForSet().size("qm:proposal:accepts:" + partyId);
        return size == null ? 0 : size;
    }

    private boolean pendingContains(String partyId) {
        return redis.opsForZSet().score("qm:proposal:pending", partyId) != null;
    }

    /** 티어를 보지 않는 모드라 needs 키에 티어 접미사가 없다 */
    private boolean needsContains(String mode, String position, String partyId) {
        return redis.opsForZSet().score(NEEDS_PREFIX + mode + NEEDS_SUFFIX + position, partyId) != null;
    }
}
