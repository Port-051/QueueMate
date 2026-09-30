package com.queuemate.notification.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 설정. 이 서비스는 <b>stateless</b> 다 — 세션을 만들지 않고, SSE 연결을 여는 요청마다 쿠키의 access 토큰을 검증한다.
 * <b>연결할 때만</b> 검증하고 열린 연결은 토큰이 만료돼도 끊지 않는다({@code ../platform/CLAUDE.md} §5.1 (바)).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                   ApiAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Spring 의 CSRF 토큰은 쓰지 않는다 — SameSite=Lax + Origin 검사(OriginCheckFilter)로 막는다 (platform §5.1 (다))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // SSE 는 서블릿 비동기다 — 연결이 끝날 때의 ASYNC 디스패치는 이미 인증을 통과한 요청의 뒷부분이다.
                        // 여기서 다시 막으면 이미 흘려보내던 응답 위에 401 을 쓰려 든다. 에러 디스패치(/error)도 원래 상태 코드를 가리지 않게 연다
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // 헬스체크 자리. 이 서비스에는 아직 actuator 가 없어 이 경로들은 404 다 — 생겨도 인증 없이 열리게 미리 둔다
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
