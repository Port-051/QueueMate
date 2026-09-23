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
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ErrorCodes;
import com.queuemate.platform.common.security.AccessTokenIssuer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Optional;

/**
 * 소셜 로그인(카카오 · 디스코드). 넷 다 인증 없이 부른다. 원본은 {@code contracts/platform-api.md} "소셜 로그인" 이다.
 *
 * <p>흐름 — {@code start}(동의 화면으로 302) → 제공자 → {@code callback}(302 세 갈래) → 처음 온 사람만 {@code pending} · {@code signup}.
 * <b>서버가 기억하는 것이 없다</b> — {@code state} 도 "가입을 기다리는 소셜 계정"도 쿠키에 있다(CLAUDE.md §5 "stateless").
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class SocialAuthController {

    static final String FRONT_HOME = "/";
    static final String FRONT_SOCIAL_SIGNUP = "/signup/social";
    static final String FRONT_LOGIN_FAILED = "/login?error=OAUTH_FAILED";

    private final OAuthClient oAuthClient;
    private final OAuthProperties oAuthProperties;
    private final OAuthStateCookie stateCookie;
    private final SocialSignupTokens socialSignupTokens;
    private final SocialLoginService socialLoginService;
    private final AccessTokenIssuer accessTokenIssuer;

    /**
     * {@code state} 를 쿠키에 넣고 제공자의 동의 화면으로 보낸다. 브라우저가 링크로 직접 오는 요청이다(fetch 가 아니다).
     * DB 도 어디도 바꾸지 않는다 — "상태를 바꾸는 GET 을 만들지 않는다"에 걸리지 않는다.
     */
    @GetMapping("/oauth/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable("provider") String providerName)
    {
        SocialProvider provider = SocialProvider.fromPathName(providerName).orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND, "없는 경로입니다"));
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
     * 제공자에서 돌아오는 자리. <b>무슨 일이 있어도 302 다</b> — 브라우저의 최상위 이동이라 JSON 에러를 내보내면 사용자가 그것을 화면으로 본다.
     * 그래서 파라미터를 전부 선택으로 받고, 예외를 전부 잡아 {@code /login?error=OAUTH_FAILED} 로 돌린다. 왜 실패했는지는 로그에만 남긴다.
     *
     * <p>"상태를 바꾸는 GET 을 만들지 않는다"의 <b>유일한 예외</b>다(OAuth 가 GET 을 강제한다) — {@code state} 의 대조가 {@code Origin} 검사의 자리를 지킨다.
     * {@code state} 쿠키는 어느 갈래로 끝나든 지운다.
     */
    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable("provider") String providerName,
                                         @RequestParam(name = "code", required = false) String code,
                                         @RequestParam(name = "state", required = false) String state,
                                         @RequestParam(name = "error", required = false) String error,
                                         @CookieValue(name = OAuthStateCookie.NAME, required = false) String stateInCookie)
    {
        try
        {
            Optional<SocialProvider> provider = SocialProvider.fromPathName(providerName)
                    .filter(oAuthClient::configured);
            if(provider.isEmpty())
            {
                return failed("모르거나 설정되지 않은 제공자다");
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

            OAuthUser user = oAuthClient.fetchUser(provider.get(), code);
            Optional<Long> linkedUserId = socialLoginService.findLinkedUserId(provider.get(), user.providerUserId());
            if(linkedUserId.isPresent())
            {
                log.info("소셜 로그인 userId={} provider={}", linkedUserId.get(), provider.get());
                String accessToken = accessTokenIssuer.issue(linkedUserId.get());
                return redirect(FRONT_HOME, accessTokenIssuer.cookie(accessToken).toString());
            }
            String signupToken = socialSignupTokens.issue(provider.get(), user);
            return redirect(FRONT_SOCIAL_SIGNUP, socialSignupTokens.cookie(signupToken).toString());
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
     * 로그인 아이디 · 닉네임을 정해 가입한다. <b>곧바로 로그인시킨다</b>({@code qm_access}) — 비밀번호가 없어 따로 로그인할 길이 없다.
     * {@code qm_social_signup} 은 지운다. POST 라서 {@code Origin} 검사를 거친다.
     */
    @PostMapping("/social/signup")
    public ResponseEntity<AuthResponse> signup(
            @CookieValue(name = SocialSignupTokens.COOKIE, required = false) String signupToken,
            @Valid @RequestBody SocialSignupRequest request)
    {
        SocialSignupTokens.Pending pending = requirePending(signupToken);
        AuthResponse response = socialLoginService.signup(pending.provider(), pending.providerUserId(), request);
        String accessToken = accessTokenIssuer.issue(response.userId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, accessTokenIssuer.cookie(accessToken).toString(),
                        socialSignupTokens.expiredCookie().toString())
                .body(response);
    }

    private SocialSignupTokens.Pending requirePending(String signupToken)
    {
        return socialSignupTokens.verify(signupToken).orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED, "NO_PENDING_SOCIAL_SIGNUP", "소셜 로그인을 다시 시작해 주세요"));
    }

    private ResponseEntity<Void> failed(String reason)
    {
        log.warn("소셜 로그인 실패 reason={}", reason);
        return redirect(FRONT_LOGIN_FAILED, null);
    }

    /** 프런트로 302. {@code state} 쿠키는 늘 같이 지운다 */
    private ResponseEntity<Void> redirect(String frontPath, String setCookie)
    {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oAuthProperties.frontUrl(frontPath)))
                .header(HttpHeaders.SET_COOKIE, stateCookie.expiredCookie().toString());
        if(setCookie != null)
        {
            builder.header(HttpHeaders.SET_COOKIE, setCookie);
        }
        return builder.build();
    }
}
