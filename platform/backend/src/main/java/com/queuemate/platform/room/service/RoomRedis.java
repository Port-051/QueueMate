package com.queuemate.platform.room.service;

import com.queuemate.platform.room.RoomErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;

import java.util.function.Supplier;

/**
 * 방 안의 일이 Redis 를 부르는 자리를 감싼다 — <b>Redis 에 닿지 못하면 503 {@code ROOM_STATE_UNAVAILABLE}</b>({@code Retry-After: 5})이다.
 *
 * <p>2026-09-25 2단계 전에는 방의 컨트롤러에만 걸리는 예외 처리기({@code RoomExceptionHandler})가 {@code DataAccessException} 을 503
 * {@code ROOM_UNAVAILABLE} 로 바꿨다. 에러 코드를 한 벌로 합치며 그 처리기를 없앴다 — 전역 처리기에서 {@code DataAccessException} 을 받으면
 * DB 오류까지 "방의 상태를 확인할 수 없다" 가 되고, 글 쓰기(트랜잭션 안에서 방을 만든다)처럼 방의 요청이 아닌 곳에서도 Redis 가 불린다.
 * 그래서 <b>Redis 를 부르는 그 자리에서</b> 옮긴다 — {@code RuntimeException} 이라 트랜잭션 안이면 그대로 되돌린다.
 */
@Slf4j
final class RoomRedis {

    private RoomRedis()
    {
    }

    static <T> T call(String what, Supplier<T> redisCall)
    {
        try
        {
            return redisCall.get();
        }
        catch(DataAccessException e)
        {
            log.error("Redis 에 닿지 못해 방의 요청을 거절한다 what={}", what, e);
            throw RoomErrors.stateUnavailable();
        }
    }
}
