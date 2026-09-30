package com.queuemate.matching.proposal;

import com.queuemate.matching.concurrency.ConcurrencyTestSupport;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.domain.condition.lol.LolPosition;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.rule.CandidateRule;
import com.queuemate.matching.service.MatchRequestService;
import com.queuemate.matching.service.ProposalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 제안 수락 / 거절의 <b>멱등성</b>을 본다.
 *
 * <p><b>왜 멱등성인가.</b> 수락은 거절될 수 있는 명령이고, 그 응답은 유실될 수 있다.
 * 응답을 못 읽은 클라이언트는 재시도하는 것이 정상이고, Redis 페일오버 뒤에는
 * {@code matching/failover/} 가 같은 호출을 처음부터 다시 부른다. Lua 는 롤백이 없으므로
 * <b>같은 요청이 두 번 실행돼도 답과 상태가 같아야</b> 한다.
 *
 * <p>가장 위험한 경우는 <b>마지막 수락자</b>다. 그가 확정을 유발해 놓고 응답을 잃는 경우인데,
 * 재시도는 {@link ProposalResult#ALREADY_RESPONDED} 를 받는다 — 확정을 만든 한 번만
 * {@link ProposalResult#CONFIRMED} 여야 그 분기에서 나가는 {@code MATCH_CONFIRMED} 알림이
 * 파티 전원에게 두 번 가지 않기 때문이다. 재전송이 실패로 보이지 않게 하는 것은 컨트롤러다
 * (수락 경로에서 이 값을 204 로 내보낸다). 확정 자체가 뒤집히지 않는 근거는 따로다 —
 * {@code accept-proposal.lua} 가 {@code SADD} 결과로 끊지 않고 매번 다시 센다.
 *
 * <p><b>거절은 예외다 — 멱등이 아니다.</b> 거절은 파티 HASH 의 제안 흔적
 * ({@code status} / {@code expiresAt})과 수락자 집합을 <b>지운다.</b> 그래야 거절자가
 * 빠진 자리가 다시 찼을 때 {@code join-party*.lua} 의 {@code HSETNX status 'PENDING'} 이
 * 다시 성공해 새 제안이 열린다 ({@code DECLINED} 를 남겨 두면 {@code HSETNX} 가 0 을
 * 돌려주어 영영 깨진 좀비 파티가 된다). 그 대가로 같은 거절의 재시도는 볼 {@code status}
 * 가 없어 {@link ProposalResult#NOT_FOUND} 를 받는다. 두 답 모두 거절한 사람이 갈 화면은
 * 같으므로(대기 화면 복귀) 그대로 두기로 했다 — {@code decline-proposal.lua} 참고.
 *
 * <p>{@code ProposalService#decline} 은 스크립트 뒤에 {@code MatchCancelService#cancel} 로
 * <b>거절한 본인을 큐에서도 뺀다.</b> 그래서 거절 뒤에는 그 사람의 활성 요청과
 * {@code member:} 필드가 사라진다 — 아래 테스트들이 인원을 셀 때 이것을 전제한다.
 *
 * <p><b>확정 뒷정리(D-42)도 여기서 본다.</b> 확정 뒤 파티는 app:platform 이 Redis 에서 파티 HASH 를
 * 직접 읽어 만들고, 이 앱은 그 HASH 를 혼자 읽어도 되게(조건 넷 · {@code confirmedAt}) 채운 뒤
 * 파티 HASH · 활성 요청 · 수락자 집합에 TTL 을 건다 — 예전처럼 "활성 요청이 TTL 없이 영원히 남는다"
 * 가 <b>아니다</b>. {@code cleanup-confirmed.lua} 머리말 참고.
 *
 * <p>{@link ConcurrencyTestSupport} 를 상속하는 이유는 동시성 때문이 아니라 Spring 컨텍스트 /
 * Redis DB 15 / 테스트마다 flush / LoL gameconfig 시드 / {@code command()} 헬퍼 때문이다
 * ({@code PushNotificationTest} 와 같은 이유다). 전부 단일 스레드 순차 실행이다.
 */
class ProposalIdempotencyTest extends ConcurrencyTestSupport {

    /** {@code ConcurrencyTestSupport} 가 시드하는 값. 2명이면 정원이 찬다 */
    private static final String MODE = "RANKED_SOLO";
    /** {@code application.yaml} 의 {@code queuemate.proposal.ttl-seconds} 기본값 */
    private static final long TTL_MILLIS = 20_000;
    /** {@code queuemate.proposal.confirmed-party-ttl-seconds} 기본값. 파티 HASH 가 남는 상한(초) */
    private static final long PARTY_TTL_SECONDS = 600;
    /** {@code queuemate.proposal.confirmed-retention-seconds} 기본값. 활성 요청 · 수락자 집합이 남는 상한(초) */
    private static final long RETENTION_SECONDS = 60;

    @Autowired
    private MatchRequestService matchRequestService;
    @Autowired
    private ProposalService proposalService;
    @Autowired
    private List<CandidateRule> candidateRules;

    /** 페일오버 재실행을 흉내 내려고 직접 부른다. 이름이 곧 빈 선택이다 */
    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> lolJoinPartyUntieredScript;
    /** 확정 뒷정리도 같은 이유로 직접 부른다. ARGV 배치는 {@code cleanup-confirmed.lua} 머리말과 같아야 한다 */
    @SuppressWarnings("rawtypes")
    @Autowired
    private RedisScript<List> cleanupConfirmedScript;

    // ── 제안이 열리는 순간 ───────────────────────────────────────────────────

    @Test
    @DisplayName("정원이 차면 파티 HASH 에 status=PENDING 과 expiresAt 이 생긴다")
    void fillingThePartyOpensAPendingProposal() {
        long before = System.currentTimeMillis();
        String partyId = fullParty();
        long after = System.currentTimeMillis();

        assertThat(field(partyId, "status")).isEqualTo("PENDING");

        // 시한은 자바가 now + ttl 로 계산해 넘긴 값이다. 배정을 시작한 시각 기준이므로
        // 두 측정 사이에 들어와야 한다
        long expiresAt = Long.parseLong(field(partyId, "expiresAt"));
        assertThat(expiresAt).isBetween(before + TTL_MILLIS, after + TTL_MILLIS);
    }

    @Test
    @DisplayName("정원이 안 찬 파티는 아직 제안이 아니다 — 수락하면 NOT_FOUND")
    void anUnfilledPartyIsNotAProposalYet() {
        enqueue(command("u1", MODE, "TOP"));
        String partyId = partyIdOf("u1");

        assertThat(field(partyId, "status")).isNull();
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.NOT_FOUND);
    }

    @Test
    @DisplayName("없는 제안은 NOT_FOUND, 남의 제안은 NOT_A_MEMBER")
    void unknownProposalAndNonMemberAreRejected() {
        String partyId = fullParty();

        assertThat(proposalService.accept(UUID.randomUUID().toString(), "u1"))
                .isEqualTo(ProposalResult.NOT_FOUND);

        assertThat(proposalService.accept(partyId, "outsider"))
                .isEqualTo(ProposalResult.NOT_A_MEMBER);
        assertThat(decline(partyId, "outsider"))
                .isEqualTo(ProposalResult.NOT_A_MEMBER);

        // 거절은 결과와 무관하게 뒤이어 취소를 부르므로(ProposalService#decline) u1 이
        // 큐에서 빠진다. 다른 단언에 영향이 없도록 맨 뒤에 둔다
        assertThat(decline(UUID.randomUUID().toString(), "u1"))
                .isEqualTo(ProposalResult.NOT_FOUND);
    }

    // ── 멱등성 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("같은 수락을 두 번 보내면 두 번 다 ACCEPTED 이고 수락자는 한 명으로 남는다")
    void repeatedAcceptGivesTheSameAnswer() {
        String partyId = fullParty();

        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ACCEPTED);

        // SET 이라 몇 번을 SADD 해도 한 명이다. 카운터였다면 3이 되어 2인 파티가
        // 혼자 수락으로 확정됐을 것이다 (INV-4)
        assertThat(acceptCount(partyId)).isEqualTo(1);
        assertThat(field(partyId, "status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("확정을 만든 호출만 CONFIRMED 다. 재시도는 ALREADY_RESPONDED")
    void onlyTheConfirmingCallSeesConfirmed() {
        String partyId = fullParty();

        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ACCEPTED);

        // u2 의 수락이 정원을 채운다. 확정을 만든 이 한 번만 CONFIRMED 다 —
        // 그 분기에서만 MATCH_CONFIRMED 알림이 나가므로, 재시도가 또 CONFIRMED 를 받으면
        // 같은 알림이 파티 전원에게 두 번 간다
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.ALREADY_RESPONDED);

        // 먼저 수락한 사람이 다시 물어도 같다. 확정이 뒤집히지 않는 것이 요점이다 (INV-5)
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ALREADY_RESPONDED);

        // 재시도가 실패로 보이지 않게 컨트롤러는 이 값을 204 로 내보낸다 (ProposalController)
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
        assertThat(acceptCount(partyId)).isEqualTo(2);
    }

    @Test
    @DisplayName("수락자 집합만 남고 확정이 안 된 채 죽어도 다음 호출이 다시 세어 확정한다")
    void aHalfDoneConfirmationHealsItself() {
        String partyId = fullParty();

        proposalService.accept(partyId, "u1");
        proposalService.accept(partyId, "u2");
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");

        // SADD 는 됐는데 HSET status 전에 죽은 상태를 만든다
        redis.opsForHash().put(partyKey(partyId), "status", "PENDING");

        // 카운터였다면 "이미 센 수락" 을 다시 셀 수 없어 영영 PENDING 이다.
        // 집합은 지금 들어 있는 것이 곧 사실이라 다시 세면 복구된다
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.CONFIRMED);
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("거절은 멱등이 아니다 — 첫 거절은 DECLINED, 재시도는 NOT_FOUND")
    void repeatedDeclineIsNotIdempotent() {
        String partyId = fullParty();

        assertThat(proposalService.decline(partyId, "u1"))
                .isEqualTo(ProposalResult.DECLINED);

        // 거절은 status 를 'DECLINED' 로 바꾸는 것이 아니라 제안 흔적을 지운다.
        // 남겨 두면 자리가 다시 찼을 때 HSETNX 가 막혀 좀비 파티가 된다
        assertThat(field(partyId, "status")).isNull();
        assertThat(field(partyId, "expiresAt")).isNull();
        assertThat(acceptCount(partyId)).isZero();

        // 그 대가로 재시도는 볼 status 가 없어 답이 달라진다. 그래도 거절한 사람이 갈
        // 화면은 두 경우 같다 — decline-proposal.lua 의 "멱등성" 참고
        assertThat(proposalService.decline(partyId, "u1"))
                .isEqualTo(ProposalResult.NOT_FOUND);

        // 파티와 남은 참가자는 살아 있다. 자리가 다시 차면 새 제안이 열린다
        assertThat(memberValues(partyKey(partyId))).hasSize(1);
    }

    // ── INV-5: 끝난 제안은 뒤집히지 않는다 ──────────────────────────────────

    @Test
    @DisplayName("한 명이 거절하면 제안이 사라진다 — 그 뒤의 수락은 NOT_FOUND")
    void acceptAfterDeclineIsRejected() {
        String partyId = fullParty();

        assertThat(decline(partyId, "u1")).isEqualTo(ProposalResult.DECLINED);

        // status 를 남기지 않고 지우므로 DECLINED 가 아니라 NOT_FOUND 다.
        // accept-proposal.lua 의 DECLINED 분기는 그래서 지금 도달하지 않는다
        // (지우지 않고 방어로 남겨 둔 것이다 — 그 파일 3번 주석 참고)
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.NOT_FOUND);

        // 깨진 제안에는 수락이 쌓이지 않는다
        assertThat(acceptCount(partyId)).isZero();
        assertThat(field(partyId, "status")).isNull();
    }

    @Test
    @DisplayName("확정된 제안은 거절로 되돌릴 수 없다 (INV-5)")
    void declineAfterConfirmIsRejected() {
        String partyId = fullParty();

        proposalService.accept(partyId, "u1");
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);

        assertThat(decline(partyId, "u1")).isEqualTo(ProposalResult.CONFIRMED);
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
        // 확정 판정 뒤라 수락자 집합도 그대로다. 흔적을 지우는 것은 5번까지 간 거절뿐이다
        assertThat(acceptCount(partyId)).isEqualTo(2);
    }

    @Test
    @DisplayName("수락해 놓고 거절을 누르면 ALREADY_RESPONDED — 재시도가 아니라 진짜 충돌이다")
    void decliningAfterOwnAcceptIsAConflict() {
        String partyId = fullParty();

        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ACCEPTED);
        assertThat(decline(partyId, "u1")).isEqualTo(ProposalResult.ALREADY_RESPONDED);

        // 스크립트는 아무것도 바꾸지 않는다. 제안 흔적도 수락 기록도 그대로다 —
        // ALREADY_RESPONDED 는 거절 재시도가 아니라 진짜 충돌이라 지우기까지 가지 않는다
        assertThat(field(partyId, "status")).isEqualTo("PENDING");
        assertThat(acceptCount(partyId)).isEqualTo(1);
    }

    // ── 배정 스크립트 재실행 ────────────────────────────────────────────────

    @Test
    @DisplayName("페일오버로 join 이 다시 실행돼도 확정된 제안이 PENDING 으로 되돌아가지 않는다")
    void replayingJoinDoesNotReopenAConfirmedProposal() {
        String partyId = fullParty();
        String expiresAt = field(partyId, "expiresAt");

        proposalService.accept(partyId, "u1");
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);

        // 마지막 합류를 통째로 다시 실행한다. 시한은 일부러 아주 뒤로 준다 —
        // HSET 이었다면 여기서 연장되고 status 도 PENDING 으로 되돌아간다
        replayLastJoin(partyId, "u2", "JUNGLE", Long.parseLong(expiresAt) + 600_000);

        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
        assertThat(field(partyId, "expiresAt")).isEqualTo(expiresAt);
        // 멤버도 늘지 않는다 — member:{userId} 는 필드 하나다 (INV-7)
        assertThat(memberValues(partyKey(partyId))).hasSize(2);
    }

    // ── 확정 뒷정리 — platform 이 읽을 파티 HASH 와 TTL (docs/11 D-42) ─────────

    @Test
    @DisplayName("확정되면 파티 HASH 에 조건 넷과 confirmedAt 이 적히고 파티 TTL 이 걸린다")
    void confirmationMakesThePartyHashSelfContainedAndBounded() {
        String partyId = fullParty();

        // 확정 전에는 조건이 활성 요청에만 있다 — 색인 키 이름에 조건이 들어가서(INV-8) 파티 안에는
        // 적을 필요가 없었다. 파티 HASH 에는 TTL 도 없다
        assertThat(field(partyId, "game")).isNull();
        assertThat(field(partyId, "confirmedAt")).isNull();
        assertThat(redis.getExpire(partyKey(partyId))).isEqualTo(-1);

        proposalService.accept(partyId, "u1");
        long before = System.currentTimeMillis();
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);
        long after = System.currentTimeMillis();

        // platform 은 이 키 하나만 읽고 파티를 만든다. 필드 이름이 앱 사이의 계약이다
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
        assertThat(field(partyId, "game")).isEqualTo("LOL");
        assertThat(field(partyId, "modeKey")).isEqualTo(MODE);
        assertThat(field(partyId, "voicePreference")).isEqualTo("REQUIRED");
        assertThat(field(partyId, "playPurpose")).isEqualTo("RANK_UP");
        assertThat(field(partyId, "target")).isEqualTo("2");
        assertThat(memberValues(partyKey(partyId))).containsExactlyInAnyOrder("TOP", "JUNGLE");

        // 확정 시각은 자바가 넘긴 now 다. 확정을 만든 호출 사이에 들어와야 한다
        assertThat(Long.parseLong(field(partyId, "confirmedAt"))).isBetween(before, after);

        // 파티는 영원히 남지 않는다. platform 이 이 안에 읽어 가야 한다
        assertThat(redis.getExpire(partyKey(partyId)))
                .as("확정된 파티 HASH 에는 TTL 이 있어야 한다")
                .isGreaterThan(0).isLessThanOrEqualTo(PARTY_TTL_SECONDS);
    }

    @Test
    @DisplayName("확정되면 파티원의 활성 요청은 status=PARTY 로 남되 TTL 이 걸린다 — 수락자 집합도")
    void confirmationBoundsTheActiveRequestsInsteadOfKeepingThemForever() {
        String partyId = fullParty();

        // 확정 전의 활성 요청은 배정 때 PERSIST 됐다 — claim 의 60초 만료가 떼어져 TTL 이 없다
        assertThat(redis.getExpire(activeRequestKey("u1"))).isEqualTo(-1);

        proposalService.accept(partyId, "u1");
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);

        for (String userId : List.of("u1", "u2")) {
            // 지우지 않는다 — 지우면 그 순간 새 매칭을 걸 수 있어 한 사람이 두 파티에 속한다(INV-2).
            // 그 동안 조회는 MATCHED 다
            assertThat(redis.<String, String>opsForHash().get(activeRequestKey(userId), "status"))
                    .isEqualTo("PARTY");
            assertThat(partyIdOf(userId)).isEqualTo(partyId);
            // 하지만 영원히 남기지도 않는다. 이 TTL 이 지나면 그 사용자는 다시 큐에 들어올 수 있고,
            // "한 번에 하나" 는 platform 의 입장 표시 키(qm:user:active-room:)가 잇는다 (D-19 · D-42)
            assertThat(redis.getExpire(activeRequestKey(userId)))
                    .as("확정된 사용자의 활성 요청에는 TTL 이 있어야 한다: " + userId)
                    .isGreaterThan(0).isLessThanOrEqualTo(RETENTION_SECONDS);
        }

        // 수락자 집합은 확정 판정에 다 쓰였다. 재전송된 수락이 도착하는 창만큼만 남긴다
        assertThat(redis.getExpire("qm:proposal:accepts:" + partyId))
                .isGreaterThan(0).isLessThanOrEqualTo(RETENTION_SECONDS);
    }

    @Test
    @DisplayName("확정 뒤에 온 수락 재전송은 confirmedAt 을 옮기지 않고 파티 TTL 을 늘리지도 않는다")
    void retriedAcceptAfterConfirmationDoesNotRerunTheCleanup() throws InterruptedException {
        String partyId = fullParty();

        proposalService.accept(partyId, "u1");
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);
        String confirmedAt = field(partyId, "confirmedAt");
        Long partyTtl = redis.getExpire(partyKey(partyId));

        // TTL 이 한 칸이라도 줄어들 수 있게 잠깐 기다린다 — 다시 걸렸다면 상한으로 되돌아간다
        Thread.sleep(1_100);

        // 뒷정리는 확정을 만든 그 한 번(CONFIRMED)에서만 돈다. 재전송은 ALREADY_RESPONDED 라
        // 뒷정리도 알림도 다시 나가지 않는다
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.ALREADY_RESPONDED);
        assertThat(proposalService.accept(partyId, "u1")).isEqualTo(ProposalResult.ALREADY_RESPONDED);

        assertThat(field(partyId, "confirmedAt")).isEqualTo(confirmedAt);
        assertThat(redis.getExpire(partyKey(partyId)))
                .as("재전송이 파티 TTL 을 처음부터 다시 세게 하면 안 된다")
                .isLessThan(partyTtl).isLessThanOrEqualTo(PARTY_TTL_SECONDS);
        assertThat(field(partyId, "status")).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("뒷정리 스크립트가 다시 실행돼도 confirmedAt 은 첫 값이다 (HSETNX)")
    void replayingTheCleanupKeepsTheFirstConfirmedAt() {
        String partyId = fullParty();

        proposalService.accept(partyId, "u1");
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);
        String confirmedAt = field(partyId, "confirmedAt");

        // 페일오버 재실행을 흉내 낸다 — 같은 스크립트를 뒤 시각으로 한 번 더. HSET 이었다면 시각이 밀린다
        @SuppressWarnings("unchecked")
        List<String> members = redis.execute(cleanupConfirmedScript,
                List.of(partyKey(partyId), "qm:proposal:accepts:" + partyId),
                "qm:user:active-request:", String.valueOf(RETENTION_SECONDS),
                String.valueOf(Long.parseLong(confirmedAt) + 600_000), String.valueOf(PARTY_TTL_SECONDS));

        assertThat(members).containsExactlyInAnyOrder("u1", "u2");
        assertThat(field(partyId, "confirmedAt")).isEqualTo(confirmedAt);
        assertThat(field(partyId, "game")).isEqualTo("LOL");
    }

    @Test
    @DisplayName("활성 요청이 이미 사라진 뒤의 재실행은 반쪽짜리 활성 요청을 되살리지 않는다")
    void replayingTheCleanupAfterRetentionDoesNotResurrectActiveRequests() {
        String partyId = fullParty();

        proposalService.accept(partyId, "u1");
        assertThat(proposalService.accept(partyId, "u2")).isEqualTo(ProposalResult.CONFIRMED);

        // 60초가 지나 활성 요청이 사라진 상태를 만든다. 이제 u1 · u2 는 다시 큐에 들어올 수 있어야 한다
        redis.delete(List.of(activeRequestKey("u1"), activeRequestKey("u2")));

        @SuppressWarnings("unchecked")
        List<String> members = redis.execute(cleanupConfirmedScript,
                List.of(partyKey(partyId), "qm:proposal:accepts:" + partyId),
                "qm:user:active-request:", String.valueOf(RETENTION_SECONDS),
                String.valueOf(System.currentTimeMillis()), String.valueOf(PARTY_TTL_SECONDS));

        // 파티원 목록은 파티 HASH 가 원본이라 그대로다. 그러나 HSET 으로 status 하나만 든 활성 요청을
        // 만들어 놓으면 그 사람은 60초 더 막히고 조회는 partyId 없는 MATCHED 를 답한다
        assertThat(members).containsExactlyInAnyOrder("u1", "u2");
        assertThat(redis.hasKey(activeRequestKey("u1"))).isFalse();
        assertThat(redis.hasKey(activeRequestKey("u2"))).isFalse();
        // 활성 요청이 없어도 먼저 베껴 둔 조건은 남아 있다 — platform 은 여전히 읽을 수 있다
        assertThat(field(partyId, "modeKey")).isEqualTo(MODE);
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

    /**
     * {@code join-party.lua} 를 같은 파티에 대고 한 번 더 실행한다.
     *
     * <p>KEYS / ARGV 배치는 {@code LolUntieredAssigner#scriptKeys} · {@code #joinArgs} 와
     * 같아야 한다. 그쪽이 private 이라 여기서 다시 조립한다 — 배치가 어긋나면 이 테스트는
     * 스크립트가 아니라 제 조립을 검사하게 되므로, 그 두 메서드를 고치면 여기도 봐야 한다.
     */
    private void replayLastJoin(String partyId, String userId, String position, long expiresAt) {
        List<String> positions = Arrays.stream(LolPosition.values()).map(Enum::name).toList();

        List<String> keys = new ArrayList<>();
        keys.add(partyKey(UUID.randomUUID().toString()));               // KEYS[1] 안 읽는다
        keys.add("qm:user:active-request:" + userId);                   // KEYS[2]
        positions.forEach(p -> keys.add(
                "qm:party:open:LOL:" + MODE + ":REQUIRED:RANK_UP:needs:" + p));   // KEYS[3..]

        List<String> args = new ArrayList<>();
        args.add(partyId);                                  // ARGV[1]
        args.add(String.valueOf(System.currentTimeMillis()));// ARGV[2]
        args.add(position);                                 // ARGV[3]
        args.add("2");                                      // ARGV[4] target
        args.add("true");                                   // ARGV[5] uniqueness
        args.add("qm:party:");                              // ARGV[6]
        args.add(userId);                                   // ARGV[7]
        args.add(String.valueOf(expiresAt));                // ARGV[8]
        args.addAll(positions);                             // ARGV[9..]

        @SuppressWarnings("unchecked")
        List<Object> result = redis.execute(lolJoinPartyUntieredScript, keys, args.toArray());
        // 2 = 들어갔고 정원이 찼다. 재실행이므로 인원은 늘지 않는다
        assertThat(((Number) result.get(0)).longValue()).isEqualTo(2);
    }

    private String partyKey(String partyId) {
        return "qm:party:" + partyId;
    }

    /**
     * 컨트롤러가 쿼리 파라미터로 받는 {@code requestId} 를 활성 요청에서 꺼내 대신 넘긴다.
     *
     * <p>거절은 스크립트 뒤에 취소까지 부르므로 {@code requestId} 가 필요하다. 이미 큐에서
     * 빠진 사용자는 꺼낼 값이 없어 빈 문자열을 넘긴다 — {@code leave-party.lua} 가
     * "활성 요청 없음" 으로 끝낸다. <b>재시도를 보는 테스트는 이 헬퍼를 쓰지 말고
     * 첫 호출 전에 {@link #requestIdOf} 로 값을 잡아 두어야 한다.</b>
     */
    private ProposalResult decline(String partyId, String userId) {
        return proposalService.decline(partyId, userId);
    }

    private String requestIdOf(String userId) {
        String requestId = redis.<String, String>opsForHash()
                .get("qm:user:active-request:" + userId, "requestId");
        return requestId == null ? "" : requestId;
    }

    private String activeRequestKey(String userId) {
        return "qm:user:active-request:" + userId;
    }

    private String partyIdOf(String userId) {
        return redis.<String, String>opsForHash().get("qm:user:active-request:" + userId, "partyId");
    }

    private String field(String partyId, String field) {
        return redis.<String, String>opsForHash().get(partyKey(partyId), field);
    }

    private long acceptCount(String partyId) {
        Long size = redis.opsForSet().size("qm:proposal:accepts:" + partyId);
        return size == null ? 0 : size;
    }
}
