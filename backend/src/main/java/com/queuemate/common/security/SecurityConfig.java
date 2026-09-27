package com.queuemate.common.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 설정 — <b>stateless</b>. 세션을 만들지 않고 요청마다 쿠키 {@code qm_access} 의 access 토큰을 검증한다
 * (2026-09-27 — 임시 식별 {@code ?userId=} 를 대신했다. docs/11 D-24 의 적용).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                                   ApiAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF 토큰은 쓰지 않는다 — SameSite=Lax(쿠키는 platform 이 굽는다) + Origin 검사(OriginCheckFilter)로 막는다
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 에러 디스패치(/error)까지 인증을 요구하면 원래의 상태 코드가 401 로 가려진다
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // actuator(health · info · metrics — 노출 목록은 application.yaml 의 management 가 정한다).
                        // 사용자 인증과 무관한 운영 경로다. load-test 가 /actuator/metrics 를 읽는다
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .authenticationEntryPoint(entryPoint)
                        .jwt(jwt -> jwt.decoder(jwtDecoder)))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint));
        return http.build();
    }
}
