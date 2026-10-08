package com.queuemate.matching.rule.valorant;

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
 * VALORANT 의 티어를 보는 모드(경쟁전)의 배정. 자기 인자를 조립하고, 스크립트를 부르고,
 * 결과를 읽는다.
 *
 * <p>{@link ValorantUntieredAssigner} 와 다른 점은 색인이 (역할군 x 티어) 격자라는 것, 그 격자를
 * 다루려고 티어 범위 표(KEYS[3])·사다리(KEYS[4])·needs 밑동(KEYS[5])을 넘긴다는 것, ARGV[9] 에
 * 내 티어 이름을 싣는다는 것이다. 그래서 역할군 목록은 ARGV[10..] 부터다.
 *
 * <p><b>티어 규칙을 해석하는 자리는 여기가 아니라 Lua 다.</b> 발로란트 규칙은 "파티 최고 티어 <=
 * 한계(파티 최저 티어)" 인데, 트리오는 줄 하나로 표현되지 않아 합류할 때마다 파티 범위를 좁혀야
 * 한다. 그 좁히기가 join 스크립트 안에 있고, 자바는 표와 사다리의 <b>키만</b> 넘긴다. 규칙이 바뀌어도
 * 고칠 곳이 Redis 데이터 한 곳으로 모인다.
 *
 * <p>같은 이유로 <b>격자를 KEYS 로 통째로 넘기지 않는다.</b> 칸이 (역할군 4 x 티어 25) = 100 개라
 * 호출마다 그만큼을 넘겨야 하기 때문이다. 대신 티어 접미사가 없는 needs 키와 밑동을 넘기고 칸 키는
 * 스크립트가 {@code ':' .. 티어이름} 으로 조립한다 — KEYS 가 {@code 5 + 역할군 개수} 로 고정된다.
 *
 * <p>호출부({@link ValorantCandidateRule#canJoin})가 후보 풀 락을 쥔 채로 {@link #assign} 을 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValorantTieredAssigner {

    private final StringRedisTemplate redis;
    private final ValorantPartyKeys keys;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> valorantCreateOrCheckPartyTieredScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> valorantJoinPartyTieredScript;
    private final PushPublisher pushPublisher;

    /**
     * 제안의 시한(초). 정원이 차는 순간 {@code expiresAt = now + ttl} 로 환산해 파티 HASH 에
     * 적는다 — 그 뒤로는 늘어나지 않는다(join-party-tiered.lua 의 HSETNX).
     *
     * <p>생성자 주입이 아니라 필드 주입인 것은 Lombok 의 {@code @RequiredArgsConstructor} 가
     * {@code @Value} 를 생성자 파라미터로 옮겨 주지 않기 때문이다.
     */
    @Value("${queuemate.proposal.ttl-seconds}")
    private long proposalTtlSeconds;

    /**
     * 들어갈 파티를 정한다. <b>후보 풀 락을 쥔 채로 실행된다.</b>
     *
     * <p>순회 전체가 한 락 안에 있어야 한다. 회차마다 락을 놓으면 그 사이 다른 요청이 색인
     * (ZSET)을 바꿔 같은 인덱스가 다른 파티를 가리킨다. 최근 거절 상대는 호출부가 이미 읽었다.
     * 차단(INV-6)은 합류 스크립트가 같은 원자 실행 안에서 본다(docs/11 D-57) — 걸리면 {@code BLOCKED} 가 온다.
     */
    public void assign(CreateMatchRequestCommand command, ValorantModeConfig config,
                       Set<String> declinedUserIds) {
        String newPartyId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        List<String> scriptKeys = scriptKeys(command, config, newPartyId);
        List<String> joinKeys = joinKeys(scriptKeys, command);

        for (int start = 0; start < MAX_CANDIDATE_SCAN; start++) {
            List<Object> found = execute(redis, valorantCreateOrCheckPartyTieredScript, scriptKeys,
                    createOrCheckArgs(command, config, newPartyId, now, start));
            long code = code(found);

            // 음수는 배정하지 않고 끝낸다는 뜻이다.
            //   -1 = 내 역할군이 목록에 없거나, tier-range 표에 내 티어 줄이 없거나,
            //        SOLO_ONLY 이거나, 사다리에 없는 티어다
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
            if (declinedWith(memberIds, declinedUserIds)) {
                continue;   // 최근 거절한(거절당한) 상대가 있다(docs/11 D-45). 다음 후보를 본다
            }
            if (joinParty(command, config, joinKeys, partyId, now, memberIds) == BLOCKED) {
                continue;   // 차단 관계다 — 합류 스크립트가 아무것도 쓰지 않고 거절했다(INV-6, D-57). 다음 후보를 본다
            }
            return;
        }

        // 상한까지 봤는데 전부 차단 · 거절 상대였다. 여기서 그냥 끝내면 이 사용자는 파티도 없고
        // 색인에도 안 올라간 채 활성 요청만 남아 영영 매칭되지 않는다.
        createNewParty(command, config, scriptKeys, newPartyId, now);
    }

    /**
     * 찾아 둔 후보 파티에 들어간다. 합류 스크립트의 반환 코드를 돌려준다 — {@code BLOCKED}(-3)면 차단 관계라
     * 들어가지 않았고 호출부가 다음 후보를 본다. 그 밖의 값이면 이 후보로 배정이 끝났다(들어갔거나, 음수라 그만뒀다).
     *
     * <p>후보로 잡혔다는 것이 곧 파티 전원과 같이 갈 수 있다는 뜻이다 — 색인의 칸이 늘 "지금 이
     * 파티에 들어올 수 있는 티어"와 같게 join 이 범위를 좁혀 두기 때문이다. 그래서 여기서 티어를
     * 다시 확인하지 않는다.
     */
    private long joinParty(CreateMatchRequestCommand command, ValorantModeConfig config,
                           List<String> joinKeys, String partyId, long now,
                           List<String> memberIds) {
        List<Object> result = execute(redis, valorantJoinPartyTieredScript, joinKeys,
                joinArgs(command, config, partyId, now));
        long code = code(result);

        // 음수면 들어가지 못했다. 아래는 들어간 것을 전제로 크기를 읽으므로 여기서 끝낸다
        //   -3 = 차단 관계 (BLOCKED — 아무것도 쓰지 않았다. 호출부가 다음 후보를 본다)
        //   -1 = 파티에 tierLo/tierHi/minTier/maxTier 가 없다 (찾은 뒤 파티가 사라졌다)
        //   -2 = claim 의 TTL 이 먼저 끝났다
        if (code < 0) {
            log.debug("join 거절 code={} partyId={} userId={}", code, partyId, command.getUserId());
            return code;
        }

        // 스크립트가 나를 넣은 뒤 센 값이다. 들어가기 전 목록을 세면 하나 모자란다
        long size = ((Number) result.get(2)).longValue();

        log.debug("join result code={} partyId={} size={} userId={}",
                code, partyId, size, command.getUserId());

        // 나 하나가 아니라 파티 전원이 받아야 한다. 정원이 찬 것도, 인원이 는 것도
        // 기존 파티원에게는 이 알림 말고 알 길이 없다
        List<String> recipients = recipients(command, memberIds);

        if (code == JOINED_AND_FULL) {
            pushPublisher.publishAll(recipients,
                    PushEventType.MATCH_PROPOSAL_CREATED,
                    Map.of("memberNumber", size,
                            "target", Integer.parseInt(config.targetPartySize()),
                            "partyId", partyId));
        } else {
            pushPublisher.publishAll(recipients,
                    PushEventType.MATCH_QUEUE_UPDATED,
                    Map.of("memberNumber", size));
        }
        return code;
    }

    /**
     * 알림 받을 사람들. <b>파티에 이미 있던 사람 + 방금 들어온 나.</b>
     *
     * <p>{@code memberIds} 는 후보를 찾는 스크립트가 돌려준 것이라 내가 들어가기 전 목록이다.
     */
    private List<String> recipients(CreateMatchRequestCommand command, List<String> memberIds) {
        List<String> recipients = new ArrayList<>(memberIds);
        recipients.add(command.getUserId());
        return recipients;
    }

    /**
     * 후보가 전부 차단이었을 때 새 파티를 만든다.
     *
     * <p>스크립트는 후보를 못 찾았을 때만 새로 만든다. 색인 끝을 지나는 start 를 주면 후보가
     * 잡히지 않으므로 그 분기로 간다. 세는 것은 <b>내 역할군 x 내 티어 칸</b>이다 — 스크립트가
     * 후보를 찾는 칸과 같아야 start 가 실제로 끝을 지난다. 락을 쥐고 있어 칸 크기는 변하지 않는다.
     */
    private void createNewParty(CreateMatchRequestCommand command, ValorantModeConfig config,
                                List<String> scriptKeys, String newPartyId, long now) {
        String myCellKey = keys.needsKey(command,
                command.getKeyCondition().getValue(), command.getTier());
        Long candidateCount = redis.opsForZSet().zCard(myCellKey);
        long pastTheEnd = candidateCount == null ? 0 : candidateCount;

        List<Object> result = execute(redis, valorantCreateOrCheckPartyTieredScript, scriptKeys,
                createOrCheckArgs(command, config, newPartyId, now, pastTheEnd));
        long code = code(result);

        log.debug("후보 {}개가 전부 차단 · 거절 상대라 새 파티를 만든다 code={} partyId={} userId={}",
                MAX_CANDIDATE_SCAN, code, newPartyId, command.getUserId());

        if (code != CREATED_NEW_PARTY) {
            log.warn("새 파티를 만들지 못했다 code={} userId={}", code, command.getUserId());
        } else {
            pushPublisher.publishAll(List.of(command.getUserId()),
                    PushEventType.MATCH_QUEUE_UPDATED,
                    Map.of("memberNumber", 1));
        }
    }

    /**
     * 합류 스크립트의 KEYS — {@link #scriptKeys} 끝에 <b>내 차단 집합</b> {@code qm:user:block-rel:{나}} 를 하나 더 붙인다
     * (INV-6, docs/11 D-57). 합류 Lua 가 {@code KEYS[#KEYS]} 로 읽어 파티원마다 {@code SISMEMBER} 하고, 걸리면 아무것도
     * 쓰지 않고 {@code BLOCKED}(-3)를 돌려준다. 찾기 스크립트에는 넘기지 않는다 — 혼자 새 파티를 만드는 자리라 견줄 상대가 없다.
     * 끝에 붙이므로 앞자리(KEYS[1..]) 배치는 찾기 스크립트와 그대로 같다.
     */
    private List<String> joinKeys(List<String> scriptKeys, CreateMatchRequestCommand command) {
        List<String> joinKeys = new ArrayList<>(scriptKeys);
        joinKeys.add(SharedKeys.blockRelKey(command.getUserId()));   // KEYS[#KEYS] 내 차단 집합 (app:platform 이 쓴다)
        return joinKeys;
    }

    /**
     * 두 스크립트가 같은 KEYS 를 쓴다. join 쪽은 KEYS[1] 을 보지 않는다. 합류 쪽은 끝에 내 차단 집합 키가 하나 더 붙는다({@link #joinKeys}).
     *
     * <p>KEYS[5] 는 <b>역할군도 티어도 없는 밑동</b>이다. join 이 파티 범위를 좁힐 때, 자기가 받은
     * 역할군 키 목록이 아니라 파티의 needs-roles SET 에 남은 역할군으로 칸을 다시 만들어야 해서
     * 조립 재료가 따로 필요하다. LoL·PUBG 에는 없는 자리다.
     *
     * <p>KEYS[6..] 의 개수는 {@link ValorantModeConfig#keyValues()} 가 정한다 — 역할군 중복을
     * 금지하는 모드면 4개 전부, 허용하는 모드면 내 역할군 하나다. ARGV 끝에 싣는 역할군 목록도
     * 같은 것에서 나오므로 순서와 개수가 어긋나지 않는다.
     */
    private List<String> scriptKeys(CreateMatchRequestCommand command, ValorantModeConfig config,
                                    String newPartyId) {
        List<String> scriptKeys = new ArrayList<>();
        scriptKeys.add(keys.partyKey(newPartyId));                  // KEYS[1]
        scriptKeys.add(keys.activeRequestKey(command.getUserId())); // KEYS[2]
        scriptKeys.add(keys.tierRangeKey(command));                 // KEYS[3] 티어 범위 표
        scriptKeys.add(keys.tierKey());                             // KEYS[4] 티어 사다리
        scriptKeys.add(keys.needsBaseKey(command));                 // KEYS[5] needs 밑동
        config.keyValues().forEach(role -> scriptKeys.add(keys.needsKey(command, role))); // KEYS[6..]
        return scriptKeys;
    }

    /** create-or-check-party-tiered.lua 인자. ARGV[1] 은 후보가 없을 때 만들 파티 id 다. */
    private List<String> createOrCheckArgs(CreateMatchRequestCommand command, ValorantModeConfig config,
                                           String newPartyId, long now, long start) {
        List<String> args = new ArrayList<>();
        args.add(newPartyId);                               // ARGV[1]
        args.add(String.valueOf(now));                      // ARGV[2]
        args.add(command.getKeyCondition().getValue());     // ARGV[3] 내 역할군
        args.add(config.targetPartySize());                 // ARGV[4]
        args.add(String.valueOf(config.unique()));          // ARGV[5]
        args.add(SharedKeys.PARTY_PREFIX);           // ARGV[6]
        args.add(command.getUserId());                      // ARGV[7]
        args.add(String.valueOf(start));                    // ARGV[8] 색인의 몇 번째부터 볼지
        args.add(command.getTier());                        // ARGV[9] 내 티어 이름
        args.addAll(config.keyValues());                    // ARGV[10..]
        return args;
    }

    /**
     * join-party-tiered.lua 인자. ARGV[1] 이 "들어갈 파티 id" 라는 점이 위와 다르다.
     *
     * <p>ARGV[8] 은 후보를 찾는 쪽의 start 자리인데 이쪽은 후보를 찾지 않는다. 비워 둘 수 없어서
     * (역할군 개수를 {@code #ARGV - 9} 로 센다) 채우던 자리이므로 그 칸에 {@code expiresAt} 을 싣는다.
     *
     * <p><b>티어 자리(ARGV[9])는 이쪽에서도 읽힌다.</b> LoL·PUBG 의 join 은 파티에 적힌 범위만
     * 보고 내 티어를 무시하지만, 발로란트는 합류할 때마다 파티 범위를 "지금 범위 x 내 줄"의
     * 교집합으로 좁히므로 내 줄을 읽을 티어 이름이 필요하다.
     */
    private List<String> joinArgs(CreateMatchRequestCommand command, ValorantModeConfig config,
                                  String partyId, long now) {
        List<String> args = new ArrayList<>();
        args.add(partyId);                                  // ARGV[1]
        args.add(String.valueOf(now));                      // ARGV[2]
        args.add(command.getKeyCondition().getValue());     // ARGV[3] 내 역할군
        args.add(config.targetPartySize());                 // ARGV[4]
        args.add(String.valueOf(config.unique()));          // ARGV[5]
        args.add(SharedKeys.PARTY_PREFIX);           // ARGV[6]
        args.add(command.getUserId());                      // ARGV[7]
        args.add(String.valueOf(expiresAt(now)));           // ARGV[8] 제안 시한
        args.add(command.getTier());                        // ARGV[9] 내 티어 이름
        args.addAll(config.keyValues());                    // ARGV[10..]
        return args;
    }

    /** 이번 합류로 정원이 찰 경우 제안이 만료될 시각. 스크립트가 다시 계산하지 않아 재시도로 늘어나지 않는다. */
    private long expiresAt(long now) {
        return now + proposalTtlSeconds * 1000;
    }
}
