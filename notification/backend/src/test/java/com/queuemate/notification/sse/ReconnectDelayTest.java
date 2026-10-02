package com.queuemate.notification.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ReconnectDelay} 가 뽑는 값의 범위와 설정 검증을 스프링 없이 본다.
 */
class ReconnectDelayTest {

    private static final int DRAWS = 10_000;

    @Test
    @DisplayName("많이 뽑아도 전부 [min, max] 안이다")
    void 뽑은_값은_전부_범위_안이다() {
        ReconnectDelay delay = new ReconnectDelay(1000, 2000);

        for (int i = 0; i < DRAWS; i++) {
            assertThat(delay.nextMs()).isBetween(1000L, 2000L);
        }
    }

    @Test
    @DisplayName("폭이 1 인 범위에서는 min 과 max 가 둘 다 나온다 (max 도 포함이다)")
    void 양_끝_값이_둘_다_나온다() {
        ReconnectDelay delay = new ReconnectDelay(5, 6);
        Set<Long> seen = new HashSet<>();

        // 한쪽만 10,000번 연속으로 나올 확률은 2^-9999 다
        for (int i = 0; i < DRAWS; i++) {
            seen.add(delay.nextMs());
        }

        assertThat(seen).containsExactlyInAnyOrder(5L, 6L);
    }

    @Test
    @DisplayName("min == max 면 항상 그 값이다")
    void min_과_max_가_같으면_항상_그_값이다() {
        ReconnectDelay delay = new ReconnectDelay(1500, 1500);

        for (int i = 0; i < 1000; i++) {
            assertThat(delay.nextMs()).isEqualTo(1500L);
        }
    }

    @Test
    @DisplayName("min < max 면 값이 하나로 고정돼 있지 않다 (고정이면 재접속 몰림이 흩어지지 않는다)")
    void 여러_번_뽑으면_서로_다른_값이_나온다() {
        ReconnectDelay delay = new ReconnectDelay(1000, 2000);
        Set<Long> seen = new HashSet<>();

        // 1001가지 값에서 10,000번 뽑아 전부 같을 확률은 1001^-9999 다
        for (int i = 0; i < DRAWS; i++) {
            seen.add(delay.nextMs());
        }

        assertThat(seen).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("min 이 0 이어도 된다")
    void min_이_0_이어도_된다() {
        assertThatCode(() -> new ReconnectDelay(0, 0)).doesNotThrowAnyException();
        assertThat(new ReconnectDelay(0, 0).nextMs()).isZero();
    }

    @Test
    @DisplayName("min > max 면 IllegalArgumentException (잘못된 설정은 기동에서 막는다)")
    void min_이_max_보다_크면_거부한다() {
        assertThatThrownBy(() -> new ReconnectDelay(2000, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("min 이 음수면 IllegalArgumentException")
    void min_이_음수면_거부한다() {
        assertThatThrownBy(() -> new ReconnectDelay(-1, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
