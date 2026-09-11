package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.common.error.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 자격 증명이 들어와 있는 제공자만 모아둔다. 설정되지 않은 제공자는 없는 것으로 취급한다. */
@Component
public class OAuthClients {

    private final Map<OAuthProvider, OAuthClient> byProvider = new EnumMap<>(OAuthProvider.class);

    public OAuthClients(List<OAuthClient> clients) {
        clients.stream()
                .filter(OAuthClient::configured)
                .forEach(client -> byProvider.put(client.provider(), client));
    }

    public OAuthClient require(OAuthProvider provider) {
        OAuthClient client = byProvider.get(provider);
        if (client == null) {
            // 설정되지 않았다는 사실은 비밀이 아니다. 404로 알린다.
            throw new NotFoundException("OAUTH_PROVIDER_NOT_AVAILABLE", "사용할 수 없는 로그인 제공자다");
        }
        return client;
    }

    public List<OAuthProvider> enabled() {
        return List.copyOf(byProvider.keySet());
    }
}
