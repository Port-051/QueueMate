package com.queuemate.matching.app;

import com.queuemate.common.domain.GameKey;
import com.queuemate.common.domain.PlayPurpose;
import com.queuemate.common.domain.VoicePreference;
import com.queuemate.common.error.ServiceUnavailableException;
import com.queuemate.gameconfig.infra.SeedGameModeConfigProvider;
import com.queuemate.matching.domain.LolPosition;
import com.queuemate.matching.domain.MatchCondition;
import com.queuemate.matching.domain.MatchRequest;
import com.queuemate.matching.infra.MatchConditionCodec;
import com.queuemate.matching.infra.MatchQueueRepository;
import com.queuemate.matching.infra.MatchRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * INV-10 검증. Redis를 쓸 수 없으면 새 매칭을 fail-closed 한다.
 *
 * <p>DB로 우회해 비원자적으로 매칭을 이어 가면 두 사람이 서로 다른 파티를 보게 된다.
 * 그래서 "느슨하게 통과"가 아니라 503으로 끊는다.
 */
class MatchRequestServiceFailClosedTest {

    private final MatchRequestRepository requests = mock(MatchRequestRepository.class);
    private final MatchQueueRepository queue = mock(MatchQueueRepository.class);
    private final MatchConditionCodec codec = new MatchConditionCodec(new ObjectMapper());

    private final MatchRequestService service = new MatchRequestService(
            requests, queue, new SeedGameModeConfigProvider(), codec);

    private static final MatchCondition CONDITION = new MatchCondition(
            GameKey.LOL, "SOLO_DUO_RANKED", LolPosition.JUNGLE,
            VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);

    @Test
    @DisplayName("Redis 연결이 끊기면 503으로 끊는다. DB로 우회하지 않는다")
    void failsClosedWhenRedisIsDown() {
        when(requests.saveAndFlush(any(MatchRequest.class))).thenAnswer(call -> call.getArgument(0));
        when(queue.acquire(any(UUID.class), any(UUID.class), anyString(), any(Instant.class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> service.start(UUID.randomUUID(), CONDITION))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("매칭을 시작할 수 없다");
    }

    @Test
    @DisplayName("Redis가 느려 타임아웃이 나도 통과시키지 않는다")
    void failsClosedOnRedisTimeout() {
        when(requests.saveAndFlush(any(MatchRequest.class))).thenAnswer(call -> call.getArgument(0));
        when(queue.acquire(any(UUID.class), any(UUID.class), anyString(), any(Instant.class)))
                .thenThrow(new QueryTimeoutException("redis timeout"));

        assertThatThrownBy(() -> service.start(UUID.randomUUID(), CONDITION))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("알 수 없는 모드는 매칭을 시작하기 전에 막는다")
    void rejectsUnknownMode() {
        MatchCondition unknown = new MatchCondition(
                GameKey.LOL, "NOT_A_MODE", LolPosition.JUNGLE,
                VoicePreference.OPTIONAL, PlayPurpose.RANK_UP);

        assertThatThrownBy(() -> service.start(UUID.randomUUID(), unknown))
                .isInstanceOf(com.queuemate.common.error.NotFoundException.class);
        // 모드 검증이 먼저이므로 Redis를 건드리지 않는다.
        org.mockito.Mockito.verify(queue, org.mockito.Mockito.never())
                .acquire(any(UUID.class), any(UUID.class), eq("qm:queue:LOL:NOT_A_MODE"),
                        any(Instant.class));
    }
}
