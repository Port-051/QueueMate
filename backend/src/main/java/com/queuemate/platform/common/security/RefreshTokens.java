package com.queuemate.platform.common.security;

import com.queuemate.platform.common.web.WebSecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * refresh 토큰 — 재발급의 열쇠다. 발급 · 확인 · 폐기 · 쿠키를 <b>이 한 곳</b>에서 한다
 * ({@code contracts/platform-api.md} "refresh 토큰". 2026-09-23 소유자 결정).
 *
 * <p><b>JWT 가 아니라 불투명 UUID 다</b>(CLAUDE.md §5.1 (마)) — 값에 아무 뜻이 없고 Redis 의 줄이 사라지면 그 자리에서 쓸 수 없게 된다.
 * access 토큰은 스스로 검증되므로(denylist 를 두지 않는다 — §5.1 (라)) 서버가 무효화할 수 있는 것은 이쪽 하나다.
 * 키는 {@code qm:auth:refresh:{uuid}} → 값은 <b>사용자 번호</b>이고, 수명은 {@code REFRESH_TOKEN_TTL} 이다.
 * {@code qm:auth:} 는 이 앱만 쓰는 접두사다 — {@code matching} 의 {@code qm:user:*} · {@code room} 의 {@code qm:room:*} 와 겹치지 않는다.
 *
 * <p><b>쓴 값은 즉시 버린다(rotation).</b> {@link #consume} 는 {@code GETDEL} <b>한 번</b>으로 읽고 지운다 —
 * {@code 조회 → 판단 → 삭제} 로 하면 그 틈에 들어온 두 요청이 <b>둘 다</b> 통과해 한 값으로 세션이 둘 생긴다.
 * 옛 값을 다시 쓰면 그냥 빈 값이다 — 탈취 감지(토큰 계보 추적)는 넣지 않는다.
 *
 * <p><b>쿠키의 {@code Path} 는 {@code /api/v1/auth} 다</b>(2026-10-02 소유자 결정 — 그 전에는 {@code /api/v1/auth/refresh} 하나였다). 재발급 경로만 받던 탓에
 * 브라우저가 로그아웃({@code POST /api/v1/auth/logout})에 이 쿠키를 싣지 않아 <b>로그아웃이 Redis 의 줄을 지우지 못했다</b> — 로그아웃 전의 값으로 재발급이 200 이었다
 * (실제 서버로 확인했다 — 2026-10-02). 사용자별 refresh 목록을 두는 길은 고르지 않았다(모든 기기 로그아웃은 미정이다 — CLAUDE.md §7).
 *
 * <p><b>옛 {@code Path} 의 쿠키가 남는 동안</b>(임시 — 2026-10-02) — 이미 {@code Path=/api/v1/auth/refresh} 쿠키를 가진 브라우저가 새 쿠키를 받으면 같은 이름의 쿠키가
 * 둘이 되고, 재발급 요청에는 <b>둘 다</b> 실린다(Path 가 긴 옛 것이 앞에 온다 — RFC 6265 §5.4). 그래서 ① 새 쿠키를 줄 때 · 지울 때 옛 {@code Path} 의 것을 같이 지우고
 * ({@link #cookies} · {@link #expiredCookies}) ② 재발급 · 로그아웃은 실린 값을 <b>전부</b> 본다({@link #valuesIn} · {@link #consume(List)} · {@link #revoke(List)}) —
 * 앞의 값이 이미 회전된 옛 값이어도 뒤의 유효한 값으로 재발급된다. <b>운영 전 · 옛 쿠키가 다 사라지면(수명 7일) ① 을 걷어낸다</b>({@link #LEGACY_COOKIE_PATH} 를 쓰는 곳).
 *
 * <p><b>Redis 가 죽었을 때</b> — 발급 · 폐기는 예외를 밖으로 내보내지 않는다(로그인과 로그아웃이 Redis 에 묶이지 않게 한다). 다만 <b>재발급은 빈 값이다</b>(fail-closed) — 확인할 방법이 없는 값을 통과시키면
 * 폐기된 토큰도 통과한다. <b>토큰 값은 어느 로그에도 찍지 않는다</b> — 그것 하나로 남의 세션을 잇는 값이다.
 */
@Slf4j
@Component
public class RefreshTokens {

    /** 접두사는 이 한 곳에만 둔다. 계약에 적힌 이름이다 */
    static final String KEY_PREFIX = "qm:auth:refresh:";

    /** 쿠키의 이름. access 쿠키({@link TokenClaims#ACCESS_COOKIE})와 짝이다 */
    public static final String COOKIE = "qm_refresh";

    /**
     * 쿠키의 {@code Path} — <b>인증 경로({@code /api/v1/auth/**})뿐이다</b>(2026-10-02 소유자 결정 — 재발급 · 로그아웃이 둘 다 받게 넓혔다).
     * 그래서 이 값은 다른 요청(목록 조회 · 회원 탈퇴 등)에 실려 가지 않는다. access 쿠키({@code Path=/})와 다른 속성이 이것과 {@code Max-Age} 다.
     */
    static final String COOKIE_PATH = "/api/v1/auth";

    /**
     * <b>옛</b> 쿠키의 {@code Path}(2026-09-23 ~ 10-02). 그 쿠키를 지우는 데만 쓴다 — 새로 주지 않는다.
     * <b>임시다(2026-10-02) — 운영 전 · 옛 쿠키가 다 사라지면(수명 7일) 이 상수와 그것을 쓰는 곳({@link #cookies} · {@link #expiredCookies})을 걷어낸다.</b>
     */
    static final String LEGACY_COOKIE_PATH = "/api/v1/auth/refresh";

    /**
     * 받은 값이 UUID 의 꼴인지 본다 — 아무 문자열로나 Redis 키를 만들지 않는다.
     * {@code UUID#fromString} 은 이보다 느슨해서 쓰지 않는다.
     */
    private static final Pattern UUID_FORM =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;
    private final WebSecurityProperties webSecurityProperties;

    public RefreshTokens(StringRedisTemplate redis, JwtProperties jwtProperties,
                         WebSecurityProperties webSecurityProperties)
    {
        this.redis = redis;
        this.jwtProperties = jwtProperties;
        this.webSecurityProperties = webSecurityProperties;
    }

    /**
     * 새 refresh 토큰을 만들어 Redis 에 적는다. <b>저장하지 못하면 빈 값이다</b> — 부르는 쪽은 refresh 쿠키 없이
     * access 만으로 로그인시킨다(로그인을 Redis 때문에 실패시키지 않는다).
     *
     * <p>값은 {@code UUID.randomUUID()} 다 — {@code SecureRandom} 으로 만든 122비트다. 사용자 정보를 담지 않는다.
     */
    public Optional<String> issue(long userId)
    {
        String token = UUID.randomUUID().toString();
        try
        {
            redis.opsForValue().set(KEY_PREFIX + token, Long.toString(userId), jwtProperties.refreshTokenTtl());
            return Optional.of(token);
        }
        catch(RuntimeException e)
        {
            log.warn("refresh 토큰을 저장하지 못했다 — access 만으로 로그인시킨다 userId={}: {}", userId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 그 토큰의 주인을 읽고 <b>같은 걸음에 지운다</b>({@code GETDEL}) — 한 값은 한 번만 쓰인다. 없거나 · 꼴이 아니거나 ·
     * 이미 쓴 값이거나 · <b>Redis 에 묻지 못했으면</b> 전부 빈 값이다. 부르는 쪽은 그 넷을 가르지 않는다.
     */
    public OptionalLong consume(String token)
    {
        if(!wellFormed(token))
        {
            return OptionalLong.empty();
        }
        try
        {
            return userId(redis.opsForValue().getAndDelete(KEY_PREFIX + token));
        }
        catch(RuntimeException e)
        {
            // fail-closed 다 — 확인하지 못한 값을 통과시키지 않는다
            log.warn("refresh 토큰을 확인하지 못했다 — 재발급을 거절한다: {}", e.toString());
            return OptionalLong.empty();
        }
    }

    /**
     * 요청에 실린 값들 가운데 <b>유효한 하나</b>의 주인 — 재발급이 부른다({@link #valuesIn} 이 준 값들). <b>실린 값은 전부 {@code GETDEL} 로 버린다</b> —
     * 이 브라우저의 값이고 곧 새 값으로 바뀌므로 살려 둘 까닭이 없다. 같은 이름의 쿠키가 둘 실리는 것은 옛 {@code Path} 의 쿠키가 남았을 때다
     * (임시 — 2026-10-02 · 클래스 주석). 그때 <b>뒤의 값부터</b> 본다 — 브라우저는 {@code Path} 가 긴 옛 쿠키를 앞에, 새 쿠키를 뒤에 싣는다(RFC 6265 §5.4).
     * 앞의 옛 값이 이미 회전된 값이어도 뒤의 새 값이 유효하면 그 사람으로 재발급된다 — 옛 값 때문에 로그인이 풀리지 않는다.
     * 하나도 유효하지 않으면(Redis 를 못 읽은 것도) 빈 값이다.
     */
    public OptionalLong consume(List<String> tokens)
    {
        List<String> newestFirst = new ArrayList<>(new LinkedHashSet<>(tokens));
        Collections.reverse(newestFirst);
        OptionalLong owner = OptionalLong.empty();
        for(String token : newestFirst)
        {
            OptionalLong userId = consume(token);
            if(owner.isEmpty())
            {
                owner = userId;
            }
        }
        return owner;
    }

    /**
     * 요청에 실린 값을 <b>전부</b> 버린다 — 로그아웃이 부른다({@link #valuesIn}). 로그아웃 경로에는 지금의 {@code Path} 의 쿠키만 실린다 — 옛 쿠키의 {@code Path} 는
     * 재발급 경로라 로그아웃에 실리지 않는다(그 값은 브라우저에서만 지워지고 Redis 의 줄은 수명까지 남는다 · 임시 · 2026-10-02).
     */
    public void revoke(List<String> tokens)
    {
        new LinkedHashSet<>(tokens).forEach(this::revoke);
    }

    /**
     * 그 토큰을 버린다 — 로그아웃이 부른다. 없는 값이어도, <b>Redis 가 죽어 있어도</b> 조용히 지나간다(로그아웃은 언제나 성공한다).
     * 그래서 Redis 가 죽은 동안의 로그아웃은 그 토큰을 살려 둔다 — 수명이 다하면 사라진다.
     */
    public void revoke(String token)
    {
        if(!wellFormed(token))
        {
            return;
        }
        try
        {
            redis.delete(KEY_PREFIX + token);
        }
        catch(RuntimeException e)
        {
            log.warn("refresh 토큰을 지우지 못했다 — 수명이 다할 때까지 살아 있다: {}", e.toString());
        }
    }

    /**
     * 요청에 실린 {@code qm_refresh} 쿠키의 값 <b>전부</b>, 실린 순서대로. {@code @CookieValue} 는 첫 쿠키 하나만 주므로 쓰지 않는다 —
     * 옛 {@code Path} 의 쿠키가 남은 브라우저는 재발급에 같은 이름의 쿠키를 둘 싣는다(임시 — 2026-10-02 · 클래스 주석). 없으면 빈 목록이다
     */
    public static List<String> valuesIn(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if(cookies == null)
        {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for(Cookie cookie : cookies)
        {
            if(COOKIE.equals(cookie.getName()))
            {
                values.add(cookie.getValue());
            }
        }
        return values;
    }

    /**
     * 로그인 응답에 싣는 쿠키 — <b>새 refresh 쿠키가 먼저</b>이고, 뒤에 옛 {@code Path} 의 쿠키를 지우는 것이 붙는다
     * (임시 — 2026-10-02 · 운영 전 · 옛 쿠키가 다 사라지면(7일) 걷어낸다). 같이 주어야 같은 이름의 쿠키가 둘 남지 않는다. 수명은 Redis 의 줄과 같다.
     * 순서를 지킨다 — 응답에서 이름으로 쿠키 하나를 꺼내는 쪽(테스트 등)이 새 쿠키를 받게.
     */
    public List<ResponseCookie> cookies(String token)
    {
        return List.of(baseCookie(token, COOKIE_PATH, jwtProperties.refreshTokenTtl()), expiredAt(LEGACY_COOKIE_PATH));
    }

    /**
     * 지우는 쿠키들({@code Max-Age=0}) — 로그아웃 · <b>재발급 실패</b> · 회원 탈퇴에 싣는다. 못 쓰는 값을 브라우저가 계속 들고 있지 않게 한다.
     * 속성이 발급 때와 같아야 브라우저가 같은 쿠키로 보고 지운다 — 특히 {@code Path} 다. 그래서 지금의 {@code Path} 의 것이 먼저이고
     * <b>옛 {@code Path} 의 것도 같이 지운다</b>(임시 — 2026-10-02 · 운영 전 · 옛 쿠키가 다 사라지면(7일) 걷어낸다).
     */
    public List<ResponseCookie> expiredCookies()
    {
        return List.of(expiredAt(COOKIE_PATH), expiredAt(LEGACY_COOKIE_PATH));
    }

    private ResponseCookie expiredAt(String path)
    {
        return baseCookie("", path, Duration.ZERO);
    }

    private ResponseCookie baseCookie(String value, String path, Duration maxAge)
    {
        // Path 와 Max-Age 만 access 쿠키와 다르다 — 나머지는 같다 (CLAUDE.md §5.1 (나))
        return ResponseCookie.from(COOKIE, value)
                .httpOnly(true)
                .secure(webSecurityProperties.cookieSecure())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static boolean wellFormed(String token)
    {
        return token != null && UUID_FORM.matcher(token).matches();
    }

    /** Redis 의 값은 사용자 번호의 십진 문자열이다. 이 앱이 적은 값이 아니면(숫자가 아니면) 없는 것으로 본다 */
    private static OptionalLong userId(String value)
    {
        if(value == null || value.isBlank())
        {
            return OptionalLong.empty();
        }
        try
        {
            return OptionalLong.of(Long.parseLong(value));
        }
        catch(NumberFormatException e)
        {
            log.warn("refresh 토큰의 값이 사용자 번호가 아니다 — 재발급을 거절한다");
            return OptionalLong.empty();
        }
    }
}
