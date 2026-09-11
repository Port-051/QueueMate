package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;

import java.net.URI;

public interface OAuthClient {

    OAuthProvider provider();

    /** 자격 증명이 들어와 있는가. 설정되지 않은 제공자는 노출하지 않는다. */
    boolean configured();

    /** 제공자 동의 화면 주소. state는 서버가 만들어 넘긴다. */
    URI authorizationUri(String state);

    /** 인가 코드를 사용자 정보로 바꾼다. 네이버는 이 단계에서도 state를 요구한다. */
    OAuthUserInfo fetchUser(String code, String state);
}
