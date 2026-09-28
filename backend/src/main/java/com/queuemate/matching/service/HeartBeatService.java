package com.queuemate.matching.service;

import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 대기 중인 매칭 요청의 접속 확인(heartbeat) — 시한을 뒤로 민다 (docs/11 D-43).
 *
 * <p>{@code qm:request:alive} ZSET 의 score 가 시한이다. 접수({@code claim-request.lua})가 첫 시한을 넣고,
 * 여기서는 같은 member 의 score 를 {@code now + 유예} 로 <b>덮어쓴다</b> — {@code ZADD NX}({@code addIfAbsent})가 아니라
 * 보통 {@code ZADD} 여야 한다. NX 면 접수 때 들어간 member 는 건드리지 않아 신호가 영영 갱신되지 않는다.
 *
 * <p><b>활성 요청이 없으면 넣지 않는다.</b> 취소 뒤에 늦게 도착한 신호가 ZSET 에 유령 member 를 만들면, 스위퍼가
 * 유예 뒤에 꺼내 {@code HGETALL} 한 번 하고 버릴 뿐이라 해는 없지만, 클라이언트에게 "네 요청은 없다"(404)를 알려 줄
 * 기회를 놓친다 — 새로고침한 화면이 대기 중인 줄 알고 계속 신호만 보내는 상태가 그것이다.
 */
@Service
@RequiredArgsConstructor
public class HeartBeatService {

    private final StringRedisTemplate redis;

    /** 신호가 끊긴 뒤 큐에서 빠지기까지의 유예(ms) — {@code queuemate.alive.grace-ms} */
    @Value("${queuemate.alive.grace-ms}")
    private long graceMs;

    /**
     * 시한을 {@code now + 유예} 로 민다.
     *
     * <p>{@code EXISTS} 뒤 {@code ZADD} 는 원자가 아니다 — 그 사이에 취소가 끼면 활성 요청이 없는 member 가 ZSET 에
     * 남는다. 감수한다. 이 틈이 만드는 것은 유령 member 하나이고, 스위퍼가 시한 뒤에 꺼내 활성 요청이 없음을 보고
     * 목록에서만 빼기 때문이다({@link RequestAliveExpiryService}). 불변식이 걸린 자리가 아니라 Lua 로 묶지 않았다.
     *
     * @return 활성 요청이 있어 시한을 밀었으면 {@code true}. 없으면 아무것도 쓰지 않고 {@code false}
     */
    public boolean update(String userId) {
        if (!Boolean.TRUE.equals(redis.hasKey(SharedKeys.activeRequestKey(userId)))) {
            return false;
        }
        long deadline = System.currentTimeMillis() + graceMs;
        redis.opsForZSet().add(SharedKeys.HEARTBEAT_KEY, userId, deadline);
        return true;
    }
}
