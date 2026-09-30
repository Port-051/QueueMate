package com.queuemate.platform.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 설정. 이 앱은 <b>stateless</b> 다 — 세션을 만들지 않고, 요청마다 쿠키의 access 토큰을 검증한다 (CLAUDE.md §5).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                   ApiAuthenticationEntryPoint entryPoint) throws Exception
    {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Spring 의 CSRF 토큰은 쓰지 않는다 — SameSite=Lax + Origin 검사(OriginCheckFilter)로 막는다 (CLAUDE.md §5.1 (다))
                .csrf(AbstractHttpConfigurer::disable)
                // 로그인(소셜) · 로그아웃은 이 앱의 컨트롤러가 한다. Spring 의 기본 처리는 전부 끈다
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 에러 디스패치(/error)까지 인증을 요구하면 원래의 상태 코드가 401 로 가려진다
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 소셜 로그인 · 재발급 · 로그아웃. 계약의 "인증이 필요 없는 요청은 /api/v1/auth/**" 그대로다 —
                        // CookieBearerTokenResolver 도 같은 접두사 아래에서는 쿠키를 집지 않는다(둘이 같은 금이어야 한다)
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // actuator 를 루트에 뒀다 (application.yaml 의 management.endpoints.web.base-path)
                        .requestMatchers("/health/**", "/health", "/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
