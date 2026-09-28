package com.queuemate.platform.party.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.party.match.MatchParty;
import com.queuemate.platform.party.match.MatchPartyReader;
import com.queuemate.platform.room.domain.MatchRoomResult;
import com.queuemate.platform.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * <b>자동 매칭 파티의 방 만들기 · 들어가기</b>(2026-09-27 소유자 결정 — docs/11 D-42). {@code matching} 은 확정 뒤 SQS 를 보내지 않고 파티 HASH 를 Redis 에
 * 600초 남긴다. 프런트가 {@code MATCH_CONFIRMED {partyId}} 를 받고 {@code POST /api/v1/match-parties/{partyId}/room} 을 부르면 이 앱이 그 HASH 를 읽어
 * 파티를 DB 에 적고 방을 만들거나(첫 사람) 들어간다(나머지). 요청의 경로 · 순서 · 에러 코드는 Claude 가 정한 세부다.
 *
 * <p><b>순서</b> — ① 파티 HASH 를 읽는다({@link MatchPartyReader}: 없거나 확정이 아니면 아래 "HASH 가 없을 때" 로 간다) → ② 내가 파티원인가
 * (아니면 403 {@code NOT_PARTY_MEMBER} — 남의 파티는 "없다"가 아니라 "네 것이 아니다"다. partyId 는 알림으로 받은 사람만 아는 UUID 라 존재를 감출 이유가 없다)
 * → ③ DB 에 파티 · 파티원을 적고 <b>커밋</b>({@link MatchPartyStore#record} — 멱등) → ④ <b>트랜잭션 밖에서</b> 방의 스크립트({@link RoomService#enterMatchRoom}).
 *
 * <p><b>왜 ④ 가 트랜잭션 밖인가.</b> "트랜잭션 안에서 Redis 를 기다리지 않는다"(CLAUDE.md §3.3)에 새 예외를 만들지 않는다 — 글 쓰기 · 방장 확정이 예외인 것은
 * 글과 방이 한쪽만 남으면 안 되기 때문이었다. 여기는 다르다: <b>파티는 이미 확정된 사실</b>이라 DB 의 파티 줄이 방보다 먼저 남아도 옳다(방을 못 만들었다고 파티가
 * 없던 일이 되지 않는다). 스크립트가 실패하면(503) 클라이언트가 다시 부르고, 그때 DB 는 {@code ON CONFLICT DO NOTHING} 으로 지나가며 방만 다시 시도한다.
 * 거꾸로 스크립트는 됐는데 DB 가 실패하는 경우는 순서상 없다.
 *
 * <p>① 과 ④ 가 같은 HASH 를 두 번 읽는다 — ① 은 DB 에 적을 값(게임 · 파티원)을 얻기 위한 것이고 ④ 는 방의 판정(확정인가 · 파티원인가 · 정원)을 <b>쓰기와 한 스크립트에서</b>
 * 하기 위한 것이다. 그 사이에 HASH 가 사라지면 ④ 가 {@link MatchRoomResult#PARTY_NOT_FOUND} 로 답하고 DB 에는 파티 줄이 남는다 — 방이 없는 {@code ACTIVE} 파티다.
 * 확정 뒤 600초 안에 아무도 방을 만들지 못한 경우이고 <b>감수한다</b>(D-42 의 절충 — 닫는 길은 미정).
 *
 * <p><b>HASH 가 없을 때</b>(2026-09-28 — 소유자 지적. "방에 있는 사람의 판정은 방 키로, 방에 없는 사람의 판정만 HASH 로"). HASH 는 수명이 600초이고 방 키는
 * 접속 확인으로 그보다 오래 산다. 그래서 ① 이 비어도 곧바로 404 를 던지지 않고 <b>방의 스크립트를 한 번 태운다</b> — 스크립트는 입장 표시 키를 HASH 보다 먼저
 * 보므로 이미 이 방에 들어와 있는 사람에게는 HASH 없이 {@link MatchRoomResult#ALREADY_IN_ROOM} 을 준다(새로고침이 200 이다). ② · ③ 은 건너뛴다 —
 * 볼 파티원 목록도 DB 에 적을 값도 없다(DB 줄은 그 사람이 처음 들어올 때 이미 적혔다). 그 밖의 결과는 전부 404 {@code MATCH_PARTY_NOT_FOUND} 다 —
 * 방에 없는 파티원은 HASH 로만 자격을 보므로 사라지면 못 들어온다. 스크립트가 {@link MatchRoomResult#IN_OTHER_ROOM} 을 줘도 404 로 둔다: 다른 방에 있으면서
 * 없는 파티를 부른 것이고, 409 로 답하면 "그 파티가 있었는지"와 무관한 사실(내가 다른 방에 있다)만 알려 줄 뿐 이 요청의 답은 "그런 파티가 없다" 다 —
 * 정보를 덜 주는 쪽을 골랐다(Claude 가 정한 세부). 자바가 입장 표시 키를 직접 {@code GET} 하지 않는 것은 방 키를 {@code RoomService} 밖에서 읽지 않기
 * 위해서다(CLAUDE.md §3.3 · §11).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchPartyService {

    static final String MATCH_PARTY_NOT_FOUND = "MATCH_PARTY_NOT_FOUND";
    static final String NOT_PARTY_MEMBER = "NOT_PARTY_MEMBER";

    private final MatchPartyReader matchPartyReader;
    private final MatchPartyStore matchPartyStore;
    private final RoomService roomService;

    /**
     * @param partyId {@code matching} 의 {@code partyId}(UUID 문자열) — 방 번호이기도 하다. 모양은 컨트롤러가 먼저 봤다
     * @return 방의 결과. 거절({@code ROOM_FULL} · {@code IN_OTHER_ROOM} · …)을 어느 상태 코드로 옮길지는 컨트롤러가 정한다
     * @throws ApiException 404 {@code MATCH_PARTY_NOT_FOUND} · 403 {@code NOT_PARTY_MEMBER} · 503 {@code ROOM_STATE_UNAVAILABLE}
     */
    public MatchRoomResult enterRoom(Long me, String partyId)
    {
        MatchParty party = matchPartyReader.findConfirmed(partyId).orElse(null);
        if(party == null)
        {
            return enterRoomWithoutParty(me, partyId);
        }
        if(!party.memberIds().contains(me))
        {
            throw notPartyMember();
        }
        // PostService#now() 와 같은 정밀도(밀리초)로 적는다
        Long recordedPartyId = matchPartyStore.record(party, me, Instant.now().truncatedTo(ChronoUnit.MILLIS));
        MatchRoomResult result = roomService.enterMatchRoom(partyId, String.valueOf(me));
        log.info("자동 매칭 파티 방 matchPartyId={} partyId={} userId={} result={}", partyId, recordedPartyId, me, result);
        return result;
    }

    /**
     * 파티 HASH 가 없을 때 — 이미 이 방에 들어와 있는 사람만 통과한다(클래스 주석 "HASH 가 없을 때"). DB 에는 아무것도 적지 않는다.
     */
    private MatchRoomResult enterRoomWithoutParty(Long me, String partyId)
    {
        MatchRoomResult result = roomService.enterMatchRoom(partyId, String.valueOf(me));
        if(result != MatchRoomResult.ALREADY_IN_ROOM)
        {
            // HASH 가 없으면 스크립트가 방을 만들거나 들여보낼 수 없다(자격을 HASH 로 본다) — 쓰기 없이 거절만 돌아온다
            throw matchPartyNotFound();
        }
        log.info("자동 매칭 파티 방 — 파티 HASH 없이 이미 들어와 있음 partyId={} userId={}", partyId, me);
        return result;
    }

    /** 이 요청에만 나오는 거절이라 {@code room.RoomErrors} 가 아니라 여기 있다 — 컨트롤러({@code MatchPartyController})도 같은 것을 던진다 */
    public static ApiException matchPartyNotFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, MATCH_PARTY_NOT_FOUND, "확정된 자동 매칭 파티가 없습니다");
    }

    public static ApiException notPartyMember()
    {
        return new ApiException(HttpStatus.FORBIDDEN, NOT_PARTY_MEMBER, "이 파티의 파티원이 아닙니다");
    }
}
