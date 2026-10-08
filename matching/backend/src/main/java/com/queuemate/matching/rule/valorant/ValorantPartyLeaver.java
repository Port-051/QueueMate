package com.queuemate.matching.rule.valorant;

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
 * VALORANT 의 매칭 요청 취소. 자기 인자를 조립하고, 스크립트를 부르고, 결과를 읽는다.
 *
 * <p>배정은 티어 유무로 두 클래스지만 <b>취소는 하나다.</b> {@code leave-party.lua} 한 벌이
 * 티어를 보지 않는 모드를 빈 접미사로 접어 같은 루프로 처리하기 때문이다. 여기서 정할 것은
 * ARGV[5](내 티어 이름 또는 {@code "NONE"})뿐이라 억지로 둘로 쪼갤 이유가 없다.
 *
 * <p>LoL 판과 마찬가지로 되돌릴 칸을 고르려고 모드 설정의 {@code positionUniqueness} 를 읽는다.
 * 지금은 모든 모드가 역할군 중복을 금지하지만 그것을 코드에 굳히지 않는다 — 이유는
 * {@link ValorantModeConfig} 주석에 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValorantPartyLeaver {

    private final StringRedisTemplate redis;
    private final ValorantPartyKeys keys;

    @SuppressWarnings("rawtypes")
    private final RedisScript<List> valorantLeavePartyScript;

    private final PushPublisher pushPublisher;

    /**
     * 티어를 보지 않는 모드가 ARGV[5] 로 넘기는 값. 스크립트는 이 값일 때 칸 키에 티어 접미사를
     * 붙이지 않는다. 그래서 {@code qm:gameconfig:VALORANT:tier} 사다리에 이 이름의 티어를 넣으면 안 된다
     */
    private static final String NO_TIER = "NONE";

    /**
     * 이 모드가 역할군 중복을 금지하나(ARGV[4]). gameconfig 는 Redis 에서 읽기만 한다 (CLAUDE.md §3).
     *
     * <p>배정 쪽 {@code loadModeConfig} 와 같은 값을 봐야 한다 — 되돌릴 자리는 등록할 때 쓴 자리와
     * 짝이다. 지금은 모든 모드가 {@code true} 지만 설정으로 두는 이유는 {@link ValorantModeConfig} 주석에 있다.
     */
    private boolean uniqueness(ActiveRequest active) {
        return "true".equals(redis.<String, String>opsForHash()
                .get(keys.gameConfigKey(active.modeKey()), "positionUniqueness"));
    }

    /** 색인을 되돌릴 후보 역할군 목록. 중복을 금지하면 4개 전부, 허용하면 내 값 하나다. */
    private List<String> keyValues(ActiveRequest active, boolean unique) {
        return unique ? ValorantPartyKeys.ROLES : List.of(active.keyValue());
    }

    /** leave-party.lua 반환 코드. 0 은 활성 요청이 없다는 뜻이라 따로 두지 않는다 */
    private static final long LEFT_PARTY = 1;
    private static final long LEFT_AND_PARTY_CLOSED = 2;
    private static final long STALE_REQUEST_ID = -1;

    /**
     * 매칭 요청을 취소하고 파티에서 뺀다.
     *
     * <p>배정과 달리 후보 풀 락을 잡지 않는다. 색인을 훑지 않고 내 파티가 있던 칸만 되돌리며,
     * 그 전부가 스크립트 한 덩어리 안에서 일어난다.
     *
     * @param active            Redis 에 저장돼 있던 활성 요청
     * @param expectedRequestId 클라이언트가 아는 요청 id. 저장된 값과 같을 때만 지운다
     */
    public CancelResult leave(ActiveRequest active, String expectedRequestId) {
        boolean tiered = tiered(active);
        boolean unique = uniqueness(active);
        List<String> keyValues = keyValues(active, unique);

        List<Object> result = execute(redis, valorantLeavePartyScript,
                scriptKeys(active, keyValues),
                leaveArgs(active, expectedRequestId, keyValues, unique, tiered));

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
     * <p><b>{@code null} 만 보면 안 된다.</b> 요청에 tier 가 없던 값은 Redis HASH 를 거쳐 돌아올 때
     * null 이 아니라 <b>빈 문자열</b>이다. 그래서 둘 다 본다. {@code "NONE"} 은 어디에도 저장되지
     * 않으므로 그 문자열과 비교하지 마라.
     */
    private boolean tiered(ActiveRequest active) {
        return active.tier() != null && !active.tier().isBlank();
    }

    /**
     * leave-party.lua 의 KEYS.
     *
     * <p>KEYS[5] 는 역할군도 티어도 없는 <b>밑동</b>, KEYS[6..] 은 역할군이 붙되 <b>티어 접미사가
     * 없는</b> needs 키다. 칸 하나를 가리키는 키는 스크립트가 {@code ':' .. 티어이름} 으로 조립한다 —
     * 배정 스크립트와 같은 규칙이다. 격자를 통째로 넘기지 않는 이유는 칸이 (역할군 4 x 티어 25) =
     * 100 개라 호출마다 그만큼을 넘겨야 하기 때문이다.
     *
     * <p>그래서 티어 범위 표(KEYS[3])와 사다리(KEYS[4])도 같이 넘긴다. 이 파티가 어느 칸에 올라가
     * 있었는지는 파티 HASH 의 {@code tierLo}/{@code tierHi} 에 <b>순번</b>으로 적혀 있고, 남은 사람
     * 기준으로 범위를 되돌리려면 표와 사다리가 필요하다.
     *
     * <p>키 문자열은 {@link ValorantPartyKeys} 가 만든다. 배정이 색인에 올려 둔 바로 그 키여야
     * 하기 때문이다 — 조립을 여기서 되풀이하면 한쪽만 바뀌어도 눈치채지 못한다.
     */
    private List<String> scriptKeys(ActiveRequest active, List<String> keyValues) {
        List<String> scriptKeys = new ArrayList<>();
        scriptKeys.add(keys.activeRequestKey(active.userId()));                     // KEYS[1]
        // 아직 파티에 배정되기 전일 수 있다. 그때는 빈 문자열을 넘기고 Lua 가 무시한다
        scriptKeys.add(active.hasParty() ? keys.partyKey(active.partyId()) : "");   // KEYS[2]
        scriptKeys.add(keys.tierRangeKey(active.modeKey()));                        // KEYS[3] 티어 범위 표
        scriptKeys.add(keys.tierKey());                                             // KEYS[4] 티어 사다리
        scriptKeys.add(keys.needsBaseKey(active));                                  // KEYS[5] needs 밑동
        keyValues.forEach(role -> scriptKeys.add(keys.needsKey(active, role)));     // KEYS[6..]
        return scriptKeys;
    }

    /**
     * leave-party.lua 인자.
     *
     * <p>ARGV[5] 는 <b>티어 이름</b>이다. 쓰레기 값이 아니라 스크립트가 보는 값이다 —
     * {@code "NONE"} 이면 칸 키에 접미사를 붙이지 않아 티어를 보지 않는 모드의 색인 모양이 된다.
     *
     * <p>ARGV[6..] 의 역할군 목록 순서는 KEYS[6..] 의 순서와 같아야 한다. 둘 다 같은
     * {@code keyValues} 하나에서 나오므로 어긋날 자리가 없다.
     */
    private List<String> leaveArgs(ActiveRequest active, String expectedRequestId,
                                   List<String> keyValues, boolean unique, boolean tiered) {
        List<String> args = new ArrayList<>();
        args.add(active.userId());                               // ARGV[1]
        args.add(expectedRequestId);                             // ARGV[2]
        args.add(active.keyValue());                             // ARGV[3] 내 역할군
        args.add(String.valueOf(unique));                        // ARGV[4]
        args.add(tiered ? active.tier() : NO_TIER);              // ARGV[5] 내 티어 이름
        args.addAll(keyValues);                                  // ARGV[6..]
        return args;
    }

    /**
     * 파티에 남은 사람들에게 인원이 줄었음을 알린다.
     *
     * <p><b>취소한 본인에게는 보내지 않는다.</b> 본인은 REST 응답으로 결과를 이미 안다. 남은
     * 사람들은 그럴 통로가 없으므로 이 알림이 유일하다. 목록에 본인이 없는 것은 스크립트가
     * {@code HDEL} 로 나를 먼저 뺀 뒤 {@code HKEYS} 를 찍기 때문이다.
     *
     * <p>{@code memberIds} 에는 스크립트 결과를 <b>통째로</b> 넘긴다. 그 함수가 자기 안에서 두 번째
     * 칸을 꺼내므로, 여기서 미리 꺼내 넘기면 목록의 두 번째 원소를 다시 목록으로 읽으려다 터진다.
     */
    private void notifyRemainingMembers(List<Object> result) {
        List<String> memberIds = memberIds(result);
        if (memberIds.isEmpty()) {
            return;
        }
        pushPublisher.publishAll(memberIds, PushEventType.MATCH_CANCELLED,
                Map.of("memberNumber", memberIds.size()));
    }

    /**
     * 스크립트 반환값을 {@link CancelResult} 로 옮긴다. 첫 칸(코드)만 본다.
     *
     * <p>{@code null} 은 스크립트가 아무것도 돌려주지 않은 경우다. 취소할 것이 없었던 것으로 본다 —
     * 없는 것을 지우려 한 요청을 실패로 올리지 않는다.
     */
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
