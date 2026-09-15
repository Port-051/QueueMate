package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.common.error.NotFoundException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 어떤 제공자를 화면에 내보낼지의 판단.
 *
 * 운영에서 자격 증명 없는 버튼이 새어 나가면 사용자가 누른 뒤에야 실패를 알게 된다.
 * 반대로 개발 중에 전부 감추면 등록 전까지 화면을 확인할 수 없다.
 */
class OAuthClientsTest {

    @Test
    void 운영에서는_설정된_제공자만_내보낸다() {
        OAuthClients clients = new OAuthClients(
                List.of(client(OAuthProvider.KAKAO, false), client(OAuthProvider.NAVER, true)), false);

        assertEquals(List.of(OAuthProvider.NAVER), clients.listed());
    }

    @Test
    void 운영에서_설정되지_않은_제공자는_없는_것으로_본다() {
        OAuthClients clients = new OAuthClients(List.of(client(OAuthProvider.KAKAO, false)), false);

        assertThrows(NotFoundException.class, () -> clients.require(OAuthProvider.KAKAO));
    }

    @Test
    void 개발_중에는_설정되지_않은_제공자도_내보낸다() {
        OAuthClients clients = new OAuthClients(
                List.of(client(OAuthProvider.KAKAO, false), client(OAuthProvider.NAVER, true)), true);

        assertTrue(clients.listed().containsAll(List.of(OAuthProvider.KAKAO, OAuthProvider.NAVER)));
        // 설정 여부는 그대로 들고 있어야 한다. 호출부가 이것을 보고 안내로 되돌린다.
        assertFalse(clients.require(OAuthProvider.KAKAO).configured());
    }

    @Test
    void 아예_모르는_제공자는_어느_쪽이든_없다() {
        OAuthClients clients = new OAuthClients(List.of(client(OAuthProvider.KAKAO, true)), true);

        assertThrows(NotFoundException.class, () -> clients.require(OAuthProvider.NAVER));
    }

    private static OAuthClient client(OAuthProvider provider, boolean configured) {
        return new OAuthClient() {
            @Override
            public OAuthProvider provider() {
                return provider;
            }

            @Override
            public boolean configured() {
                return configured;
            }

            @Override
            public URI authorizationUri(String state) {
                return URI.create("https://example.com/" + provider.key());
            }

            @Override
            public OAuthUserInfo fetchUser(String code, String state) {
                return new OAuthUserInfo("id", null, false, "닉", null);
            }
        };
    }
}
