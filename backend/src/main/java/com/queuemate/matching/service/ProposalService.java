package com.queuemate.matching.service;

import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.domain.ProposalResult;
import com.queuemate.matching.notification.PushEventType;
import com.queuemate.matching.notification.PushPublisher;
import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 제안 수락 / 거절.
 *
 * <p>수락 집계 · 거절의 판정은 전부 Lua 안에서 한다. 이 클래스가 하는 일은 <b>키를 조립해 스크립트에
 * 넘기고 돌아온 문자열을 enum 으로 바꾸는 것</b>, 그리고 스크립트 뒤에 붙는 자바 몫 둘 —
 * 확정 뒤의 뒷정리({@link #confirmed}), 거절 뒤의 취소({@link #decline}) — 이다.
 *
 * <h2>지켜지는 것</h2>
 * <ul>
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
 * <h2>확정 뒤 — 파티는 platform 이 Redis 에서 직접 읽어 간다 (docs/11 D-42)</h2>
 * <p>확정된 파티를 DB 에 만드는 것은 {@code app:platform} 이고, 그쪽은 outbox → SQS 가 아니라
 * <b>이 앱의 파티 HASH {@code qm:party:{partyId}} 를 Redis 에서 직접 읽는다</b>(gameconfig 를 읽는 D-29 ·
 * 활성 요청 키를 {@code EXISTS} 로 보는 D-19 와 같은 방식). 프런트가 {@code MATCH_CONFIRMED {partyId}} 를
 * 받아 platform 을 부른다. 그래서 <b>이 앱이 발행할 것은 없다</b> — {@code matching.outbox} 도
 * {@code ProposalConfirmed.fifo} 도 없다.
 *
 * <p>이 앱의 몫은 {@link #confirmed} 가 부르는 {@code cleanup-confirmed.lua} 두 가지다.
 * <ul>
 *   <li>파티 HASH 를 <b>키 하나로 읽히게</b> 만든다 — 활성 요청에만 있던 조건 넷
 *       ({@code game} / {@code modeKey} / {@code voicePreference} / {@code playPurpose})을 베껴 적고
 *       {@code confirmedAt} 을 찍는다. 필드 이름은 platform 과의 계약이다(그 스크립트 머리말).
 *   <li><b>확정 상태를 영원히 두지 않는다</b> — 파티 HASH 에 {@code confirmed-party-ttl-seconds}(기본 600),
 *       파티원의 활성 요청({@code status = PARTY})과 수락자 집합에 {@code confirmed-retention-seconds}(기본 60)
 *       TTL 을 건다. 활성 요청이 사라지면 그 사용자는 다시 큐에 들어올 수 있다. "한 번에 하나"는 platform 의
 *       입장 표시 키 {@code qm:user:active-room:{userId}} 가 잇는다({@code claim-request.lua} 가 본다, D-19) —
 *       platform 이 이 파티로 방을 만들 때 그 키를 세우는 것이 D-42 의 전제다.
 * </ul>
 * <p>{@code PartyClosed} 소비는 없다 — 그 큐는 platform 만 읽고(D-13), 풀리는 것은 TTL 로 대신한다.
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

    /** 파티 HASH 의 참가자 필드 접두사 — 배정 스크립트가 {@code member:{userId}} 로 적는다({@code ScriptSupport} 와 같은 값) */
    private static final String MEMBER_FIELD_PREFIX = "member:";

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

    /**
     * 확정된 제안의 수락자 집합 <b>과 파티원의 활성 요청({@code status = PARTY})</b>을 남겨 두는 시간(초).
     * 이 시간이 지나면 그 사용자는 다시 큐에 들어올 수 있다(클래스 주석 D-42).
     *
     * <p>생성자 주입이 아니라 필드 주입인 것은 Lombok 의 {@code @RequiredArgsConstructor} 가
     * {@code @Value} 를 생성자 파라미터로 옮겨 주지 않기 때문이다.
     */
    @Value("${queuemate.proposal.confirmed-retention-seconds}")
    private long confirmedRetentionSeconds;

    /**
     * 확정된 파티 HASH 를 남겨 두는 시간(초). app:platform 이 이 안에 {@code qm:party:{partyId}} 를 읽어
     * 파티를 만든다(D-42). 지나면 파티가 증발한다 — 받아들인 절충이다.
     */
    @Value("${queuemate.proposal.confirmed-party-ttl-seconds}")
    private long confirmedPartyTtlSeconds;

    /**
     * 거절한 사람과 그 제안에 있던 상대들이 서로 다시 매칭되지 않는 시간(초). {@link #decline} 이 양쪽의
     * {@code qm:user:declined:{userId}} 에 적는 score(풀리는 시각)와 키 TTL 둘 다 이 값이다.
     */
    @Value("${queuemate.proposal.decline-avoid-seconds}")
    private long declineAvoidSeconds;

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
     * <p>확정되면 {@link #confirmed} 가 뒷정리와 알림을 맡는다. 그 뒤 파티를 만드는 것은
     * 파티 HASH 를 직접 읽는 app:platform 이다(클래스 주석 D-42) — 이 앱이 더 발행할 것은 없다.
     *
     * @param proposalId 제안 id (= partyId)
     * @param userId     수락한 사용자
     */
    public ProposalResult accept(String proposalId, String userId) {
        ProposalResult value = run(acceptProposalScript, proposalId, userId);
        if (value == ProposalResult.CONFIRMED) {
            confirmed(proposalId);
        }
        return value;
    }

    /**
     * 확정 직후에 할 일. <b>이 호출이 확정을 만든 그 한 번에서만 일어난다</b> —
     * 이미 확정된 제안에 수락이 또 오면 스크립트가 {@link ProposalResult#ALREADY_RESPONDED} 를
     * 돌려주므로, 알림이 파티 전원에게 두 번 나가지 않는다.
     *
     * <p>정리 스크립트({@code cleanup-confirmed.lua})는 platform 이 읽을 조건 넷과 {@code confirmedAt} 을
     * 파티 HASH 에 적고, 파티원의 활성 요청을 <b>지우지 않고</b> {@code status = PARTY} 로 바꾼다 —
     * 지우면 그 순간 새 매칭을 걸 수 있어 한 사람이 두 파티에 속한다(INV-2). 대신 <b>TTL 을 건다</b>:
     * 활성 요청 · 수락자 집합은 {@link #confirmedRetentionSeconds}, 파티 HASH 는
     * {@link #confirmedPartyTtlSeconds}. 지나면 활성 요청이 사라져 그 사용자는 다시 큐에 들어올 수 있고,
     * 그때부터 "한 번에 하나"는 platform 의 입장 표시 키가 지킨다(클래스 주석 D-42).
     *
     * <p>{@code now} 를 자바가 넘기는 것은 다른 스크립트와 같은 이유다 — Lua 의 {@code TIME} 은
     * 복제 · 재실행에서 값이 달라진다. 스크립트가 {@code HSETNX} 로 적으므로 재실행이 시각을 옮기지 않는다.
     */
    @SuppressWarnings("unchecked")
    private void confirmed(String proposalId) {
        // ARGV: [1] 활성 요청 접두사 [2] 활성 요청·수락자 집합 TTL(초) [3] now(millis) [4] 파티 HASH TTL(초)
        List<String> members = redis.execute(cleanupConfirmedScript,
                keys(proposalId),
                SharedKeys.ACTIVE_REQUEST_PREFIX,
                String.valueOf(confirmedRetentionSeconds),
                String.valueOf(System.currentTimeMillis()),
                String.valueOf(confirmedPartyTtlSeconds));

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
     * <p><b>거절한 상대는 한동안 다시 만나지 않는다.</b> 거절한 사람이 바로 다시 큐에 들어오면 방금 거절한 사람들과 또
     * 같은 파티가 될 수 있다 — 거절한 이유가 그 사람들이라면 같은 제안이 되풀이된다. 그래서 제안을 실제로 깬 그 한 번에
     * {@link #recordDeclined} 로 양쪽의 {@code qm:user:declined:{userId}} 에 적는다(score = 풀리는 시각,
     * {@code decline-avoid-seconds}). 상대 목록은 <b>큐에서 빼기 전에</b> 파티 HASH 에서 읽는다 — 뺀 뒤에는
     * {@code leave-party.lua} 가 내 {@code member:} 를 지우고 파티가 비면 HASH 째 없앤다. 배정 선필터가 이 집합을 읽는 것은 다음 일이다.
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
     */
    public ProposalResult decline(String proposalId, String userId) {
        ProposalResult answer = run(declineProposalScript, proposalId, userId);
        if (answer == ProposalResult.DECLINED) {
            Set<String> others = otherMembers(proposalId, userId);
            // 활성 요청은 HASH 다 — requestId 는 그 필드에서 읽는다. 클라이언트가 주던 값을 서버가 직접 읽으므로
            // 값이 틀려 본인이 파티에 남는 일이 없다. 스크립트가 멤버 여부를 먼저 봤으니 옛 요청이 끼어들 틈도 없다
            String requestId = redis.<String, String>opsForHash()
                    .get(SharedKeys.activeRequestKey(userId), "requestId");
            CancelResult cancelled = matchCancelService.cancel(userId, requestId);
            if (cancelled != CancelResult.CANCELLED && cancelled != CancelResult.CANCELLED_AND_PARTY_CLOSED) {
                log.warn("거절 뒤 큐에서 빼지 못했다 partyId={} userId={} result={}", proposalId, userId, cancelled);
            }
            if (!others.isEmpty()) {
                recordDeclined(userId, others);
            }
        }
        return answer;
    }

    /** 파티 HASH 의 {@code member:} 필드에서 나를 뺀 나머지 userId. 파티가 없으면 빈 집합 */
    private Set<String> otherMembers(String partyId, String me) {
        String myField = MEMBER_FIELD_PREFIX + me;
        return redis.<String, String>opsForHash().keys(SharedKeys.partyKey(partyId)).stream()
                .filter(field -> field.startsWith(MEMBER_FIELD_PREFIX) && !field.equals(myField))
                .map(field -> field.substring(MEMBER_FIELD_PREFIX.length()))
                .collect(Collectors.toSet());
    }

    /**
     * 거절한 상대를 <b>양쪽에</b> 적는다 — 내 ZSET 에 상대들, 상대 ZSET 마다 나. score 는 풀리는 시각(epoch ms)이고
     * 키 TTL 은 매번 다시 건다(마지막 거절 기준 — 안 그러면 9분 전 거절 때 걸린 TTL 이 방금 거절한 사람의 10분을 1분으로 줄인다).
     * 그 사이 이미 풀린 member 가 키에 남을 수 있으니 읽는 쪽은 score 로 거른다. Redis 예외는 다른 자리와 같이 그대로 올라간다.
     */
    private void recordDeclined(String me, Set<String> others) {
        long until = System.currentTimeMillis() + declineAvoidSeconds * 1000;
        Duration ttl = Duration.ofSeconds(declineAvoidSeconds);
        String myKey = SharedKeys.declinedKey(me);
        Set<ZSetOperations.TypedTuple<String>> tuples = others.stream()
                .map(other -> (ZSetOperations.TypedTuple<String>) new DefaultTypedTuple<>(other, (double) until))
                .collect(Collectors.toSet());
        redis.opsForZSet().add(myKey, tuples);
        redis.expire(myKey, ttl);
        for (String other : others) {
            String key = SharedKeys.declinedKey(other);
            redis.opsForZSet().add(key, me, until);
            redis.expire(key, ttl);
        }
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
