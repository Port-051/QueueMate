package com.queuemate.platform.party.controller;

import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.party.dto.PostCreateRequest;
import com.queuemate.platform.party.dto.PostListResponse;
import com.queuemate.platform.party.dto.PostResponse;
import com.queuemate.platform.party.dto.PostUpdateRequest;
import com.queuemate.platform.party.dto.TicketResponse;
import com.queuemate.platform.party.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 파티 모집 게시판. 경로 · 본문 · 에러 코드의 원본은 {@code contracts/platform-api.md} "모집 글 · 목록 · 입장권" 이다.
 *
 * <p><b>"나"는 access 토큰에서 온다</b>({@link CurrentUserId}) — 남의 이름으로 글을 쓰거나 남의 눈으로 목록을 볼 길이 없다(목록은 보는 사람마다
 * 차단 거르기가 다르다). 목록의 단위는 <b>모집 글(방)</b>이다 — 사람을 검색하거나 둘러보는 요청은 만들지 않는다(CLAUDE.md §1).
 *
 * <p>{@code postId} 가 숫자가 아니면 400 이다({@code GlobalExceptionHandler#handleTypeMismatch}).
 * <b>방 안의 일(입장 · 강퇴 · 확정 요청)은 여기 없다</b> — {@code room} 의 일이다. {@code …/confirm} 은 확정을 <b>하는</b> 요청이 아니라
 * {@code room} 에서 이미 된 확정을 이 앱이 <b>기록하게 하는</b> 요청이다.
 */
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public ResponseEntity<PostResponse> create(@CurrentUserId Long userId, @Valid @RequestBody PostCreateRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.create(userId, request));
    }

    @PatchMapping("/{postId}")
    public PostResponse edit(@CurrentUserId Long userId, @PathVariable("postId") Long postId,
                             @Valid @RequestBody PostUpdateRequest request)
    {
        return postService.edit(userId, postId, request);
    }

    /** 지우지 않고 만료로 바꾼다. 이미 만료면 그대로 204 다 — 두 번 눌러도 결과가 같다 */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable("postId") Long postId)
    {
        postService.delete(userId, postId);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code game} 이 없으면 세 게임 전부다.
     *
     * <p><b>페이지는 커서로 나눈다</b>(2026-09-23 소유자 결정) — {@code limit} 은 없으면 20, 최대 100(벗어나면 400 {@code VALIDATION_FAILED}),
     * {@code cursor} 는 앞선 응답의 {@code nextCursor} 를 <b>그대로</b> 보내는 불투명한 문자열이다(못 읽으면 400).
     * {@code limit} 이 숫자가 아니면 형 변환에서 400 이다({@code GlobalExceptionHandler#handleTypeMismatch}).
     * 게시판 신호를 받았을 때는 <b>커서 없이 펼친 만큼의 {@code limit}</b> 으로 맨 위부터 다시 받는다.
     */
    @GetMapping
    public PostListResponse list(@CurrentUserId Long userId,
                                 @RequestParam(name = "game", required = false) String game,
                                 @RequestParam(name = "limit", required = false) Integer limit,
                                 @RequestParam(name = "cursor", required = false) String cursor)
    {
        return postService.list(userId, game, limit, cursor);
    }

    @GetMapping("/{postId}")
    public PostResponse get(@CurrentUserId Long userId, @PathVariable("postId") Long postId)
    {
        return postService.get(userId, postId);
    }

    @PostMapping("/{postId}/ticket")
    public TicketResponse ticket(@CurrentUserId Long userId, @PathVariable("postId") Long postId)
    {
        return postService.ticket(userId, postId);
    }

    @PostMapping("/{postId}/confirm")
    public PostResponse confirm(@CurrentUserId Long userId, @PathVariable("postId") Long postId)
    {
        return postService.confirm(userId, postId);
    }
}
