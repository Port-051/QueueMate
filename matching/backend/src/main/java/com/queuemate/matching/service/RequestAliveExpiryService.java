package com.queuemate.matching.service;

import com.queuemate.matching.domain.CancelResult;
import com.queuemate.matching.redisKeys.SharedKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 접속 확인이 끊긴 요청 하나를 큐에서 뺀다 (docs/11 D-43). 꺼내는 일은 {@link RequestAliveSweeper} 가 한다.
 *
 * <p><b>확정된 요청({@code status=PARTY})은 취소하지 않는다.</b> 확정 뒤 클라이언트는 방으로 넘어가 이 앱에 신호를 보내지 않고,
 * 그 뒤의 접속 확인은 platform 의 방이 맡는다(D-42). 여기서 {@code cancel()} 을 부르면 {@code leave-party.lua} 가 확정된 파티에서
 * 사람을 빼고 색인에 다시 올린다 — platform 이 파티 HASH 를 읽기 전에 멤버가 사라진다. 목록에서만 빼고 끝낸다.
 *
 * <p><b>끝난 요청은 여기서 게으르게 지운다 — Lua 는 {@code ZREM} 하지 않는다.</b> 취소 · 만료 · 확정으로 끝난 요청의 member 는
 * 시한이 지날 때까지(최대 유예만큼) 목록에 남아 있다가 한 번 꺼내지고, 활성 요청이 없거나 {@code PARTY} 라 여기서 목록에서만 빠진다.
 * {@code qm:proposal:pending} 처럼 끝나는 자리마다 {@code ZREM} 하지 않는 이유는, 그쪽은 꺼내면 {@code expire()} 가 실제 일을 하지만
 * 이쪽은 대부분 아무 일도 안 하고 비용이 {@code HGETALL} 한 번이라서다. 같은 사용자가 유예 안에 다시 접수하면 {@code ZADD} 가
 * 같은 member 의 score 를 덮어쓰므로 옛 시한이 새 요청을 죽이지도 않는다.
 * 두 인스턴스가 같은 사람을 꺼내도 {@code cancel()} 이 두 번째에는 {@code NOT_FOUND} 라 한 번만 빠진다 — 락이 없는 이유다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequestAliveExpiryService {

    private final StringRedisTemplate redis;
    private final MatchCancelService matchCancelService;

    /**
     * 신호가 끊긴 사용자 하나를 처리한다.
     *
     * @param userId 접속 확인 목록에서 꺼낸 member. 시한이 지났다는 것 말고는 아무것도 전제하지 않는다 —
     *               활성 요청이 없을 수도, 이미 확정됐을 수도 있다
     */
    public void expire(String userId) {
        // 목록에서 먼저 뺀다 — 안 빼면 스위퍼가 매 회차 다시 꺼낸다. 활성 요청을 보기 전에 빼는 것이
        // 락 없이 여러 인스턴스가 돌아도 되는 이유다 (RequestAliveSweeper 머리말)
        redis.opsForZSet().remove(SharedKeys.HEARTBEAT_KEY, userId);

        Map<String, String> active = redis.<String, String>opsForHash().entries(SharedKeys.activeRequestKey(userId));
        if (active.isEmpty()) {
            return;
        }
        if ("PARTY".equals(active.get("status"))) {
            return;
        }

        CancelResult result = matchCancelService.cancel(userId, active.get("requestId"));
        log.info("접속 확인이 끊겨 큐에서 뺀다 userId={} result={}", userId, result);
    }
}
