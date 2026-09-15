package com.queuemate.auth.api;

import com.queuemate.auth.api.AuthDtos.OAuthExchangeRequest;
import com.queuemate.auth.api.AuthDtos.OAuthProviderView;
import com.queuemate.auth.api.AuthDtos.TokenResponse;
import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.auth.oauth.OAuthClient;
import com.queuemate.auth.oauth.OAuthClients;
import com.queuemate.auth.oauth.OAuthExchangeFailedException;
import com.queuemate.auth.oauth.OAuthHandoffStore;
import com.queuemate.auth.oauth.OAuthLoginService;
import com.queuemate.auth.oauth.OAuthProperties;
import com.queuemate.auth.oauth.OAuthStateStore;
import com.queuemate.auth.service.AuthService;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.common.security.InvalidTokenException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 소셜 로그인. 흐름은 contracts/openapi.yaml의 /auth/oauth/* 와 같다.
 *
 * authorize → 제공자 동의 → callback → 프론트엔드로 일회용 코드 전달 → exchange에서 토큰.
 * 토큰을 리다이렉트 URL에 싣지 않는 이유는 URL이 브라우저 기록과 Referer, 프록시 로그에
 * 남기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/auth/oauth")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final OAuthClients clients;
    private final OAuthLoginService loginService;
    private final OAuthStateStore states;
    private final OAuthHandoffStore handoffs;
    private final OAuthProperties properties;
    private final AuthService authService;

    public OAuthController(OAuthClients clients, OAuthLoginService loginService,
                           OAuthStateStore states, OAuthHandoffStore handoffs,
                           OAuthProperties properties, AuthService authService) {
        this.clients = clients;
        this.loginService = loginService;
        this.states = states;
        this.handoffs = handoffs;
        this.properties = properties;
        this.authService = authService;
    }

    @GetMapping("/providers")
    public List<OAuthProviderView> providers() {
        return clients.listed().stream()
                .map(p -> new OAuthProviderView(p.name(), p.displayName(),
                        "/api/v1/auth/oauth/" + p.key() + "/authorize"))
                .toList();
    }

    @GetMapping("/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String provider,
                                          @RequestParam(required = false) String redirect) {
        OAuthProvider target = parse(provider);
        OAuthClient client = clients.require(target);
        // 개발 중에는 자격 증명 없이도 버튼이 보인다. 제공자로 내보내면 그쪽 오류 화면이
        // 뜨므로, 나가기 전에 무엇이 빠졌는지 알려주고 되돌린다.
        if (!client.configured()) {
            log.warn("자격 증명이 없는 제공자로 로그인 시도 provider={}", target);
            return redirectToFrontend(null, "PROVIDER_NOT_CONFIGURED", safeRedirect(redirect));
        }
        String state = states.issue(safeRedirect(redirect));
        return ResponseEntity.status(HttpStatus.FOUND).location(client.authorizationUri(state)).build();
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        OAuthProvider target = parse(provider);
        if (!clients.require(target).configured()) {
            return redirectToFrontend(null, "PROVIDER_NOT_CONFIGURED", safeRedirect(null));
        }

        // 사용자가 동의 화면에서 취소한 경우다. 실패가 아니라 선택이므로 조용히 돌려보낸다.
        if (error != null && !error.isBlank()) {
            log.info("소셜 로그인 취소 provider={} error={}", target, error);
            return redirectToFrontend(null, "ACCESS_DENIED", safeRedirect(null));
        }

        // state 검증이 이 흐름의 CSRF 방어다. 서버가 만들지 않은 state는 통과시키지 않는다.
        String redirectPath = states.consume(state).orElse(null);
        if (redirectPath == null || code == null || code.isBlank()) {
            log.warn("소셜 로그인 state 검증 실패 provider={}", target);
            return redirectToFrontend(null, "INVALID_STATE", safeRedirect(null));
        }

        try {
            UUID userId = loginService.authenticate(target, code, state);
            return redirectToFrontend(handoffs.issue(userId), null, redirectPath);
        } catch (OAuthExchangeFailedException e) {
            log.warn("소셜 로그인 교환 실패 provider={}", target, e);
            return redirectToFrontend(null, "EXCHANGE_FAILED", redirectPath);
        } catch (BadCredentialsException e) {
            return redirectToFrontend(null, "ACCOUNT_UNAVAILABLE", redirectPath);
        }
    }

    @PostMapping("/exchange")
    public ResponseEntity<TokenResponse> exchange(@Valid @RequestBody OAuthExchangeRequest request) {
        UUID userId = handoffs.consume(request.code())
                .orElseThrow(() -> new InvalidTokenException("만료됐거나 이미 사용된 코드다"));
        return ResponseEntity.ok(authService.issueTokensFor(userId));
    }

    private ResponseEntity<Void> redirectToFrontend(String code, String error, String redirectPath) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.frontendCallbackUri())
                .queryParam("redirect", redirectPath);
        if (code != null) {
            builder.queryParam("code", code);
        }
        if (error != null) {
            builder.queryParam("error", error);
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(builder.build(true).toUri()).build();
    }

    /**
     * 로그인 후 돌아갈 경로는 앱 내부 경로만 허용한다. 외부 URL을 그대로 받으면
     * 우리 도메인의 로그인 링크로 다른 사이트에 사용자를 떨어뜨릴 수 있다(open redirect).
     */
    private String safeRedirect(String redirect) {
        String fallback = properties.defaultRedirectPath();
        if (redirect == null || redirect.isBlank()) {
            return fallback;
        }
        boolean internal = redirect.startsWith("/")
                && !redirect.startsWith("//")
                && !redirect.contains(":")
                && !redirect.contains("\\");
        return internal ? redirect : fallback;
    }

    private OAuthProvider parse(String provider) {
        try {
            return OAuthProvider.valueOf(provider.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("OAUTH_PROVIDER_NOT_AVAILABLE", "사용할 수 없는 로그인 제공자다");
        }
    }
}
