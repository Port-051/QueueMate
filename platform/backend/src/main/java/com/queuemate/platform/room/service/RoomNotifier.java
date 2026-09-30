package com.queuemate.platform.room.service;

import com.queuemate.platform.common.push.PushEventType;
import com.queuemate.platform.common.push.PushPublisher;
import com.queuemate.platform.party.board.BoardSignalPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 방 안의 일이 내는 알림 둘 — 개인 알림({@code ROOM_*} · {@code WEBRTC_SIGNAL})과 게시판 채널 신호({@code BOARD_CHANGED}).
 * 발행은 이 앱의 것({@link PushPublisher} · {@link BoardSignalPublisher})을 그대로 쓴다 — 봉투도 채널 이름도 한 벌이다.
 * 2026-09-25 에 {@code room} 앱을 합치며 그쪽의 {@code notification/PushPublisher} 를 이것으로 바꿨다.
 *
 * <p><b>여기만 따로 있는 이유 — 받는 사람의 {@code userId} 가 문자열로 온다.</b> Lua 스크립트가 돌려주는 멤버와 방 키의 값은 전부 문자열이고
 * {@link PushPublisher} 는 사용자 번호({@code Long})를 받는다. 이 앱의 요청으로 방에 들어온 사람은 전부 사용자 번호라 못 파는 값이 있을 수 없지만,
 * 누가 Redis 에 손으로 넣은 값이 섞여도 <b>이미 성립한 입장 · 나가기를 500 으로 뒤집지 않도록</b> 건너뛰고 WARN 을 남긴다 —
 * 게시판이 멤버 SET 을 읽을 때({@code room.domain.RoomMemberIds})와 같은 처리다.
 *
 * <p>방 안의 일에는 트랜잭션이 없다 — 그래서 두 발행기 모두 부르는 그 자리에서 곧바로 발행한다. 어떤 예외도 밖으로 내보내지 않는 것도 두 발행기 그대로다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoomNotifier {

    private final PushPublisher pushPublisher;
    private final BoardSignalPublisher boardSignalPublisher;

    /** 한 사람에게 보낸다 */
    public void toUser(String userId, PushEventType type, Map<String, Object> payload)
    {
        try
        {
            pushPublisher.publishAfterCommit(Long.parseLong(userId), type, payload);
        }
        catch(NumberFormatException e)
        {
            // 값 자체는 남기지 않는다 — 무엇이 들어 있을지 모른다
            log.warn("사용자 번호가 아닌 userId 에게는 알림을 보내지 않는다 type={}", type);
        }
    }

    /** 여러 사람에게 같은 알림을 보낸다. 한 방은 최대 5명이라 왕복도 최대 5번이다 */
    public void toEach(Collection<String> userIds, PushEventType type, Map<String, Object> payload)
    {
        userIds.forEach(userId -> toUser(userId, type, payload));
    }

    /** 방의 인원이 바뀌었다 — 게시판을 보고 있는 사람들에게 "다시 받아라" 신호. {@code payload} 는 {@code {}} 다 */
    public void boardChanged()
    {
        boardSignalPublisher.changed();
    }
}
