package com.queuemate.platform.party.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.party.domain.RecruitPost;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 게시판 목록의 <b>커서</b> — "여기까지 읽었다"를 가리키는 값 (2026-09-23 소유자 결정 · {@code contracts/platform-api.md} "모집 글 · 목록 · 입장권").
 *
 * <p><b>담는 것은 글 번호 하나다</b>(2026-09-24 소유자 결정). 다음 줄을 고르는 조건도 {@code p.id < :postId} 한 줄이다
 * ({@code RecruitPostRepository#findBoardAfter}).
 *
 * <p><b>왜 그것만으로 되는가</b> — 커서는 정렬 키가 ① 순서대로 커지고 ② 겹치지 않고 ③ <b>변하지 않는다</b>는 전제 위에 선다.
 * {@code id} 는 {@code bigint GENERATED ALWAYS AS IDENTITY} 라 셋을 혼자 다 만족한다 — 그래서 <b>"최신순" 은 곧 {@code id} 내림차순</b>이고
 * 같은 값이 둘일 수 없어 tiebreaker 도 필요 없다.
 *
 * <p><b>{@code createdAt} 도 글의 상태도 담지 않는다.</b> 상태는 변하고 <b>그것도 목록 조회 자신이 바꾼다</b>(방이 사라진 글을 그 자리에서
 * 만료로 옮겨 적는다 — {@code PostService} 의 "허용된 부수 효과"). 정렬에 넣으면 1쪽에서 모집 중으로 나간 글이 그 사이 만료돼 2쪽에 <b>다시 걸린다.</b>
 * {@code createdAt} 은 변하지 않지만 {@code id} 와 같은 뜻의 값을 둘로 드는 것이고, 앱이 넣는 값과 DB 가 매기는 값이라 동시에 들어온 둘에서 어긋날 수 있다.
 *
 * <p><b>클라이언트에게는 불투명한 문자열이다</b> — 뜯어보지 말고 받은 그대로 다시 보낸다. 값이 글 번호 하나라도 <b>base64url 한 겹은 씌운다</b>:
 * 숫자로 보이면 빼고 더해서 남의 페이지를 짐작하려 든다. 다만 <b>서명하지 않는다</b> — 숨길 것이 없고 위조해도 남의 글이 보이지 않는다
 * (차단 거르기는 페이지마다 다시 한다). 그래서 <b>깨진 값에 500 이 나지 않게</b> 전부 400 {@code VALIDATION_FAILED} 로 옮긴다.
 */
record BoardCursor(long postId) {

    /** <b>마지막으로 읽은 줄</b>로 만든다 — 마지막으로 <b>보여 준</b> 줄이 아니다(차단으로 숨겨진 글을 다음 페이지에서 또 읽지 않게) */
    static BoardCursor of(RecruitPost post)
    {
        return new BoardCursor(post.getId());
    }

    String encode()
    {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(Long.toString(postId).getBytes(StandardCharsets.UTF_8));
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
            long postId = Long.parseLong(new String(Base64.getUrlDecoder().decode(raw.trim()), StandardCharsets.UTF_8));
            if(postId < 1)
            {
                throw invalid();
            }
            return new BoardCursor(postId);
        }
        catch(ApiException e)
        {
            throw e;
        }
        catch(RuntimeException e)
        {
            // IllegalArgumentException(base64 · 숫자) 뿐이지만 무엇이 오든 500 이 아니라 400 이다
            throw invalid();
        }
    }

    private static ApiException invalid()
    {
        return ApiException.validationFailed("cursor", "읽을 수 없는 값입니다 — 받은 그대로 보내야 합니다");
    }
}
