package com.queuemate.platform.party.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.common.gameconfig.ModePositions;
import com.queuemate.platform.party.board.BoardSignalPublisher;
import com.queuemate.platform.party.domain.PartyMember;
import com.queuemate.platform.party.domain.PostStatus;
import com.queuemate.platform.party.domain.RecruitPost;
import com.queuemate.platform.party.domain.VoicePreference;
import com.queuemate.platform.party.dto.PostCreateRequest;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 모집 글과 파티 기록의 <b>DB 쪽</b>. 메서드 하나가 트랜잭션 하나다.
 *
 * <p>{@link PostService} 와 나눈 이유 — 글 한 줄을 그리려면 DB 와 Redis(방 키)를 번갈아 읽는다. 그 전체를 트랜잭션 하나로 묶으면
 * Redis 를 기다리는 동안 DB 커넥션을 붙잡는다. 그래서 {@link PostService} 는 트랜잭션 없이 순서만 잡고, DB 에 닿는 토막만 여기서 짧게 끝낸다.
 *
 * <p><b>예외가 셋 있다 — 트랜잭션 안에서 방의 스크립트를 부른다</b>(2026-09-25 2단계): 글 쓰기가 방을 만들고({@link #create} — 소유자 결정 C),
 * 방장 확정이 방을 확정하고({@link #confirmRoom}), 글 지우기가 방을 닫는다({@link #expireByHost} — 2026-09-25 소유자 결정 "확정 전에는 방과 글이 같이 끝난다").
 * 셋 다 <b>스크립트 호출 하나(밀리초)</b>이고, 방과 글이 한쪽만 남지 않게 하려는 것이라 커넥션을 그만큼 더 붙잡는 것을 받아들인다. 목록처럼 되풀이되는 읽기는 여전히 트랜잭션 밖에서 한다.
 *
 * <p><b>게시판 신호는 여기서 예약한다</b> — 글이 생기거나 · 만료되거나 · 확정되거나 · 파티가 닫힌({@link PostLifecycle#closeParty} — 2026-10-02) 트랜잭션이 <b>커밋된 뒤에</b>
 * 한 번 나간다(글은 고칠 수 없다 — 2026-10-01 소유자 결정)
 * ({@link BoardSignalPublisher#changed()}). 되돌려진 변경은 알리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostStore {

    /** 마이그레이션(V1__schema.sql)이 붙인 부분 UNIQUE 인덱스의 이름이다. 거기서 바꾸면 여기도 바꾼다 */
    static final String ONE_RECRUITING_PER_HOST = "recruit_posts_one_recruiting_per_host";
    /** 방장의 칸에서 {@code users(id)} 로 가는 FK 의 이름이다(V1__schema.sql) */
    static final String HOST_FKEY = "recruit_posts_host_id_fkey";

    /** 입장과 수정이 같은 행 잠금을 사용해 이전 조건으로 입장하는 경합을 막는다. */
    @Transactional
    public java.util.Optional<RecruitPost> findForEntry(Long postId) {
        return postRepository.findByIdForUpdate(postId);
    }

    @Transactional
    public void update(Long me, Long postId, PostCreateRequest request, ModePositions positions, int capacity, Instant now) {
        RecruitPost post = postRepository.findByIdForUpdate(postId).orElseThrow(PostStore::postNotFound);
        if (post.getStatus() == PostStatus.EXPIRED) throw postNotRecruiting();
        Game game = PostValidation.game(request.game());
        if (game != post.getGame()) throw ApiException.validationFailed("game", "방의 게임은 변경할 수 없습니다");
        String title = PostValidation.title(request.title());
        String description = PostValidation.blankToNull(request.description());
        VoicePreference voice = PostValidation.voice(request.voice());
        String conditions = PostValidation.conditions(game, request.conditions());
        Set<String> wanted = PostValidation.wantedPositionsForMode(game, positions,
                PostValidation.wantedPositions(game, request.wantedPositions()));
        String hostPosition = PostValidation.hostPosition(game, positions, request.hostPosition(), wanted);
        boolean metadataOnly = post.getStatus() == PostStatus.CONFIRMED;
        if (metadataOnly && (!java.util.Objects.equals(post.getMode(), request.mode())
                || !java.util.Objects.equals(post.getDescription(), description)
                || !java.util.Objects.equals(post.getHostPosition(), hostPosition)
                || !post.getWantedPositions().equals(wanted) || capacity != post.getCapacity()
                || !post.getConditions().equals(conditions) || request.allowAutoJoin() != post.isAllowAutoJoin())) {
            throw ApiException.validationFailed("mode", "모집이 마감된 방은 제목과 마이크만 변경할 수 있습니다");
        }
        // Flush DB constraints before Lua. A Lua rejection rolls the database transaction back.
        post.updateSettings(request.mode(), title, description, voice, conditions, wanted, hostPosition, capacity, request.allowAutoJoin(), now);
        postRepository.flush();
        Map<Long, String> members = roomSettingsService.update(String.valueOf(postId), String.valueOf(me),
                capacity, hostPosition, wanted, metadataOnly);
        if (!metadataOnly && members.size() >= capacity) confirmRoom(me, postId, now);
        boardSignal.changed();
    }

    /** 입장 검사({@link PostEntryGate})의 두 거절 코드 — 게시판 방 먼저 합류({@code AutoJoinService})가 "다음 방으로 넘어갈 거절" 을 가르는 데 쓴다 */
    static final String POST_NOT_FOUND = "POST_NOT_FOUND";
    static final String POST_NOT_RECRUITING = "POST_NOT_RECRUITING";

    private final RecruitPostRepository postRepository;
    private final PartyRecordRepository partyRecordRepository;
    private final BoardSignalPublisher boardSignal;
    private final RoomService roomService;
    private final com.queuemate.platform.room.service.RoomSettingsService roomSettingsService;
    private final PostLifecycle postLifecycle;
    private final RecruitmentTiming recruitmentTiming;

    /** 명단 변경마다 대기 시간을 다시 시작한다. 한 명뿐이면 자동 확정하지 않는다. */
    @Transactional
    public boolean rosterChanged(Long postId, Map<Long, String> fullSnapshot, Instant now) {
        RecruitPost post = postRepository.findByIdForUpdate(postId).orElse(null);
        if (post == null || post.getStatus() != PostStatus.RECRUITING) return false;
        if (fullSnapshot != null) {
            return recordConfirmed(post, fullSnapshot.keySet(), now, fullSnapshot);
        }
        RoomState state = roomService.states(List.of(postId)).get(postId);
        if (state.confirmed()) {
            return recordConfirmed(post, state.members(), now, state.positions());
        } else if (state.hostKeyExists()) {
            post.setAutoConfirmAt(state.members().size() >= 2 ? recruitmentTiming.deadline(now) : null);
            boardSignal.changed();
            return true;
        }
        return false;
    }

    @Transactional
    public void checkAutomaticConfirmation(Long postId, Instant now) {
        if (!recruitmentTiming.enabled()) return;
        RecruitPost post = postRepository.findByIdForUpdate(postId).orElse(null);
        if (post == null || post.getStatus() != PostStatus.RECRUITING) return;
        RoomState state = roomService.states(List.of(postId)).get(postId);
        if (state.confirmed()) {
            recordConfirmed(post, state.members(), now, state.positions());
            return;
        }
        if (!state.hostKeyExists()) return;
        if (state.members().size() < 2) {
            if (post.getAutoConfirmAt() != null) { post.setAutoConfirmAt(null); boardSignal.changed(); }
            return;
        }
        if (post.getAutoConfirmAt() == null) {
            post.setAutoConfirmAt(recruitmentTiming.deadline(now));
            boardSignal.changed();
        } else if (!post.getAutoConfirmAt().isAfter(now)) {
            confirmRoom(post.getHostId(), postId, now);
        }
    }

    /** 안내 중인 방장만 연장할 수 있다. 스케줄러와 같은 행 잠금으로 만료 경합을 처리한다. */
    @Transactional
    public void extendRecruitment(Long me, Long postId, Instant now) {
        RecruitPost post = postRepository.findByIdForUpdate(postId).orElseThrow(RoomErrors::roomNotFound);
        if (!post.isHost(me)) throw notPostHost();
        if (post.getStatus() != PostStatus.RECRUITING) throw postNotRecruiting();
        Instant deadline = post.getAutoConfirmAt();
        if (!recruitmentTiming.enabled() || deadline == null || !deadline.isAfter(now) || recruitmentTiming.warningAt(deadline).isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "RECRUITMENT_NOT_EXPIRING", "연장할 수 있는 안내 시간이 아닙니다");
        }
        RoomState state = roomService.states(List.of(postId)).get(postId);
        if (!state.hostKeyExists() || state.confirmed()) throw postNotRecruiting();
        post.setAutoConfirmAt(recruitmentTiming.deadline(now));
        boardSignal.changed();
    }

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
     * <p><b>스크립트는 성공했는데 커밋이 실패하면</b>(드물다) 방이 Redis 에 고아로 남는다 — 방장 키 · 멤버 HASH · 쓴 사람의 입장 표시 키. <b>감수한다</b>:
     * 수명(600초)이 다하면 저절로 사라지고, 그동안 그 사람은 나가기({@code DELETE …/members/me})로 풀 수 있다. 글이 없으니 목록에도 입장에도 걸리지 않는다
     * (입장은 글부터 본다 — {@link PostEntryGate}).
     *
     * @param modePositions 그 모드에 포지션이 있는가 — 방장 포지션의 규칙이다(2026-09-30 — P-38). gameconfig(Redis)를 읽어야 해서 부르는 쪽이 트랜잭션 밖에서 읽었다
     * @param capacity      방의 정원 — 그 모드의 인원(2026-09-30 — P-41). 같은 까닭으로 부르는 쪽이 트랜잭션 밖에서 읽었다(모르면 5)
     */
    @Transactional
    public RecruitPost create(Long hostId, PostCreateRequest request, ModePositions modePositions, int capacity, Instant now)
    {
        Game game = PostValidation.game(request.game());
        // mode 는 부르는 쪽이 이미 검증했다 — gameconfig(Redis)를 읽어야 해서 트랜잭션 밖에서 본다 (PostService#create)
        String title = PostValidation.title(request.title());
        String description = PostValidation.blankToNull(request.description());
        VoicePreference voice = PostValidation.voice(request.voice());
        String conditions = PostValidation.conditions(game, request.conditions());
        // 이름 → 모드에 따른 규칙(있는 모드는 하나 이상 · 없는 모드는 빈 배열만 — 2026-09-30, P-38)
        Set<String> wanted = PostValidation.wantedPositionsForMode(game, modePositions,
                PostValidation.wantedPositions(game, request.wantedPositions()));
        // 방장 포지션은 찾는 포지션과 겹치면 안 된다 — 찾는 포지션을 검증한 뒤에 본다
        String hostPosition = PostValidation.hostPosition(game, modePositions, request.hostPosition(), wanted);
        // 빠른매치 입장 허용 / 금지는 필수 칸이라(@NotNull — 2026-10-02 · P-50) 여기서는 늘 값이 있다
        RecruitPost post = new RecruitPost(hostId, game, request.mode(), title, description, voice, conditions, wanted,
                hostPosition, capacity, request.allowAutoJoin(), now);
        try
        {
            // id 가 null 인 새 엔티티라 persist 다 — 반드시 INSERT 가 나가고 그때 id(= roomId)를 받는다. flush 로 위반을 지금 드러낸다
            postRepository.saveAndFlush(post);
        }
        catch(DataIntegrityViolationException e)
        {
            String constraint = ConstraintViolations.nameOf(e);
            if(ONE_RECRUITING_PER_HOST.equals(constraint))
            {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RECRUITING", "이미 모집 중인 글이 있습니다");
            }
            if(HOST_FKEY.equals(constraint))
            {
                // 토큰은 멀쩡한데 그 사용자가 DB 에 없다 — 먼저 조회해서 확인하지 않고 FK 위반으로 안다
                throw ApiException.unauthenticated();
            }
            throw e;
        }
        openRoom(post.getId(), hostId, wanted, request.hostPosition());
        boardSignal.changed();
        log.info("모집 글 작성 postId={} hostId={} game={} capacity={} allowAutoJoin={}", post.getId(), hostId, game, capacity,
                post.isAllowAutoJoin());
        return post;
    }

    /** 글의 방을 만든다 — 결과가 {@code CREATED} 가 아니면 던져서 글을 되돌린다({@link #create}) */
    private void openRoom(Long postId, Long hostId, Set<String> wanted,  String hostPosition)
    {
        CreateResult result = roomService.create(String.valueOf(postId), String.valueOf(hostId), wanted, hostPosition);
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
     * 방장이 글을 지운다 — <b>지우지 않고 만료로 바꾸고, 그 글의 방도 닫는다</b>(CLAUDE.md §7.1 · 2026-09-25 소유자 결정 — "확정 전에는 방과 글이 같이 끝난다").
     * 이미 만료면 그대로 성공이고, 확정된 글은 409 다(확정은 되돌릴 수 없다 — 방도 건드리지 않는다).
     * 방장인지는 읽어서 본다 — {@code hostId} 는 바뀌지 않는 값이라 읽은 뒤에 달라질 수 없다. 상태는 조건부 UPDATE 가 가른다.
     *
     * <p><b>방 닫기는 방장 나가기와 같은 길이다</b> — 방장으로서 나가기 스크립트를 부른다({@link RoomService#leave}). 방장 키 · 멤버 HASH · 전원의 입장 표시 키가
     * 한 스크립트 안에서 지워지고 방에 있던 사람들이 {@code ROOM_CLOSED} 를 받는다. 전에는 글만 만료되고 방이 남아, 방장이 새 글을 쓰려면
     * 먼저 방에서 나와야 했다(409 {@code IN_OTHER_ROOM}).
     *
     * <p><b>순서 — 글의 만료가 먼저, 방 닫기가 뒤다. 방 닫기가 실패해도 되돌리지 않는다</b>(WARN 만 남긴다). 글 쓰기가 "방을 못 만들면 글을 되돌린다"({@link #create})와
     * 반대 방향인 이유 — 쓰기는 없던 것을 여는 일이라 절반만 되면 입장할 수 없는 글이 남지만, <b>지우기는 이미 끝난 것을 정리하는 일이라 절반만 돼도 해가 없다.</b>
     * 글이 만료되면 새 사람이 못 들어오고(입장이 글부터 본다 — {@link PostEntryGate}), 남은 방은 수명(600초)이 다해 사라지거나 방장이 나가기로 닫는다.
     * 같은 이유로 스크립트는 성공했는데 커밋이 실패해도 해가 없다 — 방이 없는 모집 중인 글은 목록 · 단건이 만료로 옮겨 적는다.
     *
     * <p><b>이미 만료된 글을 또 지우면 방 닫기만 다시 해 본다</b> — 앞선 지우기의 방 닫기가 실패했을 때 방장이 다시 눌러 정리할 수 있다. 방이 이미 없으면
     * 스크립트가 "이 방에 없다" 로 답하고 아무것도 지우지 않는다. 방 닫기는 트랜잭션 안에서 부른다 — 스크립트 호출 하나(밀리초)이고, 그래야 방의 알림 · 신호가
     * 글의 만료와 함께 커밋 뒤에 나가고 게시판 신호가 하나로 합쳐진다({@link BoardSignalPublisher#changed()}).
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
            closeRoomAsHost(postId, me);
            return;
        }
        // 0줄 — 이미 모집 중이 아니다. 조건부 UPDATE 가 영속성 컨텍스트를 비웠으므로 다시 읽으면 지금의 상태다
        PostStatus status = postRepository.findById(postId).map(RecruitPost::getStatus).orElseThrow(PostStore::postNotFound);
        if(status == PostStatus.CONFIRMED)
        {
            throw new ApiException(HttpStatus.CONFLICT, "POST_CONFIRMED", "확정된 글은 지울 수 없습니다");
        }
        closeRoomAsHost(postId, me);
    }

    /**
     * 글의 방을 방장으로서 닫는다 — 실패해도 던지지 않는다({@link #expireByHost} 의 "순서"). 글은 이미 만료됐으니 방이 닫혔을 때 글에 할 일이 없다 —
     * 그래서 닫힌 뒤의 일({@code whenClosed})은 "신호를 따로 내지 않았다"({@code false})다. 방의 신호는 이 트랜잭션의 신호와 합쳐진다.
     *
     * <p>확정 전의 방은 방장 키의 값이 늘 글의 {@code hostId} 라 이 스크립트가 방을 닫는다. 방장 키가 다른 값이면(누가 손으로 넣었다) 스크립트는
     * 방장을 일반 멤버로 보고 멤버 HASH 에서만 뺀다 — 방을 억지로 닫지 않는다.
     */
    private void closeRoomAsHost(Long postId, Long hostId)
    {
        try
        {
            roomService.leave(String.valueOf(postId), String.valueOf(hostId), () -> false);
        }
        catch(RuntimeException e)
        {
            log.warn("글은 만료됐는데 방을 닫지 못했다 — 수명이 다하거나 방장이 나가면 닫힌다 postId={}: {}", postId, e.toString());
        }
    }

    /** 그 사람의 모집 중인 글의 번호 — 많아야 하나다. 회원 탈퇴가 그 글을 {@link #expireByHost} 로 끝낸다({@code PostService#expireRecruitingOf}) */
    @Transactional(readOnly = true)
    public Optional<Long> findRecruitingOf(Long hostId)
    {
        return postRepository.findRecruitingIdByHostId(hostId);
    }

    /**
     * <b>회원 탈퇴의 글 쪽 정리</b>(2026-10-02 소유자 결정 · P-48) — 그 사람이 쓴 글 가운데 <b>확정되지 않은 것(모집 중 · 만료)을 지운다.</b>
     * <b>확정된 글은 남긴다</b>("확정된 파티 기록은 남긴다") — 방장의 칸은 부르는 쪽이 이어서 {@code users} 를 지울 때 FK 의 {@code ON DELETE SET NULL} 이 비우고(V9),
     * 그 사람의 파티원 줄은 {@code ON DELETE CASCADE} 가 지운다. 다른 파티원의 줄과 파티는 그대로다.
     *
     * <p><b>부르는 쪽의 트랜잭션에 합류한다</b> — {@code users} 를 지우는 바로 그 트랜잭션 안에서, 그 사용자의 줄을 잠근 뒤({@code FOR UPDATE}) 지우기 직전에 불러야 한다.
     * 따로 커밋하면 그 사이에 생긴 모집 중인 글 때문에 SET NULL 이 CHECK {@code recruit_posts_host_id_check} 에 걸린다(그러면 탈퇴가 통째로 되돌려진다 — 방장 없는 모집 중인 글은 생기지 않는다).
     * 사용자 줄의 잠금이 그 사이의 글 쓰기를 막는다 — 글 쓰기의 INSERT 가 FK 검사({@code FOR KEY SHARE})에서 기다렸다가 사용자가 지워진 뒤 401 이 된다.
     *
     * <p><b>게시판 신호</b> — 그 사람의 글이 하나라도 있었거나(지운 글은 목록에서 빠지고 확정된 글은 {@code host} 가 빈다) 게시판 파티의 파티원이었으면(확정된 글의 카드에서 빠진다)
     * 커밋 뒤에 한 번 낸다({@link BoardSignalPublisher#changed()}). 되돌려지면 나가지 않는다.
     *
     * @return 지운 글의 수
     */
    @Transactional
    public int deleteUnconfirmedOf(Long hostId)
    {
        boolean onBoard = postRepository.existsByHostId(hostId) || partyRecordRepository.isBoardPartyMember(hostId);
        int deleted = postRepository.deleteUnconfirmedByHostId(hostId);
        if(onBoard)
        {
            boardSignal.changed();
        }
        return deleted;
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
     * 게시판 방 먼저 합류의 후보 글 — 그 게임 · 그 모드 · 그 음성의 모집 중인 글에서 <b>내 글과 빠른매치 입장을 금지한 글(P-50)을 뺀 것</b>을 오래된 순으로 많아야 {@code limit} 개
     * ({@link RecruitPostRepository#findAutoJoinCandidates}. 2026-09-28 · P-28. 음성 · 내 글 제외는 2026-09-29 에 자바에서 쿼리로 옮겼다 — 그 이유는 리포지토리 주석).
     * 찾는 포지션까지 쿼리 둘이다. 부르는 쪽({@code AutoJoinService})은 트랜잭션 밖에서 방 키를 읽고 스크립트를 부른다
     */
    @Transactional(readOnly = true)
    public List<RecruitPost> findAutoJoinCandidates(Game game, String mode, VoicePreference voice, Long me, int limit)
    {
        return postRepository.findAutoJoinCandidates(game, mode, voice, me, Limit.of(limit));
    }

    /**
     * 확정된 글들의 <b>파티</b> — 열려 있는가와 <b>확정 순간의 파티원</b>. <b>쿼리 한 번이다</b>(글 수만큼 되풀이하지 않는다 — {@link PartyRecordRepository#findBoardParties}).
     * 목록 · 단건이 ① 파티가 열려 있는 글만 방 키를 읽어 "방이 없어졌나" 를 보고(파티 닫힘 — P-25) ② 확정된 글의 카드를 파티원으로 그린다
     * (2026-09-30 소유자 결정 — P-40. {@code PostService#observe}).
     *
     * @return 글 번호 → 그 글의 파티. <b>파티 기록이 없는 글은 결과에 없다</b>(확정 기록은 글의 확정과 같은 트랜잭션이라 정상이면 없을 수 없다 — SQL 로 손으로 넣은 글 등)
     */
    @Transactional(readOnly = true)
    public Map<Long, BoardParty> findBoardParties(Collection<Long> postIds)
    {
        if(postIds.isEmpty())
        {
            return Map.of();
        }
        Map<Long, Boolean> active = new LinkedHashMap<>();
        Map<Long, List<BoardParty.Seat>> seats = new HashMap<>();
        for(Object[] row : partyRecordRepository.findBoardParties(postIds))
        {
            Long postId = ((Number) row[0]).longValue();
            active.put(postId, "ACTIVE".equals(row[1]));
            List<BoardParty.Seat> of = seats.computeIfAbsent(postId, id -> new ArrayList<>());
            // 파티원이 한 명도 없는 파티는 LEFT JOIN 의 빈 줄 하나다(탈퇴로 전원이 지워졌다 등)
            if(row[2] != null)
            {
                of.add(new BoardParty.Seat(((Number) row[2]).longValue(), Boolean.TRUE.equals(row[3]), (String) row[4]));
            }
        }
        Map<Long, BoardParty> parties = new LinkedHashMap<>();
        active.forEach((postId, open) -> parties.put(postId, new BoardParty(open, List.copyOf(seats.get(postId)))));
        return parties;
    }

    /**
     * 그 게임의 <b>아직 열려 있는 자동 매칭 파티</b>의 {@code match_party_id}(= 방의 {@code roomId}, UUID) — 목록이 그 방 키를 같이 읽어 "방이 없어졌나" 를
     * 본다({@code PostService#closeVanishedMatchParties} — 2026-09-28 소유자 결정). 자동 매칭 파티는 글이 없어 {@link #findBoardParties} 에 잡히지 않는다.
     * 많아야 200개다({@link PartyRecordRepository#findActiveMatchPartyIds}).
     */
    @Transactional(readOnly = true)
    public List<String> findActiveMatchPartyIds(Game game)
    {
        return partyRecordRepository.findActiveMatchPartyIds(game.name());
    }

    /**
     * 방 키를 읽고 알게 된 것을 <b>한 트랜잭션으로</b> 글에 옮긴다 — 방이 사라졌다 · 방장이 확정했다 · 확정한 방이 사라졌다(파티 닫힘 — 2026-09-26).
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
        // 파티 닫기는 나가기 · 접속 확인과 같은 메서드다 — 두 길이 겹쳐도 조건부 UPDATE 가 한 번만 통과시킨다. 이 트랜잭션에 합류한다
        for(Long postId : observations.partyGone())
        {
            postLifecycle.closeParty(postId, now);
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
     * <p><b>만료된 글의 방은 확정하지 않는다</b> — 409 {@code POST_NOT_RECRUITING}(Claude 가 정한 세부). 글을 지우면 방도 닫히지만 방 닫기가 실패했으면
     * 방이 살아 있을 수 있다({@link #expireByHost}). 그 방을 확정하면 파티를 적을 글이 없다(조건부 UPDATE 가 0줄이다).
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
            recordConfirmed(post, RoomMemberIds.parse(String.valueOf(postId), confirmation.members()), now, confirmation.positions());
        }
        else if(confirmation.result() == ConfirmResult.ALREADY_CONFIRMED && post.getStatus() == PostStatus.RECRUITING)
        {
            healConfirmed(post, now);
        }
        return confirmation.result();
    }

    /**
     * "이미 확정" 인데 글은 모집 중이다 — 앞선 확정의 커밋이 실패했다. 지금의 멤버 HASH 로 기록한다(확정 순간의 것과 다를 수 있다 — 감수한다).
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
     * @param members 그 순간 멤버 HASH 의 전원(사용자 번호 — 숫자가 아닌 값은 방 키를 읽을 때 이미 걸러졌다). 비어 있으면(이미 다 나갔다) 방장만 기록한다.
     *                <b>가입하지 않은 번호는 적지 않는다</b> — {@code party_members.user_id} 의 FK 때문이다({@link PartyRecordRepository#insertMemberIfAbsent}).
     *                {@code is_host} 는 {@code room} 의 방장 키의 값이 아니라 <b>글의 {@code hostId}</b> 로 정한다 — 확정한 방은 방장이 바뀔 수 있다(D-23)
     * @return 이 호출이 기록했으면 {@code true}
     */
    @Transactional
    public boolean recordConfirmed(RecruitPost post, Set<Long> members, Instant now)
    {
        Map<Long, String> positions = roomService.states(List.of(post.getId())).get(post.getId()).positions();
        return recordConfirmed(post, members, now, positions);
    }

    private boolean recordConfirmed(RecruitPost post, Set<Long> members, Instant now, Map<Long, String> positions)
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
            String position = positions.get(member);
            if (position != null && !position.isBlank()) partyRecordRepository.rememberPosition(partyId, member, position);
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
        return new ApiException(HttpStatus.NOT_FOUND, POST_NOT_FOUND, "없는 글입니다");
    }

    static ApiException postNotRecruiting()
    {
        return new ApiException(HttpStatus.CONFLICT, POST_NOT_RECRUITING, "모집 중인 글이 아닙니다");
    }

    private static ApiException notPostHost()
    {
        return new ApiException(HttpStatus.FORBIDDEN, "NOT_POST_HOST", "글을 쓴 사람만 할 수 있습니다");
    }
}
