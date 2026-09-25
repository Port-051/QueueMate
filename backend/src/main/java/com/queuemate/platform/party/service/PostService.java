package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.dto.UserGameProfile;
import com.queuemate.platform.account.service.GameProfileReader;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.gameconfig.GameConfigReader;
import com.queuemate.platform.party.domain.PartyMember;
import com.queuemate.platform.party.domain.PostStatus;
import com.queuemate.platform.party.domain.RecruitPost;
import com.queuemate.platform.party.dto.MemberCard;
import com.queuemate.platform.party.dto.PostCreateRequest;
import com.queuemate.platform.party.dto.PostListResponse;
import com.queuemate.platform.party.dto.PostResponse;
import com.queuemate.platform.party.dto.PostUpdateRequest;
import com.queuemate.platform.party.dto.TicketResponse;
import com.queuemate.platform.party.room.RoomState;
import com.queuemate.platform.party.room.RoomStateReader;
import com.queuemate.platform.party.room.RoomStateUnavailableException;
import com.queuemate.platform.social.service.BlockReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 파티 모집 게시판 — 글 · 실시간 목록 · 입장권 · 방장 확정의 기록 ({@code contracts/platform-api.md} "모집 글 · 목록 · 입장권").
 *
 * <p><b>글 한 줄은 이 앱이 전부 조립한다</b>(docs/11 D-20) — 글(DB) + 방 안에 누가 있나({@code room} 의 방 키, <b>읽기만 한다</b>) +
 * 그 사람들의 게임 프로필({@code account} 의 창구) + 차단 거르기({@code social} 의 창구). 서비스 간 호출은 없다 — {@code room} 의 API 를 부르지 않는다.
 *
 * <p><b>글이 몇 개든 왕복 수가 같다</b> — 글 쿼리 하나(+ 찾는 포지션 하나), Redis 파이프라인 한 번, 프로필은 게임마다 한 번(많아야 세 번),
 * 차단은 한 번. 이 목록은 게시판 신호가 올 때마다 다시 불린다 — 글 수나 사람 수만큼 되풀이하지 마라.
 *
 * <p><b>이 클래스에는 트랜잭션이 없다</b> — DB 에 닿는 토막은 {@link PostStore} 가 짧게 끝낸다. Redis 를 기다리는 동안 DB 커넥션을 붙잡지 않는다.
 *
 * <p><b>목록 조회(GET)가 글을 만료 · 확정으로 바꾸고 {@code room_seen_at} 을 적는다</b> — {@code room} 은 방이 없어져도 이 앱에 알리지 않아서
 * 이 앱이 방 키를 볼 때 스스로 옮겨 적는다(CLAUDE.md §3.3). 허용된 부수 효과이고 전부 조건부 UPDATE 라 멱등하다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(BoardProperties.class)
public class PostService {

    private final PostStore postStore;
    private final RoomStateReader roomStateReader;
    private final GameProfileReader gameProfileReader;
    private final BlockReader blockReader;
    private final RoomTicketIssuer roomTicketIssuer;
    private final BoardProperties boardProperties;
    private final GameConfigReader gameConfig;

    /**
     * 글을 쓴다. <b>전적을 긁지 않는다</b>(2026-09-24 소유자 결정 — 2026-09-23 의 "긁는 시점은 둘" 가운데 이쪽을 되물렸다.
     * {@code contracts/platform-api.md} "전적을 긁는 것" · P-13) — 긁는 것은 <b>비동기</b>라 이 응답의 {@code host.profile.stats} 에
     * 반영되지도 않으면서 Riot 호출 20여 번을 쓴다. 전적은 <b>게임 계정을 저장할 때만</b> 갱신된다.
     */
    public PostResponse create(Long me, PostCreateRequest request)
    {
        // gameconfig(Redis)를 읽는 검증은 여기서 한다 — PostStore 의 트랜잭션이 Redis 를 기다리며 DB 커넥션을 붙잡지 않게 (2026-09-24)
        PostValidation.mode(gameConfig, PostValidation.game(request.game()), request.mode());
        RecruitPost post = postStore.create(me, request, now());
        // 방금 쓴 글이다 — 방이 있을 수 없다(브라우저가 이 응답의 id 로 room 의 방 만들기를 부른다). 방 키를 읽지 않는다
        return renderAll(me, List.of(post), Map.of(), false).getFirst();
    }

