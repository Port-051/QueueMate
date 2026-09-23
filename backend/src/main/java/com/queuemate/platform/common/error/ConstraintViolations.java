package com.queuemate.platform.common.error;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * DB 의 제약 위반에서 <b>어느 제약인지</b>를 꺼낸다. 불변식은 DB 가 지키고(CLAUDE.md §5) 앱은 위반을 에러 코드로 옮긴다 —
 * 그러려면 어느 제약이 깨졌는지 알아야 한다. 그래서 마이그레이션의 제약에는 전부 이름을 붙인다.
 */
public final class ConstraintViolations {

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
}
