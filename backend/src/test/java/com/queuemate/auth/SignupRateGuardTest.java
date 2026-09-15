package com.queuemate.auth;

import com.queuemate.auth.service.SignupRateGuard;
import com.queuemate.common.error.TooManyRequestsException;
import com.queuemate.common.ratelimit.RateLimiter;
import com.queuemate.common.ratelimit.RateLimiter.OnUnavailable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 창이 둘이라는 것과 Redis 장애 시 거절한다는 것을 고정한다.
 * 둘 다 통합 테스트로는 확인하기 어렵다. 하루 창은 기다릴 수 없고 장애는 흉내 내야 한다.
 */
class SignupRateGuardTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final SignupRateGuard guard = new SignupRateGuard(rateLimiter);

    @Test
    void 짧은_창과_하루_창을_함께_센다() {
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(), any())).thenReturn(true);

        guard.checkAllowed("1.2.3.4");

        ArgumentCaptor<Duration> windows = ArgumentCaptor.forClass(Duration.class);
        verify(rateLimiter, org.mockito.Mockito.times(2))
                .tryAcquire(anyString(), eq("1.2.3.4"), anyInt(), windows.capture(), any());
        // 짧은 창만 두면 속도를 맞춰 하루 720건이 지나간다. 실제 상한은 하루 창이 정한다.
        assertEquals(List.of(Duration.ofMinutes(10), Duration.ofDays(1)), windows.getAllValues());
    }

    @Test
    void Redis가_죽으면_가입을_거절한다() {
        // 통과시키면 장애 동안 만들어진 계정이 장애가 끝나도 남는다.
        // 일시적인 고장이 영구적인 결과를 만든다. 막았을 때의 손해는 잠시 뒤 다시 하면 된다.
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(),
                eq(OnUnavailable.REJECT))).thenReturn(false);

        TooManyRequestsException e = assertThrows(TooManyRequestsException.class,
                () -> guard.checkAllowed("1.2.3.4"));
        assertEquals("SIGNUP_RATE_EXCEEDED", e.getCode());
    }

    @Test
    void 두_창_중_하나만_막혀도_거절한다() {
        when(rateLimiter.tryAcquire(anyString(), anyString(), anyInt(), any(), any())).thenReturn(true);
        when(rateLimiter.tryAcquire(eq("signup:ip:daily"), anyString(), anyInt(), any(), any()))
                .thenReturn(false);

        assertThrows(TooManyRequestsException.class, () -> guard.checkAllowed("1.2.3.4"));
    }
}
