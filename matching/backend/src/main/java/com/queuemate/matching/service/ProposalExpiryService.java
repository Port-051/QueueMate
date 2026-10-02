package com.queuemate.matching.service;

import com.queuemate.matching.notification.PushEventType;
import com.queuemate.matching.notification.PushPublisher;
import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 시한이 지난 제안 하나를 정리한다 (INV-5 expired). 꺼내는 일은 {@link ProposalSweeper} 가 한다.
 *
 * <p><b>안 누른 사람만 큐에서 뺀다.</b> 수락한 사람은 파티에 그대로 둔다 — 잘못은 무응답자가
 * 했는데 기다리던 사람까지 빼면 그 사람은 처음부터 다시 줄을 서야 한다. 대신 수락 기록은
 * 스크립트가 지우므로, 빈자리가 새로 채워져 제안이 다시 열리면 <b>다시 눌러야 한다</b>.
 * 그 사람은 새 멤버를 본 적이 없기 때문이다.
 *
 * <p><b>멤버를 빼고 색인을 되돌리는 일은 여기서 하지 않는다.</b> {@link MatchCancelService#cancel}
 * 이 게임별 {@code leave-party.lua} 를 태우고, 그 스크립트가 비운 자리를 색인에 되돌리고
 * (발로란트는 티어 범위까지) 마지막 한 명이면 파티까지 지운다. 같은 일을 여기서 다시 쓰지 않는다.
 *
 * <p><b>알림 대상은 스크립트가 같이 돌려준다.</b> 자바가 파티 HASH 를 다시 읽지 않는 이유는
 * 왕복이 늘어서만이 아니다 — 그 사이에 누가 취소하면 목록이 달라진다. 스크립트가 수락자 집합을
 * 지우기 전에 읽어 둔 값이라야 "그 제안에 있던 사람들"이 정확하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProposalExpiryService {

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> expireProposalScript;
    private final MatchCancelService matchCancelService;
    private final PushPublisher pushPublisher;

    /**
     * 제안 하나를 만료시킨다.
     *
     * <p>{@code now} 를 같이 넘긴다. 스위퍼가 목록에서 꺼낼 때 시한을 봤어도, 이 스크립트가 돌 때는
     * 같은 partyId 에 새 제안이 열려 있을 수 있다(옛 제안이 깨지고 빈자리가 다시 찬 경우). 그 판단은
     * 스크립트 안에서 다시 해야 한다 — 자바가 꺼낸 목록은 그 사이에 낡는다 ({@code expiry-proposal.lua}).
     * {@code now} 를 자바가 넘기는 것은 다른 스크립트와 같은 이유다 — Lua 의 {@code TIME} 은 복제 · 재실행에서
     * 값이 달라진다({@link ProposalService} 의 {@code confirmed}).
     *
     * @param partyId 만료 대기 목록에서 꺼낸 파티 id (= proposalId)
     */
    @SuppressWarnings("unchecked")
    public void expire(String partyId) {
        List<Object> result = redis.execute(expireProposalScript,
                List.of(SharedKeys.partyKey(partyId),
                        SharedKeys.acceptsKey(partyId),
                        SharedKeys.PENDING_KEY),
                partyId,
                String.valueOf(System.currentTimeMillis()));

        // 빈 결과는 할 일이 없었다는 뜻이다 — 이미 확정·거절됐거나 시한 전이다 (expiry-proposal.lua)
        if (result == null || result.isEmpty()) {
            return;
        }

        // [0] = 수락하지 않은 사람들의 {userId, requestId} 쌍, [1] = 수락한 사람들의 userId
        List<List<String>> notAccepted = (List<List<String>>) result.get(0);
        List<String> accepted = (List<String>) result.get(1);

        notAccepted.forEach(pair -> matchCancelService.cancel(pair.get(0), pair.get(1)));

        log.debug("제안 만료 partyId={} 무응답={} 수락={}",
                partyId, notAccepted.size(), accepted.size());

        // 수락한 사람도 받아야 한다. 그 사람들은 제안 화면에서 기다리고 있어서,
        // 이 알림이 없으면 파티가 깨진 줄 모르고 그 화면에 갇힌다
        pushPublisher.publishAll(recipients(notAccepted, accepted),
                PushEventType.MATCH_PROPOSAL_EXPIRED,
                Map.of("partyId", partyId));
    }

    /** 알림 받을 사람들. 큐에서 빠진 무응답자와, 파티에 남아 다시 기다리게 된 수락자 전부다. */
    private List<String> recipients(List<List<String>> notAccepted, List<String> accepted) {
        List<String> recipients = new ArrayList<>(accepted);
        notAccepted.forEach(pair -> recipients.add(pair.get(0)));
        return recipients;
    }
}
