package com.queuemate.matching.block;

import com.queuemate.matching.redisKeys.SharedKeys;
import com.queuemate.matching.rule.ScriptSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 확정 직전 최종 검증 (INV-6 두 번째 겹, docs/11 D-1) — <b>이 파티원들 사이에 차단이 있나</b>.
 *
 * <p><b>왜 선필터만으로 모자란가.</b> 배정 경로의 선필터({@link BlockedUsers} · {@code ScriptSupport#blockedWith})는
 * <b>내가 들어갈 때</b> 후보 파티에 나와 차단 관계인 사람이 있는지만 본다. 그래서 두 가지가 샌다.
 * <ul>
 *   <li>파티가 찬 <b>뒤에</b> 생긴 차단 — 들어갈 때는 없었다.
 *   <li>선필터가 볼 상대가 없던 파티 — 정원 2 인 모드에서 두 번째 사람이 들어갈 때 첫 사람과의 차단은 걸리지만,
 *       첫 사람은 빈 파티를 만든 것이라 아무도 거르지 않았다. 그 사이에 차단이 생기면 위와 같다.
 * </ul>
 * 그래서 수락이 들어올 때마다 파티원 전원의 조합을 DB 에 한 번 더 묻는다 ({@link BlockRepository#findBlocksAmong}).
 *
 * <p><b>여기서는 찾기만 한다.</b> 제안을 깨는 것(양쪽을 큐에서 빼고, 전원에게 알리는 것)은 제안의 생애를 아는
 * {@code ProposalService} 의 몫이다 — 거절 뒤의 취소와 확정 뒤의 알림도 거기 있다. 이 패키지는
 * {@link BlockedUsers} 와 같이 "{@code Long} 인 차단 테이블과 문자열인 이 앱의 {@code userId} 를 잇는 자리"로 둔다.
 *
 * <p><b>PENDING 인 제안만 본다.</b> {@code status} 가 없으면 아직 제안이 아니고(정원 미달), {@code CONFIRMED} 면
 * 이미 확정된 제안에 수락이 재전송된 것이다. 확정은 되돌릴 수 없으므로(INV-5) 그 파티를 여기서 깨면 안 된다 —
 * 수락 스크립트가 {@code ALREADY_RESPONDED} 를 돌려주게 그대로 둔다.
 *
 * <p><b>숫자가 아닌 {@code userId} 는 건너뛴다.</b> {@link BlockedUsers} 와 같은 정책이다 — 차단 테이블에 있을 수
 * 없는 사람이라 WARN 한 줄만 남기고 조회 대상에서 뺀다. 숫자인 사람이 둘 미만이면 물을 조합이 없다.
 *
 * <p><b>DB 실패는 삼키지 않는다.</b> {@code DataAccessException} 이 그대로 올라가 {@code GlobalExceptionHandler} 가
 * 503 {@code MATCHING_UNAVAILABLE} 로 바꾼다. 차단을 확인하지 못한 채 확정하는 길을 두지 않는다 (INV-10 fail-closed).
 * 이 검증은 {@code @Async} 밖, 수락 요청 스레드 위라 사용자가 그 503 을 그대로 받는다 — 재시도하면 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PartyBlockCheck {

    /** {@code join-party*.lua} 가 정원이 찰 때 쓰는 값. 이 값일 때만 제안이 살아 있다 */
    private static final String PENDING = "PENDING";

    private final StringRedisTemplate redis;
    private final BlockRepository blockRepository;

    /**
     * 검증 결과.
     *
     * @param members        그 제안에 있던 파티원 전부 (숫자가 아닌 id 포함). 제안이 깨지면 이들 모두가 알림 대상이다
     * @param blockedUserIds 차단 쌍 어느 한쪽에라도 나온 사람. 비어 있으면 확정해도 된다
     * @param pairCount      발견한 차단 행 수. 로그용이다
     */
    public record Verdict(List<String> members, Set<String> blockedUserIds, int pairCount) {

        /** 차단이 없다 (또는 볼 제안이 아니다) */
        static final Verdict CLEAN = new Verdict(List.of(), Set.of(), 0);

        public boolean blocked() {
            return !blockedUserIds.isEmpty();
        }
    }

    /**
     * 이 제안의 파티원들 사이에 차단이 있는지 본다.
     *
     * @param partyId 제안 id (= partyId)
     * @return 차단 쌍이 있으면 그 양쪽과 파티원 전원. PENDING 제안이 아니거나 차단이 없으면 {@link Verdict#blocked()} 가 false
     * @throws org.springframework.dao.DataAccessException Redis 나 DB 를 못 읽었다. 삼키지 않는다 (INV-10)
     */
    public Verdict check(String partyId) {
        // HKEYS 가 아니라 HGETALL 인 이유: status 값(PENDING 인가)과 member: 필드를 한 왕복으로 읽는다
        Map<String, String> party = redis.<String, String>opsForHash().entries(SharedKeys.partyKey(partyId));
        if (!PENDING.equals(party.get("status"))) {
            return Verdict.CLEAN;
        }

        List<String> members = ScriptSupport.memberIdsFromFields(party.keySet());
        if (members.size() < 2) {
            return Verdict.CLEAN;
        }

        List<Long> ids = numericIds(members);
        if (ids.size() < 2) {
            return Verdict.CLEAN;
        }

        List<Block> blocks = blockRepository.findBlocksAmong(ids);
        if (blocks.isEmpty()) {
            return Verdict.CLEAN;
        }

        // 방향은 의미 없다 — 쌍의 양쪽 다 뺀다. 순서를 지키는 것은 취소 순서를 로그에서 따라가기 위해서다
        Set<String> blocked = new LinkedHashSet<>();
        for (Block block : blocks) {
            blocked.add(String.valueOf(block.getBlockerId()));
            blocked.add(String.valueOf(block.getBlockedId()));
        }
        return new Verdict(members, Collections.unmodifiableSet(blocked), blocks.size());
    }

    /** 숫자인 userId 만 {@code Long} 으로. 아닌 것은 WARN 하고 건너뛴다 ({@link BlockedUsers#of} 와 같은 정책) */
    private static List<Long> numericIds(List<String> members) {
        List<Long> ids = new ArrayList<>(members.size());
        for (String member : members) {
            try {
                ids.add(Long.parseLong(member));
            } catch (NumberFormatException e) {
                log.warn("userId 가 사용자 번호가 아니라 확정 직전 차단 검증에서 건너뛴다 userId={}", member);
            }
        }
        return ids;
    }
}
