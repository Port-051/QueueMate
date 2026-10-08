package com.queuemate.matching.rule.valorant;

import java.util.List;

/**
 * gameconfig 에서 읽은, 이번 배정에 필요한 값들.
 *
 * <p>LoL 판과 같은 모양이다. 발로란트는 지금 모든 모드가 역할군 중복을 금지하지만, 그것을
 * 코드에 굳히지 않고 <b>설정({@code positionUniqueness})으로 둔다</b> — 게임 규칙이 역할군 중복을
 * 막는 것이 아니라 우리가 제품으로 정한 것이라, 같은 역할군끼리 모이는 모드를 나중에 열 수 있다.
 * 그때 고칠 곳이 자바가 아니라 {@code seed/gameconfig.redis} 한 곳이어야 한다.
 *
 * <p>{@code keyValues} 는 색인 대상 역할군 목록이다. 중복을 금지하면 4개 전부이고, 허용하면
 * 내 역할군 하나뿐이다 — 아직 필요한 자리가 그것뿐이기 때문이다.
 *
 * <p>{@code tierRule} 은 자바가 해석하지 않는다. 티어 유무는 요청의 tier 가 있는지로 갈리고,
 * 표를 읽어 범위를 정하는 것은 Lua 다. 여기 담아 두는 것은 로그·디버깅용이다.
 */
public record ValorantModeConfig(String targetPartySize, boolean unique, List<String> keyValues, String tierRule) {
}
