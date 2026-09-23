package com.queuemate.platform.account.oauth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트용 가짜 제공자 — 카카오 · 디스코드의 <b>토큰 주소와 사용자 정보 주소</b>를 흉내 낸다. JDK 의 {@link HttpServer} 를 임의 포트로 띄운다
 * (WireMock 같은 새 의존성을 들이지 않는다). 동의 화면(인가 주소)은 흉내 내지 않는다 — 브라우저가 가는 곳이라 앱이 부르지 않는다.
 *
 * <p>약속 — 토큰 주소는 {@code code} 를 받아 {@code access_token = "at-" + code} 를 돌려주고, 사용자 정보 주소는 그 토큰에 맞춰
 * {@link #stubUser} 로 미리 넣어 둔 JSON 을 돌려준다. {@code code} 가 {@link #CODE_TOKEN_ERROR} 면 토큰 주소가 500,
 * 넣어 둔 JSON 이 없으면 사용자 정보 주소가 500 이다.
 */
final class FakeOAuthProvider {

    static final String CODE_TOKEN_ERROR = "token-endpoint-blows-up";

    private final HttpServer server;
    /** {@code "kakao:code"} → 사용자 정보 JSON */
    private final Map<String, String> users = new ConcurrentHashMap<>();
    /** {@code "kakao"} → 마지막 토큰 요청의 form */
    private final Map<String, Map<String, String>> lastTokenForms = new ConcurrentHashMap<>();

    FakeOAuthProvider()
    {
        try
        {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        }
        catch(IOException e)
        {
            throw new UncheckedIOException(e);
        }
        for(String provider : new String[]{"kakao", "discord"})
        {
            server.createContext("/" + provider + "/token", exchange -> token(provider, exchange));
            server.createContext("/" + provider + "/me", exchange -> me(provider, exchange));
        }
        server.start();
    }

    String baseUrl()
    {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void stop()
    {
        server.stop(0);
    }

    void stubUser(String provider, String code, String userInfoJson)
    {
        users.put(provider + ":" + code, userInfoJson);
    }

    Map<String, String> lastTokenForm(String provider)
    {
        return lastTokenForms.get(provider);
    }

    private void token(String provider, HttpExchange exchange) throws IOException
    {
        Map<String, String> form = parseForm(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        form.put("(method)", exchange.getRequestMethod());
        form.put("(content-type)", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
        lastTokenForms.put(provider, form);
        if(CODE_TOKEN_ERROR.equals(form.get("code")))
        {
            respond(exchange, 500, "{\"error\":\"server_error\"}");
            return;
        }
        respond(exchange, 200, "{\"token_type\":\"bearer\",\"access_token\":\"at-" + form.get("code") + "\",\"expires_in\":3600}");
    }

    private void me(String provider, HttpExchange exchange) throws IOException
    {
        String authorization = String.valueOf(exchange.getRequestHeaders().getFirst("Authorization"));
        if(!authorization.startsWith("Bearer at-"))
        {
            respond(exchange, 401, "{\"msg\":\"no token\"}");
            return;
        }
        String userInfo = users.get(provider + ":" + authorization.substring("Bearer at-".length()));
        if(userInfo == null)
        {
            respond(exchange, 500, "{\"msg\":\"boom\"}");
            return;
        }
        respond(exchange, 200, userInfo);
    }

    private static Map<String, String> parseForm(String body)
    {
        Map<String, String> form = new HashMap<>();
        for(String pair : body.split("&"))
        {
            int eq = pair.indexOf('=');
            if(eq > 0)
            {
                form.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return form;
    }

    private static void respond(HttpExchange exchange, int status, String json) throws IOException
    {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try(var out = exchange.getResponseBody())
        {
            out.write(bytes);
        }
    }
}
