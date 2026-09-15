package com.queuemate.common.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void requestId를_만들어_MDC와_응답헤더에_넣는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> seen = new HashMap<>();

        filter.doFilter(request, response, capturing(seen));

        String requestId = seen.get(MdcKeys.REQUEST_ID);
        assertNotNull(requestId);
        assertEquals(requestId, response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER));
        // 분산 tracing 전에는 trace가 요청과 1:1이다.
        assertEquals(requestId, seen.get(MdcKeys.TRACE_ID));
    }

    @Test
    void 들어온_requestId와_traceId를_이어받는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "edge-request-1");
        request.addHeader(RequestLoggingFilter.TRACE_ID_HEADER, "edge-trace-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> seen = new HashMap<>();

        filter.doFilter(request, response, capturing(seen));

        assertEquals("edge-request-1", seen.get(MdcKeys.REQUEST_ID));
        assertEquals("edge-trace-1", seen.get(MdcKeys.TRACE_ID));
    }

    @Test
    void 형식이_어긋난_헤더는_버리고_새로_만든다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "bad id\nlevel=ERROR fake log line");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Map<String, String> seen = new HashMap<>();

        filter.doFilter(request, response, capturing(seen));

        String requestId = seen.get(MdcKeys.REQUEST_ID);
        assertNotNull(requestId);
        assertTrue(requestId.matches("[a-f0-9]{32}"), "생성된 id여야 한다: " + requestId);
    }

    @Test
    void 요청이_끝나면_MDC를_비운다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                // 하위 코드가 넣은 키도 함께 정리되어야 다음 요청으로 새지 않는다.
                MDC.put(MdcKeys.PARTY_ID, "party-1"));

        assertNull(MDC.get(MdcKeys.REQUEST_ID));
        assertNull(MDC.get(MdcKeys.PARTY_ID));
    }

    @Test
    void 예외가_나도_MDC를_비운다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");

        assertThrows(ServletException.class, () ->
                filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    throw new ServletException("handler 폭발");
                }));

        assertNull(MDC.get(MdcKeys.REQUEST_ID));
        assertNull(MDC.get(MdcKeys.TRACE_ID));
    }

    /** chain 안에서 보이는 MDC 상태를 잡아둔다. 필터가 끝난 뒤에는 비워져 확인할 수 없다. */
    private static MockFilterChain capturing(Map<String, String> target) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request,
                                 jakarta.servlet.ServletResponse response)
                    throws IOException, ServletException {
                target.put(MdcKeys.REQUEST_ID, MDC.get(MdcKeys.REQUEST_ID));
                target.put(MdcKeys.TRACE_ID, MDC.get(MdcKeys.TRACE_ID));
                super.doFilter(request, response);
            }
        };
    }
}
