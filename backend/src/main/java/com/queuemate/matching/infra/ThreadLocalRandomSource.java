package com.queuemate.matching.infra;

import com.queuemate.matching.domain.RandomSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 운영용 무작위 원천. 매칭 결과는 비밀이 아니므로 암호학적 난수가 필요하지 않고,
 * 매처가 여러 스레드에서 도는 만큼 경합 없는 쪽을 택한다.
 */
@Component
public class ThreadLocalRandomSource implements RandomSource {

    @Override
    public <T> T pick(List<T> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("고를 후보가 없다");
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
