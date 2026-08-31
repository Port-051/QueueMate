package com.queuemate.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청 하나의 로그 상관관계 컨텍스트를 세우고 access log 한 줄을 남긴다. docs/09 §3.
 * security filter chain(order -100)보다 먼저 돌아야 인증 실패 로그에도 requestId가 붙는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    /** 외부 헤더를 그대로 MDC에 넣으면 로그가 오염된다. 길이와 문자를 제한한다. */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        String requestId = sanitized(request.getHeader(REQUEST_ID_HEADER), newId());
        // 분산 tracing 도입 전까지 요청 하나가 곧 trace 하나다. edge가 값을 주면 그쪽을 따른다.
        String traceId = sanitized(request.getHeader(TRACE_ID_HEADER), requestId);

        MDC.put(MdcKeys.REQUEST_ID, requestId);
        MDC.put(MdcKeys.TRACE_ID, traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            if (!isHealthProbe(request)) {
                log.info("{} {} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), response.getStatus(),
                        (System.nanoTime() - startedAt) / 1_000_000);
            }
            // 요청 처리 중 하위 코드가 넣은 키까지 지운다. thread pool에서 다음 요청으로 새면 안 된다.
            MDC.clear();
        }
    }

    /** health probe는 초당 여러 번 들어와 access log를 의미 없이 채운다. */
    private boolean isHealthProbe(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    private static String sanitized(String value, String fallback) {
        return value != null && SAFE_ID.matcher(value).matches() ? value : fallback;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
