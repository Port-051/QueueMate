package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.dto.UserGameProfile;
import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.party.dto.MatchPartyMembersResponse;
import com.queuemate.platform.party.match.MatchParty;
import com.queuemate.platform.party.match.MatchPartyReader;
import com.queuemate.platform.party.match.MatchPartyRoster;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.MatchRoomResult;
import com.queuemate.platform.room.domain.RoomStateUnavailableException;
import com.queuemate.platform.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>자동 매칭 파티의 방 만들기 · 들어가기</b>(2026-09-27 소유자 결정 — docs/11 D-42). {@code matching} 은 확정 뒤 SQS 를 보내지 않고 파티 HASH 를 Redis 에
 * 600초 남긴다. 프런트가 {@code MATCH_CONFIRMED {partyId}} 를 받고 {@code POST /api/v1/match-parties/{partyId}/room} 을 부르면 이 앱이 그 HASH 를 읽어
 * 파티를 DB 에 적고 방을 만들거나(첫 사람) 들어간다(나머지). 요청의 경로 · 순서 · 에러 코드는 Claude 가 정한 세부다.
 * <b>퀵 매칭 파티의 팀원 카드</b>({@link #members} — 2026-10-01 소유자 결정)도 여기 있다 — 같은 파티 HASH 를 읽지만 제안 중인 파티도 보고 아무것도 쓰지 않는다.
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
    private final GameProfileReader gameProfileReader;

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

    /**
     * <b>퀵 매칭 파티의 팀원 카드</b>(2026-10-01 소유자 결정 — {@code GET /api/v1/match-parties/{partyId}/members}). 게시판 카드 수준의 정보(닉네임 · 고른 포지션 ·
     * 그 게임의 게임 프로필)를 파티원 전원에게 준다. <b>읽기만 한다</b> — 파티 HASH 에도 DB 에도 쓰지 않는다.
     *
     * <p><b>자격 — 공개 조회가 되면 안 된다.</b> ① 파티 HASH 가 <b>제안 중이거나 확정</b>이면({@link MatchPartyReader#findRoster}) 거기에 {@code member:{나}} 가
     * 있어야 한다 — 팀원은 HASH 의 {@code member:*} 전원이고 포지션은 그 값이다. ② HASH 가 없으면(확정 600초 뒤 수명으로 사라졌다) DB 의 기록
     * ({@code parties.match_party_id} · {@code party_members})에 내가 있어야 한다 — 팀원은 {@code party_members}(가입한 사용자만)이고 포지션은 {@code null} 이다.
     * ③ 둘 다 아니면 — 그 파티가 (HASH 나 DB 에) 있으면 403 {@code NOT_PARTY_MEMBER}, 없으면 404 {@code MATCH_PARTY_NOT_FOUND}(방 만들기와 같은 코드).
     * <b>Redis 를 못 읽으면 DB 로 보고, DB 에도 없으면 503 {@code ROOM_STATE_UNAVAILABLE}</b> — 못 읽은 것을 "없는 파티" 로 읽지 않는다(fail-closed).
     *
     * <p><b>게임</b> — 서버가 아는 게임(확정 HASH 의 {@code game} · DB 의 {@code parties.game})이 있으면 그것을 쓰고 쿼리는 보지 않는다. 제안 중인 HASH 에는
     * {@code game} 이 없어(확정 뒤에 적힌다) 그때만 쿼리 {@code game} 이 필수다 — 없거나 모르는 이름이면 400 {@code VALIDATION_FAILED} {@code "game: …"}.
     * 자격(403)을 게임(400)보다 먼저 본다 — 남의 파티에 대고 "게임을 달라" 고 답하지 않는다.
     *
     * <p><b>차단은 거르지 않는다</b>(소유자 — "이미 같은 파티원인데 안 보여 주기도 뭐 하니까"). 게시판 카드와 다른 점이다.
     *
     * @param gameParam 쿼리 {@code ?game=} 글자 그대로 — 서버가 게임을 모를 때만 본다
     * @throws ApiException 403 {@code NOT_PARTY_MEMBER} · 404 {@code MATCH_PARTY_NOT_FOUND} · 400 {@code VALIDATION_FAILED} · 503 {@code ROOM_STATE_UNAVAILABLE}
     */
    public MatchPartyMembersResponse members(Long me, String partyId, String gameParam)
    {
        MatchPartyRoster roster = null;
        boolean redisUnreadable = false;
        try
        {
            roster = matchPartyReader.findRoster(partyId).orElse(null);
        }
        catch(RoomStateUnavailableException e)
        {
            redisUnreadable = true;
        }
        if(roster != null)
        {
            if(!roster.members().containsKey(me))
            {
                throw notPartyMember();
            }
            Game game = roster.game() != null ? roster.game() : requiredGame(gameParam);
            return cards(partyId, game, roster.members());
        }
        MatchPartyStore.Recorded recorded = matchPartyStore.findRecorded(partyId).orElse(null);
        if(recorded != null)
        {
            if(!recorded.memberIds().contains(me))
            {
                throw notPartyMember();
            }
            // 포지션은 DB 에 적지 않았다 — HASH 가 사라진 뒤에는 보여 줄 값이 없다
            Map<Long, String> members = new LinkedHashMap<>();
            recorded.memberIds().forEach(id -> members.put(id, null));
            return cards(partyId, recorded.game(), members);
        }
        if(redisUnreadable)
        {
            throw RoomErrors.stateUnavailable();
        }
        throw matchPartyNotFound();
    }

    /** 쿼리의 게임 — 제안 중인 파티라 서버가 게임을 모를 때만 부른다. 없으면 · 모르는 이름이면 400 이다 */
    private static Game requiredGame(String gameParam)
    {
        if(gameParam == null || gameParam.isBlank())
        {
            throw ApiException.validationFailed("game", "필요합니다");
        }
        return PostValidation.game(gameParam);
    }

    /**
     * 팀원 카드 — 프로필은 게시판이 쓰는 창구 그대로({@code GameProfileReader#findProfiles} — 쿼리 한 번). 가입하지 않은 번호도 빼지 않고
     * {@code nickname} · {@code profile} 을 {@code null} 로 남긴다(게시판 카드와 같다). 순서는 닉네임순(없는 사람은 뒤로) → 사용자 번호순이다.
     *
     * @param members 사용자 번호 → 고른 값(HASH 의 {@code member:*} 값 · DB 로 답할 때는 {@code null})
     */
    private MatchPartyMembersResponse cards(String partyId, Game game, Map<Long, String> members)
    {
        Map<Long, UserGameProfile> profiles = gameProfileReader.findProfiles(members.keySet(), game);
        List<MatchPartyMembersResponse.Member> cards = members.entrySet().stream()
                .map(member -> {
                    UserGameProfile found = profiles.get(member.getKey());
                    return new MatchPartyMembersResponse.Member(member.getKey(), found == null ? null : found.nickname(),
                            positionOf(game, member.getValue()), found == null ? null : found.profile());
                })
                .sorted(MEMBER_ORDER)
                .toList();
        return new MatchPartyMembersResponse(partyId, cards);
    }

    /**
     * HASH 의 값을 포지션으로 — <b>그 게임의 포지션 이름일 때만</b>이다({@code Game#positions()} — {@code matching} 의 {@code LolPosition} · {@code ValorantRole} 과
     * 같은 이름이다). 포지션이 없는 모드의 {@code NONE}, PUBG 의 자리 채움({@code EXIST}), 빈 값은 {@code null} 이다
     */
    private static String positionOf(Game game, String value)
    {
        return (value != null && game.positions().contains(value)) ? value : null;
    }

    /** 닉네임순(없는 사람은 뒤로) → 사용자 번호순 — 게시판 카드({@code PostService} 의 순서)에서 방장 먼저를 뺀 것이다 */
    private static final Comparator<MatchPartyMembersResponse.Member> MEMBER_ORDER = Comparator
            .comparing(MatchPartyMembersResponse.Member::nickname, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MatchPartyMembersResponse.Member::userId);

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