    /**
     * 글을 고친다. <b>방에 방장 말고 누가 있으면 고칠 수 없다</b>(2026-09-24 <b>소유자 결정</b> — {@code contracts/platform-api.md} "모집 글 · 목록 · 입장권" · P-19).
     *
     * <p><b>왜</b> — 고칠 수 있는 칸에 {@code mode} · {@code voice} · {@code purpose} · {@code conditions} 가 있다. {@code NO_VOICE} 를 보고 들어와 앉아 있는
     * 사람 앞에서 {@code REQUIRED} 로 바꿀 수 있고, <b>그 사람에게 바뀌었다고 알려 줄 길이 없다</b> — 게시판 신호는 목록을 보는 사람에게 가고,
     * 방 안 알림({@code ROOM_*})은 {@code room} 이 내는데 이 앱은 {@code room} 을 부르지 않는다(CLAUDE.md §3.3). 그래서 칸을 가리지 않고 아예 막는다.
     *
     * <p><b>방 키와 gameconfig 는 트랜잭션 밖에서 읽는다</b> — {@link PostStore#edit} 은 글의 줄을 잠근 채 돌아서, 그 안에서 Redis 를 기다리면 DB 커넥션을
     * 붙잡는다(두 클래스를 나눈 이유가 그것이다 — 이 클래스의 머리 주석). 대가로 <b>읽은 뒤 저장하기 전에 누가 들어오는 창이 남는다</b> — 그 창은 짧고,
     * 막으려는 것이 "사람이 있는데 조건이 바뀌는 것" 이라 감수한다({@code contracts/platform-api.md}).
     */
    public PostResponse edit(Long me, Long postId, PostUpdateRequest request)
    {
        // 잠금 밖에서 글을 한 번만 읽는다 — 모드 검증(글의 게임)과 방 안 사람 검사가 같이 쓴다. 없는 글이면 여기서 이미 404 다(PostStore#edit 과 같다)
        RecruitPost post = postStore.find(postId).orElseThrow(PostStore::postNotFound);
        if(request.mode() != null)
        {
            // 글의 game 은 바뀌지 않으므로 잠금 밖에서 읽어도 뒤에 달라질 수 없다
            PostValidation.mode(gameConfig, post.getGame(), request.mode());
        }
        // 방 안을 보기 전에 방장 · 상태를 본다 — 남의 글이나 끝난 글에 "방에 사람이 있다"를 알려 주면 그 자체가 새는 정보다.
        // 최종 판정은 줄을 잠그는 PostStore#edit 이 다시 한다(그 사이에 만료 · 확정이 끼어들 수 있다)
        if(!post.isHost(me))
        {
            throw PostStore.notPostHost();
        }
        if(post.getStatus() != PostStatus.RECRUITING)
        {
            throw PostStore.postNotRecruiting();
        }
        requireHostAlone(post);
        postStore.edit(me, postId, request, now());
        // 고친 글은 방이 떠 있는 글이다 — 방 안 사람까지 채운 한 줄을 돌려준다. 방장의 글이라 차단으로 걸러지지 않는다
        return get(me, postId);
    }

