package com.queuemate.matching.domain.condition;

/**
 * 플레이 목적. 사용자가 요청에 실어 보내는 매칭 조건 4개 중 4번 줄이고, 세 게임 모두 같다.
 *
 * <ul>
 *   <li>{@code RANK_UP} — 랭크 상승</li>
 *   <li>{@code TRYHARD} — 빡겜. 진지하게(빡세게) 한다</li>
 *   <li>{@code FUN} — 즐겜</li>
 * </ul>
 *
 * <p><b>{@code TRYHARD} 는 옛 {@code NORMAL}(일반 플레이)을 바꾼 것이다</b> (2026-09-29, docs/11 D-49 — 소유자 결정).
 * 이름만이 아니라 뜻도 바뀌었다 — "평범하게 한다" 가 아니라 "진지하게 한다" 다. 화면의 "일반 플레이" 는
 * 랭크 상승 · 즐겜과 무엇이 다른지가 흐렸다. 값 이름 {@code TRYHARD} 는 Claude 가 정했다(소유자 검토).
 *
 * <p>매칭 색인 키의 한 조각이므로(qm:party:open:...:{purpose}:needs:...) 값이 다르면
 * 애초에 같은 후보 풀에 들어오지 않는다. 키에는 {@link #name()} 이 그대로 들어간다 —
 * 그래서 이름을 바꾸면 배포 순간 Redis 에 옛 이름으로 들어가 있던 대기 요청 · 색인과 이어지지 않는다(D-49 "감수하는 것").
 */
public enum PlayPurpose {
    RANK_UP, TRYHARD, FUN
}
