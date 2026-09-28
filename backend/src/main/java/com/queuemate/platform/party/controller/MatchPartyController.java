package com.queuemate.platform.party.controller;

import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.party.dto.MatchRoomResponse;
import com.queuemate.platform.party.service.MatchPartyService;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.MatchRoomResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.regex.Pattern;

/**
 * 자동 매칭 파티 — 확정된 파티의 방을 만들거나 들어간다(2026-09-27 소유자 결정 — docs/11 D-42. 경로 · 응답 · 에러 코드는 Claude 가 정한 세부다 —
 * {@code contracts/platform-api.md} "자동 매칭 파티"). {@code matching} 이 {@code MATCH_CONFIRMED {partyId}} 를 보내면 프런트가 이것을 부른다.
 *
 * <p><b>"나"는 access 토큰에서 온다</b>({@link CurrentUserId}). 파티원인지는 {@code matching} 의 파티 HASH 로 가른다 — 남의 partyId 로는 방에 못 들어간다.
 *
 * <p><b>{@code partyId} 는 UUID 모양이어야 한다</b> — 아니면 그런 파티가 있을 수 없어 404 {@code MATCH_PARTY_NOT_FOUND} 다(400 이 아니다 — 없는 파티와 같은 답이다.
 * 방의 요청이 숫자가 아닌 {@code roomId} 를 404 로 답하는 것과 같다). Redis 에 묻기 전에 거른다 — 아무 문자열로 {@code qm:party:} 아래를 더듬지 못하게.
 */
@RestController
@RequestMapping("/api/v1/match-parties")
@RequiredArgsConstructor
public class MatchPartyController {

    /** {@code matching} 의 partyId 는 {@code UUID.randomUUID().toString()} 이다 — 36자, 16진수와 하이픈 */
    private static final Pattern PARTY_ID = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final MatchPartyService matchPartyService;

    /**
     * 방을 만들거나(첫 사람 — 201) 들어간다(나머지 — 200). 이미 들어와 있으면 200 이다 — 재시도 · 새로고침이다(파티 HASH 가 600초로 사라진 뒤에도 — 방 키가 원본이다). 응답의 {@code roomId} 는 {@code partyId} 와 같다.
     *
     * <p>거절 — 404 {@code MATCH_PARTY_NOT_FOUND}(확정된 파티가 없다 — 아직 제안 중 · 600초가 지나 사라짐(방에 아직 없는 사람) · 없는 id · UUID 가 아닌 경로) ·
     * 403 {@code NOT_PARTY_MEMBER} · 409 {@code ROOM_FULL} · 409 {@code IN_OTHER_ROOM} · 503 {@code ROOM_STATE_UNAVAILABLE}.
     * 404 · 403 은 서비스가 HASH 를 읽고 던지지만, 그 뒤 스크립트가 같은 것을 다시 볼 수 있어(그 사이 HASH 가 사라졌다) 아래 {@code switch} 에도 있다.
     */
    @PostMapping("/{partyId}/room")
    public ResponseEntity<MatchRoomResponse> enterRoom(@CurrentUserId Long userId, @PathVariable String partyId)
    {
        if(!PARTY_ID.matcher(partyId).matches())
        {
            throw MatchPartyService.matchPartyNotFound();
        }
        MatchRoomResult result = matchPartyService.enterRoom(userId, partyId);
        MatchRoomResponse body = new MatchRoomResponse(partyId);

        return switch (result) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(body);
            // 있던 방에 들어왔거나 이미 들어와 있다 — 둘 다 새로 만든 것은 없다
            case ENTERED, ALREADY_IN_ROOM -> ResponseEntity.ok(body);
            case ROOM_FULL -> throw RoomErrors.roomFull();
            case IN_OTHER_ROOM -> throw RoomErrors.inOtherRoom();
            case PARTY_NOT_FOUND -> throw MatchPartyService.matchPartyNotFound();
            case NOT_PARTY_MEMBER -> throw MatchPartyService.notPartyMember();
        };
    }
}