    /**
     * 방에 <b>방장 말고</b> 누가 있으면 고치지 못하게 막는다(위 {@link #edit}). 방이 아직 없거나(방 만들기를 안 불렀다) 방장 혼자면 고칠 수 있다 —
     * 바뀐 조건을 보고 들어온 사람이 없다.
     *
     * <p><b>방 키를 못 읽으면 막는다</b> — 입장권 발급과 <b>같은 503 {@code ROOM_STATE_UNAVAILABLE}</b> 이다(fail-closed). 누가 방에 있는지 확인이 안 되는데
     * 고치게 하면 이 규칙이 없는 것과 같고, 사유("방의 상태를 확인할 수 없다")도 그쪽과 똑같아서 코드를 새로 두지 않았다.
     */
    private void requireHostAlone(RecruitPost post)
    {
        RoomState state = readRoomStates(List.of(post.getId()), true).get(post.getId());
        if(state == null)
        {
            return;
        }
        // 사용자 번호로 팔 수 없는 값은 방 키를 읽는 자리에서 이미 걸러졌다(RedisRoomStateReader) — 그런 값 때문에 막히지는 않는다
        if(state.members().stream().anyMatch(member -> !post.isHost(member)))
        {
            throw new ApiException(HttpStatus.CONFLICT, "ROOM_HAS_OTHER_MEMBERS",
                    "방에 다른 사람이 있으면 글을 고칠 수 없습니다");
        }
    }

    public void delete(Long me, Long postId)
    {
        postStore.expireByHost(me, postId, now());
    }

