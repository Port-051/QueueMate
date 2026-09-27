package com.queuemate.matching.service;

import com.queuemate.matching.block.PartyBlockCheck;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.notification.PushEventType;
import com.queuemate.matching.notification.PushPublisher;
import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 제안 수락 / 거절.
 *
 * <p>수락 집계 · 거절의 판정은 전부 Lua 안에서 한다. 이 클래스가 하는 일은 <b>키를 조립해 스크립트에
 * 넘기고 돌아온 문자열을 enum 으로 바꾸는 것</b>, 그리고 스크립트 앞뒤에 붙는 자바 몫 셋 —
 * 수락 전의 차단 검증({@link #accept} → {@link PartyBlockCheck}), 확정 뒤의 뒷정리({@link #confirmed}),
 * 거절 뒤의 취소({@link #decline}) — 이다.
 *
 * <h2>지켜지는 것</h2>
 * <ul>
 *   <li><b>INV-6</b> — 차단 관계인 사용자는 같은 파티가 될 수 없다. 배정 경로의 선필터가 못 보는 것
 *       (파티가 찬 뒤에 생긴 차단 · 정원 2 인 파티)을 <b>수락이 들어올 때마다</b> {@link PartyBlockCheck} 가
 *       {@code blocks} 를 동기 SELECT 로 다시 묻는다 (docs/11 D-1). 차단이 있으면 그 쌍의 양쪽을 큐에서 빼고
 *       전원에게 {@code MATCH_PROPOSAL_EXPIRED} 를 보낸 뒤 {@link ProposalResult#NOT_FOUND} 다
 *       ({@link #breakForBlocks}). DB 를 못 읽으면 {@code DataAccessException} 이 그대로 올라가 503 이다 —
 *       차단을 확인하지 못한 채 확정하지 않는다 (INV-10 fail-closed).
 *   <li><b>INV-4</b> — 참가자 전원이 수락하기 전에는 확정하지 않는다.
 *       {@code accept-proposal.lua} 가 {@code SCARD} 로 세어 {@code target} 과 비교한다.
 *   <li><b>INV-5</b> — 확정된 제안은 다시 뒤집히지 않는다. 두 스크립트 모두 쓰기 전에
 *       {@code status} 를 먼저 본다. 거절된 제안은 {@code status} 자체가 지워져
 *       ({@code decline-proposal.lua} 참고) 확정 경로에 아예 들어오지 못한다.
 *   <li><b>원자성 규칙</b> (CLAUDE.md §4) — {@code GET → 애플리케이션 판단 → SET} 으로
 *       위 둘을 지키지 않는다. 다섯 명이 동시에 누르면 확인과 쓰기 사이가 벌어져
 *       둘 다 "내가 마지막이다"라고 판단한다. <b>수락 집계는 Lua 한 덩어리다</b>
 *       (docs/11 #28).
 *   <li><b>멱등</b> — <b>수락만</b> 멱등이다. 같은 수락이 두 번 들어와도 답이 같다.
 *       <b>거절은 멱등이 아니다</b> — 재시도는 {@link ProposalResult#NOT_FOUND} 를 받는다.
 *       왜 그래도 되는지는 {@code decline-proposal.lua} 의 "멱등성" 절에 있다.
 * </ul>
 *
 * <h2>아직 없는 것</h2>
 * <ul>
 *   <li>{@code matching.outbox} 기록 → {@code ProposalConfirmed.fifo} 발행. 그것이 붙어야
 *       {@code app:platform} 이 파티를 DB 에 만든다 (CLAUDE.md §9). INV-6 최종 검증은 그 앞에 서 있으므로
 *       (수락마다 돈다 — 확정을 만든 마지막 수락도 포함) 발행이 붙어도 그 자리가 바뀌지 않는다.
 *   <li>{@code PartyClosed} 소비 — 확정된 사용자는 활성 요청이 {@code status = PARTY} 로
 *       남아 새 매칭을 걸 수 없다. 그 파티가 닫혔다는 것을 이 앱은 알 수 없으므로,
 *       platform 이 알려 줄 때까지 풀리지 않는다.
 * </ul>
 *
 * <h2>제안 상태를 어디에 두는가</h2>
 * <p>{@code docs/07 §5-1} 은 {@code qm:proposal:{id}} / {@code qm:proposal:members:{id}} 를
 * 따로 두라고 하지만 <b>따르지 않는다.</b> 그 설계는 proposal 과 party 를 별개로 보는
 * 전제인데, 이 제품은 둘을 같은 것으로 정했다(CLAUDE.md §1). 그래서 {@code status} 와
 * {@code expiresAt} 은 파티 HASH 에 얹고, 수락자만 {@code qm:proposal:accepts:{partyId}}
 * SET 에 담는다. 문서와의 이 차이는 {@code docs/11} 에 기록해야 한다.
 *
 * <p><b>제안 id 는 partyId 다.</b> proposal 하나는 언제나 하나의 party 를 뜻하므로
 * (CLAUDE.md §1) 별도 식별자를 두지 않는다. {@code MATCH_PROPOSAL_CREATED} 알림이
 * payload 에 싣는 {@code partyId} 가 클라이언트가 여기로 되돌려 보내는 값이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalService {

    private final StringRedisTemplate redis;

    /**
     * {@code RedisScript<String>} 빈이 둘이라 Spring 은 <b>필드 이름</b>으로 고른다.
     * 이 이름은 {@code RedisConfig} 의 빈 메서드 이름과 같아야 한다.
     */
    private final RedisScript<String> acceptProposalScript;
    private final RedisScript<String> declineProposalScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> cleanupConfirmedScript;

    private final PushPublisher pushPublisher;

    private final MatchCancelService matchCancelService;

    /** INV-6 확정 직전 최종 검증. 수락 스크립트보다 먼저 돈다 ({@link #accept}) */
    private final PartyBlockCheck partyBlockCheck;

    /**
     * 확정된 제안의 수락자 집합을 남겨 두는 시간(초).
     *
     * <p>생성자 주입이 아니라 필드 주입인 것은 Lombok 의 {@code @RequiredArgsConstructor} 가
     * {@code @Value} 를 생성자 파라미터로 옮겨 주지 않기 때문이다.
     */
    @Value("${queuemate.proposal.confirmed-retention-seconds}")
    private long confirmedRetentionSeconds;

    /**
     * 한 참가자의 수락을 기록한다. 그 수락으로 전원이 차면 확정까지 한다.
     *
     * <p>같은 요청이 두 번 들어와도 결과가 같아야 한다 — 네트워크가 끊겨 응답을 못 읽은
     * 클라이언트는 재시도하는 것이 정상이다. "이 사용자의 수락 상태를 ACCEPTED 로 둔다"는
     * 멱등 연산으로 설계하면 재시도가 안전해진다.
     *
     * <p>스크립트가 {@code SADD} 결과로 끊지 않고 매번 다시 세기 때문에 재시도해도 답이
     * 같다. 자세한 근거는 {@code accept-proposal.lua} 머리 주석에 있다.
     *
     * <p><b>스크립트 앞에 INV-6 최종 검증이 선다.</b> {@link PartyBlockCheck#check} 가 파티원 사이의 차단을
     * DB 에 묻고, 있으면 {@link #breakForBlocks} 가 제안을 깬 뒤 {@link ProposalResult#NOT_FOUND} 를 돌려준다.
     * 수락자 자신이 차단 쌍에 없어도 그렇다 — 그 파티는 어차피 성립할 수 없다. PENDING 이 아닌 제안(정원 미달 ·
     * 이미 확정)은 검증을 건너뛰고 스크립트가 전처럼 답한다. {@code DataAccessException} 은 잡지 않는다 —
     * {@code GlobalExceptionHandler} 가 503 으로 바꾼다 (INV-10).
     *
     * <p>확정되면 {@link #confirmed} 가 뒷정리와 알림을 맡는다. 아직 없는 것은
     * {@code matching.outbox} 기록 → {@code ProposalConfirmed.fifo} 발행 하나다.
     *
     * @param proposalId 제안 id (= partyId)
     * @param userId     수락한 사용자
     * @throws org.springframework.dao.DataAccessException 차단 조회나 Redis 가 실패했다. 503 으로 나간다
     */
    public ProposalResult accept(String proposalId, String userId) {
        // INV-6 최종 검증. 스크립트보다 먼저다 — 차단이 있는 파티에 수락을 쌓아 두면 안 된다.
        // DataAccessException 은 여기서 잡지 않는다. 차단을 확인하지 못하면 확정도 하지 않는다 (INV-10)
        PartyBlockCheck.Verdict verdict = partyBlockCheck.check(proposalId);
        if (verdict.blocked()) {
            breakForBlocks(proposalId, verdict);
            return ProposalResult.NOT_FOUND;
        }

        ProposalResult value = run(acceptProposalScript, proposalId, userId);
        if (value == ProposalResult.CONFIRMED) {
            confirmed(proposalId);
        }
        return value;
    }

    /**
     * 파티원 사이에 차단이 있어 제안을 깬다 (INV-6).
     *
     * <p><b>차단 쌍의 양쪽을 큐에서 뺀다.</b> 각자의 활성 요청에서 {@code requestId} 를 읽어
     * {@link MatchCancelService#cancel} 을 부른다 — 그것이 게임별 {@code leave-party.lua} 를 태워 제안 흔적
     * ({@code status}/{@code expiresAt} · 수락자 SET · {@code qm:proposal:pending})을 지우고, 멤버를 빼고, 비운
     * 자리를 색인에 되돌리고, 남은 사람에게 {@code MATCH_CANCELLED} 를 보낸다. 같은 일을 여기서 다시 쓰지 않는다.
     * 첫 취소가 제안 흔적을 이미 지우지만 두 번째 취소도 같은 것을 지운다 — 없는 값에 거는 HDEL/DEL 이라 무해하다.
     * 잘못이 없는 나머지는 파티에 남아 다시 기다린다 (거절 · 만료와 같은 정책).
     *
     * <p><b>그 제안에 있던 전원에게 {@code MATCH_PROPOSAL_EXPIRED} 를 보낸다.</b> 뜻은 만료와 같다 — "이 제안은
     * 없어졌다. 상태를 다시 조회해라". 빠진 사람은 대기 화면을 떠나야 하고 남은 사람은 제안 화면에서 나와야 하는데,
     * 그 둘을 가르는 새 알림 종류를 만들면 계약이 바뀐다. 수신자는 검증이 읽어 둔 목록이다 — 취소가 돈 뒤
     * 파티 HASH 를 다시 읽으면 빠진 사람이 목록에 없다.
     *
     * <p>돌려주는 값이 {@link ProposalResult#NOT_FOUND} 인 이유도 같다 — 제안은 더 이상 없고, 클라이언트가
     * 갈 곳은 스위퍼가 걷어간 뒤와 같다(대기 화면 복귀). 결과 값을 하나 더 만들지 않는다.
     *
     * <p>로그는 WARN 이다. 선필터를 지나 여기까지 온 차단은 드물어야 하고, 잦으면 선필터가 새는 것이다.
     */
    private void breakForBlocks(String proposalId, PartyBlockCheck.Verdict verdict) {
        for (String userId : verdict.blockedUserIds()) {
            String requestId = redis.<String, String>opsForHash()
                    .get(SharedKeys.activeRequestKey(userId), "requestId");
            if (requestId == null) {
                // 그사이 스스로 취소했다. 뺄 것이 없다
                continue;
            }
            CancelResult cancelled = matchCancelService.cancel(userId, requestId);
            log.debug("차단으로 큐에서 뺀다 partyId={} userId={} result={}", proposalId, userId, cancelled);
        }

        log.warn("파티원 사이에 차단이 있어 제안을 깼다 partyId={} pairs={} removed={} members={}",
                proposalId, verdict.pairCount(), verdict.blockedUserIds().size(), verdict.members().size());

        pushPublisher.publishAll(verdict.members(), PushEventType.MATCH_PROPOSAL_EXPIRED,
                Map.of("partyId", proposalId));
    }

    /**
     * 확정 직후에 할 일. <b>이 호출이 확정을 만든 그 한 번에서만 일어난다</b> —
     * 이미 확정된 제안에 수락이 또 오면 스크립트가 {@link ProposalResult#ALREADY_RESPONDED} 를
     * 돌려주므로, 알림이 파티 전원에게 두 번 나가지 않는다.
     *
     * <p>정리 스크립트는 파티원의 활성 요청을 <b>지우지 않고</b> {@code status = PARTY} 로 바꾼다.
     * 확정된 사람은 이미 파티에 속해 있어서, 지우면 그 순간 새 매칭을 걸 수 있게 되어 한 사람이
     * 두 파티에 속한다(INV-2). 이 상태를 푸는 것은 파티를 닫는 app:platform 쪽이다(미구현).
     *
     * <p><b>아직 없는 것</b>: {@code matching.outbox} 기록 → {@code ProposalConfirmed.fifo} 발행.
     * 그것이 붙어야 app:platform 이 파티를 DB 에 만든다.
     */
    @SuppressWarnings("unchecked")
    private void confirmed(String proposalId) {
        List<String> members = redis.execute(cleanupConfirmedScript,
                keys(proposalId),
                SharedKeys.ACTIVE_REQUEST_PREFIX, String.valueOf(confirmedRetentionSeconds));

        if (members == null || members.isEmpty()) {
            return;
        }

        // partyId 를 싣는 이유: 알림은 휘발성이고 순서 보장도 없어서, 클라이언트는
        // 이것을 "다시 조회하라"는 신호로 쓴다 (contracts/events.md)
        pushPublisher.publishAll(members, PushEventType.MATCH_CONFIRMED,
                Map.of("partyId", proposalId));
    }

    /**
     * 한 참가자의 거절을 기록하고 제안을 깬다.
     *
     * <p>거절로 제안이 깨져도 <b>수락했던 나머지 사람들은 큐에 남긴다.</b> 그들의
     * 활성 요청까지 지우면, 잘못은 거절한 사람이 했는데 매칭을 기다리던 사람들이
     * 함께 빠진다. 취소({@code leave-party.lua})와 다른 점이 이것이다.
     *
     * <p><b>스크립트는 {@code status} 를 {@code DECLINED} 로 바꾸지 않는다. 지운다.</b>
     * {@code status}/{@code expiresAt} 과 수락자 집합만 지우고 파티와 참가자는 그대로 둔다.
     * 그래야 거절자가 빠진 자리가 다시 찼을 때 {@code join-party*.lua} 의
     * {@code HSETNX status 'PENDING'} 이 다시 성공해 새 제안이 열린다. 상태를 남기면
     * {@code HSETNX} 가 0 을 돌려주어 {@code DECLINED} 로 굳은 좀비 파티가 된다 —
     * 자세한 근거는 {@code decline-proposal.lua} 머리 주석에 있다.
     *
     * <p><b>거절한 본인은 큐에서 뺀다.</b> 스크립트가 답을 돌려준 뒤
     * {@link MatchCancelService#cancel(String, String)} 을 부른다. 제안을 거절한 사람을
     * 같은 파티에 그대로 두면 바로 다시 매칭될 수 있기 때문이다.
     *
     * <p><b>거절은 멱등이 아니다.</b> 첫 호출만 {@link ProposalResult#DECLINED} 이고,
     * 같은 거절의 재시도는 {@code status} 가 이미 지워졌으므로
     * {@link ProposalResult#NOT_FOUND} 다. 두 답 모두 클라이언트가 갈 화면은 같다
     * (대기 화면 복귀) — 판단 근거는 스크립트 머리 주석에 적어 두었다.
     *
     * <p><b>TODO</b> — 거절 뒤에 와야 할 것들. 이번 범위가 아니다.
     * <ul>
     *   <li>남은 사람들에게 제안이 깨졌음을 알리기 ({@code MATCH_PROPOSAL_EXPIRED} 계열)
     * </ul>
     *
     * @param proposalId 제안 id (= partyId)
     * @param userId     거절한 사용자
     * @param requestId  거절한 사용자의 활성 요청 id. 큐에서 빼는 compare-and-delete 에 쓴다
     */
    public ProposalResult decline(String proposalId, String userId, String requestId) {

        ProposalResult answer = run(declineProposalScript, proposalId, userId);
        if (answer == ProposalResult.DECLINED) {
            matchCancelService.cancel(userId, requestId);
        }
        return answer;
    }

    /**
     * 스크립트를 부르고 답을 enum 으로 바꾼다.
     *
     * <p><b>스크립트가 {@link ProposalResult} 에 없는 값을 돌려주면 여기서
     * {@link IllegalArgumentException} 이 나고 500 으로 나간다.</b> 반환 문자열은
     * Lua 안에 리터럴로 박혀 있어 컴파일러가 맞춰 주지 않으므로, 스크립트의 반환 문자열을
     * 고칠 때는 이 enum 도 함께 봐야 한다. 잡아서 다른 결과로 뭉개지 않는 것은 의도다 —
     * 계약이 어긋난 것을 성공처럼 보이게 하면 그 순간부터 아무도 못 찾는다.
     *
     * <p>스크립트가 {@code nil} 을 돌려주는 경로는 없다. 모든 분기가 문자열을 반환한다.
     */
    private ProposalResult run(RedisScript<String> script, String proposalId, String userId) {
        // ARGV[2] 는 만료 대기 목록(qm:proposal:pending)에서 뺄 때 쓴다. 제안이 끝나는 자리마다
        // ZREM 을 해야 스위퍼가 이미 끝난 제안을 다시 꺼내지 않는다
        // ARGV[3] 은 지금 시각이다. 수락 스크립트가 파티의 expiresAt 과 비교해, 스위퍼가
        // 걷어가기 전이라도 시한이 지난 제안은 확정시키지 않는다 (INV-5 expired)
        String answer = redis.execute(script, keys(proposalId),
                userId, proposalId, String.valueOf(System.currentTimeMillis()));
        return ProposalResult.valueOf(answer);
    }

    /**
     * KEYS[1] = 파티 HASH, KEYS[2] = 수락자 SET. 수락 · 거절 · 확정 정리가 모두 같은 두 키를 받는다.
     *
     * <p>proposalId 를 그대로 KEYS 에 넣으면 안 된다 — 클라이언트가 돌려보내는 값은
     * partyId(UUID)이고 Redis 키가 아니다.
     *
     * <p>접두사는 {@link SharedKeys} 한 자리에서 온다. 배정 Lua 와 <b>문자열까지 같은 키</b>를
     * 만들어야 하는데, 여기에 한 번 더 적어 두면 한쪽만 고쳐도 컴파일은 통과하고 그때부터
     * 아무도 못 찾는 키를 만들게 된다. 제안은 게임을 보지 않으므로(조건을 읽지 않는다)
     * 게임별 {@code *PartyKeys} 를 빌려 오지 않는다.
     */
    private List<String> keys(String proposalId) {
        return List.of(SharedKeys.partyKey(proposalId), SharedKeys.acceptsKey(proposalId));
    }

}
