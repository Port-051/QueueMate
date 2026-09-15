package com.queuemate.matching.domain;

import java.util.List;

/**
 * 같은 등급 안에서 후보를 고르는 무작위 원천 (docs/08 §4).
 *
 * <p>매칭은 추천이 아니라 "호환되는 사람 중 무작위"다. 그 무작위를 테스트에서 고정할 수 있어야
 * 같은 fixture가 항상 같은 결과를 낸다. 그래서 {@code Math.random()}을 직접 부르지 않는다.
 */
public interface RandomSource {

    /**
     * 후보 중 하나를 고른다.
     *
     * @throws IllegalArgumentException 후보가 비어 있는 경우
     */
    <T> T pick(List<T> candidates);
}