    /**
     * 게시판 목록 <b>한 페이지</b>. 차단 관계로 숨겨진 글은 빠져 있다 — 빠졌다는 흔적도 없다.
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
     * 입장권 발급도 그 글을 보고 같은 일을 하므로 "들어갈 수 있는 죽은 방"은 생기지 않는다({@code contracts/platform-api.md}).
     *
     * <p><b>글을 상태로 가리지 않는다</b>(2026-09-25 소유자 결정) — 모집 중 · 확정 · 만료가 전부 {@code id} 내림차순으로 나오고 <b>끝난 글도 계속 남는다.</b>
     * 그 전에는 만료 · 확정된 지 10분이 안 된 글만 남겼는데, 그 조건이 세 컬럼에 걸친 {@code OR} 셋이라 {@code (game, id DESC)} 인덱스를
     * 깨끗하게 타지 못했다({@code RecruitPostRepository#findBoard}). 끝난 글은 "모집이 얼마나 활발한가"를 보여 주는 쪽으로도 쓰인다.
     */
    public PostListResponse list(Long me, String gameName, Integer limitParam, Long cursor)
    {
        Game game = (gameName == null || gameName.isBlank()) ? null : PostValidation.game(gameName);
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
            Observed observed = observe(page, now, false);
            visible.addAll(renderAll(me, observed.posts(), observed.members(), true));
            if(!more)
            {
                break;
            }
        }
        return new PostListResponse(visible, more ? cursor : null);
    }

    /** 단건. 차단 관계로 숨겨진 글은 없는 글과 똑같이 404 다 — 숨겨졌다는 것을 알려 주지 않는다 */
    public PostResponse get(Long me, Long postId)
    {
        RecruitPost post = postStore.find(postId).orElseThrow(PostStore::postNotFound);
        Observed observed = observe(List.of(post), now(), false);
        return renderAll(me, observed.posts(), observed.members(), true).stream()
                .findFirst().orElseThrow(PostStore::postNotFound);
    }

    /**
     * 입장권. <b>발급 직전에 방 키를 새로 읽는다</b> — 방이 사라졌으면 글을 만료시키고, 확정됐으면 기록하고(길 ②), 방 안의 <b>전원</b>과 차단을 대조한다
     * (목록에서만 숨기고 입장은 되면 의미가 없다 — D-20). 방 키를 읽지 못하면 내주지 않는다(fail-closed) — 차단 대조를 못 했다.
     */
    public TicketResponse ticket(Long me, Long postId)
    {
        RecruitPost post = postStore.find(postId).orElseThrow(PostStore::postNotFound);
        Observed observed = observe(List.of(post), now(), true);
        RecruitPost current = observed.posts().getFirst();
        Set<Long> members = observed.members().getOrDefault(postId, Set.of());

        // 숨겨진 글인지를 상태보다 먼저 본다 — 차단 관계인 사람에게는 "모집이 끝났다"도 알려 주지 않는다
        if(isHidden(me, current, members, blockedAmong(me, List.of(current), observed.members())))
        {
            throw PostStore.postNotFound();
        }
        if(current.getStatus() != PostStatus.RECRUITING)
        {
            throw PostStore.postNotRecruiting();
        }
        TicketResponse ticket = roomTicketIssuer.issue(me, current.getId(), current.getHostId());
        log.info("입장권 발급 postId={} userId={}", postId, me);
        return ticket;
    }

    /**
     * 방장 확정의 기록, 길 ① — 브라우저가 {@code room} 의 확정이 성공한 뒤 부른다. <b>그 말을 믿지 않는다</b> — 확정 표시 키와 멤버 SET 을
     * 직접 읽어 검증한 뒤 기록한다. 그래서 부르는 사람이 방장이 아니어도 된다 — 읽은 것만 기록한다.
     *
     * <p>응답의 {@code members} 는 <b>파티원</b>이다(DB 에 적힌 것). 이미 확정된 글이면 기록 없이 같은 응답이다 — 두 번 불러도 결과가 같다.
     */
    public PostResponse confirm(Long me, Long postId)
    {
        RecruitPost post = postStore.find(postId).orElseThrow(PostStore::postNotFound);
        if(post.getStatus() == PostStatus.RECRUITING)
        {
            RoomState state = readRoomStates(List.of(postId), true).get(postId);
            if(state == null || !state.confirmed())
            {
                throw new ApiException(HttpStatus.CONFLICT, "ROOM_NOT_CONFIRMED", "방장이 확정하지 않은 방입니다");
            }
            postStore.recordConfirmed(post, state.members(), now());
            // 이 호출이 기록했든 동시에 온 다른 호출이 기록했든 지금의 상태를 다시 읽는다
            post = postStore.find(postId).orElseThrow(PostStore::postNotFound);
        }
        if(post.getStatus() != PostStatus.CONFIRMED)
        {
            // 만료된 글이다(방장이 글을 지운 뒤에 방을 확정했다 등). 계약의 confirm 에 이 갈래가 없어 가장 가까운 코드로 답한다
            throw PostStore.postNotRecruiting();
        }
        Set<Long> partyMembers = postStore.findPartyMembers(postId).stream()
                .map(PartyMember::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return renderAll(me, List.of(post), Map.of(postId, partyMembers), true).stream()
                .findFirst().orElseThrow(PostStore::postNotFound);
    }

    // ---- 방 키 읽기와 옮겨 적기 ----

    /** 방 키를 읽은 뒤의 글과, 글마다 방 안에 지금 있는 사람. 모집 중이 아닌 글은 {@code members} 에 없다(멤버를 비운다) */
    private record Observed(List<RecruitPost> posts, Map<Long, Set<Long>> members) {
    }

    /**
     * 모집 중인 글들의 방 키를 <b>한 번에</b> 읽고, 알게 된 것을 글에 옮겨 적은 뒤, 지금의 글과 방 안 사람을 돌려준다.
     *
     * <p><b>방 키를 읽지 못하면</b>({@code failClosed} 가 아닐 때) 방 정보를 비운 채 글만 돌려주고 <b>아무것도 옮겨 적지 않는다</b> —
     * 못 읽은 것을 "방이 없다"로 읽으면 멀쩡한 글이 전부 만료된다.
     */
    private Observed observe(List<RecruitPost> posts, Instant now, boolean failClosed)
    {
        List<Long> recruitingIds = posts.stream()
                .filter(post -> post.getStatus() == PostStatus.RECRUITING)
                .map(RecruitPost::getId)
                .toList();
        if(recruitingIds.isEmpty())
        {
            return new Observed(posts, Map.of());
        }
        Map<Long, RoomState> states = readRoomStates(recruitingIds, failClosed);
        if(states == null)
        {
            return new Observed(posts, Map.of());
        }

        RoomObservations observations = new RoomObservations();
        for(RecruitPost post : posts)
        {
            RoomState state = states.get(post.getId());
            if(post.getStatus() == PostStatus.RECRUITING && state != null)
            {
                note(observations, post, state, now);
            }
        }
        List<RecruitPost> current = posts;
        if(!observations.isEmpty())
        {
            postStore.applyObservations(observations, now);
            current = reread(posts, observations.statusTouched());
        }

        Map<Long, Set<Long>> members = new HashMap<>();
        for(RecruitPost post : current)
        {
            RoomState state = states.get(post.getId());
            if(post.getStatus() == PostStatus.RECRUITING && state != null)
            {
                members.put(post.getId(), state.members());
            }
        }
        return new Observed(current, members);
    }

    /**
     * 글 하나의 방 상태에서 옮겨 적을 것을 고른다 ({@code contracts/platform-api.md} 의 {@code room_seen_at} 대목).
     * <ul>
     *   <li>확정 표시 키가 있다 → 확정을 기록한다(길 ②). 방장 키가 없어도 그렇다 — 확정한 방은 방장 키만 잠깐 없을 수 있다(D-23)</li>
     *   <li>방장 키가 있다 → 처음 본 것이면 {@code room_seen_at} 을 적는다</li>
     *   <li>방장 키가 없다 → <b>봤던 방이면</b> 사라진 것이다. 본 적이 없으면 아직 안 만들어진 방이다 — 쓴 지 {@code roomGrace} 가 지났을 때만 만료시킨다.
     *       <b>방금 쓴 글을 만료시키지 않는다</b>(CLAUDE.md §11)</li>
     * </ul>
     */
    private void note(RoomObservations observations, RecruitPost post, RoomState state, Instant now)
    {
        if(state.confirmed())
        {
            observations.confirmed(post, state.members());
        }
        else if(state.hostKeyExists())
        {
            if(post.getRoomSeenAt() == null)
            {
                observations.firstSeen(post.getId());
            }
        }
        else if(post.getRoomSeenAt() != null || !post.getCreatedAt().plus(boardProperties.roomGrace()).isAfter(now))
        {
            observations.vanished(post.getId());
        }
    }

    /** @return 읽지 못했고 {@code failClosed} 가 아니면 {@code null} */
    private Map<Long, RoomState> readRoomStates(List<Long> postIds, boolean failClosed)
    {
        try
        {
            return roomStateReader.read(postIds);
        }
        catch(RoomStateUnavailableException e)
        {
            if(failClosed)
            {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ROOM_STATE_UNAVAILABLE",
                        "방의 상태를 확인할 수 없습니다. 잠시 뒤에 다시 시도해 주세요");
            }
            log.warn("방 키를 읽지 못해 방 정보를 비운 채 내려 준다 — 만료 판정도 하지 않는다 posts={}", postIds.size());
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
     * @param members      글마다 카드로 보여 줄 사람들. 없는 글은 멤버가 비어 있다
     * @param filterBlocked 차단 관계로 숨겨진 글을 뺄지. 방금 내가 쓴 글처럼 걸러질 수 없는 경우에만 끈다
     */
    private List<PostResponse> renderAll(Long me, List<RecruitPost> posts, Map<Long, Set<Long>> members,
                                         boolean filterBlocked)
    {
        Set<Long> blocked = filterBlocked ? blockedAmong(me, posts, members) : Set.of();
        List<RecruitPost> visible = posts.stream()
                .filter(post -> !isHidden(me, post, members.getOrDefault(post.getId(), Set.of()), blocked))
                .sorted(BOARD_ORDER)
                .toList();

        // 게임마다 한 번 — 카드의 profile 은 "그 글의 게임"에 연결한 게임 계정이다
        Map<Game, Set<Long>> idsByGame = new EnumMap<>(Game.class);
        for(RecruitPost post : visible)
        {
            Set<Long> ids = idsByGame.computeIfAbsent(post.getGame(), game -> new HashSet<>());
            ids.add(post.getHostId());
            ids.addAll(members.getOrDefault(post.getId(), Set.of()));
        }
        Map<Game, Map<Long, UserGameProfile>> profiles = new EnumMap<>(Game.class);
        idsByGame.forEach((game, ids) -> profiles.put(game, gameProfileReader.findProfiles(ids, game)));

        List<PostResponse> responses = new ArrayList<>();
        for(RecruitPost post : visible)
        {
            responses.add(render(post, members.getOrDefault(post.getId(), Set.of()), profiles.get(post.getGame())));
        }
        return responses;
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
     * 글들의 방장과 방 안 사람 가운데 나와 <b>어느 방향으로든</b> 차단 관계인 사람 — <b>쿼리 한 번이다</b>(글마다 묻지 않는다).
     * 결과를 응답에 싣지 마라 — 누가 나를 차단했는지가 새어 나간다.
     */
    private Set<Long> blockedAmong(Long me, List<RecruitPost> posts, Map<Long, Set<Long>> members)
    {
        Set<Long> others = new HashSet<>();
        for(RecruitPost post : posts)
        {
            others.add(post.getHostId());
            others.addAll(members.getOrDefault(post.getId(), Set.of()));
        }
        others.remove(me);
        return blockReader.findBlockedEitherWay(me, others);
    }

    /**
     * 나에게 숨겨야 하는 글인가 — <b>방 안의 누구든, 또는 방장</b>과 나 사이에 차단이 있으면 그렇다(D-20). 들어가면 음성으로 바로 마주치기 때문이다.
     * <b>내가 쓴 글은 숨기지 않는다</b> — 내 방에 나와 차단 관계인 사람이 들어와 있어도 내 글은 내 것이다(내보내는 것은 강퇴다).
     */
    private static boolean isHidden(Long me, RecruitPost post, Set<Long> members, Set<Long> blocked)
    {
        if(post.isHost(me) || blocked.isEmpty())
        {
            return false;
        }
        return blocked.contains(post.getHostId()) || members.stream().anyMatch(blocked::contains);
    }

    private static PostResponse render(RecruitPost post, Set<Long> memberIds, Map<Long, UserGameProfile> profiles)
    {
        List<MemberCard> cards = memberIds.stream()
                .map(userId -> card(userId, post, profiles))
                .sorted(CARD_ORDER)
                .toList();
        // DB 의 줄에는 순서가 없다 — 그 게임의 포지션 순서로 세운다
        List<String> wanted = post.getGame().positions().stream().filter(post.getWantedPositions()::contains).toList();

        return new PostResponse(post.getId(), post.getHostId(), post.getGame().name(), post.getMode(), post.getTitle(),
                post.getDescription(), post.getVoice().name(), post.getPurpose().name(), post.getConditions(),
                wanted, post.getStatus().name(), post.getCreatedAt(),
                cards.size(), BoardProperties.ROOM_CAPACITY, cards.size() >= BoardProperties.ROOM_CAPACITY,
                card(post.getHostId(), post, profiles), cards);
    }

    /**
     * 프로필을 못 찾은 사람(이 앱에 가입하지 않은 사용자 번호)도 <b>카드에서 빼지 않는다</b> — {@code nickname} · {@code profile} 이 {@code null} 이다.
     * 빼면 {@code memberCount} 가 방의 실제 인원과 어긋난다.
     */
    private static MemberCard card(Long userId, RecruitPost post, Map<Long, UserGameProfile> profiles)
    {
        UserGameProfile found = profiles.get(userId);
        return new MemberCard(userId, found == null ? null : found.nickname(), post.isHost(userId),
                found == null ? null : found.profile());
    }

    /** 방장 먼저, 나머지는 닉네임순(닉네임이 없는 사람은 뒤로, 그 안에서는 사용자 번호순). SET 은 순서가 없다 — 응답이 매번 흔들리지 않게 */
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
