package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.common.error.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 우리가 아는 제공자와 그중 무엇을 화면에 내보낼지 판단한다.
 *
 * 운영에서는 자격 증명이 설정된 것만 내보낸다. 누른 뒤에야 실패를 알게 되는 버튼을
 * 그리지 않기 위해서다. 개발 중에는 설정 전에도 화면을 확인할 수 있어야 하므로 전부 내보내고,
 * 대신 누르면 제공자로 나가기 전에 안내로 되돌린다.
 */
@Component
public class OAuthClients {

    private final Map<OAuthProvider, OAuthClient> byProvider = new EnumMap<>(OAuthProvider.class);
    private final boolean exposeUnconfigured;

    // 생성자가 둘이면 Spring이 기본 생성자를 찾다 실패한다. 쓸 것을 지목해 둔다.
    @Autowired
    public OAuthClients(List<OAuthClient> clients, OAuthProperties properties) {
        this(clients, properties.exposeUnconfiguredProviders());
    }

    OAuthClients(List<OAuthClient> clients, boolean exposeUnconfigured) {
        clients.forEach(client -> byProvider.put(client.provider(), client));
        this.exposeUnconfigured = exposeUnconfigured;
    }

    /** 화면에 버튼을 그릴 제공자. */
    public List<OAuthProvider> listed() {
        return byProvider.values().stream()
                .filter(client -> client.configured() || exposeUnconfigured)
                .map(OAuthClient::provider)
                .toList();
    }

    /**
     * 목록에 없는 제공자는 없는 것으로 본다. 설정 여부는 비밀이 아니라 404로 알린다.
     * 목록에 있지만 설정되지 않은 제공자는 여기서 통과하고, 호출부가 안내로 되돌린다.
     */
    public OAuthClient require(OAuthProvider provider) {
        OAuthClient client = byProvider.get(provider);
        if (client == null || (!client.configured() && !exposeUnconfigured)) {
            throw new NotFoundException("OAUTH_PROVIDER_NOT_AVAILABLE", "사용할 수 없는 로그인 제공자다");
        }
        return client;
    }
}
