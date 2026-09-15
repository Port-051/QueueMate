package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.auth.domain.UserIdentity;
import com.queuemate.auth.repository.UserIdentityRepository;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 제공자 신원을 QueueMate 계정에 연결한다.
 *
 * 트랜잭션 경계를 OAuthLoginService와 분리해 둔다. 같은 빈 안에서 부르면 프록시를 거치지
 * 않아 @Transactional이 걸리지 않는다.
 */
@Service
public class OAuthAccountLinker {

    private static final Logger log = LoggerFactory.getLogger(OAuthAccountLinker.class);

    private final UserIdentityRepository identities;
    private final UserRepository users;
    private final NicknameAllocator nicknames;

    public OAuthAccountLinker(UserIdentityRepository identities, UserRepository users,
                              NicknameAllocator nicknames) {
        this.identities = identities;
        this.users = users;
        this.nicknames = nicknames;
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findLinkedUser(OAuthProvider provider, String providerUserId) {
        return identities.findByProviderAndProviderUserId(provider, providerUserId)
                .map(UserIdentity::getUserId);
    }

    @Transactional
    public UUID linkOrCreate(OAuthProvider provider, OAuthUserInfo info) {
        // 확인된 이메일이 같은 계정이 이미 있으면 그 계정에 붙인다. 같은 사람이 이메일로 가입한 뒤
        // 소셜로 들어온 경우다. 확인되지 않은 이메일은 근거가 되지 않는다. 남의 이메일을 적어둔
        // 계정으로 남의 계정을 가져갈 수 있기 때문이다.
        Optional<User> existing = info.hasVerifiedEmail()
                ? users.findByEmail(info.email())
                : Optional.empty();

        User user = existing.orElseGet(() -> users.saveAndFlush(User.createSocial(
                info.hasVerifiedEmail() ? info.email() : null,
                nicknames.allocate(info.nickname()))));

        requireActive(user);
        identities.saveAndFlush(UserIdentity.link(user.getId(), provider, info.providerUserId(), info.email()));
        log.info("소셜 계정 연결 provider={} userId={} 신규={}", provider, user.getId(), existing.isEmpty());
        return user.getId();
    }

    @Transactional(readOnly = true)
    public void requireActiveUser(UUID userId) {
        requireActive(users.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("사용할 수 없는 계정이다")));
    }

    private void requireActive(User user) {
        if (!user.isActive()) {
            throw new BadCredentialsException("사용할 수 없는 계정이다");
        }
    }
}
