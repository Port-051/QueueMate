package com.queuemate.platform.account.controller;

import com.queuemate.platform.account.domain.SocialProvider;
import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.dto.SocialPendingResponse;
import com.queuemate.platform.account.dto.SocialSignupRequest;
import com.queuemate.platform.account.oauth.OAuthClient;
import com.queuemate.platform.account.oauth.OAuthProperties;
import com.queuemate.platform.account.oauth.OAuthStateCookie;
import com.queuemate.platform.account.oauth.OAuthUser;
import com.queuemate.platform.account.oauth.SocialSignupTokens;
import com.queuemate.platform.account.service.SocialLoginService;
import com.queuemate.platform.account.stats.GameStatsLoginRefresher;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.security.SessionCookies;
import com.queuemate.platform.common.security.TokenClaims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 소셜 로그인(카카오 · 디스코드 · 구글 — 구글은 2026-09-29 · P-33). 넷 다 인증 없이 부른다. 원본은 {@code contracts/platform-api.md} "소셜 로그인" 이다.
 *
 * <p>흐름 — {@code start}(동의 화면으로 302) → 제공자 → {@code callback}(302 세 갈래) → 처음 온 사람만 {@code pending} · {@code signup}.
 * <b>로그인된 채(유효한 {@code qm_access}) 콜백에 오면 잇기다</b> — 그 소셜 계정을 나에게 잇고 {@code /settings} 로 보낸다(2026-09-27 소유자 결정 · P-27).
 * <b>서버가 기억하는 것이 없다</b> — {@code state} 도 "가입을 기다리는 소셜 계정"도 쿠키에 있다(CLAUDE.md §5 "stateless").
 *
 * <p><b>로그인시킬 때(이미 가입한 사람의 콜백 · 소셜 가입) 낡은 전적을 뒤에서 다시 받게 한다</b>(2026-09-30 소유자 결정 · P-42 — {@link GameStatsLoginRefresher}).
 * 잇기는 로그인이 아니라 부르지 않는다. 응답은 기다리지 않는다.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class SocialAuthController {

    static final String FRONT_HOME = "/";
    static final String FRONT_SOCIAL_SIGNUP = "/signup/social";
    static final String FRONT_LOGIN_FAILED = "/login?error=OAUTH_FAILED";
    /** 잇기(로그인된 채 온 콜백)가 돌아가는 화면. 뒤에 {@code ?linked={PROVIDER}} 나 {@code ?error=…} 가 붙는다 */
    static final String FRONT_SETTINGS = "/settings";

    private final OAuthClient oAuthClient;
    private final OAuthProperties oAuthProperties;
    private final OAuthStateCookie stateCookie;
    private final SocialSignupTokens socialSignupTokens;
    private final SocialLoginService socialLoginService;
    private final SessionCookies sessionCookies;
    private final JwtDecoder jwtDecoder;
    private final GameStatsLoginRefresher gameStatsLoginRefresher;

    /**
     * {@code state} 를 쿠키에 넣고 제공자의 동의 화면으로 보낸다. 브라우저가 링크로 직접 오는 요청이다(fetch 가 아니다).
     * DB 도 어디도 바꾸지 않는다 — "상태를 바꾸는 GET 을 만들지 않는다"에 걸리지 않는다.
     */
    @GetMapping("/oauth/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable("provider") SocialProvider provider)
    {
        // 모르는 이름 · 소문자는 스프링의 enum 변환이 400 으로 거절한다 — 게시판 목록의 game 과 같다
        if(!oAuthClient.configured(provider))
        {
            throw new ApiException(HttpStatus.NOT_FOUND, "OAUTH_PROVIDER_NOT_CONFIGURED", "설정되지 않은 로그인 방식입니다");
        }
        String state = stateCookie.newState();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(oAuthClient.authorizationUri(provider, state))
                .header(HttpHeaders.SET_COOKIE, stateCookie.cookie(state).toString())
                .build();
    }

    /**
     * 제공자에서 돌아오는 자리. <b>무슨 일이 있어도 302 다</b>(모르는 제공자 이름만 예외다 — 경로 변수의 enum 변환이 컨트롤러에 닿기 전에 400 을 낸다.
     * 제공자가 그런 주소로 돌려보낼 일은 없다) — 브라우저의 최상위 이동이라 JSON 에러를 내보내면 사용자가 그것을 화면으로 본다.
     * 그래서 파라미터를 전부 선택으로 받고, 예외를 전부 잡아 {@code /login?error=OAUTH_FAILED} 로 돌린다. 왜 실패했는지는 로그에만 남긴다.
     *
     * <p>"상태를 바꾸는 GET 을 만들지 않는다"의 <b>유일한 예외</b>다(OAuth 가 GET 을 강제한다) — {@code state} 의 대조가 {@code Origin} 검사의 자리를 지킨다.
     * {@code state} 쿠키는 어느 갈래로 끝나든 지운다.
     */
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable("provider") SocialProvider provider,
                                         @RequestParam(name = "code", required = false) String code,
                                         @RequestParam(name = "state", required = false) String state,
                                         @RequestParam(name = "error", required = false) String error,
                                         @CookieValue(name = OAuthStateCookie.NAME, required = false) String stateInCookie,
                                         @CookieValue(name = TokenClaims.ACCESS_COOKIE, required = false) String accessToken)
    {
        try
        {
            if(!oAuthClient.configured(provider))
            {
                return failed("설정되지 않은 제공자다");
            }
            // state 를 먼저 본다 — 내가 시작한 흐름이 아니면 나머지 파라미터를 믿을 이유가 없다
            if(!stateCookie.matches(stateInCookie, state))
            {
                return failed("state 가 쿠키와 다르거나 없다");
            }
            if(error != null)
            {
                return failed("사용자가 거절했거나 제공자가 에러를 돌려줬다");
            }
            if(code == null || code.isBlank())
            {
                return failed("code 가 없다");
            }

            OAuthUser user = oAuthClient.fetchUser(provider, code);
            // 로그인된 사람이 부른 콜백이면 로그인 · 가입이 아니라 잇기다(P-27)
            Optional<Long> me = currentUserId(accessToken);
            if(me.isPresent())
            {
                return link(provider, user, me.get());
            }
            Optional<Long> linkedUserId = socialLoginService.findLinkedUserId(provider, user.providerUserId());
            if(linkedUserId.isPresent())
            {
                log.info("소셜 로그인 userId={} provider={}", linkedUserId.get(), provider);
                // 쿠키 둘이다 — access 와 refresh
                List<String> cookies = sessionCookies.login(linkedUserId.get());
                // 낡은 전적을 뒤에서 — 곧바로 돌아오고 예외를 내지 않는다(P-42)
                gameStatsLoginRefresher.refreshStale(linkedUserId.get());
                return redirect(FRONT_HOME, cookies);
            }
            String signupToken = socialSignupTokens.issue(provider, user);
            return redirect(FRONT_SOCIAL_SIGNUP, List.of(socialSignupTokens.cookie(signupToken).toString()));
        }
        catch(RuntimeException e)
        {
            // 예외의 메시지에 제공자의 응답 본문이 들어 있을 수 있다 — 종류만 남긴다. 자세한 것은 debug 에서 본다
            log.debug("소셜 로그인 콜백의 예외", e);
            return failed(e.getClass().getSimpleName());
        }
    }

    /** 가입 화면이 미리 채울 값. {@code qm_social_signup} 이 없거나 · 만료됐거나 · 깨졌으면 401 이다 */
    @GetMapping("/social/pending")
    public SocialPendingResponse pending(
            @CookieValue(name = SocialSignupTokens.COOKIE, required = false) String signupToken)
    {
        SocialSignupTokens.Pending pending = requirePending(signupToken);
        return new SocialPendingResponse(pending.provider().name(), pending.suggestedNickname());
    }

    /**
     * 닉네임을 정해 가입한다. <b>곧바로 로그인시킨다</b>({@code qm_access} · {@code qm_refresh}) — 다시 소셜 로그인을 타게 하지 않는다.
     * {@code qm_social_signup} 은 지운다. POST 라서 {@code Origin} 검사를 거친다.
     */
    @PostMapping("/social/signup")
    public ResponseEntity<AuthResponse> signup(
            @CookieValue(name = SocialSignupTokens.COOKIE, required = false) String signupToken,
            @Valid @RequestBody SocialSignupRequest request)
    {
        SocialSignupTokens.Pending pending = requirePending(signupToken);
        AuthResponse response = socialLoginService.signup(pending.provider(), pending.providerUserId(), request);
        List<String> cookies = new ArrayList<>(sessionCookies.login(response.userId()));
        cookies.add(socialSignupTokens.expiredCookie().toString());
        // 새 사용자라 받을 게임 계정이 아직 없다 — 진짜 로그인의 한 길이라 같이 부른다(P-42)
        gameStatsLoginRefresher.refreshStale(response.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, SessionCookies.array(cookies))
                .body(response);
    }

    private SocialSignupTokens.Pending requirePending(String signupToken)
    {
        return socialSignupTokens.verify(signupToken).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "NO_PENDING_SOCIAL_SIGNUP", "소셜 로그인을 다시 시작해 주세요"));
    }

    /**
     * 잇기의 갈래를 302 로 옮긴다. <b>로그인 쿠키는 새로 주지 않는다</b> — 이미 로그인돼 있다.
     * 토큰의 사용자가 DB 에 없으면(지워진 계정) 로그인 실패와 같은 {@code OAUTH_FAILED} 로 보낸다.
     */
    private ResponseEntity<Void> link(SocialProvider provider, OAuthUser user, Long userId)
    {
        SocialLoginService.LinkResult result = socialLoginService.link(provider, user.providerUserId(), userId);
        return switch(result)
        {
            case LINKED -> redirect(FRONT_SETTINGS + "?linked=" + provider.name(), List.of());
            case SOCIAL_ALREADY_LINKED, PROVIDER_ALREADY_LINKED ->
            {
                log.info("소셜 잇기 거절 userId={} provider={} reason={}", userId, provider, result);
                yield redirect(FRONT_SETTINGS + "?error=" + result.name(), List.of());
            }
            case USER_NOT_FOUND -> failed("잇기 — 토큰의 사용자가 DB 에 없다");
        };
    }

    /**
     * 요청의 {@code qm_access} 가 유효하면 그 사용자 번호. <b>이 경로는 {@code permitAll} 이라 보안 필터가 토큰을 보지 않는다</b>
     * ({@code CookieBearerTokenResolver} 가 {@code /api/v1/auth/**} 에서 집지 않는다) — 그래서 여기서 같은 디코더로 직접 본다
     * (서명 · {@code iss} · {@code exp} · {@code token_use == access} · {@code sub} 숫자). <b>깨진 · 만료된 쿠키는 "로그인 안 됨"이다</b> — 401 을 내지 않고
     * 지금의 로그인 · 가입 흐름으로 간다(토큰이 만료된 사람이 소셜로 다시 로그인하는 길이 막히면 안 된다).
     */
    private Optional<Long> currentUserId(String accessToken)
    {
        if(accessToken == null || accessToken.isBlank())
        {
            return Optional.empty();
        }
        try
        {
            return Optional.of(Long.valueOf(jwtDecoder.decode(accessToken).getSubject()));
        }
        catch(JwtException | NumberFormatException e)
        {
            log.debug("콜백의 qm_access 가 유효하지 않다 — 로그인 안 된 것으로 본다: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private ResponseEntity<Void> failed(String reason)
    {
        log.warn("소셜 로그인 실패 reason={}", reason);
        return redirect(FRONT_LOGIN_FAILED, List.of());
    }

    /** 프런트로 302. {@code state} 쿠키는 늘 같이 지운다 */
    private ResponseEntity<Void> redirect(String frontPath, List<String> setCookies)
    {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oAuthProperties.frontUrl(frontPath)))
                .header(HttpHeaders.SET_COOKIE, stateCookie.expiredCookie().toString());
        for(String setCookie : setCookies)
        {
            builder.header(HttpHeaders.SET_COOKIE, setCookie);
        }
        return builder.build();
    }
}
