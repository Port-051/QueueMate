package com.queuemate.platform.room;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 방의 설정값. {@code application.yaml} 의 {@code platform.room.*} 이고 환경변수로 바꿀 수 있다.
 * 환경변수 이름({@code ROOM_TTL_SECONDS})은 {@code room} 앱이던 때의 것 그대로다 — 계약이다({@code contracts/platform-api.md} "방").
 *
 * @param ttlSeconds 방 키(방장 키 · 멤버 HASH · 찾는 포지션 SET)와 입장 표시 키의 수명(초). 접속 확인이 올 때마다 이 값으로 다시 건다.
 *                   브라우저는 이보다 훨씬 자주(1분마다) 신호를 보낸다 — 신호 몇 번을 놓쳐도 쫓겨나지 않게 하려는 것이다.
 *                   길수록 말없이 사라진 방장의 방이 목록에 오래 남는다(목록은 방장 키가 사라진 것을 보고서야 글을 만료시킨다)
 */
@ConfigurationProperties(prefix = "platform.room")
public record RoomProperties(@DefaultValue("600") int ttlSeconds) {
}
