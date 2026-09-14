package com.queuemate.matching.rule.lol;

import java.util.List;

/**
 * gameconfig 에서 읽은, 이번 배정에 필요한 값들.
 *
 * <p>티어를 보는 배정과 보지 않는 배정이 같은 값을 쓰므로 어느 한쪽에 두지 않는다.
 */
public record ModeConfig(String targetPartySize, boolean unique, List<String> keyValues,
                         String tierRule) {
}
