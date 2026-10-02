package com.queuemate.matching.domain.condition;

/**
 * 게임마다 이름만 다른 핵심 조건(keyValue)의 종류. 게임당 하나다.
 * 사용자가 요청에 실어 보내는 매칭 조건 4개 중 2번 줄이라 {@code domain.condition} 에 있다.
 *
 * <p><b>PUBG 는 플레이 스타일이 아니라 플랫폼이다.</b> 처음에는 {@code PLAY_STYLE} 이었으나
 * 바꿨다. 스팀과 카카오는 서버가 분리되어 있어 <b>서로 파티 자체를 맺을 수 없다</b>
 * (카카오게임즈 공식 FAQ: "스팀 및 기타 타 플랫폼 이용자와 게임 진행이 불가능합니다").
 * 플랫폼을 조건에 넣지 않으면 게임에 같이 들어갈 수 없는 파티가 만들어진다. 반면 플레이
 * 스타일은 취향이라 어긋나도 게임은 된다. 조건은 게임당 4개로 고정이므로(CLAUDE.md §2)
 * 추가가 아니라 교체다.
 */
public enum KeyConditionType {
    POSITION,    // LoL
    ROLE,        // VALORANT
    PLATFORM     // PUBG — STEAM / KAKAO
}
