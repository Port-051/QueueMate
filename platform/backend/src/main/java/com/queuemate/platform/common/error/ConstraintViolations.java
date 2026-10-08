package com.queuemate.platform.common.error;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Set;

/**
 * DB 의 제약 위반에서 <b>어느 제약인지</b>를 꺼낸다. 불변식은 DB 가 지키고(CLAUDE.md §5) 앱은 위반을 에러 코드로 옮긴다 —
 * 그러려면 어느 제약이 깨졌는지 알아야 한다. 그래서 마이그레이션의 제약에는 전부 이름을 붙인다.
 */
public final class ConstraintViolations {

    /**
     * 사용자 번호를 담는 칸에서 {@code users(id)} 로 가는 FK 의 이름 전부({@code V1__schema.sql} — 2026-09-26 소유자 결정으로 걸었다).
     * 이 가운데 하나가 깨졌다 = <b>그런 사용자가 없다</b>. 대상이 있는 사용자인지를 조회로 먼저 보지 않고 INSERT 의 이 위반을
     * 404 {@code USER_NOT_FOUND} 로 옮긴다({@link #isMissingUser}). 이름을 바꾸면 마이그레이션과 같이 바꾼다.
     *
     * <p><b>"나"의 칸</b>({@code blocks_blocker_id_fkey} · {@code friend_requests_requester_id_fkey} · {@code reports_reporter_id_fkey} ·
     * {@code recruit_posts_host_id_fkey})이 깨진 것은 토큰은 멀쩡한데 그 사용자가 DB 에 없다는 뜻이라 부르는 쪽이 401 로 먼저 가른다.
     */
    private static final Set<String> USER_FOREIGN_KEYS = Set.of(
            "blocks_blocker_id_fkey",
            "blocks_blocked_id_fkey",
            "friend_requests_requester_id_fkey",
            "friend_requests_receiver_id_fkey",
            "friendships_user_low_id_fkey",
            "friendships_user_high_id_fkey",
            "reports_reporter_id_fkey",
            "reports_target_user_id_fkey",
            "recent_players_user_id_fkey",
            "recent_players_other_user_id_fkey",
            "recruit_posts_host_id_fkey",
            "party_members_user_id_fkey");

    private ConstraintViolations()
    {
    }

    /**
     * 깨진 제약의 이름(예: {@code users_nickname_key}). 제약 위반이 아니거나 이름을 알 수 없으면 {@code null} —
     * 그때는 부른 쪽이 예외를 그대로 다시 던진다(500 이 된다. 모르는 위반을 아는 에러 코드로 둔갑시키지 않는다).
     */
    public static String nameOf(DataIntegrityViolationException e)
    {
        for(Throwable cause = e; cause != null; cause = cause.getCause())
        {
            if(cause instanceof ConstraintViolationException violation)
            {
                return violation.getConstraintName();
            }
        }
        return null;
    }

    /** 깨진 제약이 사용자 번호의 FK 인가 — 그렇다면 그 번호의 사용자가 없다. {@code null} 이면 {@code false} */
    public static boolean isMissingUser(String constraintName)
    {
        return constraintName != null && USER_FOREIGN_KEYS.contains(constraintName);
    }
}
