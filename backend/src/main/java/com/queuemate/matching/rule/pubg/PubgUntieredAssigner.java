package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.notification.PushEventType;
import com.queuemate.matching.notification.PushPublisher;
import com.queuemate.matching.rule.ScriptSupport;
import com.queuemate.matching.rule.lol.LolModeConfig;
import com.queuemate.matching.rule.lol.LolPartyKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.queuemate.matching.rule.ScriptSupport.*;


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


    public void assign(CreateMatchRequestCommand command, PubgModeConfig config, Set<String> blockedUserIds)
    {
        String newPartyId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        List<String> scriptKeys = scriptKeys(command, config, newPartyId);

        for (int start = 0; start < MAX_CANDIDATE_SCAN; start++)
        {
            List<Object> found = redis.execute(pubgCreateOrCheckPartyUntieredScript, scriptKeys,
                                                createOrCheckArgs(command, config, newPartyId, now, start));

            long code = code(found);

            // 1 = 후보가 없어 새로 만들고 들어갔다 / -1 = 설정과 맞지 않는 keyValue
            // 음수는 배정하지 않고 끝낸다는 뜻이다.
            //   -1 = 설정과 맞지 않는 keyValue
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
    }

    private List<String> scriptKeys(CreateMatchRequestCommand command, PubgModeConfig config, String newPartyId) {
        List<String> scriptKeys = new ArrayList<>();
        scriptKeys.add(keys.partyKey(newPartyId));                                  // KEYS[1]
        scriptKeys.add(keys.activeRequestKey(command.getUserId()));                 // KEYS[2]
        scriptKeys.add(keys.needsKey(command)); // KEYS[3..]
        return scriptKeys;
    }

    private List<String> createOrCheckArgs(CreateMatchRequestCommand command, PubgModeConfig config,
                                           String newPartyId, long now, int start)
    {

        List<String> args = new ArrayList<>();
        args.add(newPartyId);
        args.add(String.valueOf(now));
        args.add(config.targetPartySize());
        args.add(PubgPartyKeys.PARTY_PREFIX);
        args.add(command.getUserId());
        args.add(String.valueOf(start));
        return args;
    }

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

    private List<String> joinArgs(CreateMatchRequestCommand command, PubgModeConfig config,
                                  String partyId, long now)
    {

    }
}
