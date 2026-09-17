package com.queuemate.matching.rule.valorant;

import com.queuemate.matching.domain.ActiveRequest;
import com.queuemate.matching.domain.GameKey;
import com.queuemate.matching.domain.valorant.ValorantRole;
import com.queuemate.matching.dto.CreateMatchRequestCommand;
import com.queuemate.matching.redisKeys.SharedKeys;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * VALORANT 배정이 쓰는 Redis 키를 조립한다.
 *
 * <p>키 조립을 한 자리에 모은 이유는 <b>배정 경로와 취소 경로가 같은 키를 만들어야</b> 하기
 * 때문이다. 취소는 배정이 색인에 올려 둔 바로 그 키에서 파티를 빼거나 되돌리는데, 조립 규칙이
 * 두 클래스로 흩어지면 한쪽만 고쳐도 컴파일은 통과하고 그 순간부터 비운 자리를 아무도 찾지 못한다.
 *
 * <p>요청 DTO({@link CreateMatchRequestCommand})와 저장된 활성 요청({@link ActiveRequest})
 * 두 벌이 있는 것도 같은 이유다. 취소는 들어온 값이 아니라 Redis 에 저장된 값을 근거로 움직이므로
 * 출처가 다를 뿐, 나오는 문자열은 같아야 한다.
 */
@Component
public class ValorantPartyKeys {

    /** 이 클래스가 조립하는 것은 발로란트의 needs 색인과 gameconfig 뿐이다. 나머지는 {@link SharedKeys} */
    private static final GameKey GAME = GameKey.VALORANT;

    /**
     * 역할군 <b>전체</b> 목록. 역할군 중복을 금지하는 모드가 색인 대상으로 쓴다.
     *
     * <p>이것을 쓸지 내 역할군 하나만 쓸지는 LoL 과 똑같이 모드 설정의
     * {@code positionUniqueness} 가 정한다 ({@link ValorantModeConfig} 주석). 지금은 모든 모드가 중복을 금지하지만 그것은 게임 규칙이 아니라 우리가 정한 제품
     * 규칙이라, 같은 역할군끼리 모이는 모드를 나중에 열 수 있다. 그때 고칠 곳이 자바가 아니라
     * {@code seed/gameconfig.redis} 한 곳이어야 한다.
     *
     * <p><b>순서가 곧 스크립트의 역할군 순번이다.</b> KEYS 의 역할군별 needs 키 순서와 ARGV 끝에
     * 싣는 역할군 목록 순서가 같아야 Lua 가 {@code KEYS[5 + p]} 로 칸을 찾는다. 그래서 배정·취소가
     * 전부 같은 목록 하나에서 KEYS 와 ARGV 를 함께 만든다.
     */
    public static final List<String> ROLES =
            Arrays.stream(ValorantRole.values()).map(Enum::name).toList();

    /** 잠글 후보 풀. needs 키에서 역할군만 뺀 조합이어야 같은 색인을 잠근다 (PoolLock 참고). */
    public String poolKey(CreateMatchRequestCommand command) {
        return SharedKeys.poolKey(GAME, command.getModeKey(),
                command.getVoicePreference().name(),
                command.getPlayPurpose().name());
    }

    /** 취소 경로의 후보 풀 키. 배정 쪽과 같은 문자열이어야 한다. */
    public String poolKey(ActiveRequest active) {
        return SharedKeys.poolKey(GAME, active.modeKey(),
                active.voicePreference().name(),
                active.playPurpose().name());
    }

    /**
     * 역할군도 티어도 없는 needs <b>밑동</b>.
     *
     * <p>티어 스크립트가 KEYS[5] 로 받아 {@code 밑동 .. ':' .. 역할군 .. ':' .. 티어이름} 으로
     * 칸을 조립한다. 이미 만들어진 파티의 범위를 좁힐 때 자기가 받은 KEYS 에 없는 역할군의 칸까지
     * 다시 만들어야 하기 때문에, 조립 재료가 되는 이 키가 따로 필요하다.
     */
    public String needsBaseKey(CreateMatchRequestCommand command) {
        return SharedKeys.needsBaseKey(poolKey(command));
    }

    /** 취소 경로의 needs 밑동. */
    public String needsBaseKey(ActiveRequest active) {
        return SharedKeys.needsBaseKey(poolKey(active));
    }

    /**
     * 그 역할군을 아직 못 채운 파티 목록. <b>티어 접미사는 없다</b> — 티어 모드에서는
     * Lua 가 {@code ':' .. 티어이름} 을 붙여 칸을 만든다.
     */
    public String needsKey(CreateMatchRequestCommand command, String role) {
        return SharedKeys.needsKey(poolKey(command), role);
    }

    /** 취소 경로에서 쓰는 needs 키. 조립 규칙은 배정 쪽과 같아야 한다. */
    public String needsKey(ActiveRequest active, String role) {
        return SharedKeys.needsKey(poolKey(active), role);
    }

    /**
     * 티어 격자의 한 칸을 가리키는 needs 키. <b>자바에서 칸 크기를 셀 때만 쓴다</b> —
     * 스크립트에는 접미사 없는 키를 넘긴다.
     *
     * <p>접미사 규칙이 Lua 와 어긋나면 세는 칸과 실제 칸이 달라져, 새 파티를 만들려고 준
     * start 가 색인 끝을 지나지 못한다. 그래서 규칙을 문자열 상수 한 자리로 모았다.
     *
     * @param tierName 티어 사다리({@code qm:gameconfig:VALORANT:tier})에 있는 티어 이름
     */
    public String needsKey(CreateMatchRequestCommand command, String role, String tierName) {
        return SharedKeys.withTier(needsKey(command, role), tierName);
    }

    public String partyKey(String partyId) {
        return SharedKeys.partyKey(partyId);
    }

    public String activeRequestKey(String userId) {
        return SharedKeys.activeRequestKey(userId);
    }

    public String gameConfigKey(CreateMatchRequestCommand command) {
        return gameConfigKey(command.getModeKey());
    }

    public String gameConfigKey(String modeKey) {
        return SharedKeys.gameConfigKey(GAME, modeKey);
    }

    /** 모드별 티어 범위 표. HASH, 필드 = 티어 이름, 값 = "최저:최고" 또는 SOLO_ONLY. */
    public String tierRangeKey(CreateMatchRequestCommand command) {
        return tierRangeKey(command.getModeKey());
    }

    public String tierRangeKey(String modeKey) {
        return SharedKeys.tierRangeKey(GAME, modeKey);
    }

    /** 티어 사다리. ZSET, score = 단계 번호. */
    public String tierKey() {
        return SharedKeys.tierKey(GAME);
    }
}
