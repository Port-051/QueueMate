package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import com.queuemate.platform.common.gameconfig.GameConfigUnavailableException;
import com.queuemate.platform.common.gameconfig.ModeConfig;
import com.queuemate.platform.party.domain.RecruitPost;
import com.queuemate.platform.party.domain.VoicePreference;
import com.queuemate.platform.party.dto.AutoJoinRequest;
import com.queuemate.platform.party.dto.AutoJoinResponse;
import com.queuemate.platform.party.dto.KeyConditionType;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.EnterResult;
import com.queuemate.platform.room.domain.RoomState;
import com.queuemate.platform.room.domain.RoomStateUnavailableException;
import com.queuemate.platform.room.service.RoomMemberService;
import com.queuemate.platform.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>자동 매칭 전에 조건 맞는 게시판 방에 먼저 합류한다</b> — {@code POST /api/v1/posts/auto-join}(2026-09-27 소유자 결정 D-40 의 세부 넷 + 2026-09-28 소유자가 정한
 * 경로 · 본문 · 응답 · 정원. {@code contracts/platform-api.md} "자동 매칭이 게시판 방에 먼저 합류하는 길" · P-28).
 * "매칭 시작" 을 누르면 프런트가 <b>먼저 이것을</b> 부르고, 404 {@code NO_MATCHING_POST} 면 그대로 {@code matching} 의 매칭 요청을 부른다 —
 * <b>서버가 {@code matching} 을 부르지 않고 활성 요청 키를 만들지 않는다</b>(누른 순간 한 번만 게시판을 본다 — D-19 그대로).
 *
 * <p><b>흐름</b> — ① 본문 검증(게임 · 음성 · 조건의 종류와 값 — 400) → ② gameconfig 로 모드 · 티어 규칙 검증({@code matching} 의 validator 와 같은 순서 — 400) →
 * ③ 후보 글 조회(그 게임 · 그 모드 · 그 음성 · 모집 중 · 내 글 제외 · 오래된 순 · 많아야 {@code platform.board.auto-join-scan} 개) →
 * ④ 자바 필터(10분 안에 나갔거나 강퇴당한 방 — {@code RoomService#noAutoJoinRooms} · PUBG 시점 · 포지션 · 방장 티어) →
 * ⑤ 방 키를 파이프라인 한 번으로 읽어 사라진 방 · 확정된 방 · 정원이 찬 방 · 내 포지션을 이미 누가 고른 방 제외 → ⑥ 남은 순서대로 입장({@link RoomMemberService#enter} — 안에서 {@link PostEntryGate} 가
 * 차단 · 상태를 본다). 들어갔으면 200, 다음 방으로 넘어갈 수 없는 거절({@code IN_OTHER_ROOM} · {@code ALREADY_QUEUED})은 그 코드로 409, 다 돌아도 없으면 404.
 *
 * <p><b>포지션을 골라 들어간다</b>(2026-09-30 소유자 결정 — P-44: 참가할 때 남은 찾는 포지션 가운데 하나를 고른다). 찾는 포지션이 있는 글이면 <b>요청의 포지션</b>
 * ({@code keyCondition} 의 값 — 필터가 이미 그 글의 찾는 포지션에 든 것만 남겼다)으로 들어가 멤버 HASH 에 그 포지션이 적힌다. 찾는 포지션이 빈 글(포지션이 없는 모드 · 옛 글)은
 * 포지션 없이 들어간다. 그 포지션을 방 안의 누가 이미 골랐으면 그 방은 맞지 않는 방이다 — ⑤ 에서 빼고, 그 사이에 누가 골라 스크립트가
 * {@code INVALID_POSITION} 으로 거절해도(고른 포지션은 남은 찾는 포지션 SET 에서 빠진다) 다음 방으로 넘어간다(만석 · 확정과 같은 쪽 — Claude 가 정한 세부).
 *
 * <p><b>트랜잭션이 없다</b> — {@code PostService} 와 같은 자리다. DB 는 {@link PostStore} · {@link GameProfileReader} 가 짧게 끝내고, Redis(gameconfig · 방 키 · 스크립트)는 그 밖에서 읽는다.
 * <b>Redis 를 못 읽으면 503 {@code ROOM_STATE_UNAVAILABLE}(fail-closed)</b> — 방에 넣는 일이라 gameconfig 의 fail-open 과 다르다(어차피 방 키 없이는 끝낼 수 없다).
 * <b>gameconfig 가 안 심긴 Redis</b> 면 티어 검사를 건너뛴다({@link GameConfigReader}).
 *
 * <p><b>정원은 글에 적힌 방의 정원이다</b>(2026-09-30 소유자 결정 — P-41: 게시판 방의 정원 = 그 모드의 인원 · {@link RecruitPost#getCapacity()}). 그 전에는 이 요청만
 * 모드의 {@code targetPartySize} 를 읽어 거르고 방 자체의 정원은 5 였다. 이제 글을 쓸 때 그 값이 글에 적히고 직접 입장 · 응답의 {@code full} · 이 요청이 같은 값 하나를 본다
 * (후보는 요청과 같은 모드의 글이라 결과는 전과 같다. 다른 것은 V8 전에 쓴 옛 글 — 정원이 적히지 않아 5 다 — 과 gameconfig 를 못 읽은 채 쓴 글(5)뿐이다).
 *
 * <p><b>빈 순환이 없다</b> — 이 클래스는 {@link RoomMemberService} · {@link RoomService} 를 물고 그 둘은 {@code party} 의 {@code PostService} 를 물지 않는다
 * ({@code RoomMemberService → PostEntryGate → PostStore → RoomService}). 이 클래스를 무는 것은 컨트롤러뿐이다.
 *
 * <p><b>Claude 가 정한 세부</b>(소유자 검토 항목) — 방장의 티어만 본다(방 안 다른 사람의 티어는 보지 않는다 — {@code matching} 이 파티를 만든 사람의 줄을 쓰는 것과 같다.
 * 그 티어는 <b>그 모드의 {@code tierLadder} 사다리 값</b>이다 — 2026-09-29 P-36. 모드 HASH 에 {@code tierLadder} 가 없으면(옛 seed) 티어를 보는 글을 전부 건너뛴다 + WARN) ·
 * 내 글은 후보에서 뺀다 · PUBG 의 {@code PLATFORM} 값은 방장의 {@code server} 와 대조하지 않는다(결정에 없다 — 미정) · {@code positionUniqueness} 는 읽지 않는다
 * (포지션 개념이 없는 모드에 포지션을 주면 {@code matching} 은 400 인데 여기는 그 포지션으로 글을 거른다) · 사라진 방 · 확정된 방은 스크립트에 가기 전에 뺀다 ·
 * 이미 내가 들어 있는 방은 정원을 보지 않고 200 이다(재시도) · 필터와 입장 사이의 경쟁(그 사이에 만석 · 확정 · 차단이 생긴다)은 스크립트 · {@link PostEntryGate} 의 거절로 다음 방에 간다
 * (2026-09-30 부터 스크립트의 정원도 글의 정원이라 그 창에서 정원을 넘기지 않는다 — 전에는 스크립트의 정원이 5 라 모드 정원을 한 명 넘길 수 있었다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoJoinService {

    static final String NO_MATCHING_POST = "NO_MATCHING_POST";

    /** 포지션 개념이 없는 모드의 값 — {@code matching} 의 {@code LolPosition.NONE} 과 같은 글자다 */
    private static final String NO_POSITION = "NONE";
    private static final String SOLO_ONLY = "SOLO_ONLY";
    /** 글의 {@code conditions}(jsonb 의 글자 그대로 — DB 가 공백을 넣어 돌려줄 수 있다)에서 시점을 꺼낸다 */
    private static final Pattern PERSPECTIVE = Pattern.compile("\"perspective\"\\s*:\\s*\"([A-Z]+)\"");

    private final PostStore postStore;
    private final RoomService roomService;
    private final RoomMemberService roomMemberService;
    private final GameConfigReader gameConfig;
    private final GameProfileReader gameProfileReader;
    private final BoardProperties boardProperties;

    /**
     * @return 들어간 글과 그 방(둘 다 같은 숫자다)
     * @throws ApiException 400 {@code VALIDATION_FAILED} · 404 {@code NO_MATCHING_POST} · 409 {@code IN_OTHER_ROOM} · 409 {@code ALREADY_QUEUED} · 503 {@code ROOM_STATE_UNAVAILABLE}
     */
    public AutoJoinResponse join(Long me, AutoJoinRequest request)
    {
        Game game = PostValidation.game(request.game());
        String modeKey = request.modeKey();
        VoicePreference voice = VoicePreference.fromName(request.voicePreference()).orElseThrow(
                () -> ApiException.validationFailed("voicePreference", "REQUIRED · NO_VOICE 가운데 하나여야 합니다"));
        String myPosition = positionOf(game, request.keyCondition());

        Gate gate;
        List<RecruitPost> candidates;
        Map<Long, RoomState> states;
        try
        {
            gate = gate(game, modeKey, request.tier());
            candidates = postStore.findAutoJoinCandidates(game, modeKey, voice, me, boardProperties.autoJoinScan());
            // 10분 안에 내가 나갔거나 강퇴당한 방은 후보에서 뺀다(2026-09-29 소유자 결정) — 방 키는 room 이 읽는다(§3.3)
            Set<String> skip = roomService.noAutoJoinRooms(String.valueOf(me));
            candidates = filter(game, modeKey, myPosition, gate, skip, candidates);
            states = roomService.states(candidates.stream().map(RecruitPost::getId).toList());
        }
        catch(GameConfigUnavailableException | RoomStateUnavailableException e)
        {
            throw RoomErrors.stateUnavailable();
        }

        int tried = 0;
        for(RecruitPost post : candidates)
        {
            RoomState state = states.get(post.getId());
            // 사라진 방 · 확정된 방 · 정원이 찬 방은 스크립트에 가지 않는다 — 어차피 거절될 것이고 입장 검사(DB · Redis)를 아낀다.
            // 정원은 글에 적힌 방의 정원이다(2026-09-30 — P-41) — 스크립트도 같은 값으로 가른다.
            // 이미 내가 들어 있는 방은 정원과 무관하다 — 재시도 · 새로고침이 "이미 들어와 있다"(200)로 답하게 스크립트에 보낸다(입장 요청과 같다)
            if(state == null || !state.hostKeyExists() || state.confirmed())
            {
                continue;
            }
            if(!state.members().contains(me) && state.members().size() >= post.getCapacity())
            {
                continue;
            }
            // 포지션을 골라 들어가는 방이면 내 포지션으로 들어간다(P-44). 이미 누가 고른 포지션이면 맞지 않는 방이다 — 이미 내가 들어 있는 방은 위와 같이 스크립트에 보낸다(200)
            String entryPosition = post.getWantedPositions().isEmpty() ? null : myPosition;
            if(!state.members().contains(me) && state.positionTaken(entryPosition))
            {
                continue;
            }
            tried++;
            if(tryEnter(post.getId(), me, entryPosition))
            {
                log.info("게시판 방 먼저 합류 userId={} game={} mode={} postId={} candidates={} tried={}", me, game, modeKey,
                        post.getId(), candidates.size(), tried);
                return new AutoJoinResponse(post.getId(), post.getId());
            }
        }
        log.info("게시판 방 먼저 합류 — 맞는 방이 없다 userId={} game={} mode={} candidates={} tried={}", me, game, modeKey,
                candidates.size(), tried);
        throw new ApiException(HttpStatus.NOT_FOUND, NO_MATCHING_POST, "조건에 맞는 모집 중인 방이 없습니다");
    }

    // ---- 본문 검증 ----

    /**
     * {@code keyCondition} 에서 내 포지션을 꺼낸다 — 없거나 {@code NONE} 이면 {@code null}("포지션이 없다" — {@code wantedPositions} 가 빈 글만 맞는다).
     * 종류는 게임과 맞아야 하고 값은 그 종류의 목록에 있어야 한다(400). PUBG 의 {@code PLATFORM} 값은 검증만 하고 쓰지 않는다.
     */
    private static String positionOf(Game game, AutoJoinRequest.KeyCondition condition)
    {
        if(condition == null)
        {
            return null;
        }
        KeyConditionType type = KeyConditionType.fromName(condition.type());
        if(type == null)
        {
            throw ApiException.validationFailed("keyCondition.type", "POSITION · ROLE · PLATFORM 가운데 하나여야 합니다");
        }
        if(type.game() != game)
        {
            throw ApiException.validationFailed("keyCondition.type", game.name() + " 의 조건이 아닙니다");
        }
        String value = condition.value();
        if(type == KeyConditionType.PLATFORM)
        {
            if(!game.servers().contains(value))
            {
                throw ApiException.validationFailed("keyCondition.value", "STEAM · KAKAO 가운데 하나여야 합니다");
            }
            return null;
        }
        if(NO_POSITION.equals(value))
        {
            return null;
        }
        if(!game.positions().contains(value))
        {
            throw ApiException.validationFailed("keyCondition.value", game.name() + " 의 포지션이 아닙니다");
        }
        return value;
    }

    // ---- gameconfig — 모드 · 티어 규칙 (matching 의 validator 와 같은 순서) ----

    /**
     * 이 요청이 글을 거르는 잣대 가운데 gameconfig 에서 오는 것.
     *
     * @param seesTier 방장 티어를 봐야 하는가(심긴 Redis 이고 그 모드의 {@code tierRule} 이 {@code EXIST})
     * @param myScore  내 티어의 사다리 단계 번호({@code seesTier} 일 때만)
     * @param ladder   그 모드가 보는 티어 사다리({@code tierLadder} — 2026-09-29 P-36). 방장의 게임 계정에서 <b>이 사다리의 티어</b>를 본다.
     *                 {@code seesTier} 인데 {@code null} 이면(옛 seed) 방장 티어를 알 수 없다 — 티어를 보는 글을 전부 건너뛴다
     */
    private record Gate(boolean seesTier, double myScore, String ladder) {
    }

    /**
     * {@code matching} 의 validator 와 같은 순서로 거른다 — 모드가 없으면 400 · {@code tierRule=NONE} 인데 티어가 있으면 400 · {@code EXIST} 인데 티어가 없거나 · 사다리에 없거나 ·
     * 허용 범위 표에 줄이 없거나 {@code SOLO_ONLY} 면 400. <b>안 심긴 Redis 면 아무것도 보지 않는다</b>.
     */
    private Gate gate(Game game, String modeKey, String tier)
    {
        if(!gameConfig.seeded(game))
        {
            return new Gate(false, 0, null);
        }
        ModeConfig mode = gameConfig.modeConfig(game, modeKey).orElseThrow(
                () -> ApiException.validationFailed("modeKey", game.name() + " 에 없는 모드입니다"));
        if(mode.tierRule() == null)
        {
            // 필드 하나만 빠진 모드 — matching 도 매칭하지 않는다(설정이 불완전하면 조용히 다른 규칙이 적용되는 것보다 낫다)
            throw ApiException.validationFailed("modeKey", modeKey + " 의 설정이 불완전합니다(tierRule)");
        }
        if(!mode.seesTier())
        {
            if(tier != null)
            {
                throw ApiException.validationFailed("tier", modeKey + " 는 티어를 보지 않는 모드입니다");
            }
            return new Gate(false, 0, null);
        }
        if(tier == null)
        {
            throw ApiException.validationFailed("tier", "필요합니다");
        }
        Double myScore = gameConfig.tierScores(game, List.of(tier)).get(tier);
        if(myScore == null)
        {
            throw ApiException.validationFailed("tier", game.name() + " 의 티어가 아닙니다");
        }
        String myRange = gameConfig.tierRanges(game, modeKey, List.of(tier)).get(tier);
        if(myRange == null || SOLO_ONLY.equals(myRange))
        {
            throw ApiException.validationFailed("tier", modeKey + " 에서 파티를 맺을 수 없는 티어입니다");
        }
        return new Gate(true, myScore, mode.tierLadder());
    }

    // ---- 글 거르기 ----

    /**
     * 건너뛸 방(10분 안에 나갔거나 강퇴당한 방 — {@link RoomService#noAutoJoinRooms}) · PUBG 시점 · 포지션 · 방장 티어. 순서는 그대로다(오래된 순).
     * 내 글 제외 · 음성은 쿼리가 본다({@link PostStore#findAutoJoinCandidates} — 2026-09-29 에 옮겼다.
     * 여기 남은 것은 SQL 로 못 보거나 어색한 것이다 — 건너뛸 방과 티어는 Redis, 시점은 jsonb 안, 포지션은 별도 표에 "비어 있으면 누구든")
     */
    private List<RecruitPost> filter(Game game, String modeKey, String myPosition, Gate gate, Set<String> skip, List<RecruitPost> candidates)
    {
        String perspective = perspectiveOf(game, modeKey);
        List<RecruitPost> matched = new ArrayList<>();
        for(RecruitPost post : candidates)
        {
            if(skip.contains(String.valueOf(post.getId())))
            {
                continue;
            }
            if(perspective != null && !perspective.equals(perspectiveOf(post)))
            {
                continue;
            }
            // wantedPositions 가 비어 있으면 누구든 된다. 값이 있으면 내 포지션이 그 안에 있어야 한다 — 내 포지션이 없으면(NONE) 빈 글만 맞는다
            Set<String> wanted = post.getWantedPositions();
            if(!wanted.isEmpty() && (myPosition == null || !wanted.contains(myPosition)))
            {
                continue;
            }
            matched.add(post);
        }
        return gate.seesTier() ? byHostTier(game, modeKey, gate, matched) : matched;
    }

    /**
     * 방장의 그 게임 게임 계정 티어로 "내 티어가 그 방의 허용 범위 안인가" 를 본다 — 파티를 만든 사람의 줄이 그 파티의 범위다({@code matching} 과 같다. 게시판이면 방장).
     * <b>방장 티어는 그 모드의 사다리({@code tierLadder}) 값이다</b>(2026-09-29 — P-36. 자유랭크 방을 방장의 솔로랭크 티어로 보지 않는다 — SQL 이 jsonb 에서 뽑는다).
     * 방장 티어가 없거나 · 표에 줄이 없거나 · {@code SOLO_ONLY} 거나 · 범위의 끝 이름이 사다리에 없으면 그 방은 건너뛴다. DB 한 번 · {@code HMGET} 한 번 · {@code ZMSCORE} 한 번이다.
     * <b>모드 HASH 에 {@code tierLadder} 가 없거나(옛 seed) 그 게임의 사다리가 아니면</b> 방장 티어를 알 수 없는 것으로 보고 전부 건너뛴다 + WARN(Claude 가 정한 세부 —
     * "방장 티어가 없으면 건너뛴다" 와 같은 쪽이다. 모드 이름으로 사다리를 짐작하지 않는다).
     */
    private List<RecruitPost> byHostTier(Game game, String modeKey, Gate gate, List<RecruitPost> posts)
    {
        if(posts.isEmpty())
        {
            return posts;
        }
        String ladder = gate.ladder();
        if(ladder == null || !game.tierLadders().contains(ladder))
        {
            log.warn("gameconfig 모드 {}:{} 의 tierLadder 가 없거나 {} 의 사다리가 아니다 ladder={} — 방장 티어를 알 수 없어 후보 {}개를 건너뛴다"
                    + "(matching/seed/gameconfig.redis 를 다시 심어라)", game, modeKey, game, ladder, posts.size());
            return List.of();
        }
        Set<Long> hostIds = new HashSet<>();
        posts.forEach(post -> hostIds.add(post.getHostId()));
        Map<Long, String> hostTiers = gameProfileReader.findTiers(hostIds, game, ladder);

        List<String> tierNames = hostTiers.values().stream().filter(tier -> tier != null).distinct().toList();
        Map<String, String> ranges = gameConfig.tierRanges(game, modeKey, tierNames);
        Set<String> boundNames = new HashSet<>();
        for(String range : ranges.values())
        {
            String[] bounds = range.split(":", 2);
            if(bounds.length == 2)
            {
                boundNames.add(bounds[0]);
                boundNames.add(bounds[1]);
            }
        }
        Map<String, Double> scores = gameConfig.tierScores(game, boundNames);

        List<RecruitPost> matched = new ArrayList<>();
        for(RecruitPost post : posts)
        {
            String hostTier = hostTiers.get(post.getHostId());
            String range = (hostTier == null) ? null : ranges.get(hostTier);
            if(range == null || SOLO_ONLY.equals(range))
            {
                continue;
            }
            String[] bounds = range.split(":", 2);
            Double lo = (bounds.length == 2) ? scores.get(bounds[0]) : null;
            Double hi = (bounds.length == 2) ? scores.get(bounds[1]) : null;
            if(lo != null && hi != null && lo <= gate.myScore() && gate.myScore() <= hi)
            {
                matched.add(post);
            }
        }
        return matched;
    }

    /** PUBG 의 모드 이름에 접힌 시점({@code RANKED_DUO_TPP} → {@code TPP}). PUBG 가 아니거나 이름에 시점이 없으면 {@code null} — 그러면 시점을 보지 않는다(모드가 같은 것으로 족하다) */
    private static String perspectiveOf(Game game, String modeKey)
    {
        if(game != Game.PUBG)
        {
            return null;
        }
        if(modeKey.endsWith("_TPP"))
        {
            return "TPP";
        }
        if(modeKey.endsWith("_FPP"))
        {
            return "FPP";
        }
        return null;
    }

    private static String perspectiveOf(RecruitPost post)
    {
        String conditions = post.getConditions();
        if(conditions == null)
        {
            return null;
        }
        Matcher matcher = PERSPECTIVE.matcher(conditions);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ---- 입장 ----

    /**
     * 그 방에 들어가 본다. 들어갔거나 이미 들어와 있으면 {@code true}. 다음 방으로 넘어갈 수 있는 거절(만석 · 확정 · 사라진 방 · 포지션이 찼다 · 입장 검사의 404 · 409)은 {@code false},
     * 넘어갈 수 없는 거절(다른 방에 있다 · 자동 매칭 중)은 그 코드 그대로 409 로 던진다. 그 밖의 {@link ApiException}(503 등)도 그대로 던진다.
     *
     * @param position 고를 포지션 — 찾는 포지션이 빈 글이면 {@code null}(P-44)
     */
    private boolean tryEnter(Long postId, Long me, String position)
    {
        EnterResult result;
        try
        {
            result = roomMemberService.enter(String.valueOf(postId), String.valueOf(me), position);
        }
        catch(ApiException e)
        {
            if(PostStore.POST_NOT_FOUND.equals(e.getCode()) || PostStore.POST_NOT_RECRUITING.equals(e.getCode()))
            {
                // 그 사이에 글이 끝났거나(만료 · 확정) 방 안에 나와 차단 관계인 사람이 들어왔다 — 다음 방
                return false;
            }
            throw e;
        }
        return switch(result)
        {
            case ENTERED, ALREADY_ENTERED -> true;
            // 강퇴당한 지 10분이 안 된 방은 다음 방으로 — 후보 거르기가 no-auto-join 목록으로 먼저 빼지만, 그 목록에 없고 no-entry 에만 있는 경우는 없다(강퇴는 둘 다 쓴다).
            // 그래도 스크립트가 거절하면 넘어간다(두 목록의 수명이 어긋난 창)
            case FULL, ROOM_CONFIRMED, ROOM_NOT_FOUND, KICKED_RECENTLY -> false;
            // 포지션(P-44) — INVALID_POSITION 은 그 사이에 누가 내 포지션을 골라 남은 찾는 포지션 SET 에서 빠졌다(글은 고칠 수 없다 — 2026-10-01 소유자 결정).
            // 이 방은 맞지 않는 방이라 다음 방으로 간다.
            // POSITION_TAKEN 은 지금 스크립트가 돌려주지 않는다 — switch 가 enum 을 다 덮어야 해서 같이 둔다
            case POSITION_TAKEN, INVALID_POSITION -> false;
            case IN_OTHER_ROOM -> throw RoomErrors.inOtherRoom();
            case ACTIVE_REQUEST_EXISTS -> throw RoomErrors.alreadyQueued("자동 매칭을 돌리는 동안에는 게시판 방에 들어갈 수 없습니다");
        };
    }
}
