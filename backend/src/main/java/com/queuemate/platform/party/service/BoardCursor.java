package com.queuemate.platform.party.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.party.domain.PostStatus;
import com.queuemate.platform.party.domain.RecruitPost;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 게시판 목록의 <b>커서</b> — "여기까지 읽었다"를 가리키는 값 (2026-09-23 소유자 결정 · {@code contracts/platform-api.md} "모집 글 · 목록 · 입장권").
 *
 * <p><b>정렬에 쓰는 값 셋을 그대로 담는다</b> — {@code statusOrder}(모집 중이 0, 나머지 1) · {@code createdAt} · {@code postId}.
 * 하나라도 빠지면 같은 시각에 쓰인 글에서 어긋난다(건너뛰거나 두 번 보인다). 다음 줄을 고르는 조건은 이 셋의 튜플 비교다
 * ({@code RecruitPostRepository#findBoardAfter}).
 *
 * <p><b>클라이언트에게는 불투명한 문자열이다</b> — 뜯어보지 말고 받은 그대로 다시 보낸다. 다만 <b>서명하지 않는다</b>:
 * 숨길 것이 없고 위조해도 남의 글이 보이지 않는다(차단 거르기는 페이지마다 다시 한다). 뜻이 드러나도 상관없는 값만 담는다.
 * 그래서 base64url 한 겹뿐이다 — <b>깨진 값에 500 이 나지 않게</b> 전부 400 {@code VALIDATION_FAILED} 로 옮긴다.
 *
 * <p>시각은 <b>마이크로초</b>로 담는다 — PostgreSQL 의 {@code timestamptz} 가 마이크로초까지라서 그 아래로 잃을 것이 없고,
 * 밀리초로 줄이면 같은 밀리초 안의 글에서 튜플 비교가 어긋난다.
 */
record BoardCursor(int statusOrder, Instant createdAt, long postId) {

    /** 모집 중인 글이 먼저다 — {@code PostService} 의 정렬(그리고 목록 쿼리의 {@code order by})과 같은 값이어야 한다 */
    static int statusOrder(PostStatus status)
    {
        return status == PostStatus.RECRUITING ? 0 : 1;
    }

    /** <b>마지막으로 읽은 줄</b>로 만든다 — 마지막으로 <b>보여 준</b> 줄이 아니다(차단으로 숨겨진 글을 다음 페이지에서 또 읽지 않게) */
    static BoardCursor of(RecruitPost post)
    {
        return new BoardCursor(statusOrder(post.getStatus()), post.getCreatedAt(), post.getId());
    }

    String encode()
    {
        String plain = statusOrder + "|" + micros(createdAt) + "|" + postId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param raw 없거나 비어 있으면 {@code null}(맨 위부터)
     * @throws ApiException 400 {@code VALIDATION_FAILED} — 못 읽는 값이다({@code details} 에 {@code cursor} 한 줄)
     */
    static BoardCursor decode(String raw)
    {
        if(raw == null || raw.isBlank())
        {
            return null;
        }
        try
        {
            String plain = new String(Base64.getUrlDecoder().decode(raw.trim()), StandardCharsets.UTF_8);
            String[] parts = plain.split("\\|");
            if(parts.length != 3)
            {
                throw invalid();
            }
            int statusOrder = Integer.parseInt(parts[0]);
            long micros = Long.parseLong(parts[1]);
            long postId = Long.parseLong(parts[2]);
            if(statusOrder < 0 || statusOrder > 1 || postId < 1)
            {
                throw invalid();
            }
            return new BoardCursor(statusOrder, Instant.EPOCH.plus(micros, ChronoUnit.MICROS), postId);
        }
        catch(ApiException e)
        {
            throw e;
        }
        catch(RuntimeException e)
        {
            // IllegalArgumentException(base64 · 숫자 · 시각의 범위) 뿐이지만 무엇이 오든 500 이 아니라 400 이다
            throw invalid();
        }
    }

    private static long micros(Instant at)
    {
        return ChronoUnit.MICROS.between(Instant.EPOCH, at);
    }

    private static ApiException invalid()
    {
        return ApiException.validationFailed("cursor", "읽을 수 없는 값입니다 — 받은 그대로 보내야 합니다");
    }
}
