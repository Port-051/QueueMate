package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.auth.domain.UserIdentity;
import com.queuemate.auth.repository.UserIdentityRepository;
import com.queuemate.user.domain.User;
import com.queuemate.user.domain.UserStatus;
import com.queuemate.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 제공자 신원을 계정에 붙이는 규칙. 여기가 틀리면 남의 계정으로 로그인된다. */
class OAuthLoginServiceTest {

    private static final OAuthProvider PROVIDER = OAuthProvider.KAKAO;

    private UserRepository users;
    private UserIdentityRepository identities;
    private List<User> savedUsers;
    private List<UserIdentity> savedIdentities;
    private FakeClient client;
    private OAuthLoginService service;

    @BeforeEach
    void setUp() {
        savedUsers = new ArrayList<>();
        savedIdentities = new ArrayList<>();

        users = mock(UserRepository.class);
        when(users.existsByNickname(anyString())).thenReturn(false);
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.saveAndFlush(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            savedUsers.add(u);
            when(users.findById(u.getId())).thenReturn(Optional.of(u));
            return u;
        });

        identities = mock(UserIdentityRepository.class);
        when(identities.findByProviderAndProviderUserId(any(), anyString())).thenReturn(Optional.empty());
        when(identities.saveAndFlush(any(UserIdentity.class))).thenAnswer(i -> {
            UserIdentity identity = i.getArgument(0);
            savedIdentities.add(identity);
            return identity;
        });

        client = new FakeClient();
        service = new OAuthLoginService(
                new OAuthClients(List.of(client), true),
                new OAuthAccountLinker(identities, users, new NicknameAllocator(users)));
    }

    @Test
    void 처음_들어온_신원이면_계정을_만든다() {
        client.info = new OAuthUserInfo("kakao-1", "a@example.com", true, "칼바람장인", null);

        UUID userId = service.authenticate(PROVIDER, "code", "state");

        assertEquals(1, savedUsers.size());
        assertEquals(userId, savedUsers.get(0).getId());
        assertEquals(1, savedIdentities.size());
        assertEquals("kakao-1", savedIdentities.get(0).getProviderUserId());
    }

    @Test
    void 이미_연결된_신원이면_같은_계정으로_들어간다() {
        User existing = User.createSocial("a@example.com", "기존계정");
        when(users.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(identities.findByProviderAndProviderUserId(PROVIDER, "kakao-1"))
                .thenReturn(Optional.of(UserIdentity.link(existing.getId(), PROVIDER, "kakao-1", "a@example.com")));
        client.info = new OAuthUserInfo("kakao-1", "a@example.com", true, "칼바람장인", null);

        assertEquals(existing.getId(), service.authenticate(PROVIDER, "code", "state"));
        assertEquals(0, savedUsers.size());
    }

    @Test
    void 확인된_이메일이_같으면_기존_계정에_붙인다() {
        User existing = User.create("a@example.com", "hash", "이메일가입자");
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(existing));
        client.info = new OAuthUserInfo("kakao-1", "a@example.com", true, "칼바람장인", null);

        assertEquals(existing.getId(), service.authenticate(PROVIDER, "code", "state"));
        // 계정을 새로 만들지 않고 신원만 추가한다.
        assertEquals(0, savedUsers.size());
        assertEquals(1, savedIdentities.size());
    }

    @Test
    void 확인되지_않은_이메일로는_기존_계정에_붙이지_않는다() {
        // 제공자가 확인하지 않은 이메일을 근거로 삼으면 남의 이메일을 적어두는 것만으로
        // 남의 계정을 가져갈 수 있다.
        User existing = User.create("a@example.com", "hash", "이메일가입자");
        when(users.findByEmail("a@example.com")).thenReturn(Optional.of(existing));
        client.info = new OAuthUserInfo("kakao-1", "a@example.com", false, "칼바람장인", null);

        UUID userId = service.authenticate(PROVIDER, "code", "state");

        assertNotEquals(existing.getId(), userId);
        assertEquals(1, savedUsers.size());
    }

    @Test
    void 정지된_계정은_소셜로도_들어올_수_없다() {
        User existing = User.createSocial("a@example.com", "정지계정");
        // 상태를 바꾸는 도메인 메서드가 아직 없다. 다른 auth 테스트와 같은 방식으로 넣는다.
        ReflectionTestUtils.setField(existing, "status", UserStatus.SUSPENDED);
        when(users.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(identities.findByProviderAndProviderUserId(PROVIDER, "kakao-1"))
                .thenReturn(Optional.of(UserIdentity.link(existing.getId(), PROVIDER, "kakao-1", null)));
        client.info = new OAuthUserInfo("kakao-1", null, false, "칼바람장인", null);

        assertThrows(BadCredentialsException.class, () -> service.authenticate(PROVIDER, "code", "state"));
    }

    @Test
    void 동시에_두_번_들어오면_먼저_만든_계정을_쓴다() {
        // unique 제약이 하나만 통과시킨다. 진 쪽은 새 계정을 만들지 않고 이긴 쪽을 읽는다.
        UUID winner = UUID.randomUUID();
        when(identities.saveAndFlush(any(UserIdentity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(identities.findByProviderAndProviderUserId(PROVIDER, "kakao-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(UserIdentity.link(winner, PROVIDER, "kakao-1", null)));
        client.info = new OAuthUserInfo("kakao-1", null, false, "칼바람장인", null);

        assertEquals(winner, service.authenticate(PROVIDER, "code", "state"));
    }

    private static final class FakeClient implements OAuthClient {
        private OAuthUserInfo info;

        @Override
        public OAuthProvider provider() {
            return PROVIDER;
        }

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public URI authorizationUri(String state) {
            return URI.create("https://example.com/authorize?state=" + state);
        }

        @Override
        public OAuthUserInfo fetchUser(String code, String state) {
            return info;
        }
    }
}
