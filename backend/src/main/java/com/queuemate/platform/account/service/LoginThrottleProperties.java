package com.queuemate.platform.account.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 로그인 실패 제한의 설정값. {@code application.yaml} 의 {@code platform.auth.login-throttle.*}.
 * 기본값의 원본은 {@code contracts/platform-api.md} "계정"의 "로그인 실패 제한" 이다 — 15분 안에 5번 틀리면 잠그고, 그 뒤로 틀릴 때마다 두 배(1 → 2 → 4 → 8 → 15분).
 *
 * @param maxFailures 이 횟수째 실패부터 잠근다
 * @param window      실패를 세는 창. 첫 실패부터 이만큼 지나면 횟수가 저절로 사라진다
 * @param firstLock   첫 잠금의 길이. 그 뒤로 틀릴 때마다 두 배가 된다
 * @param maxLock     잠금 길이의 상한. <b>영구 잠금은 없다</b> — 비밀번호를 되찾는 길이 없어서 남이 내 계정을 영영 잠글 수 있게 된다
 */
@ConfigurationProperties(prefix = "platform.auth.login-throttle")
public record LoginThrottleProperties(
        @DefaultValue("5") int maxFailures,
        @DefaultValue("PT15M") Duration window,
        @DefaultValue("PT1M") Duration firstLock,
        @DefaultValue("PT15M") Duration maxLock
) {
}
