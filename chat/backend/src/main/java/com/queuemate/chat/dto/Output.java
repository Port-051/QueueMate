package com.queuemate.chat.dto;

import com.queuemate.chat.domain.Type;

import java.time.Instant;

/**
 * 방 전체로 나가는 봉투(CLAUDE.md §3.1 — 2026-10-11 소유자 결정). {@code PUBLISH} 되어 모든 인스턴스의 리스너가 그대로 소켓에 쓴다.
 *
 * @param type   {@link Type#MSG}
 * @param roomId 방 — 글 번호 또는 파티 UUID(문자열)
 * @param from   보낸 사람의 사용자 번호 — <b>세션 attributes 의 userId</b>(핸드셰이크에서 토큰으로 확인한 값). 브라우저가 보낸 값은 쓰지 않는다
 * @param nick   표시 이름 — 브라우저가 실어 보낸 것 그대로(서버는 길이만 본다). 없으면 null
 * @param text   본문(trim 뒤 · 500자 이하)
 * @param cid    브라우저가 만든 메시지 번호 — 보낸 사람이 자기 MSG 를 알아보는 열쇠
 * @param sentAt 서버가 받은 시각(UTC)
 */
public record Output(Type type, String roomId, String from, String nick, String text, String cid, Instant sentAt) {
}
