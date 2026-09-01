package com.queuemate.realtime;

import com.queuemate.realtime.session.ClusterPresence;
import com.queuemate.realtime.session.NodeIdentity;
import com.queuemate.realtime.session.SessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis를 못 읽을 때의 답을 고정한다. 통합 테스트로는 컨테이너를 죽여야 해서 흉내로 본다.
 */
class ClusterPresenceFallbackTest {

    @Test
    void 확인할_수_없으면_접속_중으로_본다() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members(anyString())).thenThrow(new QueryTimeoutException("redis down"));

        SessionRegistry sessions = mock(SessionRegistry.class);
        when(sessions.hasLocalSession(any())).thenReturn(false);

        ClusterPresence presence = new ClusterPresence(redis, sessions, new NodeIdentity());

        // 아니라고 잘못 답하면 게임 중인 사용자가 파티에서 빠진다. 되돌릴 수 없다.
        // 맞다고 잘못 답하면 이미 나간 사용자의 정리가 늦어질 뿐이다.
        assertTrue(presence.isOnline(UUID.randomUUID()));
    }
}
