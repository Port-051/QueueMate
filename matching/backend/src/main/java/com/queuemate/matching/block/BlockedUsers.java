package com.queuemate.matching.block;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 배정 경로가 쓰는 "나와 차단 관계인 사람" — {@code Long} 인 차단 테이블과 문자열인 이 앱의 {@code userId} 를 잇는 자리.
 *
 * <p><b>왜 문자열로 되돌리나.</b> 차단 테이블의 두 칸은 사용자 번호({@code bigint})지만, 이 앱은 {@code userId} 를
 * 요청 파라미터 · Redis 키 · 파티 HASH 의 {@code member:{userId}} 에서 전부 숫자 문자열({@code "42"})로 다룬다
 * ({@code platform} 의 P-11 — 그쪽이 숫자를 십진 문자열로 준다). {@code ScriptSupport#blockedWith} 가
 * 파티 HASH 에서 잘라 낸 문자열과 {@code contains} 로 비교하므로 <b>이 집합도 문자열이어야 한다</b> —
 * {@code Long} 인 채로 넘기면 컴파일도 테스트도 통과한 채 차단이 조용히 안 걸린다.
 *
 * <p><b>숫자가 아닌 {@code userId}.</b> {@code platform} 의 access 토큰이 붙기 전의 임시 식별({@code ?userId=})로
 * 아무 문자열이나 올 수 있다. 그런 사용자는 차단 테이블에 있을 수 없으므로 빈 집합으로 다루고 WARN 한 줄만 남긴다 —
 * 요청을 실패시키지 않는다.
 */
@Slf4j
public final class BlockedUsers {

    private BlockedUsers() {
    }

    /** {@code userId} 와 차단 관계(방향 무관)인 상대의 {@code userId} 를 십진 문자열로. */
    public static Set<String> of(BlockRepository blockRepository, String userId) {
        long id;
        try {
            id = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            log.warn("userId 가 사용자 번호가 아니라 차단 조회를 건너뛴다 userId={}", userId);
            return Set.of();
        }
        return blockRepository.findBlockedUserIds(id).stream()
                .map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
