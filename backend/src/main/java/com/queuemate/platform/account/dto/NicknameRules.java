package com.queuemate.platform.account.dto;

/** 닉네임의 검증 규칙. 소셜 가입과 닉네임 바꾸기가 같이 쓴다 */
final class NicknameRules {

    /**
     * 첫 글자와 끝 글자가 공백이 아니다. {@code (?U)} 는 전각 공백 같은 유니코드 공백도 공백으로 치게 한다.
     * {@code .} 이 줄바꿈에 안 맞아서 줄바꿈이 든 닉네임도 같이 걸러진다.
     */
    static final String NO_EDGE_WHITESPACE = "(?U)^\\S(.*\\S)?$";

    private NicknameRules()
    {
    }
}
