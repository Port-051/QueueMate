package com.queuemate.chat.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 설정 — {@code notification} 과 같은 틀이다. 이 서비스는 <b>stateless</b> 다 — 세션을 만들지 않고, REST 요청과 WebSocket 핸드셰이크마다
 * 쿠키의 access 토큰을 검증한다. <b>연결할 때만</b> 검증하고 열린 소켓은 토큰이 만료돼도 끊지 않는다({@code ../platform/CLAUDE.md} §5.1 (바)).
 *
 * <p>WebSocket 핸드셰이크는 보통의 HTTP GET 이라 같은 필터 체인을 지난다 — 소켓 설정({@code WebSocketConfigurer} · 핸드셰이크 인터셉터)은
 * 소유자가 따로 넣는다. 거기서 "나"({@code sub})는 핸드셰이크 요청의 {@code Principal}(인증을 통과한 {@code Jwt})에서 꺼낸다.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                   ApiAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Spring 의 CSRF 토큰은 쓰지 않는다 — SameSite=Lax + Origin 검사(OriginCheckFilter · 소켓 쪽 setAllowedOrigins)로 막는다 (platform §5.1 (다))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 비동기 요청이 끝날 때의 ASYNC 디스패치는 이미 인증을 통과한 요청의 뒷부분이다. 에러 디스패치(/error)도 원래 상태 코드를 가리지 않게 연다
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // actuator 의 자리 — 인증 없이 연다. /health/live(ALB 가 본다) · /health/ready(Redis 까지 본다) · /info (application.yaml 의 management)
                        .requestMatchers("/health/**", "/health", "/info").permitAll()
                        // 소켓 핸드셰이크(/ws/chat/rooms/{roomId} · /ws/chat/dm)와 REST(/api/v1/chat/**) — 전부 쿠키의 access 토큰이 있어야 한다
                        .requestMatchers("/ws/**", "/api/v1/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
