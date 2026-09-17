package com.queuemate.matching.domain.condition;

/**
 * 플레이 목적. 사용자가 요청에 실어 보내는 매칭 조건 4개 중 4번 줄이고, 세 게임 모두 같다.
 *
 * <p>매칭 색인 키의 한 조각이므로(qm:party:open:...:{purpose}:needs:...) 값이 다르면
 * 애초에 같은 후보 풀에 들어오지 않는다.
 */
public enum PlayPurpose {
    RANK_UP, NORMAL, FUN
}
