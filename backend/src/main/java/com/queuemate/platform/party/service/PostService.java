package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.dto.UserGameProfile;
import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import com.queuemate.platform.common.gameconfig.ModePositions;
import com.queuemate.platform.party.domain.PostStatus;
import com.queuemate.platform.party.domain.RecruitPost;
import com.queuemate.platform.party.dto.MemberCard;
import com.queuemate.platform.party.dto.PostCreateRequest;
import com.queuemate.platform.party.dto.PostListResponse;
import com.queuemate.platform.party.dto.PostResponse;
import com.queuemate.platform.party.service.BoardParty.Seat;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.RoomState;
import com.queuemate.platform.room.domain.RoomStateUnavailableException;
import com.queuemate.platform.room.service.RoomService;
import com.queuemate.platform.social.service.BlockReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 파티 모집 게시판 — 글 · 실시간 목록 · 방장 확정 ({@code contracts/platform-api.md} "모집 글 · 목록").
 *
 * <p><b>글은 고칠 수 없다</b>(2026-10-01 소유자 결정 — {@code PATCH /api/v1/posts/{postId}} 를 없앴다). 조건을 바꾸려면 지우고 다시 쓴다 —
 * 그래서 글 쓰기가 방에 적은 방장 포지션 · 찾는 포지션이 글과 어긋날 일이 없다.
 *
 * <p><b>글 한 줄은 여기서 전부 조립한다</b>(docs/11 D-20) — 글(DB) + 방 안에 누가 있나({@code room} 의 창구 {@link RoomService#states}) +
 * 그 사람들의 게임 프로필({@code account} 의 창구) + 차단 거르기({@code social} 의 창구).
 *
 * <p><b>{@code room} 과는 서비스 메서드로 맞물린다</b>(2026-09-25 2단계 — 두 앱이던 때는 입장권과 방 키 읽기로만 이었다). 글 쓰기가 방을 만들고
 * ({@link PostStore#create}), 방장 확정이 한 요청에서 방과 글을 같이 바꾸고({@link #confirmRoom}), 목록이 방 안을 {@link RoomService#states} 로 읽는다.
 * 거꾸로 방의 입장이 글을 보는 것은 이 클래스가 아니라 따로 선 창구 {@link PostEntryGate} 다 — 이 클래스가 {@link RoomService} 를 물므로
 * 방 쪽이 이 클래스를 물면 빈 순환이 된다.
 *
 * <p><b>글이 몇 개든 왕복 수가 같다</b> — 글 쿼리 하나(+ 찾는 포지션 하나), 확정된 글의 파티와 파티원 하나(확정된 글이 있을 때 — {@link PostStore#findBoardParties}),
 * Redis 파이프라인 한 번, 프로필은 게임마다 한 번(많아야 세 번), 차단은 한 번. 이 목록은 게시판 신호가 올 때마다 다시 불린다 — 글 수나 사람 수만큼 되풀이하지 마라.
 *
 * <p><b>카드({@code members})는 글의 상태에 따라 다르다</b>(2026-09-30 소유자 결정 — P-40) — 모집 중이면 <b>방 안에 지금 있는 사람</b>, 확정이면 <b>확정 순간의 파티원 전원</b>
 * (방에서 나간 뒤에도 — DB 의 {@code party_members}), 만료면 비어 있다({@code host} 는 늘 채운다).
 *
 * <p><b>이 클래스에는 트랜잭션이 없다</b> — DB 에 닿는 토막은 {@link PostStore} 가 짧게 끝낸다. Redis 를 기다리는 동안 DB 커넥션을 붙잡지 않는다.
 *
 * <p><b>목록 조회(GET)가 글을 만료 · 확정으로 바꾼다</b> — 방은 수명이 다하면 Redis 에서 저절로 사라져 그 순간 돌아가는 코드가 없다.
 * 그래서 방 키를 볼 때 스스로 옮겨 적는다(CLAUDE.md §3.3). 허용된 부수 효과이고 전부 조건부 UPDATE 라 멱등하다.
 * <b>같은 목록이 그 게임의 열려 있는 자동 매칭 파티도 닫는다</b>(2026-09-28 소유자 결정 — {@link #closeVanishedMatchParties}). 글이 없어 글에서 출발하는
 * 옮겨 적기가 닿지 않던 파티다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(BoardProperties.class)
public class PostService {

    private final PostStore postStore;
    private final MatchPartyStore matchPartyStore;
    private final RoomService roomService;
    private final GameProfileReader gameProfileReader;
    private final BlockReader blockReader;
    private final BoardProperties boardProperties;
    private final GameConfigReader gameConfig;

    /**
     * 글을 쓴다. <b>전적을 긁지 않는다</b>(2026-09-24 소유자 결정 — 2026-09-23 의 "긁는 시점은 둘" 가운데 이쪽을 되물렸다.
     * {@code contracts/platform-api.md} "전적을 긁는 것" · P-13) — 그때는 긁는 것이 비동기라 이 응답의 {@code host.profile.stats} 에
     * 반영되지도 않으면서 Riot 호출 20여 번을 썼다. 전적은 <b>게임 계정을 연결할 때와 로그인 · 재발급 때(1시간이 지난 계정만 — P-42)</b> 갱신된다.
     *
     * <p><b>방도 같이 만든다</b>(2026-09-25 2단계 — 소유자 결정 C). 방을 못 만들면 글도 되돌려진다({@link PostStore#create}).
     * 그래서 응답의 {@code members} 에 방장이 들어 있고 {@code memberCount} 는 1 이다 — 브라우저가 방 만들기를 따로 부르지 않는다.
     *
     * <p><b>방장 포지션</b>(2026-09-30 소유자 결정 — P-38)의 규칙은 그 모드에 포지션이 있는가(gameconfig 의 {@code positionUniqueness})로 정해진다 —
     * 그것도 여기서 읽고, 검증은 찾는 포지션을 본 뒤에 {@link PostStore#create} 가 한다(둘이 겹치면 안 된다).
     *
     * <p><b>방의 정원 = 그 모드의 인원</b>(2026-09-30 소유자 결정 — P-41. gameconfig 의 {@code targetPartySize}) — 여기서 읽어 글에 적는다({@link #capacityOf}).
     */
    public PostResponse create(Long me, PostCreateRequest request)
    {
        // gameconfig(Redis)를 읽는 검증은 여기서 한다 — PostStore 의 트랜잭션이 Redis 를 기다리며 DB 커넥션을 붙잡지 않게 (2026-09-24)
        Game game = PostValidation.game(request.game());
        PostValidation.mode(gameConfig, game, request.mode());
        ModePositions modePositions = PostValidation.modePositions(gameConfig, game, request.mode());
        OptionalInt knownCapacity = knownCapacityOf(game, request.mode());
        // 찾는 포지션 수 ≥ 정원 − 1(2026-09-30 소유자 결정) — 정원을 실제로 알 때만 본다. 나머지 찾는 포지션 · 방장 포지션 규칙은 PostStore#create 가 본다
        PostValidation.enoughWantedPositions(game, modePositions,
                PostValidation.wantedPositions(game, request.wantedPositions()), knownCapacity);
        int capacity = knownCapacity.orElse(RecruitPost.MAX_CAPACITY);
        RecruitPost post = postStore.create(me, request, modePositions, capacity, now());
        // 방금 만든 방이다 — 방장 혼자 들어 있고 멤버 HASH 의 방장 값이 글의 방장 포지션인 것을 안다(create-room.lua). 방 키를 다시 읽지 않는다
        return renderAll(me, List.of(post), Map.of(post.getId(), List.of(new Seat(me, true, post.getHostPosition()))), Set.of(), false)
                .getFirst();
    }

    /**
     * 그 모드의 인원을 <b>실제로 읽었을 때만</b> 돌려준다 — 모르면 비어 있다({@link #capacityOf} 가 대신 채우는 값을 믿지 않는다).
     * 찾는 포지션 수 검사({@link PostValidation#enoughWantedPositions})가 5 로 채운 정원으로 거절하지 않게 하려는 것이다(2026-09-30).
     */
    private OptionalInt knownCapacityOf(Game game, String mode)
    {
        int capacity = capacityOf(game, mode, -1);
        return capacity < 0 ? OptionalInt.empty() : OptionalInt.of(capacity);
    }

    /**
     * 그 모드의 방 정원 — gameconfig 모드 HASH 의 {@code targetPartySize}(2026-09-30 소유자 결정 — "게시판 방의 정원 = 모드의 인원", P-41).
     * 값의 목록은 gameconfig 가 원본이다 — 이 앱에 베껴 두지 않는다.
     *
     * <p><b>모르면 {@code fallback}</b> 이다(fail-open — 글 쓰기의 모드 검증과 같은 쪽, P-16): Redis 를 못 읽었거나 · 안 심겼거나 · 숫자가 아니거나
     * ({@link GameConfigReader#partySize} 가 WARN 을 남긴다), 방의 범위({@value RecruitPost#MIN_CAPACITY} ~ {@value RecruitPost#MAX_CAPACITY})를 벗어난 값이다
     * (여기서 WARN — 5 를 넘으면 방 정원의 상한 5 로 자른다. 게시판 방 먼저 합류가 전부터 그렇게 잘랐다). 글 쓰기는 모르면 상한 5 를 적는다(Claude 가 정한 세부).
     */
    private int capacityOf(Game game, String mode, int fallback)
    {
        OptionalInt size = gameConfig.partySize(game, mode);
        if(size.isEmpty())
        {
            return fallback;
        }
        int value = size.getAsInt();
        if(value > RecruitPost.MAX_CAPACITY)
        {
            log.warn("gameconfig 의 모드 인원이 방 정원의 상한을 넘는다 — {} 로 자른다 game={} mode={} size={}", RecruitPost.MAX_CAPACITY, game, mode, value);
            return RecruitPost.MAX_CAPACITY;
        }
        if(value < RecruitPost.MIN_CAPACITY)
        {
            log.warn("gameconfig 의 모드 인원이 파티가 될 수 없는 값이다 — 모르는 값으로 친다 game={} mode={} size={}", game, mode, value);
            return fallback;
        }
        return value;
    }

    public void delete(Long me, Long postId)
    {
        postStore.expireByHost(me, postId, now());
    }

    // ---- 회원 탈퇴의 창구 (2026-10-02 소유자 결정 · P-48 — account.service.AccountDeletionService 가 부른다) ----

    /**
     * 탈퇴하는 사람의 <b>모집 중인 글이 남아 있으면 글 지우기({@link #delete})와 같은 효과</b>를 낸다 — 글을 만료시키고 방장으로서 그 방을 닫는다
     * (방에 있던 사람에게 {@code ROOM_CLOSED} · 게시판 신호). 보통은 이미 없다 — 탈퇴가 먼저 그 사람을 방에서 내보내고(평소 나가기), 확정 전의 방장이 나가면
     * 글이 그 자리에서 만료된다. 남는 것은 방 키가 수명으로 사라졌는데 아직 아무 목록도 만료로 옮겨 적지 않은 글 같은 드문 경우다. 방 닫기가 실패해도 던지지 않는다
     * ({@link PostStore#expireByHost} 의 "순서" — 글은 곧 지워진다).
     */
    public void expireRecruitingOf(Long hostId)
    {
        postStore.findRecruitingOf(hostId).ifPresent(postId -> postStore.expireByHost(hostId, postId, now()));
    }

    /**
     * 탈퇴하는 사람의 <b>확정되지 않은 글을 지운다</b> — 확정된 글은 남고 방장의 칸만 빈다({@link PostStore#deleteUnconfirmedOf}).
     * <b>트랜잭션이 없는 이 클래스지만 부르는 쪽의 트랜잭션에 합류한다</b> — {@code users} 를 지우는 트랜잭션 안에서 불러야 한다(그 이유는 {@link PostStore#deleteUnconfirmedOf}).
     *
     * @return 지운 글의 수
     */
    public int deleteUnconfirmedOf(Long hostId)
    {
        return postStore.deleteUnconfirmedOf(hostId);
    }

    /**
     * 게시판 목록 <b>한 페이지</b>. 차단 관계로 숨겨진 글은 빠져 있다 — 빠졌다는 흔적도 없다.
     *
     * <p><b>{@code game} 은 필수다</b>(2026-09-25 소유자 결정 — {@code contracts/platform-api.md} · P-21). <b>게시판은 게임별로 나뉜 페이지이고
     * 사용자는 늘 한 게임의 게시판을 본다 — "세 게임 전부" 화면이 없다.</b> 값이 없거나 모르는 이름이면 컨트롤러에 닿기 전에 400 이라
     * <b>여기서는 게임이 늘 정해져 있다</b>({@link com.queuemate.platform.party.controller.PostController#list}).
     *
     * <p><b>페이지는 커서로 나눈다</b>(2026-09-23 소유자 결정 — {@code contracts/platform-api.md}). {@code limit} 은 없으면
     * {@value BoardProperties#DEFAULT_PAGE_LIMIT}, 많아야 {@value BoardProperties#MAX_PAGE_LIMIT} 이다. {@code cursor} 가 없으면 맨 위부터다.
     * <b>신호({@code BOARD_CHANGED})를 받은 프런트는 커서를 쓰지 않는다</b> — 펼친 만큼을 {@code limit} 으로 맨 위부터 다시 받는다.
     *
     * <p><b>커서는 글 번호 하나다 — 감싸지 않는다</b>(2026-09-25 소유자 결정. 그 전에는 base64url 한 겹을 씌운 불투명한 문자열이었다).
     * <b>감싸도 얻는 것이 없었다</b> — 서명하지 않아 보안 값이 0 이고(위조해도 남의 글이 보이지 않는다. 차단 거르기를 페이지마다 다시 한다),
     * <b>글 번호는 응답의 {@code postId} 로 이미 다 나간다</b>. 커서가 {@code id} 하나라 형식이 바뀔 여지도 작다. 그래서 <b>문자열을 숫자로 바꾸는 일을
     * 스프링에 맡긴다</b> — 숫자가 아닌 커서는 컨트롤러에 닿기 전에 400 이고({@code GlobalExceptionHandler#handleTypeMismatch}),
     * 0 이하이거나 맨 끝을 넘은 번호는 {@code p.id < :postId} 가 아무것도 고르지 못해 <b>빈 페이지</b>가 된다(따로 막지 않는다 — 막아도 알려 줄 것이 없다).
     *
     * <p><b>커서가 {@code id} 하나인 이유</b> — 커서는 정렬 키가 ① 순서대로 커지고 ② 겹치지 않고 ③ <b>변하지 않는다</b>는 전제 위에 선다.
     * {@code id} 는 {@code bigint GENERATED ALWAYS AS IDENTITY} 라 셋을 혼자 다 만족한다(2026-09-24 소유자 결정 — {@code BOARD_ORDER}).
     * <b>글의 상태를 정렬에 넣으면 커서가 중복을 낸다</b> — 상태는 변하고 <b>그것도 이 조회 자신이 바꾼다</b>(아래 "허용된 부수 효과").
     *
     * <p><b>차단 거르기는 글을 읽어 온 뒤에 한다</b>(방 안에 누가 있는지를 Redis 에서 읽어야 안다) — 그래서 {@code limit} 만큼 읽어도
     * 보이는 것이 그보다 적을 수 있다. 모자라면 <b>그 뒤를 더 읽어 채운다</b>({@code platform.board.max-refills} 번까지. 그래도 모자라면 있는 만큼이다).
     * 채우기 한 번은 목록 조립 한 벌(글 쿼리 · Redis 파이프라인 한 번 · 프로필 · 차단)이고 <b>그 안에서는 글이 몇 개든 왕복이 늘지 않는다.</b>
     *
     * <p><b>{@code nextCursor} 는 "마지막으로 읽은 줄"이다</b> — 마지막으로 <b>보여 준</b> 줄이 아니다. 숨겨진 글을 다음 페이지에서 또 읽지 않게 하려는 것이다.
     * 다음이 있는지는 <b>보여 줄 것보다 한 개 더 읽어</b> 안다.
     *
     * <p><b>만료 · 확정 옮겨 적기는 읽은 글에만 걸린다</b> — 목록을 전부 읽지 않으므로 깊은 곳의 글은 누가 그 페이지를 볼 때 옮겨진다.
     * 그래도 "들어갈 수 있는 죽은 방"은 생기지 않는다 — 입장은 방의 스크립트가 방장 키 · 확정 표시 키를 직접 본다({@code contracts/platform-api.md}).
     *
     * <p><b>글을 상태로 가리지 않는다</b>(2026-09-25 소유자 결정) — 모집 중 · 확정 · 만료가 전부 {@code id} 내림차순으로 나오고 <b>끝난 글도 계속 남는다.</b>
     * 그 전에는 만료 · 확정된 지 10분이 안 된 글만 남겼는데, 그 조건이 세 컬럼에 걸친 {@code OR} 셋이라 {@code (game, id DESC)} 인덱스를
     * 깨끗하게 타지 못했다({@code RecruitPostRepository#findBoard}). 끝난 글은 "모집이 얼마나 활발한가"를 보여 주는 쪽으로도 쓰인다.
     */
    public PostListResponse list(Long me, Game game, Integer limitParam, Long cursor)
    {
        int limit = PostValidation.limit(limitParam);
        Instant now = now();

        List<PostResponse> visible = new ArrayList<>();
        boolean more = false;
        // 첫 읽기 + 채우기. 채우기의 상한이 없으면 차단이 많은 사용자의 한 번의 목록 조회가 게시판을 끝까지 훑는다
        int reads = 1 + Math.max(0, boardProperties.maxRefills());
        for(int read = 0; read < reads && visible.size() < limit; read++)
        {
            int want = limit - visible.size();
            List<RecruitPost> rows = postStore.findBoard(game, cursor, want + 1);
            if(rows.isEmpty())
            {
                more = false;
                break;
            }
            more = rows.size() > want;
            // 하나 더 읽은 줄은 "다음이 있는가"를 본 것뿐이다 — 이 페이지에서 다루지 않고 다음 페이지가 처음부터 읽는다
            List<RecruitPost> page = more ? rows.subList(0, want) : rows;
            // 마지막으로 "읽은" 줄이다 — 보여 준 줄이 아니다(차단으로 숨겨진 글을 다음 페이지에서 또 읽지 않게)
            cursor = page.getLast().getId();
            Observed observed = observe(page, now);
            visible.addAll(renderAll(me, observed.posts(), observed.seats(), observed.closed(), true));
            if(!more)
            {
                break;
            }
        }
        // 글에서 출발하는 옮겨 적기(observe)가 닿지 않는 자동 매칭 파티 — 목록 한 번에 한 번, 글이 하나도 없는 게시판이어도 본다
        closeVanishedMatchParties(game);
        return new PostListResponse(visible, more ? cursor : null);
    }

    /** 단건. 차단 관계로 숨겨진 글은 없는 글과 똑같이 404 다 — 숨겨졌다는 것을 알려 주지 않는다 */
    public PostResponse get(Long me, Long postId)
    {
        RecruitPost post = postStore.find(postId).orElseThrow(PostStore::postNotFound);
        Observed observed = observe(List.of(post), now());
        return renderAll(me, observed.posts(), observed.seats(), observed.closed(), true).stream()
                .findFirst().orElseThrow(PostStore::postNotFound);
    }

    /**
     * 방장 확정 — {@code POST /api/v1/rooms/{roomId}/confirm} 이 부른다(2026-09-25 2단계). 방의 확정과 확정의 기록을 한 요청에서 한다 —
     * 순서와 커밋이 실패했을 때의 자가 치유는 {@link PostStore#confirmRoom} 에 있다. 결과를 응답 코드로 옮기는 것은 방의 컨트롤러다.
     */
    public ConfirmResult confirmRoom(Long me, Long postId)
    {
        return postStore.confirmRoom(me, postId, now());
    }

    // ---- 방 키 읽기와 옮겨 적기 ----

    /**
     * 방 키를 읽은 뒤의 글과, 글마다 카드로 보여 줄 사람 — <b>모집 중인 글은 방 안에 지금 있는 사람, 확정된 글은 확정 순간의 파티원 전원</b>
     * (2026-09-30 소유자 결정 — P-40). 만료된 글은 {@code seats} 에 없다(멤버를 비운다 — P-20 그대로)
     *
     * @param closed 파티가 닫힌 확정된 글의 번호(2026-10-01 소유자 결정 — 응답의 {@code closed}). 이 조회가 방금 닫은 파티도 들어 있다
     */
    private record Observed(List<RecruitPost> posts, Map<Long, List<Seat>> seats, Set<Long> closed) {
    }

    /**
     * 모집 중인 글들의 방 키를 <b>한 번에</b> 읽고, 알게 된 것을 글에 옮겨 적은 뒤, 지금의 글과 카드로 보여 줄 사람을 돌려준다.
     *
     * <p><b>확정된 글 가운데 파티가 아직 열려 있는 것도 읽는다</b>(2026-09-26 소유자 결정 — 확정된 방이 없어질 때 파티가 닫힌다). 전원이 말없이 사라져
     * 키가 수명으로 없어진 방은 그 순간 돌아가는 코드가 없어, 글의 만료와 같은 방식으로 여기서 발견한다. 파티가 이미 닫힌 글은 읽지 않는다.
     *
     * <p><b>확정된 글의 카드는 방이 아니라 파티다</b>(2026-09-30 소유자 결정 — "확정 순간의 파티원 전원". P-40) — {@code parties} · {@code party_members} 에서 읽고,
     * 방에서 나간 사람도 · 방이 없어진 뒤에도 · 파티가 닫힌 뒤에도 그대로 보여 준다. 그 전에는 확정된 글도 멤버를 비워 방장 카드만 나갔다(나간 뒤에는 볼 방이 없어서).
     * 파티를 읽는 쿼리는 "열린 파티" 를 가리는 쿼리와 <b>같은 한 번</b>이다({@link PostStore#findBoardParties}) — 목록의 SQL 문장 수가 늘지 않는다.
     * 이 조회가 방금 확정으로 옮겨 적은 글(자가 치유)만 옮겨 적은 뒤에 한 번 더 읽는다.
     *
     * <p><b>방 키를 읽지 못하면</b> 방 정보를 비운 채 글만 돌려주고 <b>아무것도 옮겨 적지 않는다</b>(fail-open) —
     * 못 읽은 것을 "방이 없다"로 읽으면 멀쩡한 글이 전부 만료된다. 확정된 글의 파티원은 DB 에서 오므로 그때도 나간다.
     */
    private Observed observe(List<RecruitPost> posts, Instant now)
    {
        List<Long> recruitingIds = posts.stream()
                .filter(post -> post.getStatus() == PostStatus.RECRUITING)
                .map(RecruitPost::getId)
                .toList();
        List<Long> confirmedIds = posts.stream()
                .filter(post -> post.getStatus() == PostStatus.CONFIRMED)
                .map(RecruitPost::getId)
                .toList();
        Map<Long, BoardParty> parties = new HashMap<>(postStore.findBoardParties(confirmedIds));
        Set<Long> openParties = parties.entrySet().stream()
                .filter(entry -> entry.getValue().active())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        List<Long> toRead = new ArrayList<>(recruitingIds);
        toRead.addAll(openParties);
        if(toRead.isEmpty())
        {
            return new Observed(posts, seatsOf(posts, Map.of(), parties), closedOf(posts, parties));
        }
        Map<Long, RoomState> states = readRoomStates(toRead);
        if(states == null)
        {
            return new Observed(posts, seatsOf(posts, Map.of(), parties), closedOf(posts, parties));
        }

        RoomObservations observations = new RoomObservations();
        for(RecruitPost post : posts)
        {
            RoomState state = states.get(post.getId());
            if(post.getStatus() == PostStatus.RECRUITING && state != null)
            {
                note(observations, post, state);
            }
            else if(post.getStatus() == PostStatus.CONFIRMED && openParties.contains(post.getId()) && state != null
                    && isGone(state))
            {
                observations.partyGone(post.getId());
            }
        }
        List<RecruitPost> current = posts;
        if(!observations.isEmpty())
        {
            postStore.applyObservations(observations, now);
            current = reread(posts, observations.statusTouched());
            // 방금 확정으로 옮겨 적은 글(자가 치유 — 또는 옮겨 적으려는 사이 다른 요청이 확정한 글)의 파티는 위에서 읽지 않았다 — 그것만 한 번 더 읽는다
            List<Long> newlyConfirmed = current.stream()
                    .filter(post -> post.getStatus() == PostStatus.CONFIRMED && !parties.containsKey(post.getId()))
                    .map(RecruitPost::getId)
                    .toList();
            parties.putAll(postStore.findBoardParties(newlyConfirmed));
            // 방금 닫은 파티는 위에서 ACTIVE 로 읽었다 — 다시 읽지 않고 닫힌 것으로 바꿔 이 응답부터 closed 가 참이게 한다(2026-10-01).
            // 닫기는 ACTIVE 만 바꾸는 조건부 UPDATE 라 다른 요청이 먼저 닫았어도 결과는 닫힘이다
            observations.partyGone().forEach(postId -> parties.computeIfPresent(postId, (id, party) -> party.closedNow()));
        }
        return new Observed(current, seatsOf(current, states, parties), closedOf(current, parties));
    }

    /** 파티가 닫힌 확정된 글의 번호 — 응답의 {@code closed}(2026-10-01 소유자 결정). 파티 기록이 없는 확정된 글은 닫히지 않은 것으로 친다 */
    private static Set<Long> closedOf(List<RecruitPost> posts, Map<Long, BoardParty> parties)
    {
        Set<Long> closed = new HashSet<>();
        for(RecruitPost post : posts)
        {
            BoardParty party = parties.get(post.getId());
            if(post.getStatus() == PostStatus.CONFIRMED && party != null && !party.active())
            {
                closed.add(post.getId());
            }
        }
        return closed;
    }

    /**
     * 글마다 카드로 보여 줄 사람 — 모집 중인 글은 <b>방 안에 지금 있는 사람</b>(방 키를 못 읽었으면 없다), 확정된 글은 <b>확정 순간의 파티원 전원</b>
     * (2026-09-30 — P-40. 파티 기록이 없으면 없다), 만료된 글은 없다(P-20 그대로).
     *
     * <p><b>포지션은 모집 중인 글에만 싣는다</b>(2026-10-01 소유자 결정) — 방 키를 읽을 때 같이 온 멤버 HASH 의 값({@link RoomState#positions})이라 Redis 를 더 부르지 않는다.
     * 방장의 값은 방장 포지션이다. {@code ""}(고르지 않았다)는 {@code null} 로 내보낸다. 확정된 글의 파티원은 포지션이 없다({@link Seat#partyMember}).
     */
    private static Map<Long, List<Seat>> seatsOf(List<RecruitPost> posts, Map<Long, RoomState> states, Map<Long, BoardParty> parties)
    {
        Map<Long, List<Seat>> seats = new HashMap<>();
        for(RecruitPost post : posts)
        {
            if(post.getStatus() == PostStatus.RECRUITING)
            {
                RoomState state = states.get(post.getId());
                if(state != null)
                {
                    seats.put(post.getId(), state.members().stream()
                            .map(userId -> new Seat(userId, post.isHost(userId), chosenPosition(state, userId)))
                            .toList());
                }
            }
            else if(post.getStatus() == PostStatus.CONFIRMED)
            {
                BoardParty party = parties.get(post.getId());
                if(party != null)
                {
                    seats.put(post.getId(), party.seats());
                }
            }
        }
        return seats;
    }

    /** 멤버 HASH 에 적힌 그 사람의 포지션 — 고르지 않았으면({@code ""}) {@code null} 이다 */
    private static String chosenPosition(RoomState state, Long userId)
    {
        String position = state.positions().get(userId);
        return (position == null || position.isEmpty()) ? null : position;
    }

    /**
     * 모집 중인 글 하나의 방 상태에서 옮겨 적을 것을 고른다 ({@code contracts/platform-api.md} "만료").
     * <ul>
     *   <li>확정 표시 키가 있다 → 확정을 기록한다(<b>자가 치유</b> — 확정 요청의 커밋이 실패했던 글이다). 방장 키가 없어도 그렇다 —
     *       확정한 방은 방장 키만 잠깐 없을 수 있다(D-23)</li>
     *   <li>방장 키가 없다 → <b>사라진 방이다 — 만료.</b> 글 쓰기가 방을 같이 만들므로(2026-09-25 2단계) "아직 안 만들어진 방" 이 없다.
     *       그 전에는 {@code room_seen_at} 과 10분의 유예로 둘을 갈랐다</li>
     * </ul>
     */
    private static void note(RoomObservations observations, RecruitPost post, RoomState state)
    {
        if(state.confirmed())
        {
            observations.confirmed(post, state.members());
        }
        else if(!state.hostKeyExists())
        {
            observations.vanished(post.getId());
        }
    }

    /**
     * 확정한 방이 <b>없어졌는가</b> — 방장 키 · 멤버 HASH · 확정 표시 키가 <b>셋 다</b> 없을 때만이다. 확정한 방은 <b>방장 키만 잠깐 없을 수 있다</b>
     * (방장이 말없이 사라져 승계를 기다리는 중 — D-23. 멤버의 접속 확인이 곧 넘겨받는다). 멤버 HASH 나 확정 표시 키가 남아 있으면 아직 방이다.
     * 방 번호는 글 번호라 다시 쓰이지 않는다 — 셋 다 없어진 방이 되살아나는 일은 없다.
     */
    private static boolean isGone(RoomState state)
    {
        return !state.hostKeyExists() && state.members().isEmpty() && !state.confirmed();
    }

    /**
     * 목록 · 단건이 방 안을 읽는다 — <b>fail-open 만</b>이다(못 읽어도 글은 내려 준다). fail-closed 로 읽던 고치기의 "방장만 있나" 검사는
     * 글 고치기와 함께 없어졌다(2026-10-01). 입장의 fail-closed 는 {@link PostEntryGate} 가 따로 한다.
     *
     * @return 읽지 못했으면 {@code null}
     */
    private Map<Long, RoomState> readRoomStates(List<Long> postIds)
    {
        try
        {
            return roomService.states(postIds);
        }
        catch(RoomStateUnavailableException e)
        {
            log.warn("방 키를 읽지 못해 방 정보를 비운 채 내려 준다 — 만료 판정도 하지 않는다 posts={}", postIds.size());
            return null;
        }
    }

    // ---- 자동 매칭 파티 — 사라진 방의 발견 (2026-09-28 소유자 결정) ----

    /**
     * 그 게임의 <b>열려 있는 자동 매칭 파티</b> 가운데 방이 없어진 것을 닫는다 — 게시판 파티의 <b>길 ②</b>(전원이 말없이 사라져 방 키가 수명으로 없어진 것을
     * 목록 · 단건이 발견해서 닫는다 — D-36 · P-25)의 <b>자동 매칭 판</b>이다(2026-09-28 소유자 결정 — {@code contracts/platform-api.md} "자동 매칭 파티의 방").
     *
     * <p><b>왜 여기인가</b> — 자동 매칭 파티({@code source = MATCH})는 글이 없어 {@link #observe} 가 글에서 출발해 읽는 방 키에 잡히지 않았다. 그래서 전원이
     * 말없이 사라지면 그 파티는 {@code ACTIVE} 로 영원히 남고 최근 함께한 사람도 적히지 않았다. 목록 GET 이 글을 만료 · 확정 · 닫힘으로 옮겨 적는 것은
     * 허용된 부수 효과다(CLAUDE.md §3.3) — 이것도 그 하나다. 방 키에서 읽은 사실을 옮기는 것이고 누가 불러도 결과가 같다.
     *
     * <p>규칙은 게시판 파티와 같다 — <b>방장 키 · 멤버 HASH · 확정 표시 키가 셋 다 없을 때만</b>({@link #isGone}) 닫는다(방장 키만 없는 것은 승계 중이다 — D-23).
     * 닫는 것은 {@link MatchPartyStore#closeByRoomClosed} 다 — 나가기 · 접속 확인이 쓰는 것과 같은 조건부 UPDATE 라 몇 길이 겹쳐도 한 번이고, 그 호출이
     * 최근 함께한 사람을 적는다. <b>그 게임의 파티만</b> 본다(게시판은 게임별 페이지다 — P-21). 한 번에 많아야 200개다(넘치면 다음 목록이 이어서 본다).
     * <b>방 키를 못 읽으면 아무것도 닫지 않는다</b>(목록과 같은 fail-open — 못 읽은 것을 "방이 없다"로 읽으면 멀쩡한 파티가 닫힌다).
     * 단건 조회({@link #get})에서는 부르지 않는다 — 글 하나를 보는 요청이 게임 전체를 훑을 이유가 없다.
     * 그 게임의 게시판을 아무도 열지 않으면 닫히지 않는 것은 게시판 파티의 길 ② 와 같다.
     */
    private void closeVanishedMatchParties(Game game)
    {
        List<String> partyIds = postStore.findActiveMatchPartyIds(game);
        if(partyIds.isEmpty())
        {
            return;
        }
        Map<String, RoomState> states = readRoomStatesOf(partyIds);
        if(states == null)
        {
            return;
        }
        int closed = 0;
        for(String partyId : partyIds)
        {
            RoomState state = states.get(partyId);
            if(state != null && isGone(state) && matchPartyStore.closeByRoomClosed(partyId))
            {
                closed++;
            }
        }
        if(closed > 0)
        {
            log.info("목록이 사라진 방의 자동 매칭 파티를 닫았다 game={} closed={} seen={}", game, closed, partyIds.size());
        }
    }

    /**
     * {@link #readRoomStates} 의 {@code roomId} 문자열 판 — 자동 매칭 파티의 방은 {@code roomId} 가 UUID 다. <b>fail-open 만</b>이다(목록에서만 부른다).
     *
     * @return 읽지 못했으면 {@code null}
     */
    private Map<String, RoomState> readRoomStatesOf(List<String> roomIds)
    {
        try
        {
            return roomService.statesOf(roomIds);
        }
        catch(RoomStateUnavailableException e)
        {
            log.warn("방 키를 읽지 못해 자동 매칭 파티의 닫힘 판정을 하지 않는다 parties={}", roomIds.size());
            return null;
        }
    }

    /** 상태가 바뀌었을 수 있는 글만 다시 읽어 제자리에 끼운다 — 조건부 UPDATE 가 0줄이었으면(다른 요청이 먼저 바꿨다) 무엇으로 바뀌었는지는 읽어 봐야 안다 */
    private List<RecruitPost> reread(List<RecruitPost> posts, Set<Long> touched)
    {
        if(touched.isEmpty())
        {
            return posts;
        }
        Map<Long, RecruitPost> fresh = postStore.findAll(touched).stream()
                .collect(Collectors.toMap(RecruitPost::getId, post -> post));
        return posts.stream().map(post -> fresh.getOrDefault(post.getId(), post)).toList();
    }

    // ---- 글 한 줄 조립 ----

    /**
     * 글들을 응답의 모양으로 만든다. 프로필은 게임마다 한 번, 차단은 전부 합쳐 한 번 읽는다.
     *
     * @param seats        글마다 카드로 보여 줄 사람들(모집 중이면 방 안 사람, 확정이면 확정 순간의 파티원 — {@link #observe}). 없는 글은 멤버가 비어 있다
     * @param closed       파티가 닫힌 확정된 글의 번호({@link Observed#closed}) — 응답의 {@code closed}
     * @param filterBlocked 차단 관계로 숨겨진 글을 뺄지. 방금 내가 쓴 글처럼 걸러질 수 없는 경우에만 끈다
     */
    private List<PostResponse> renderAll(Long me, List<RecruitPost> posts, Map<Long, List<Seat>> seats, Set<Long> closed,
                                         boolean filterBlocked)
    {
        Set<Long> blocked = filterBlocked ? blockedAmong(me, posts, seats) : Set.of();
        List<RecruitPost> visible = posts.stream()
                .filter(post -> !isHidden(me, post, idsOf(seats.get(post.getId())), blocked))
                .sorted(BOARD_ORDER)
                .toList();

        // 게임마다 한 번 — 카드의 profile 은 "그 글의 게임"에 연결한 게임 계정이다. 확정된 글의 파티원도 여기에 같이 싣는다(따로 읽지 않는다)
        Map<Game, Set<Long>> idsByGame = new EnumMap<>(Game.class);
        for(RecruitPost post : visible)
        {
            Set<Long> ids = idsByGame.computeIfAbsent(post.getGame(), game -> new HashSet<>());
            // 방장이 탈퇴한 확정된 글은 방장 번호가 없다(2026-10-02 · P-48) — IN 절에 null 을 싣지 않는다
            if(post.getHostId() != null)
            {
                ids.add(post.getHostId());
            }
            ids.addAll(idsOf(seats.get(post.getId())));
        }
        Map<Game, Map<Long, UserGameProfile>> profiles = new EnumMap<>(Game.class);
        idsByGame.forEach((game, ids) -> profiles.put(game, gameProfileReader.findProfiles(ids, game)));

        List<PostResponse> responses = new ArrayList<>();
        for(RecruitPost post : visible)
        {
            responses.add(render(post, seats.getOrDefault(post.getId(), List.of()), closed.contains(post.getId()),
                    profiles.get(post.getGame())));
        }
        return responses;
    }

    private static Set<Long> idsOf(List<Seat> seats)
    {
        if(seats == null || seats.isEmpty())
        {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        seats.forEach(seat -> ids.add(seat.userId()));
        return ids;
    }

    /**
     * <b>{@code id} 내림차순 하나 = 최신순이다</b>(2026-09-24 소유자 결정 — 옛 정렬은 모집 중인 글을 앞으로 당기고 그 뒤에 {@code createdAt} 을 봤다).
     * {@code id} 가 identity 라 넣은 순서대로 커진다. <b>tiebreaker 가 없어도 된다</b> — PK 라 같은 값이 둘일 수 없다.
     * 목록 쿼리의 {@code order by} 와 같은 값이어야 한다({@code RecruitPostRepository}).
     *
     * <p>글의 상태를 쓰지 않는 이유는 {@link #list} 의 주석에 있다 — 커서가 선 정렬 키는 변하지 않아야 하고 상태는 변한다.
     * 만료 · 확정된 글이 목록 위쪽에 섞여 나오는 것은 받아들인 것이다(응답의 {@code status} 로 가른다).
     */
    private static final Comparator<RecruitPost> BOARD_ORDER =
            Comparator.comparing(RecruitPost::getId, Comparator.reverseOrder());

    /**
     * 글들의 방장과 카드에 오를 사람(방 안 사람 · 확정된 글의 파티원) 가운데 나와 <b>어느 방향으로든</b> 차단 관계인 사람 — <b>쿼리 한 번이다</b>(글마다 묻지 않는다).
     * 결과를 응답에 싣지 마라 — 누가 나를 차단했는지가 새어 나간다.
     */
    private Set<Long> blockedAmong(Long me, List<RecruitPost> posts, Map<Long, List<Seat>> seats)
    {
        Set<Long> others = new HashSet<>();
        for(RecruitPost post : posts)
        {
            if(post.getHostId() != null)
            {
                others.add(post.getHostId());
            }
            others.addAll(idsOf(seats.get(post.getId())));
        }
        others.remove(me);
        return blockReader.findBlockedEitherWay(me, others);
    }

    /**
     * 나에게 숨겨야 하는 글인가 — <b>방 안의 누구든, 또는 방장</b>과 나 사이에 차단이 있으면 그렇다(D-20). 들어가면 음성으로 바로 마주치기 때문이다.
     * <b>확정된 글은 방 안 사람 대신 확정 순간의 파티원 전원과 본다</b>(2026-09-30 — P-40. Claude 가 정한 세부) — 카드로 보여 주는 사람과 같은 범위다.
     * 차단 관계인 사람의 카드가 나에게 보이는 일이 없게 하려는 것이다(방에서 나간 파티원이라도 카드에는 남는다).
     * <b>내가 쓴 글은 숨기지 않는다</b> — 내 방에 나와 차단 관계인 사람이 들어와 있어도 내 글은 내 것이다(내보내는 것은 강퇴다).
     * 입장 검사({@link PostEntryGate})도 이 판정을 쓴다 — 목록에서 숨긴 글에 들어갈 수 있으면 의미가 없다.
     * <b>방장이 탈퇴한 확정된 글</b>(방장 번호가 {@code null} — 2026-10-02 · P-48)은 남은 파티원과만 본다.
     *
     * @param members 카드에 오를 사람 — 모집 중이면 방 안 사람, 확정이면 파티원(입장 검사는 둘을 합친 것)
     */
    static boolean isHidden(Long me, RecruitPost post, Set<Long> members, Set<Long> blocked)
    {
        if(post.isHost(me) || blocked.isEmpty())
        {
            return false;
        }
        return (post.getHostId() != null && blocked.contains(post.getHostId())) || members.stream().anyMatch(blocked::contains);
    }

    /**
     * 글 한 줄. <b>{@code memberCount} 는 카드의 수</b>다 — 모집 중이면 방 안 인원, 확정이면 <b>파티원 수</b>(2026-09-30 — P-40), 만료면 0.
     * <b>{@code capacity} 는 그 글의 방 정원</b>(2026-09-30 — P-41. 그 모드의 인원 — 옛 글은 5)이다.
     * <b>{@code full} 은 모집 중인 글에서만 참이 될 수 있다</b>(Claude 가 정한 세부) — "빈자리가 없어 못 들어간다" 는 뜻이라 확정된 글은 정원이 찬 파티여도 {@code false} 다
     * (들어갈 수 없는 까닭은 {@code status} 가 말한다). 만료된 글은 전부터 카드가 없어 {@code false} 였다.
     * <b>{@code host} 카드의 {@code position}</b> 은 {@code members} 에 있는 방장 카드의 것과 같다(Claude 가 정한 세부) — 모집 중이면 방장 포지션, 확정 · 만료면 {@code null}.
     * <b>{@code closed} 는 확정된 글의 파티가 닫혔는가</b>(2026-10-01 소유자 결정) — 부르는 쪽이 파티 기록을 읽어 넘긴다({@link Observed#closed}).
     * <b>방장이 탈퇴한 확정된 글은 {@code hostId} · {@code host} 가 {@code null}</b> 이다(2026-10-02 · P-48 — 그릴 사람이 없다. 빈 카드를 지어내지 않는다).
     * {@code members} 에도 그 사람이 없다 — 그 사람의 파티원 줄은 탈퇴가 지웠다.
     */
    private static PostResponse render(RecruitPost post, List<Seat> seats, boolean closed, Map<Long, UserGameProfile> profiles)
    {
        List<MemberCard> cards = seats.stream()
                .map(seat -> card(seat.userId(), seat.host(), seat.position(), profiles))
                .sorted(CARD_ORDER)
                .toList();
        String hostCardPosition = seats.stream()
                .filter(seat -> seat.userId().equals(post.getHostId()))
                .map(Seat::position)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        // DB 의 줄에는 순서가 없다 — 그 게임의 포지션 순서로 세운다
        List<String> wanted = post.getGame().positions().stream().filter(post.getWantedPositions()::contains).toList();
        boolean full = post.getStatus() == PostStatus.RECRUITING && cards.size() >= post.getCapacity();

        return new PostResponse(post.getId(), post.getHostId(), post.getGame().name(), post.getMode(), post.getTitle(),
                post.getDescription(), post.getVoice().name(), post.getConditions(),
                wanted, post.getHostPosition(), post.getStatus().name(), post.getCreatedAt(),
                cards.size(), post.getCapacity(), full, closed,
                post.getHostId() == null ? null : card(post.getHostId(), true, hostCardPosition, profiles), cards);
    }

    /**
     * 프로필을 못 찾은 사람(이 앱에 가입하지 않은 사용자 번호)도 <b>카드에서 빼지 않는다</b> — {@code nickname} · {@code profile} 이 {@code null} 이다.
     * 빼면 {@code memberCount} 가 방의 실제 인원과 어긋난다. (확정된 글의 파티원은 가입한 사람만 적혀 있어 이런 카드가 없다 — FK.)
     */
    private static MemberCard card(Long userId, boolean host, String position, Map<Long, UserGameProfile> profiles)
    {
        UserGameProfile found = profiles.get(userId);
        return new MemberCard(userId, found == null ? null : found.nickname(), host, position,
                found == null ? null : found.profile());
    }

    /**
     * 방장 먼저, 나머지는 닉네임순(닉네임이 없는 사람은 뒤로, 그 안에서는 사용자 번호순). SET 은 순서가 없다 — 응답이 매번 흔들리지 않게.
     * <b>확정된 글의 파티원도 같은 순서다</b>(2026-09-30 — P-40) — {@code party_members.joined_at} 은 확정 순간 한 값이라 들어온 순서를 가를 수 없다
     */
    private static final Comparator<MemberCard> CARD_ORDER = Comparator
            .comparing((MemberCard card) -> card.host() ? 0 : 1)
            .thenComparing(MemberCard::nickname, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MemberCard::userId);

    /** 밀리초까지만 남긴다 — 알림 봉투의 occurredAt 과 같은 정밀도다 (CLAUDE.md §3.2) */
    private static Instant now()
    {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
