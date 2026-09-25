package com.queuemate.platform.room.domain;

/**
 * 강퇴 시도의 결과. 컨트롤러가 이 값으로 응답 코드를 정한다.
 *
 * <p>{@link #code} 는 {@code lua/kick-room.lua} 의 반환값과 짝이다. <b>한쪽을 고치면 다른 쪽도 고친다</b> —
 * 어긋나도 컴파일은 통과하고, 모르는 값이 오면 {@link #fromCode(Long)} 가 예외로 알린다.
 */
public enum KickResult {

    /** 강퇴했다. 대상이 멤버 SET 에서 빠지고, 대상의 입장 표시 키가 이 방을 가리키고 있었다면 지워졌다 */
    KICKED(1),

    /** 대상이 이 방의 멤버가 아니다. 이미 나갔거나 들어온 적이 없다 — 아무것도 지우지 않았다 */
    TARGET_NOT_IN_ROOM(-3),

    /** 그런 방이 없다. 만들어진 적이 없거나, 방장이 나가서(사라져서) 없어졌다 */
    ROOM_NOT_FOUND(-4),

    /**
     * 부른 사람이 방장이 아니다. 이 방의 멤버여도, 방 밖의 사람이어도, 다른 방의 방장이어도 마찬가지다.
     *
     * <p>방장인지는 스크립트가 방장 키와 비교해서 안다 — 방장은 계정의 권한이 아니라 방마다 다른 Redis 의 상태다.
     */
    NOT_HOST(-5),

    /** 방장이 자기 자신을 강퇴하려 했다. 방장이 나가려면 나가기를 쓴다 — 그러면 방이 닫힌다 */
    CANNOT_KICK_SELF(-6);

    private final long code;

    KickResult(long code)
    {
        this.code = code;
    }

    /**
     * 스크립트의 반환값을 결과로 바꾼다. 모르는 값(그리고 {@code null})을 성공으로 읽지 않는다.
     */
    public static KickResult fromCode(Long code)
    {
        for (KickResult result : values())
        {
            if (code != null && result.code == code)
            {
                return result;
            }
        }
        throw new IllegalStateException("kick-room.lua 가 모르는 값을 돌려줬다: " + code);
    }
}
