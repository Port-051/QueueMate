package com.queuemate.platform.account.service;

import com.queuemate.platform.account.domain.SocialIdentity;
import com.queuemate.platform.account.domain.SocialIdentityId;
import com.queuemate.platform.account.domain.SocialProvider;
import com.queuemate.platform.account.domain.User;
import com.queuemate.platform.account.dto.AuthResponse;
import com.queuemate.platform.account.dto.SocialSignupRequest;
import com.queuemate.platform.account.repository.SocialIdentityRepository;
import com.queuemate.platform.account.repository.UserRepository;
import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 소셜 로그인의 DB 쪽 — "이 소셜 계정은 누구인가"와 "처음 온 사람의 가입". 제공자와 주고받는 것은 {@code account.oauth} 가,
 * 토큰 · 쿠키는 컨트롤러가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    /** 마이그레이션(V1__schema.sql)이 붙인 제약의 이름이다. 거기서 바꾸면 여기도 바꾼다 */
    static final String SOCIAL_IDENTITIES_PK = "social_identities_pkey";

    private final UserRepository userRepository;
    private final SocialIdentityRepository socialIdentityRepository;

    /** 이미 연결된 소셜 계정이면 그 사용자의 번호. 처음 온 사람이면 빈 값이다 — 콜백이 "로그인인가 가입인가"를 이것으로 가른다 */
    @Transactional(readOnly = true)
    public Optional<Long> findLinkedUserId(SocialProvider provider, String providerUserId)
    {
        return socialIdentityRepository.findById(new SocialIdentityId(provider, providerUserId))
                .map(SocialIdentity::getUserId);
    }

    /**
     * 사용자와 소셜 연결을 <b>한 트랜잭션으로</b> 만든다 — 연결이 실패하면 사용자도 남지 않는다(로그인할 길이 없는 계정이 된다).
     * 정하는 것은 닉네임 하나다(2026-09-26 소유자 결정 — 로그인 아이디 · 비밀번호가 없다).
     *
     * <p><b>중복은 DB 가 막는다</b> — 닉네임은 {@code users} 의 UNIQUE 가, 같은 소셜 계정의 두 번째 가입은 {@code social_identities} 의 PK 가 막는다.
     * 같은 {@code qm_social_signup} 을 든 두 요청이 동시에 와도 하나만 통과한다.
     */
    @Transactional
    public AuthResponse signup(SocialProvider provider, String providerUserId, SocialSignupRequest request)
    {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        User user;
        try
        {
            // id 는 DB 가 매긴다(IDENTITY) — INSERT 가 나가야 번호를 안다. flush 로 위반을 지금 드러낸다
            user = userRepository.saveAndFlush(new User(request.nickname(), now));
            socialIdentityRepository.saveAndFlush(new SocialIdentity(provider, providerUserId, user.getId(), now));
        }
        catch(DataIntegrityViolationException e)
        {
            if(SOCIAL_IDENTITIES_PK.equals(ConstraintViolations.nameOf(e)))
            {
                throw new ApiException(HttpStatus.CONFLICT, "SOCIAL_ALREADY_LINKED", "이미 가입에 쓴 소셜 계정입니다");
            }
            throw AuthService.translate(e);
        }
        // 제공자 쪽 회원 번호는 남기지 않는다 — 어느 제공자인지만 남긴다
        log.info("소셜 가입 userId={} provider={}", user.getId(), provider);
        return new AuthResponse(user.getId(), user.getNickname());
    }
}
