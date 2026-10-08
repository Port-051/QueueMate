package com.queuemate.notification.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CSRF 대응의 절반 — 상태를 바꾸는 요청(POST/PUT/PATCH/DELETE)의 {@code Origin} 헤더를 허용 목록과 대조한다.
 * {@code platform} 의 {@code common/web/OriginCheckFilter} 와 같은 모양이다({@code ../platform/CLAUDE.md} §5.1 (다)).
 *
 * <p><b>지금 이 서비스에는 걸릴 요청이 없다</b> — 엔드포인트가 {@code GET /api/v1/events} 하나뿐이고 이 필터는 GET 을 보지 않는다.
 * 나중에 상태를 바꾸는 요청이 생겨도 빠뜨리지 않게 미리 넣어 둔다. <b>{@code Origin} 이 없는 요청은 통과한다</b>(curl · 서버 사이).
 * 보안 필터보다 먼저 돈다 — 허용되지 않은 출처의 요청은 로그인 여부와 상관없이 403 {@code ORIGIN_NOT_ALLOWED} 다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(WebSecurityProperties.class)
public class OriginCheckFilter extends OncePerRequestFilter {

    static final String ORIGIN_NOT_ALLOWED = "ORIGIN_NOT_ALLOWED";
    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final Set<String> allowedOrigins;
    private final ErrorResponseWriter errorResponseWriter;

    public OriginCheckFilter(WebSecurityProperties properties, ErrorResponseWriter errorResponseWriter) {
        // 설정에 끝 슬래시를 붙여 적어도 맞게 한다 — 브라우저가 보내는 Origin 에는 끝 슬래시가 없다
        this.allowedOrigins = properties.allowedOrigins().stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .collect(Collectors.toUnmodifiableSet());
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        // "null" 이라는 글자도 목록에 없으니 거절된다 (sandbox iframe · 파일에서 연 페이지가 보내는 값이다)
        if (origin != null && STATE_CHANGING_METHODS.contains(request.getMethod()) && !allowedOrigins.contains(origin)) {
            // 헤더 값은 남이 정하는 글자다 — 줄바꿈을 지우고 남긴다
            log.warn("허용하지 않는 Origin 의 요청을 거절한다 origin={} method={} path={}",
                    origin.replaceAll("[\\r\\n]", ""), request.getMethod(), request.getRequestURI());
            errorResponseWriter.write(response, HttpStatus.FORBIDDEN, ORIGIN_NOT_ALLOWED, "허용하지 않는 출처의 요청입니다");
            return;
        }
        chain.doFilter(request, response);
    }
}
