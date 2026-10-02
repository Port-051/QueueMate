package com.queuemate.platform.social.domain;

/**
 * 차단 관계 사본(Redis {@code qm:user:block-rel:*})을 <b>고치지 못했다</b>(Redis 가 죽었거나 느리다 — 2026-10-02 · docs/11 D-57 · P-52).
 * 차단 · 해제는 이것을 503 {@code BLOCK_STATE_UNAVAILABLE} 로 바꿔 트랜잭션째 되돌린다 — DB 에는 있는데 사본에는 없는 차단이 생기지 않게 한다
 * ({@code BlockService}). 회원 탈퇴는 받아 넘긴다(재구성이 치운다 — {@code AccountDeletionService}).
 */
public class BlockRelationUnavailableException extends RuntimeException {

    public BlockRelationUnavailableException(Throwable cause)
    {
        super("차단 관계 사본(Redis)을 고치지 못했다", cause);
    }
}
