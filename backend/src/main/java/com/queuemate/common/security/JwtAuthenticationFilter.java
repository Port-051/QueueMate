package com.queuemate.common.security;

import com.queuemate.common.logging.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** Authorization: Bearer <access token>을 SecurityContext의 CurrentUser로 바꾼다. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenService tokenService;

    public JwtAuthenticationFilter(JwtTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                UUID userId = tokenService.parseSubject(token, TokenType.ACCESS);
                var authentication = new UsernamePasswordAuthenticationToken(
                        new CurrentUser(userId), null, List.of());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 이후 모든 로그에 주체를 붙인다. RequestLoggingFilter가 요청 끝에서 지운다.
                MDC.put(MdcKeys.USER_ID, userId.toString());
            } catch (InvalidTokenException e) {
                // 인증 실패는 인증 없음으로 처리하고 EntryPoint가 401을 낸다.
                // 토큰 본문은 로그에 남기지 않는다 (docs/09).
                SecurityContextHolder.clearContext();
                log.debug("access token 검증 실패: {}", e.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        String value = header.substring(PREFIX.length()).trim();
        return value.isEmpty() ? null : value;
    }
}
