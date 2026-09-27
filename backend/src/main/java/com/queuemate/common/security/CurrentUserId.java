package com.queuemate.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자에 붙이면 <b>로그인한 사용자의 번호</b>가 들어온다 — access 토큰의 {@code sub} 그대로({@code String}, {@code "42"}).
 *
 * <p>{@code platform} 은 이것을 {@code Long} 으로 파는데 이 앱은 <b>문자열 그대로</b> 받는다 — 매칭 엔진 안(Redis 키 · Lua ·
 * 알림 채널)이 {@code userId} 를 처음부터 문자열로 다뤄서다({@code platform} 계약 P-11 "옆 서비스 코드 변경 없음").
 * 숫자인지는 검증기({@link JwtConfig#jwtDecoder})가 이미 봤다.
 *
 * <p>2026-09-27 에 {@code ?userId=} 쿼리 파라미터와 요청 본문의 {@code userId} 를 이것이 대신했다. 인증이 필요한 경로에서만 쓴다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
