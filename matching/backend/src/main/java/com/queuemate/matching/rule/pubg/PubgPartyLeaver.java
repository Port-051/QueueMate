package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.notification.PushEventType;
import com.queuemate.matching.notification.PushPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.queuemate.matching.rule.ScriptSupport.*;

/**
 * PUBG 의 매칭 요청 취소. 자기 인자를 조립하고, 스크립트를 부르고, 결과를 읽는다.
 *
 * <p>배정은 티어 유무로 두 클래스지만 취소는 하나다. {@code leave-party.lua} 한 벌이
 * 티어를 보지 않는 모드를 빈 접미사로 접어 같은 루프로 처리하기 때문이다.
 *
 * <p>LoL 과 다른 점: 포지션과 중복 금지가 없어 needs 줄이 내 플랫폼 <b>하나</b>(KEYS[4])다.
 * 그래서 keyValue 목록을 넘기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PubgPartyLeaver {

    private final StringRedisTemplate redis;
    private final PubgPartyKeys keys;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> pubgLeavePartyScript;

    private final PushPublisher pushPublisher;

    /**
     * 티어를 보지 않는 모드가 ARGV[5] 로 넘기는 값. 스크립트는 이 값일 때 칸 키에
     * 티어 접미사를 붙이지 않는다. 실제 티어 이름과 겹치면 안 된다
     */
    private static final String NO_TIER = "NONE";

    /** leave-party.lua 반환 코드. 0 은 활성 요청이 없다는 뜻이라 따로 두지 않는다 */
    private static final long LEFT_PARTY = 1;
    private static final long LEFT_AND_PARTY_CLOSED = 2;
    private static final long STALE_REQUEST_ID = -1;

    /**
     * 매칭 요청을 취소하고 파티에서 뺀다.
     *
     * <p>배정과 달리 후보 풀 락을 잡지 않는다. 색인을 훑지 않고 내 파티가 있던 칸만
     * 되돌리며, 그 전부가 스크립트 한 덩어리 안에서 일어난다.
     *
     * @param active            Redis 에 저장돼 있던 활성 요청
     * @param expectedRequestId 클라이언트가 아는 요청 id. 저장된 값과 같을 때만 지운다
     */
    public CancelResult leave(ActiveRequest active, String expectedRequestId) {
        boolean tiered = tiered(active);

        List<Object> result = execute(redis, pubgLeavePartyScript,
                scriptKeys(active),
                leaveArgs(active, expectedRequestId, tiered));

        CancelResult cancelResult = toCancelResult(result);
        if (cancelResult == CancelResult.CANCELLED) {
            notifyRemainingMembers(result);
        }

        log.debug("leave result={} tiered={} userId={} partyId={}",
                cancelResult, tiered, active.userId(), active.partyId());
        return cancelResult;
    }

    /**
     * 티어를 보는 모드인가.
     *
     * <p><b>{@code null} 만 보면 안 된다.</b> 요청에 tier 가 없던 값은 Redis HASH 를 거쳐
     * 돌아올 때 null 이 아니라 빈 문자열이다. 그래서 둘 다 본다.
     */
    private boolean tiered(ActiveRequest active) {
        return active.tier() != null && !active.tier().isBlank();
    }

    /** leave-party.lua 의 KEYS. KEYS[4] 는 티어 접미사가 없는 needs 키다 — 접미사는 스크립트가 붙인다. */
    private List<String> scriptKeys(ActiveRequest active) {
        List<String> scriptKeys = new ArrayList<>();
        scriptKeys.add(keys.activeRequestKey(active.userId()));                     // KEYS[1]
        // 아직 파티에 배정되기 전일 수 있다. 그때는 빈 문자열을 넘기고 Lua 가 무시한다
        scriptKeys.add(active.hasParty() ? keys.partyKey(active.partyId()) : "");   // KEYS[2]
        scriptKeys.add(keys.tierKey());                                             // KEYS[3] 티어 사다리
        scriptKeys.add(keys.needsKey(active));                                      // KEYS[4] 내 플랫폼 줄
        return scriptKeys;
    }

    /**
     * leave-party.lua 인자.
     *
     * <p>ARGV[3]·ARGV[4] 는 스크립트가 읽지 않는다. LoL 판에서 온 자리라 배치만 맞춰 둔다 —
     * 배그에는 중복 금지가 없으므로 uniqueness 는 늘 {@code "false"} 다.
     */
    private List<String> leaveArgs(ActiveRequest active, String expectedRequestId, boolean tiered) {
        List<String> args = new ArrayList<>();
        args.add(active.userId());                               // ARGV[1]
        args.add(expectedRequestId);                             // ARGV[2]
        args.add(active.keyValue());                             // ARGV[3] 안 읽음
        args.add("false");                                       // ARGV[4] 안 읽음
        args.add(tiered ? active.tier() : NO_TIER);              // ARGV[5] 내 티어 이름
        return args;
    }

    /**
     * 파티에 남은 사람들에게 인원이 줄었음을 알린다. 취소한 본인은 REST 응답으로 이미 안다.
     *
     * <p>스크립트가 나를 {@code HDEL} 한 뒤 {@code HKEYS} 를 찍으므로 목록에 본인은 없다.
     * {@code memberIds} 에는 결과를 통째로 넘긴다 — 그 함수가 두 번째 칸을 스스로 꺼낸다.
     */
    private void notifyRemainingMembers(List<Object> result) {
        List<String> memberIds = memberIds(result);
        if (memberIds.isEmpty()) {
            return;
        }
        pushPublisher.publishAll(memberIds, PushEventType.MATCH_CANCELLED,
                Map.of("memberNumber", memberIds.size()));
    }

    /** 스크립트 반환값을 {@link CancelResult} 로 옮긴다. 첫 칸(코드)만 본다. */
    private CancelResult toCancelResult(List<Object> result) {
        if (result == null) {
            return CancelResult.NOT_FOUND;
        }
        long code = code(result);
        if (code == LEFT_PARTY) {
            return CancelResult.CANCELLED;
        }
        if (code == LEFT_AND_PARTY_CLOSED) {
            return CancelResult.CANCELLED_AND_PARTY_CLOSED;
        }
        if (code == STALE_REQUEST_ID) {
            return CancelResult.REQUEST_MISMATCH;
        }
        return CancelResult.NOT_FOUND;
    }
}
