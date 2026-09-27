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
    /** 한 사용자에게 한 제공자는 하나다 — 카카오 A 를 이은 사람이 카카오 B 를 또 이으면 이것이 깨진다 */
    static final String SOCIAL_IDENTITIES_USER_PROVIDER = "social_identities_user_id_provider_key";
    /** 잇기의 INSERT 에서 이것이 깨졌다 = 토큰은 멀쩡한데 그 사용자가 DB 에 없다 */
    static final String SOCIAL_IDENTITIES_USER_FK = "social_identities_user_id_fkey";

    /** 잇기(콜백의 로그인된 갈래)의 결과. 콜백이 이것을 302 대상으로 옮긴다 */
    public enum LinkResult {
        /** 이었다 — 또는 이미 나한테 이어져 있었다(멱등) */
        LINKED,
        /** 그 소셜 계정은 이미 남의 것이다 — 뺏지 않는다 */
        SOCIAL_ALREADY_LINKED,
        /** 나한테 같은 제공자의 다른 계정이 이미 있다 */
        PROVIDER_ALREADY_LINKED,
        /** 토큰의 사용자가 DB 에 없다 */
        USER_NOT_FOUND
    }

    static final String LAST_SOCIAL_IDENTITY = "LAST_SOCIAL_IDENTITY";

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

    /**
     * 로그인한 사용자에게 소셜 계정을 <b>잇는다</b>(2026-09-27 소유자 결정 — P-27). 그 뒤엔 어느 제공자로 로그인해도 같은 사용자다.
     *
     * <p><b>조회 → 판단 → 삽입이 아니다</b> — 먼저 INSERT 하고 깨진 제약으로 가른다. PK({@code social_identities_pkey})가 깨졌으면
     * 그 소셜 계정이 이미 누군가의 것이라 <b>그 뒤에</b> 주인을 읽어 "나"(멱등)와 "남"을 가른다. 두 제약이 같이 걸릴 수 있는 경우(이미 나한테 있는 바로 그 계정)도
     * 주인을 읽는 것으로 같은 답이 된다.
     *
     * <p>{@code @Transactional} 을 붙이지 않는다 — 위반이 난 트랜잭션에서는 주인을 읽을 수 없다(PostgreSQL 이 그 트랜잭션을 버린다).
     * {@code saveAndFlush} 와 {@code findById} 가 각자 자기 트랜잭션에서 돈다.
     */
    public LinkResult link(SocialProvider provider, String providerUserId, Long userId)
    {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        try
        {
            socialIdentityRepository.saveAndFlush(new SocialIdentity(provider, providerUserId, userId, now));
            log.info("소셜 잇기 userId={} provider={}", userId, provider);
            return LinkResult.LINKED;
        }
        catch(DataIntegrityViolationException e)
        {
            String constraint = ConstraintViolations.nameOf(e);
            if(SOCIAL_IDENTITIES_USER_FK.equals(constraint))
            {
                return LinkResult.USER_NOT_FOUND;
            }
            if(!SOCIAL_IDENTITIES_PK.equals(constraint) && !SOCIAL_IDENTITIES_USER_PROVIDER.equals(constraint))
            {
                throw e;
            }
            Optional<Long> owner = findLinkedUserId(provider, providerUserId);
            if(owner.isPresent())
            {
                return owner.get().equals(userId) ? LinkResult.LINKED : LinkResult.SOCIAL_ALREADY_LINKED;
            }
            // 주인이 없다 — PK 가 깨진 뒤 그 사이에 끊긴 드문 경우다(이번엔 잇지 않았다). (user_id, provider) 가 깨졌으면 나한테 같은 제공자의 다른 계정이 있다
            return SOCIAL_IDENTITIES_USER_PROVIDER.equals(constraint)
                    ? LinkResult.PROVIDER_ALREADY_LINKED : LinkResult.SOCIAL_ALREADY_LINKED;
        }
    }

    /**
     * 내 소셜 연결 하나를 <b>끊는다</b>(P-27). 그 제공자가 나한테 없으면 아무것도 하지 않는다(멱등 — 204).
     * <b>마지막 하나는 못 끊는다</b> — 409 {@code LAST_SOCIAL_IDENTITY}(로그인은 소셜뿐이라 다시 로그인할 길이 없어진다).
     *
     * <p><b>동시성 — 사용자 줄을 {@code FOR UPDATE} 로 잠근 뒤 세고 지운다.</b> "하나뿐인가"를 {@code DELETE … WHERE (SELECT count(*) …) > 1}
     * 한 문장으로 보는 것은 READ COMMITTED 에서 막지 못한다 — 두 요청이 각각 <b>다른 줄</b>(카카오 · 디스코드)을 지우면 행 잠금이 겹치지 않고,
     * 둘 다 커밋 전의 "2개"를 보고 지워 0개가 된다. 같은 사용자의 끊기가 {@code users} 의 한 줄에서 줄을 서게 한다.
     */
    @Transactional
    public void unlink(Long userId, SocialProvider provider)
    {
        if(userRepository.lockById(userId).isEmpty())
        {
            // 토큰은 멀쩡한데 그 사용자가 DB 에 없다
            throw ApiException.unauthenticated();
        }
        if(socialIdentityRepository.findByUserIdAndIdProvider(userId, provider).isEmpty())
        {
            return;
        }
        if(socialIdentityRepository.countByUserId(userId) <= 1)
        {
            throw new ApiException(HttpStatus.CONFLICT, LAST_SOCIAL_IDENTITY, "마지막 로그인 수단은 끊을 수 없습니다");
        }
        socialIdentityRepository.deleteByUserIdAndProvider(userId, provider.name());
        log.info("소셜 끊기 userId={} provider={}", userId, provider);
    }
}
