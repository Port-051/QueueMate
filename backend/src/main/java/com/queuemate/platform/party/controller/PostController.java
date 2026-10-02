package com.queuemate.platform.party.controller;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.party.dto.AutoJoinRequest;
import com.queuemate.platform.party.dto.AutoJoinResponse;
import com.queuemate.platform.party.dto.PostCreateRequest;
import com.queuemate.platform.party.dto.PostListResponse;
import com.queuemate.platform.party.dto.PostResponse;
import com.queuemate.platform.party.service.AutoJoinService;
import com.queuemate.platform.party.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 파티 모집 게시판. 경로 · 본문 · 에러 코드의 원본은 {@code contracts/platform-api.md} "모집 글 · 목록" 이다.
 *
 * <p><b>"나"는 access 토큰에서 온다</b>({@link CurrentUserId}) — 남의 이름으로 글을 쓰거나 남의 눈으로 목록을 볼 길이 없다(목록은 보는 사람마다
 * 차단 거르기가 다르다). 목록의 단위는 <b>모집 글(방)</b>이다 — 사람을 검색하거나 둘러보는 요청은 만들지 않는다(CLAUDE.md §1).
 *
 * <p>{@code postId} 가 숫자가 아니면 400 이다({@code GlobalExceptionHandler#handleTypeMismatch}).
 * <b>방 안의 일(입장 · 강퇴 · 방장 확정)은 여기 없다</b> — {@code /api/v1/rooms/**}({@code room} 패키지)다. 2026-09-25 2단계로 입장권
 * ({@code …/ticket})과 확정의 기록({@code …/confirm})이 여기서 빠졌다 — 입장이 글을 직접 검사하고, 방장 확정 한 요청이 기록까지 한다.
 *
 * <p><b>{@code POST /api/v1/posts/auto-join}</b>(2026-09-28)은 {@code /{postId}} 와 겹치지 않는다 — {@code POST} 가 걸린 경로 변수 매핑이 없고, 있더라도 스프링은
 * 글자 그대로의 경로를 경로 변수보다 먼저 고른다.
 *
 * <p>2026-10-03 방 설정 요청: 현재 방장이 PATCH로 조건을 수정한다. 게임은 고정이며, 마감 뒤에는 제목과 마이크만 수정한다.
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final AutoJoinService autoJoinService;

    /**
     * 글을 쓰고 <b>그 글의 방을 같이 만든다</b>(2026-09-25 2단계 — 소유자 결정 C). 쓴 사람이 방장이고 방에 들어와 있다 — 응답의 {@code members} 에 방장이 있다.
     * 방을 못 만들면 글도 안 써진다 — 409 {@code ALREADY_QUEUED}(자동 매칭 중) · {@code IN_OTHER_ROOM}(이미 방에 있다) · 503 {@code ROOM_STATE_UNAVAILABLE}.
     */
    @PostMapping
    public ResponseEntity<PostResponse> create(@CurrentUserId Long userId, @Valid @RequestBody PostCreateRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(userId, request));
    }

    @PatchMapping("/{postId}")
    public PostResponse update(@CurrentUserId Long userId, @PathVariable("postId") Long postId,
                               @Valid @RequestBody PostCreateRequest request) {
        return postService.update(userId, postId, request);
    }

    /**
     * <b>자동 매칭 전에 조건 맞는 게시판 방에 먼저 합류한다</b>(2026-09-28 소유자 결정 · P-28 · docs/11 D-40). 본문은 {@code matching} 의 매칭 요청과 같은 모양이다 —
     * 맞는 방이 있으면 넣고 200 {@code {postId, roomId}}, 없으면 404 {@code NO_MATCHING_POST}(프런트가 {@code matching} 을 부른다).
     * 409 {@code IN_OTHER_ROOM} · {@code ALREADY_QUEUED}(다음 방으로 넘어갈 수 없는 거절) · 503 {@code ROOM_STATE_UNAVAILABLE}(Redis 를 못 읽었다 — fail-closed).
     * 활성 요청 키를 만들지 않는다 — 누른 순간 한 번만 본다({@link AutoJoinService}).
     */
    @PostMapping("/auto-join")
    public AutoJoinResponse autoJoin(@CurrentUserId Long userId, @Valid @RequestBody AutoJoinRequest request)
    {
        return autoJoinService.join(userId, request);
    }

    /** 지우지 않고 만료로 바꾼다. 이미 만료면 그대로 204 다 — 두 번 눌러도 결과가 같다 */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable("postId") Long postId)
    {
        postService.delete(userId, postId);
        return ResponseEntity.noContent().build();
    }

    /**
     * <b>{@code game} 은 필수다</b>(2026-09-25 <b>소유자 결정</b>) — <b>게시판은 게임별로 나뉜 페이지이고 "세 게임 전부" 화면이 없다.</b>
     * 그 전에는 없으면 전부 내려 주었는데 아무도 쓰지 않는 갈래였다({@code contracts/platform-api.md} "모집 글 · 목록" · P-21).
     *
     * <p><b>페이지는 커서로 나눈다</b>(2026-09-23 소유자 결정) — {@code limit} 은 없으면 20, 최대 100(벗어나면 400 {@code VALIDATION_FAILED}),
     * {@code cursor} 는 앞선 응답의 {@code nextCursor}(<b>마지막으로 읽은 글의 번호</b>)를 그대로 보내는 것이다.
     * 게시판 신호를 받았을 때는 <b>커서 없이 펼친 만큼의 {@code limit}</b> 으로 맨 위부터 다시 받는다.
     *
     * <p><b>세 파라미터의 값이 틀리면 여기 닿기 전에 400 이다</b> — 모르는 게임 이름({@code ?game=LOLL})과 숫자가 아닌
     * {@code limit} · {@code cursor} 는 형 변환에서({@code GlobalExceptionHandler#handleTypeMismatch}), {@code game} 을 아예 안 보냈으면
     * 파라미터를 채우는 자리에서({@code GlobalExceptionHandler#handleMissingParam}) 떨어진다. <b>그래서 이 셋을 검사하는 코드가 따로 없다.</b>
     * <b>{@code game} 은 대소문자를 가린다</b> — {@code ?game=lol} 은 스프링의 기본 enum 변환이 받지 않아 400 이다(계약의 이름은 대문자다).
     */
    @GetMapping
    public PostListResponse list(@CurrentUserId Long userId,
                                 @RequestParam(name = "game") Game game,
                                 @RequestParam(name = "limit", required = false) Integer limit,
                                 @RequestParam(name = "cursor", required = false) Long cursor)
    {
        return postService.list(userId, game, limit, cursor);
    }

    @GetMapping("/{postId}")
    public PostResponse get(@CurrentUserId Long userId, @PathVariable("postId") Long postId)
    {
        return postService.get(userId, postId);
    }
}
