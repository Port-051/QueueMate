package com.queuemate.matching.proposal;

import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.redisKeys.SharedKeys;
import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.MatchRequestService;
import com.queuemate.matching.service.ProposalExpiryService;
import com.queuemate.matching.service.ProposalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제안 만료({@link ProposalExpiryService#expire})가 <b>시한을 스크립트 안에서 다시 보는지</b> 본다 (INV-5 expired).
 *
 * <p>스위퍼는 Redis 를 두 번 부른다 — {@code qm:proposal:pending} 에서 시한 지난 partyId 를 꺼낼 때와
 * {@code expiry-proposal.lua} 를 돌릴 때. 그 사이에 같은 partyId 에 <b>새 제안</b>이 열릴 수 있다
 * (옛 제안이 깨져도 남은 사람은 파티에 그대로이고, 빈자리가 다시 차면 {@code join-party*.lua} 가
 * {@code status=PENDING} 과 새 {@code expiresAt} 을 적는다). 스크립트가 {@code status} 만 보면 그 새 제안을
 * 옛 제안으로 알고 깬다 — 아직 아무도 수락하지 않았으니 전원이 무응답자로 나와 통째로 큐에서 빠진다.
 * 태스크가 둘이면 같은 목록을 둘 다 들고 있어 이 창이 넓어진다(2026-10-02 배포 점검).
 *
 * <p>{@link ProposalExpiryService#expire} 를 <b>직접 부른다</b> — 스위퍼가 목록에서 꺼낸 뒤의 자리를 흉내 내는 것이다.
 * 시한은 자지 않고 파티 HASH 의 {@code expiresAt} 과 목록의 점수를 직접 과거 · 미래로 옮긴다.
 */
class ProposalExpiryTest extends ConcurrencyTestSupport {

    /** {@code ConcurrencyTestSupport} 가 시드하는 값. 2명이면 정원이 찬다 */
    private static final String MODE = "RANKED_SOLO";

    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private ProposalService proposalService;
    @Autowired
    private ProposalExpiryService proposalExpiryService;
    @Autowired
    private List<CandidateRule> candidateRules;

    @Test
    @DisplayName("시한이 지난 제안은 안 누른 사람만 큐에서 빠지고 수락한 사람은 파티에 남는다")
    void expiredProposalDropsOnlyTheSilentMembers() {
        String partyId = fullParty();
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.ACCEPTED);
        moveDeadline(partyId, System.currentTimeMillis() - 1_000);

        proposalExpiryService.expire(partyId);

        // u1 은 안 눌렀으니 빠졌다
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u1"))).isFalse();
        // u2 는 눌렀으니 파티에 남아 다시 기다린다
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u2"))).isTrue();
        assertThat(field(partyId, "member:u2")).isEqualTo("JUNGLE");
        assertThat(field(partyId, "member:u1")).isNull();
        // 제안 흔적은 지워졌다 — 다시 차면 새 제안이 열려야 한다
        assertThat(field(partyId, "status")).isNull();
        assertThat(field(partyId, "expiresAt")).isNull();
        assertThat(redis.hasKey(SharedKeys.acceptsKey(partyId))).isFalse();
        assertThat(pendingScore(partyId)).isNull();
    }

    @Test
    @DisplayName("같은 partyId 에 새 제안이 열려 있으면(시한 전) 옛 목록으로 들어온 만료는 아무것도 하지 않는다")
    void aFreshProposalOnTheSamePartyIsLeftAlone() {
        // 1) 옛 제안이 시한을 넘겨 깨진다 — 스위퍼 A 가 처리한 자리. u2 는 눌러 두어 파티에 남는다
        //    (아무도 안 눌렀으면 둘 다 빠지고 파티째 사라져 "같은 partyId" 가 되지 않는다)
        String partyId = fullParty();
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.ACCEPTED);
        moveDeadline(partyId, System.currentTimeMillis() - 1_000);
        proposalExpiryService.expire(partyId);
        assertThat(field(partyId, "status")).isNull();
        assertThat(field(partyId, "member:u2")).isEqualTo("JUNGLE");

        // 2) 빈자리가 다시 차서 같은 파티에 새 제안이 열린다 — join-party*.lua 가 status · expiresAt · 목록 점수를 적는다
        enqueue(command("u1", MODE, "TOP"));
        assertThat(partyIdOf("u1")).as("빈자리에 다시 들어가 같은 파티가 차야 한다").isEqualTo(partyId);
        assertThat(field(partyId, "status")).isEqualTo("PENDING");
        String freshExpiresAt = field(partyId, "expiresAt");
        assertThat(Long.parseLong(freshExpiresAt)).isGreaterThan(System.currentTimeMillis());
        assertThat(pendingScore(partyId)).isEqualTo((double) Long.parseLong(freshExpiresAt));

        // 3) 스위퍼 B 가 아까 꺼낸 옛 목록으로 같은 partyId 를 만료시키러 온다
        proposalExpiryService.expire(partyId);

        // 새 제안은 그대로다 — status 만 봤다면 u1 · u2 전원이 무응답자로 큐에서 빠졌을 자리다
        assertThat(field(partyId, "status")).isEqualTo("PENDING");
        assertThat(field(partyId, "expiresAt")).isEqualTo(freshExpiresAt);
        // 시한을 보고 돌아간 것이지 목록에서 빠진 것이 아니다 — 빼면 새 제안은 영영 만료되지 않는다
        assertThat(pendingScore(partyId)).isEqualTo((double) Long.parseLong(freshExpiresAt));
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u1"))).isTrue();
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u2"))).isTrue();
        assertThat(field(partyId, "member:u1")).isEqualTo("TOP");
        assertThat(field(partyId, "member:u2")).isEqualTo("JUNGLE");
    }

    @Test
    @DisplayName("시한 전에 들어온 만료는 제안을 건드리지 않는다 — 목록도 그대로")
    void expiryBeforeTheDeadlineIsANoOp() {
        String partyId = fullParty();
        String expiresAt = field(partyId, "expiresAt");
        Double score = pendingScore(partyId);

        proposalExpiryService.expire(partyId);

        assertThat(field(partyId, "status")).isEqualTo("PENDING");
        assertThat(field(partyId, "expiresAt")).isEqualTo(expiresAt);
        assertThat(pendingScore(partyId)).isEqualTo(score);
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u1"))).isTrue();
        assertThat(redis.hasKey(SharedKeys.activeRequestKey("u2"))).isTrue();
    }

    // ── 자리 ────────────────────────────────────────────────────────────────

    private String fullParty() {
        enqueue(command("u1", MODE, "TOP"));
        enqueue(command("u2", MODE, "JUNGLE"));

        String partyId = partyIdOf("u2");
        assertThat(partyId).as("정원이 찬 파티가 만들어지지 않았다").isNotBlank();
        assertThat(partyId).isEqualTo(partyIdOf("u1"));
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

    /**
     * 시한을 옮긴다 — 파티 HASH 의 {@code expiresAt} 과 목록의 점수를 같이. 스위퍼가 목록에서 꺼내는 조건이
     * 점수이고 스크립트가 보는 것이 HASH 라서 둘을 같이 옮겨야 "시한이 지난 제안" 이 된다.
     */
    private void moveDeadline(String partyId, long expiresAt) {
        redis.opsForHash().put(SharedKeys.partyKey(partyId), "expiresAt", String.valueOf(expiresAt));
        redis.opsForZSet().add(SharedKeys.PENDING_KEY, partyId, expiresAt);
    }

    private Double pendingScore(String partyId) {
        return redis.opsForZSet().score(SharedKeys.PENDING_KEY, partyId);
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get(SharedKeys.activeRequestKey(userId), "partyId");
    }

    private String field(String partyId, String field) {
        return redis.<String, String>opsForHash().get(SharedKeys.partyKey(partyId), field);
    }
}
