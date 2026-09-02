package com.queuemate.matching.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 매칭/만료 주기 작업을 켠다.
 * 다른 모듈이 같은 어노테이션을 켜도 중복으로 문제되지 않는다.
 */
@Configuration
@EnableScheduling
public class MatchingSchedulingConfig {
}
