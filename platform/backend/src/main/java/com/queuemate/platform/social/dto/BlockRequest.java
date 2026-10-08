package com.queuemate.platform.social.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code POST /blocks} 의 본문 — {@code userId} 는 차단할 사람의 <b>사용자 번호</b>다. 문자열로 받아 서비스가 {@code Long} 으로 판다({@code common.web.Ids}) —
 * 숫자가 아니면 있을 수 없는 사용자라 없는 사용자와 같은 404 {@code USER_NOT_FOUND} 로 답한다(비어 있는지만 여기서 본다).
 */
public record BlockRequest(@NotBlank(message = "필요합니다") String userId) {
}
