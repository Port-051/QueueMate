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
 * PUBG 의 티어를 보는 모드(랭크)의 배정. 자기 인자를 조립하고, 스크립트를 부르고, 결과를 읽는다.
 *
 * <p>{@link PubgUntieredAssigner} 와 다른 점은 색인이 내 플랫폼 줄의 <b>티어 칸</b>이라는 것과,
 * 그 칸을 고르려고 ARGV[7] 에 내 티어 이름을 싣는다는 것뿐이다. ARGV[1..6] 은 같은 배치다.
 * 포지션이 없어 LoL 처럼 (포지션 x 티어) 격자가 아니라 티어 한 줄이다.
 *
 * <p><b>티어 규칙을 해석하는 자리는 여기가 아니라 Lua 다.</b> 스크립트가 {@code tier-range} 표와
 * 티어 사다리({@code qm:gameconfig:PUBG:tier})를 직접 읽는다. 자바는 키만 넘긴다.
 *
 * <p>호출부({@link PubgCandidateRule#canJoin})가 후보 풀 락을 쥔 채로 {@link #assign} 을 부른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubgTieredAssigner {

    private final StringRedisTemplate redis;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> pubgCreateOrCheckPartyTieredScript;
    @SuppressWarnings("rawtypes")
    private final RedisScript<List> pubgJoinPartyTieredScript;
    private final PubgPartyKeys keys;
    private final PushPublisher pushPublisher;

    /**
     * 제안의 시한(초). 정원이 차는 순간 {@code expiresAt = now + ttl} 로 환산해
     * 파티 HASH 에 적는다 — 그 뒤로는 늘어나지 않는다(join-party-tiered.lua 의 HSETNX).
     */
    @Value("${queuemate.proposal.ttl-seconds}")
    private long proposalTtlSeconds;

    /**
     * 들어갈 파티를 정한다. <b>후보 풀 락을 쥔 채로 실행된다.</b>
     *
     * <p>순회 전체가 한 락 안에 있어야 한다. 회차마다 락을 놓으면 그 사이 다른 요청이
     * 색인(ZSET)을 바꿔 같은 인덱스가 다른 파티를 가리킨다. 최근 거절 상대는 호출부가 이미 읽었다.
     * 차단(INV-6)은 합류 스크립트가 같은 원자 실행 안에서 본다(docs/11 D-57) — 걸리면 {@code BLOCKED} 가 온다.
     */
    public void assign(CreateMatchRequestCommand command, PubgModeConfig config, Set<String> declinedUserIds) {
        String newPartyId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        List<String> scriptKeys = scriptKeys(command, newPartyId);
        List<String> joinKeys = joinKeys(scriptKeys, command);

        for (int start = 0; start < MAX_CANDIDATE_SCAN; start++) {
            List<Object> found = execute(redis, pubgCreateOrCheckPartyTieredScript, scriptKeys,
                    createOrCheckArgs(command, config, newPartyId, now, start));
            long code = code(found);

            // 1 = 후보가 없어 새로 만들고 들어갔다 / 2 = 후보를 찾았다 (멤버 목록을 받는다)
            // 음수는 배정하지 않고 끝낸다는 뜻이다.
            //   -1 = tier-range 표에 내 티어 줄이 없거나 SOLO_ONLY 이거나 사다리에 없는 티어다
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

    /** 두 스크립트가 같은 KEYS 를 쓴다. join 쪽은 KEYS[1] 과 KEYS[3] 을 보지 않는다. 합류 쪽은 끝에 내 차단 집합 키가 하나 더 붙는다({@link #joinKeys}). */
    private List<String> scriptKeys(CreateMatchRequestCommand command, String newPartyId) {
        List<String> scriptKeys = new ArrayList<>();
        scriptKeys.add(keys.partyKey(newPartyId));                  // KEYS[1]
        scriptKeys.add(keys.activeRequestKey(command.getUserId())); // KEYS[2]
        scriptKeys.add(keys.tierRangeKey(command));                 // KEYS[3] 티어 범위 표
        scriptKeys.add(keys.tierKey());                             // KEYS[4] 티어 사다리
        scriptKeys.add(keys.needsKey(command));                     // KEYS[5] 티어 접미사 없는 내 플랫폼 줄
        return scriptKeys;
    }

    /** create-or-check-party-tiered.lua 인자. ARGV[1] 은 후보가 없을 때 만들 파티 id 다. */
    private List<String> createOrCheckArgs(CreateMatchRequestCommand command, PubgModeConfig config,
                                           String newPartyId, long now, long start) {
        List<String> args = new ArrayList<>();
        args.add(newPartyId);                               // ARGV[1]
        args.add(String.valueOf(now));                      // ARGV[2]
        args.add(config.targetPartySize());                 // ARGV[3]
        args.add(SharedKeys.PARTY_PREFIX);               // ARGV[4]
        args.add(command.getUserId());                      // ARGV[5]
        args.add(String.valueOf(start));                    // ARGV[6] 내 칸의 몇 번째 후보를 볼지
        args.add(command.getTier());                        // ARGV[7] 내 티어 이름
        return args;
    }

    /**
     * 찾아 둔 후보 파티에 들어간다. 합류 스크립트의 반환 코드를 돌려준다 — {@code BLOCKED}(-3)면 차단 관계라 들어가지 않았고
     * 호출부가 다음 후보를 본다. 그 밖의 값이면 이 후보로 배정이 끝났다(들어갔거나, 음수라 그만뒀다).
     */
    private long joinParty(CreateMatchRequestCommand command, PubgModeConfig config,
                           List<String> joinKeys, String partyId, long now, List<String> memberIds) {
        List<Object> result = execute(redis, pubgJoinPartyTieredScript, joinKeys,
                joinArgs(command, config, partyId, now));
        long code = code(result);

        // 음수면 들어가지 못했다. 아래는 들어간 것을 전제로 크기를 읽으므로 여기서 끝낸다
        //   -3 = 차단 관계 (BLOCKED — 아무것도 쓰지 않았다. 호출부가 다음 후보를 본다)
        //   -1 = 파티에 tierLo/tierHi 가 없다 (찾은 뒤 파티가 사라졌다)
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
     * join-party-tiered.lua 인자. create 판과 자리를 맞추되 ARGV[1] 이 "들어갈 파티 id",
     * ARGV[6] 이 start 대신 제안 시한이다.
     */
    private List<String> joinArgs(CreateMatchRequestCommand command, PubgModeConfig config,
                                  String partyId, long now) {
        List<String> args = new ArrayList<>();
        args.add(partyId);                                  // ARGV[1]
        args.add(String.valueOf(now));                      // ARGV[2]
        args.add(config.targetPartySize());                 // ARGV[3]
        args.add(SharedKeys.PARTY_PREFIX);               // ARGV[4]
        args.add(command.getUserId());                      // ARGV[5]
        args.add(String.valueOf(expiresAt(now)));           // ARGV[6] 제안 시한
        args.add(command.getTier());                        // ARGV[7] 안 읽지만 배치를 맞춘다
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
     * 새 파티를 만들고 들어간다. 내 티어 칸의 끝을 지나는 start 를 주면 후보가 잡히지 않아
     * 스크립트가 새로 만드는 분기로 간다. 락을 쥐고 있어 칸 크기는 그동안 변하지 않는다.
     */
    private void createNewParty(CreateMatchRequestCommand command, PubgModeConfig config,
                                List<String> scriptKeys, String newPartyId, long now) {
        Long candidateCount = redis.opsForZSet()
                .zCard(keys.needsKey(command, command.getTier()));
        long pastTheEnd = candidateCount == null ? 0 : candidateCount;

        List<Object> result = execute(redis, pubgCreateOrCheckPartyTieredScript, scriptKeys,
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
}
