package com.queuemate.platform.party.dto;

import com.queuemate.platform.account.domain.Game;

/**
 * 게시판 방 먼저 합류 요청({@link AutoJoinRequest})의 {@code keyCondition.type} — <b>값의 이름은 {@code matching} 의 {@code domain.condition.KeyConditionType} 과 같다</b>
 * (프런트가 객체 하나를 두 요청에 그대로 보낸다 — 2026-09-28 소유자 결정 · P-28). 게임당 하나다.
 */
public enum KeyConditionType {

    /** LoL — 값은 {@code TOP} · {@code JUNGLE} · {@code MID} · {@code ADC} · {@code SUPPORT} · {@code NONE}(포지션 개념이 없는 모드) */
    POSITION(Game.LOL),

    /** VALORANT — 값은 {@code DUELIST} · {@code INITIATOR} · {@code CONTROLLER} · {@code SENTINEL}(글의 {@code wantedPositions} 와 같은 이름 체계다) */
    ROLE(Game.VALORANT),

    /** PUBG — 값은 {@code STEAM} · {@code KAKAO}. 게시판 방 먼저 합류는 이 값을 <b>보지 않는다</b>(방장의 {@code server} 와 대조하지 않는다 — 미정) */
    PLATFORM(Game.PUBG);

    private final Game game;

    KeyConditionType(Game game)
    {
        this.game = game;
    }

    public Game game()
    {
        return game;
    }

    /** {@code matching} 과 같은 대문자 이름만 받는다 */
    public static KeyConditionType fromName(String name)
    {
        for(KeyConditionType type : values())
        {
            if(type.name().equals(name))
            {
                return type;
            }
        }
        return null;
    }
}
