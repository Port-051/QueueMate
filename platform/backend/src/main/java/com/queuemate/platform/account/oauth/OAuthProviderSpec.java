package com.queuemate.platform.account.oauth;

import com.queuemate.platform.account.domain.SocialProvider;
import tools.jackson.databind.JsonNode;

/**
 * 제공자마다 다른 것 — scope 와, 사용자 정보 JSON 에서 회원 번호 · 닉네임을 꺼내는 법. 주소 셋과 클라이언트 id 는 설정에서 온다
 * ({@link OAuthProperties}). 인가 코드 흐름 자체는 제공자와 상관없이 같다({@link OAuthClient}).
 *
 * <p>제공자를 더하려면 이것의 구현 하나 · {@link SocialProvider} 의 값 하나 · 설정 한 묶음 · DB 의 CHECK 를 더한다.
 */
public interface OAuthProviderSpec {

    SocialProvider provider();

    /** 인가 요청의 {@code scope}. 회원 번호와 닉네임을 받는 데 필요한 것만 청한다 */
    String scope();

    /**
     * 사용자 정보 응답에서 회원 번호와 닉네임을 꺼낸다.
     *
     * @throws OAuthException 회원 번호가 없다 — 그 응답으로는 누구인지 알 수 없다
     */
    OAuthUser parseUser(JsonNode userInfo);
}
