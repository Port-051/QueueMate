package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.party.board.BoardSignalPublisher;
import com.queuemate.platform.party.domain.PartyMember;
import com.queuemate.platform.party.domain.PostStatus;
import com.queuemate.platform.party.domain.RecruitPost;
import com.queuemate.platform.party.dto.PostCreateRequest;
import com.queuemate.platform.party.dto.PostUpdateRequest;
import com.queuemate.platform.party.repository.PartyRecordRepository;
import com.queuemate.platform.party.repository.RecruitPostRepository;
import com.queuemate.platform.room.RoomErrors;
import com.queuemate.platform.room.domain.Confirmation;
import com.queuemate.platform.room.domain.ConfirmResult;
import com.queuemate.platform.room.domain.CreateResult;
import com.queuemate.platform.room.domain.RoomMemberIds;
import com.queuemate.platform.room.domain.RoomState;
import com.queuemate.platform.room.domain.RoomStateUnavailableException;
import com.queuemate.platform.room.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 모집 글과 파티 기록의 <b>DB 쪽</b>. 메서드 하나가 트랜잭션 하나다.
 *
 * <p>{@link PostService} 와 나눈 이유 — 글 한 줄을 그리려면 DB 와 Redis(방 키)를 번갈아 읽는다. 그 전체를 트랜잭션 하나로 묶으면
 * Redis 를 기다리는 동안 DB 커넥션을 붙잡는다. 그래서 {@link PostService} 는 트랜잭션 없이 순서만 잡고, DB 에 닿는 토막만 여기서 짧게 끝낸다.
 *
 * <p><b>예외가 둘 있다 — 트랜잭션 안에서 방의 스크립트를 부른다</b>(2026-09-25 2단계): 글 쓰기가 방을 만들고({@link #create} — 소유자 결정 C),
 * 방장 확정이 방을 확정한다({@link #confirmRoom}). 둘 다 <b>스크립트 호출 하나(밀리초)</b>이고, 방과 글이 한쪽만 남지 않게 하려는 것이라
 * 커넥션을 그만큼 더 붙잡는 것을 받아들인다. 목록처럼 되풀이되는 읽기는 여전히 트랜잭션 밖에서 한다.
 *
 * <p><b>게시판 신호는 여기서 예약한다</b> — 글이 생기거나 · 고쳐지거나 · 만료되거나 · 확정된 트랜잭션이 <b>커밋된 뒤에</b> 한 번 나간다
 * ({@link BoardSignalPublisher#changed()}). 되돌려진 변경은 알리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostStore {

    /** 마이그레이션(V5)이 붙인 부분 UNIQUE 인덱스의 이름이다. 거기서 바꾸면 여기도 바꾼다 */
    static final String ONE_RECRUITING_PER_HOST = "recruit_posts_one_recruiting_per_host";

    private final RecruitPostRepository postRepository;
    private final PartyRecordRepository partyRecordRepository;
    private final BoardSignalPublisher boardSignal;
    private final RoomService roomService;

    /**
     * 글을 쓰고 <b>그 글의 방을 만든다</b>(2026-09-25 2단계 — 소유자 결정 C: 방을 못 만들면 글도 되돌린다). 쓴 사람이 방장이고 곧바로 방에 들어와 있다.
     *
     * <p><b>"모집 중인 글은 한 사람에 하나"는 DB 가 막는다</b> — 있는지 먼저 조회하지 않고 INSERT 한 뒤 부분 UNIQUE 인덱스의 위반을
     * 409 로 옮긴다. 같은 사람의 글 쓰기가 동시에 여러 번 와도 하나만 통과한다(CLAUDE.md §5).
     *
     * <p><b>순서</b> — INSERT({@code saveAndFlush} — 여기서 {@code id} = {@code roomId} 를 받는다) → <b>커밋 전에</b> 방 만들기 스크립트 → 커밋.
     * 스크립트가 거절하면(자동 매칭 중 · 이미 다른 방에 있다 · 그 번호의 방이 이미 있다) 그 코드로 409 를 던져 <b>글이 되돌려진다</b> —
     * 그래서 <b>이미 방에 들어가 있는 사람은 글을 쓸 수 없다</b>. Redis 에 닿지 못해도 503 으로 던져 되돌린다.
     *
     * <p><b>스크립트는 성공했는데 커밋이 실패하면</b>(드물다) 방이 Redis 에 고아로 남는다 — 방장 키 · 멤버 SET · 쓴 사람의 입장 표시 키. <b>감수한다</b>:
     * 수명(600초)이 다하면 저절로 사라지고, 그동안 그 사람은 나가기({@code DELETE …/members/me})로 풀 수 있다. 글이 없으니 목록에도 입장에도 걸리지 않는다
     * (입장은 글부터 본다 — {@link PostEntryGate}).
     */
    @Transactional
    public RecruitPost create(Long hostId, PostCreateRequest request, Instant now)
    {
        Game game = PostValidation.game(request.game());
        // mode 는 부르는 쪽이 이미 검증했다 — gameconfig(Redis)를 읽어야 해서 트랜잭션 밖에서 본다 (PostService#create)
        RecruitPost post = new RecruitPost(hostId, game,
                request.mode(), PostValidation.title(request.title()),
                PostValidation.blankToNull(request.description()), PostValidation.voice(request.voice()),
                PostValidation.purpose(request.purpose()), PostValidation.conditions(game, request.conditions()),
                PostValidation.wantedPositions(game, request.wantedPositions()), now);
        try
        {
            // id 가 null 인 새 엔티티라 persist 다 — 반드시 INSERT 가 나가고 그때 id(= roomId)를 받는다. flush 로 위반을 지금 드러낸다
            postRepository.saveAndFlush(post);
        }
        catch(DataIntegrityViolationException e)
        {
            if(ONE_RECRUITING_PER_HOST.equals(ConstraintViolations.nameOf(e)))
            {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RECRUITING", "이미 모집 중인 글이 있습니다");
            }
            throw e;
        }
        openRoom(post.getId(), hostId);
        boardSignal.changed();
        log.info("모집 글 작성 postId={} hostId={} game={}", post.getId(), hostId, game);
        return post;
    }

    /** 글의 방을 만든다 — 결과가 {@code CREATED} 가 아니면 던져서 글을 되돌린다({@link #create}) */
    private void openRoom(Long postId, Long hostId)
    {
        CreateResult result = roomService.create(String.valueOf(postId), String.valueOf(hostId));
        switch(result)
        {
            case CREATED ->
            {
            }
            case ACTIVE_REQUEST_EXISTS -> throw RoomErrors.alreadyQueued("자동 매칭을 돌리는 동안에는 모집 글을 쓸 수 없습니다");
            case IN_OTHER_ROOM -> throw RoomErrors.inOtherRoom();
            // 방금 받은 글 번호의 방이 이미 있다 — 글 번호는 DB 가 새로 매긴 것이라 정상이면 일어나지 않는다(누가 손으로 키를 넣었다).
            // ALREADY_CREATED(내가 이미 방장인 방)도 같은 까닭으로 있을 수 없는 갈래다 — 둘 다 방을 만들지 못한 것으로 보고 되돌린다
            case ROOM_EXISTS, ALREADY_CREATED -> throw new ApiException(HttpStatus.CONFLICT, "ROOM_ALREADY_EXISTS",
                    "이미 만들어진 파티방입니다");
        }
    }

    /**
     * 글을 고친다 — 준 것만 바꾼다. <b>줄을 잠그고 읽는다</b> — "모집 중인가"를 보고 저장하는 사이에 만료 · 확정이 끼어들지 못한다.
     * 글의 게임은 읽어 봐야 알아서 포지션과 {@code conditions} 의 검증이 이 안에 있다.
     *
     * <p><b>"방에 방장 말고 누가 있으면 고칠 수 없다"는 검사는 여기 없다</b>(2026-09-24 소유자 결정) — 방 키(Redis)를 읽어야 해서
     * {@link PostService#edit} 이 트랜잭션 밖에서 한다. 여기 있는 방장 · 상태 검사는 그쪽에서도 한 번 하지만 <b>잠금 안의 이 판정이 최종</b>이다.
     */
    @Transactional
    public RecruitPost edit(Long me, Long postId, PostUpdateRequest request, Instant now)
    {
        RecruitPost post = postRepository.findByIdForUpdate(postId).orElseThrow(PostStore::postNotFound);
        if(!post.isHost(me))
        {
            throw notPostHost();
        }
        if(post.getStatus() != PostStatus.RECRUITING)
        {
            throw postNotRecruiting();
        }
        Game game = post.getGame();
        post.edit(
                // 준 mode 는 부르는 쪽이 이미 검증했다(트랜잭션 밖 — PostService#edit). 빈 문자열로 비우는 길은 없어졌다
                request.mode() == null ? post.getMode() : request.mode(),
                request.title() == null ? post.getTitle() : PostValidation.title(request.title()),
                request.description() == null ? post.getDescription() : PostValidation.blankToNull(request.description()),
                request.voice() == null ? post.getVoice() : PostValidation.voice(request.voice()),
                request.purpose() == null ? post.getPurpose() : PostValidation.purpose(request.purpose()),
                request.conditions() == null ? post.getConditions() : PostValidation.conditions(game, request.conditions()),
                request.wantedPositions() == null ? new LinkedHashSet<>(post.getWantedPositions())
                        : PostValidation.wantedPositions(game, request.wantedPositions()),
                now);
        postRepository.saveAndFlush(post);
        boardSignal.changed();
        log.info("모집 글 수정 postId={}", postId);
        return post;
    }

    /**
     * 방장이 글을 지운다 — <b>지우지 않고 만료로 바꾼다</b>(CLAUDE.md §7.1). 이미 만료면 그대로 성공이고, 확정된 글은 409 다(확정은 되돌릴 수 없다).
     * 방장인지는 읽어서 본다 — {@code hostId} 는 바뀌지 않는 값이라 읽은 뒤에 달라질 수 없다. 상태는 조건부 UPDATE 가 가른다.
     */
    @Transactional
    public void expireByHost(Long me, Long postId, Instant now)
    {
        RecruitPost post = postRepository.findById(postId).orElseThrow(PostStore::postNotFound);
        if(!post.isHost(me))
        {
            throw notPostHost();
        }
        if(postRepository.expireIfRecruiting(postId, now) == 1)
        {
            boardSignal.changed();
            log.info("모집 글 만료 postId={} reason=방장이 지웠다", postId);
            return;
        }
        // 0줄 — 이미 모집 중이 아니다. 조건부 UPDATE 가 영속성 컨텍스트를 비웠으므로 다시 읽으면 지금의 상태다
        PostStatus status = postRepository.findById(postId).map(RecruitPost::getStatus).orElseThrow(PostStore::postNotFound);
        if(status == PostStatus.CONFIRMED)
        {
            throw new ApiException(HttpStatus.CONFLICT, "POST_CONFIRMED", "확정된 글은 지울 수 없습니다");
        }
    }

    @Transactional(readOnly = true)
    public Optional<RecruitPost> find(Long postId)
    {
        return postRepository.findById(postId);
    }

    /** 찾는 포지션까지 쿼리 둘이다 — 글 수만큼 되풀이하지 않는다 */
    @Transactional(readOnly = true)
    public List<RecruitPost> findAll(Collection<Long> postIds)
    {
        return postIds.isEmpty() ? List.of() : postRepository.findAllById(postIds);
    }

    /**
     * 게시판 목록에 오를 글 <b>한 페이지</b>. <b>게임 하나만이다</b>(2026-09-25 소유자 결정 — 게시판은 게임별로 나뉜 페이지다).
     * <b>글의 상태로 가리지 않는다</b> — 모집 중 · 확정 · 만료가 전부 나온다(2026-09-25 소유자 결정).
     *
     * @param game   {@code null} 일 수 없다 — 컨트롤러가 필수로 받는다
     * @param cursor 마지막으로 읽은 글의 번호. {@code null} 이면 맨 위부터, 아니면 그 글 <b>다음</b>부터다.
     *               0 이하이거나 맨 끝을 넘은 번호는 아무것도 고르지 못해 빈 목록이다 — 따로 막지 않는다({@code PostService#list})
     * @param limit  많아야 이만큼 읽는다. 부르는 쪽이 <b>보여 줄 것보다 하나 더</b> 달라고 해서 "다음이 있는가"를 안다
     *               ({@code PostService#list})
     */
    @Transactional(readOnly = true)
    public List<RecruitPost> findBoard(Game game, Long cursor, int limit)
    {
        Limit max = Limit.of(limit);
        return (cursor == null)
                ? postRepository.findBoard(game, max)
                : postRepository.findBoardAfter(game, cursor, max);
    }

    /**
     * 방 키를 읽고 알게 된 것을 <b>한 트랜잭션으로</b> 글에 옮긴다 — 방이 사라졌다 · 방장이 확정했다.
     * 전부 조건부 UPDATE 라 같은 관찰이 동시에 여러 요청에서 와도 한 번만 바뀐다. 신호는 몇 개가 바뀌든 커밋 뒤에 한 번이다.
     */
    @Transactional
    public void applyObservations(RoomObservations observations, Instant now)
    {
        for(Long postId : observations.vanished())
        {
            if(postRepository.expireIfRecruiting(postId, now) == 1)
            {
                boardSignal.changed();
                log.info("모집 글 만료 postId={} reason=방이 없다", postId);
            }
        }
        for(RoomObservations.Confirmed confirmed : observations.confirmed())
        {
            recordConfirmed(confirmed.post(), confirmed.members(), now);
        }
    }

    /**
     * <b>방장 확정</b> — {@code POST /api/v1/rooms/{roomId}/confirm} 한 요청에서 방의 확정(Redis)과 확정의 기록(DB)을 같이 한다
     * (2026-09-25 2단계. 두 앱이던 때는 브라우저가 {@code room} 의 확정 뒤에 {@code POST /api/v1/posts/{postId}/confirm} 을 따로 불렀다).
     *
     * <p><b>순서</b> — 글의 줄을 {@code FOR UPDATE} 로 잠근다 → 확정 스크립트 → {@code CONFIRMED} 면 그 스크립트가 돌려준 <b>확정 순간의 멤버</b>로
     * 파티와 파티원을 적는다 → 커밋. 스크립트가 거절하면(방이 없다 · 방장이 아니다 · 2명이 안 된다) 기록 없이 그 결과를 돌려준다(컨트롤러가 4xx 로 옮긴다).
     *
     * <p><b>스크립트는 성공했는데 커밋이 실패하면</b> "확정된 방인데 글은 모집 중" 이 남는다. <b>자가 치유가 고친다</b> — 목록 · 단건이 방 키를 읽다
     * 확정 표시 키를 보면 그 자리에서 같은 기록을 한다({@link #applyObservations}). 같은 사람이 다시 누르면 스크립트가 "이미 확정" 으로 답하고,
     * 그때 글이 아직 모집 중이면 여기서 멤버를 읽어 기록한다.
     *
     * <p><b>만료된 글의 방은 확정하지 않는다</b> — 409 {@code POST_NOT_RECRUITING}(Claude 가 정한 세부). 방장이 글을 지운 뒤에도 방은 살아 있을 수 있는데,
     * 그 방을 확정하면 파티를 적을 글이 없다(조건부 UPDATE 가 0줄이다).
     *
     * @param postId 글의 번호 = {@code roomId}. 그런 글이 없으면 방도 있을 수 없다 — 404 {@code ROOM_NOT_FOUND} 다
     */
    @Transactional
    public ConfirmResult confirmRoom(Long me, Long postId, Instant now)
    {
        RecruitPost post = postRepository.findByIdForUpdate(postId).orElseThrow(RoomErrors::roomNotFound);
        if(post.getStatus() == PostStatus.EXPIRED)
        {
            throw postNotRecruiting();
        }
        Confirmation confirmation = roomService.confirm(String.valueOf(postId), String.valueOf(me));
        if(confirmation.result() == ConfirmResult.CONFIRMED)
        {
            recordConfirmed(post, RoomMemberIds.parse(String.valueOf(postId), confirmation.members()), now);
        }
        else if(confirmation.result() == ConfirmResult.ALREADY_CONFIRMED && post.getStatus() == PostStatus.RECRUITING)
        {
            healConfirmed(post, now);
        }
        return confirmation.result();
    }

    /**
     * "이미 확정" 인데 글은 모집 중이다 — 앞선 확정의 커밋이 실패했다. 지금의 멤버 SET 으로 기록한다(확정 순간의 것과 다를 수 있다 — 감수한다).
     * 방 키를 못 읽으면 넘어간다 — 확정은 이미 성립했고, 다음 목록 · 단건이 같은 일을 한다.
     */
    private void healConfirmed(RecruitPost post, Instant now)
    {
        try
        {
            RoomState state = roomService.states(List.of(post.getId())).get(post.getId());
            recordConfirmed(post, state.members(), now);
        }
        catch(RoomStateUnavailableException e)
        {
            log.warn("확정은 됐는데 기록을 못 했다 — 목록 · 단건의 자가 치유에 맡긴다 postId={}", post.getId());
        }
    }

    /**
     * <b>방장 확정의 기록</b> — 확정 요청({@link #confirmRoom})과 자가 치유(목록 · 단건이 방 키를 읽다 발견 — {@link #applyObservations})가 같이 쓴다
     * ({@code contracts/platform-api.md} "방장 확정"). 부르기 전에 <b>방이 확정된 것을 확인했어야 한다.</b>
     *
     * <p><b>멱등이다.</b> 글을 조건부 UPDATE 로 {@code CONFIRMED} 로 바꾼 호출 하나만 파티와 파티원을 적는다 — 0줄이면 다른 호출이 이미 기록한 것이라
     * 조용히 끝낸다. 동시에 온 호출은 그 UPDATE 의 줄 잠금에서 기다렸다가 0줄을 받는다. 파티 · 파티원의 INSERT 도 {@code ON CONFLICT DO NOTHING} 이다 —
     * 파티는 {@code UNIQUE (post_id)} 가 "한 글에 하나"를 지킨다(파티의 id 는 DB 가 매긴다).
     *
     * @param members 그 순간 멤버 SET 의 전원(사용자 번호 — 숫자가 아닌 값은 방 키를 읽을 때 이미 걸러졌다). 비어 있으면(이미 다 나갔다) 방장만 기록한다.
     *                {@code is_host} 는 {@code room} 의 방장 키의 값이 아니라 <b>글의 {@code hostId}</b> 로 정한다 — 확정한 방은 방장이 바뀔 수 있다(D-23)
     * @return 이 호출이 기록했으면 {@code true}
     */
    @Transactional
    public boolean recordConfirmed(RecruitPost post, Set<Long> members, Instant now)
    {
        Long postId = post.getId();
        if(postRepository.confirmIfRecruiting(postId, now) == 0)
        {
            log.debug("방장 확정은 이미 기록됐다(또는 모집 중이 아니다) postId={}", postId);
            return false;
        }
        partyRecordRepository.insertBoardPartyIfAbsent(postId, post.getGame().name(), now);
        // 방금 넣었든(보통) 이미 있었든 그 글의 파티는 하나다 — UNIQUE (post_id). 그 id 로 파티원을 적는다
        Long partyId = partyRecordRepository.findPartyIdByPostId(postId).orElseThrow(
                () -> new IllegalStateException("방금 기록한 파티가 없다 postId=" + postId));

        Set<Long> partyMembers = new LinkedHashSet<>(members);
        if(partyMembers.isEmpty())
        {
            partyMembers.add(post.getHostId());
        }
        for(Long member : partyMembers)
        {
            partyRecordRepository.insertMemberIfAbsent(partyId, member, member.equals(post.getHostId()), now);
        }
        boardSignal.changed();
        log.info("방장 확정 기록 postId={} partyId={} members={}", postId, partyId, partyMembers.size());
        return true;
    }

    /** 확정된 파티의 파티원. 글의 id 로 파티를 찾는다({@code parties.post_id}). 확정 기록이 없으면 빈 목록이다 */
    @Transactional(readOnly = true)
    public List<PartyMember> findPartyMembers(Long postId)
    {
        return partyRecordRepository.findPartyIdByPostId(postId)
                .map(partyRecordRepository::findByPartyId)
                .orElse(List.of());
    }

    static ApiException postNotFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "없는 글입니다");
    }

    static ApiException postNotRecruiting()
    {
        return new ApiException(HttpStatus.CONFLICT, "POST_NOT_RECRUITING", "모집 중인 글이 아닙니다");
    }

    /** {@link PostService#edit} 도 쓴다 — 방 안을 보기 전에 방장인지 먼저 갈라야 해서다(그쪽 주석) */
    static ApiException notPostHost()
    {
        return new ApiException(HttpStatus.FORBIDDEN, "NOT_POST_HOST", "글을 쓴 사람만 할 수 있습니다");
    }
}
