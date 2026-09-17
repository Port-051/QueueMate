package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.notification.PushEventType;
import com.queuemate.matching.notification.PushPublisher;
import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.queuemate.matching.rule.ScriptSupport.*;

/**
 * PUBG 의 티어를 보지 않는 모드의 배정. 자기 인자를 조립하고, 스크립트를 부르고, 결과를 읽는다.
 *
 * <p>LoL 과 다른 점: 핵심 조건이 플랫폼(STEAM / KAKAO)이고 포지션이 없다. 한 파티에 같은
 * 플랫폼이 여럿인 것이 정상이라 중복 금지(uniqueness)가 없다. 그래서 needs 색인이 내 플랫폼
 * <b>한 칸</b>(KEYS[3])뿐이고, ARGV 에 keyValue·uniqueness 자리가 없어 두 스크립트 모두 6개다.
 *
 * <p>호출부({@link PubgCandidateRule#canJoin})가 후보 풀 락을 쥔 채로 {@link #assign} 을 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubgUntieredAssigner {

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> pubgCreateOrCheckPartyUntieredScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> pubgJoinPartyUntieredScript;
    private final PubgPartyKeys keys;
    private final PushPublisher pushPublisher;


    /**
     * 제안의 시한(초). 정원이 차는 순간 {@code expiresAt = now + ttl} 로 환산해
     * 파티 HASH 에 적는다 — 그 뒤로는 늘어나지 않는다(join-party.lua 의 HSETNX).
     */
    @Value("${queuemate.proposal.ttl-seconds}")
    private long proposalTtlSeconds;

    /**
     * 들어갈 파티를 정한다. <b>후보 풀 락을 쥔 채로 실행된다.</b>
     *
     * <p>순회 전체가 한 락 안에 있어야 한다. 회차마다 락을 놓으면 그 사이 다른 요청이
     * 색인(ZSET)을 바꿔 같은 인덱스가 다른 파티를 가리킨다. 차단 조회는 호출부가 이미 끝냈다.
     */
    public void assign(CreateMatchRequestCommand command, PubgModeConfig config, Set<String> blockedUserIds)
    {
        String newPartyId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        List<String> scriptKeys = scriptKeys(command, config, newPartyId);

        for (int start = 0; start < MAX_CANDIDATE_SCAN; start++)
        {
            List<Object> found = execute(redis, pubgCreateOrCheckPartyUntieredScript, scriptKeys,
                    createOrCheckArgs(command, config, newPartyId, now, start));

            long code = code(found);

            // 1 = 후보가 없어 새로 만들고 들어갔다 / 2 = 후보를 찾았다 (멤버 목록을 받는다)
            // 음수는 배정하지 않고 끝낸다는 뜻이다. 이 스크립트가 돌려주는 음수는 하나다.
            //   -2 = claim 의 TTL 이 먼저 끝나 활성 요청이 사라졌다 (claim-request.lua 참고)
            if (code < 0) {
                log.debug("create-or-check result code={} userId={}", code, command.getUserId());
                return;
            }

            if (code == CREATED_NEW_PARTY) {
                pushPublisher.publishAll(List.of(command.getUserId()),
                        PushEventType.MATCH_QUEUE_UPDATED,
                        Map.of("memberNumber", 1));
                return;
            }

            String partyId = (String) found.get(2);
            List<String> memberIds = memberIds(found);
            if (blockedWith(memberIds, blockedUserIds)) {
                continue;   // 차단 관계다. 다음 후보를 본다
            }
            joinParty(command, config, scriptKeys, partyId, now, memberIds);
            return;
        }

        // 상한까지 봤는데 전부 차단이었다. 더 훑는 대신 새 파티를 만든다.
        createNewParty(command, config, scriptKeys, newPartyId, now);
    }

    /** 두 스크립트가 같은 KEYS 를 쓴다. join-party 쪽은 KEYS[1] 을 보지 않는다. */
    private List<String> scriptKeys(CreateMatchRequestCommand command, PubgModeConfig config, String newPartyId) {
        List<String> scriptKeys = new ArrayList<>();
        scriptKeys.add(keys.partyKey(newPartyId));                                  // KEYS[1]
        scriptKeys.add(keys.activeRequestKey(command.getUserId()));                 // KEYS[2]
        scriptKeys.add(keys.needsKey(command));                                     // KEYS[3] 내 플랫폼 색인 한 칸
        return scriptKeys;
    }

    /** create-or-check-party-untiered.lua 인자. ARGV[1] 은 후보가 없을 때 만들 파티 id 다. */
    private List<String> createOrCheckArgs(CreateMatchRequestCommand command, PubgModeConfig config,
                                           String newPartyId, long now, long start)
    {
        List<String> args = new ArrayList<>();
        args.add(newPartyId);                               // ARGV[1]
        args.add(String.valueOf(now));                      // ARGV[2]
        args.add(config.targetPartySize());                 // ARGV[3]
        args.add(SharedKeys.PARTY_PREFIX);               // ARGV[4]
        args.add(command.getUserId());                      // ARGV[5]
        args.add(String.valueOf(start));                    // ARGV[6] 색인의 몇 번째 후보를 볼지
        return args;
    }

    /** 찾아 둔 후보 파티에 들어간다. */
    private void joinParty(CreateMatchRequestCommand command, PubgModeConfig config,
                           List<String> scriptKeys, String partyId, long now, List<String> memberIds)
    {
        List<Object> result = execute(redis, pubgJoinPartyUntieredScript, scriptKeys,
                joinArgs(command, config, partyId, now));
        long code = code(result);

        // 음수면 들어가지 못했다. 아래는 들어간 것을 전제로 크기를 읽으므로 여기서 끝낸다
        if (code < 0) {
            log.debug("join 거절 code={} partyId={} userId={}", code, partyId, command.getUserId());
            return;
        }

        // 스크립트가 나를 넣은 뒤 센 값이다. 들어가기 전 목록을 세면 하나 모자란다
        long size = ((Number) result.get(2)).longValue();

        log.debug("join result code={} partyId={} size={} userId={}",
                code, partyId, size, command.getUserId());

        // 나 하나가 아니라 파티 전원이 받아야 한다. 정원이 찬 것도, 인원이 는 것도
        // 기존 파티원에게는 이 알림 말고 알 길이 없다
        List<String> recipients = recipients(command, memberIds);

        if (code == JOINED_AND_FULL) {
            // partyId 는 수락 API 의 경로 값이 된다 — 클라이언트가 이 값을 그대로 되돌려 보낸다.
            // target 은 "5명 중 3명 수락" 같은 표시용이다. 확정 판단에는 쓰지 않는다 —
            // 그건 서버가 파티 HASH 의 target 을 읽어서 한다
            pushPublisher.publishAll(recipients,
                    PushEventType.MATCH_PROPOSAL_CREATED,
                    Map.of("memberNumber", size,
                            "target", Integer.parseInt(config.targetPartySize()),
                            "partyId", partyId));
        } else {
            // 대기 화면 숫자 갱신에는 인원만 있으면 된다
            pushPublisher.publishAll(recipients,
                    PushEventType.MATCH_QUEUE_UPDATED,
                    Map.of("memberNumber", size));
        }
    }

    /**
     * join-party.lua 인자. ARGV[1] 이 "들어갈 파티 id" 라는 점과, ARGV[6] 이 start 대신
     * 제안 시한이라는 점이 위와 다르다.
     */
    private List<String> joinArgs(CreateMatchRequestCommand command, PubgModeConfig config,
                                  String partyId, long now)
    {
        List<String> args = new ArrayList<>();
        args.add(partyId);                                  // ARGV[1]
        args.add(String.valueOf(now));                      // ARGV[2]
        args.add(config.targetPartySize());                 // ARGV[3]
        args.add(SharedKeys.PARTY_PREFIX);               // ARGV[4]
        args.add(command.getUserId());                      // ARGV[5]
        args.add(String.valueOf(expiresAt(now)));           // ARGV[6] 제안 시한
        return args;
    }

    /** 이번 합류로 정원이 찰 경우 제안이 만료될 시각. 스크립트가 다시 계산하지 않아 재시도로 늘어나지 않는다. */
    private long expiresAt(long now) {
        return now + proposalTtlSeconds * 1000;
    }

    /**
     * 알림 받을 사람들. 파티에 이미 있던 사람 + 방금 들어온 나.
     * {@code memberIds} 는 들어가기 전 목록이라 나를 따로 더한다.
     */
    private List<String> recipients(CreateMatchRequestCommand command, List<String> memberIds) {
        List<String> recipients = new ArrayList<>(memberIds);
        recipients.add(command.getUserId());
        return recipients;
    }

    /**
     * 새 파티를 만들고 들어간다. 색인 끝을 지나는 start 를 주면 후보가 잡히지 않아
     * 스크립트가 새로 만드는 분기로 간다. 락을 쥐고 있어 색인 크기는 그동안 변하지 않는다.
     */
    private void createNewParty(CreateMatchRequestCommand command, PubgModeConfig config,
                                List<String> scriptKeys, String newPartyId, long now) {
        Long candidateCount = redis.opsForZSet()
                .zCard(keys.needsKey(command));
        long pastTheEnd = candidateCount == null ? 0 : candidateCount;

        List<Object> result = execute(redis, pubgCreateOrCheckPartyUntieredScript, scriptKeys,
                createOrCheckArgs(command, config, newPartyId, now, pastTheEnd));
        long code = code(result);

        log.debug("후보 {}개가 전부 차단이라 새 파티를 만든다 code={} partyId={} userId={}",
                MAX_CANDIDATE_SCAN, code, newPartyId, command.getUserId());

        if (code != CREATED_NEW_PARTY) {
            log.warn("새 파티를 만들지 못했다 code={} userId={}", code, command.getUserId());
        }
        else
        {
            pushPublisher.publishAll(List.of(command.getUserId()),
                    PushEventType.MATCH_QUEUE_UPDATED,
                    Map.of("memberNumber", 1));
        }
    }

}
