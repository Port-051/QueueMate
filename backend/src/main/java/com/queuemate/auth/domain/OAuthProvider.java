package com.queuemate.auth.domain;

/** 지원하는 소셜 로그인 제공자. DB의 user_identities.provider CHECK 제약과 같은 값이다. */
public enum OAuthProvider {

    KAKAO("카카오"),
    NAVER("네이버"),
    /** 로컬 개발용 가짜 제공자. local/test 프로파일에서만 등록된다. */
    DEV("개발용 계정");

    private final String displayName;

    OAuthProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String key() {
        return name().toLowerCase();
    }
}
