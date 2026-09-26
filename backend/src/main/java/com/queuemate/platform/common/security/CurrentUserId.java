package com.queuemate.platform.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 인자에 붙이면 <b>로그인한 사용자의 번호</b>({@code Long} — {@code users.id})가 들어온다 — access 토큰의 {@code sub} 를
 * {@link CurrentUserIdArgumentResolver} 가 {@code Long} 으로 판 것이다.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * public UserResponse me(@CurrentUserId Long userId)
 * }</pre>
 *
 * <p>"사용자 id 는 {@code sub} 이고 숫자다"를 한 곳에만 적어 두려는 것이다. {@code sub} 가 숫자가 아니면 검증기({@code JwtConfig#jwtDecoder})가
 * 이미 401 로 거절했고, 그래도 못 팔면 리졸버가 401 {@code UNAUTHENTICATED} 를 던진다. 인증이 필요한 경로에서만 쓴다 —
 * {@code permitAll} 경로에서는 {@code null} 이 들어온다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUserId {
}
