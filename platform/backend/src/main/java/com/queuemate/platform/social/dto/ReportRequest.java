package com.queuemate.platform.social.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /reports} 의 본문.
 *
 * <p>애너테이션은 비어 있는지와 길이만 본다. <b>{@code targetUserId} · {@code contextId} 가 숫자인지 · {@code reason} 이 이름의 목록에 드는지는 서비스가 검증한다</b>
 * ({@code ReportService}) — enum · {@code Long} 으로 받으면 틀린 값이 "본문을 읽을 수 없다"로 떨어져 어느 필드가 틀렸는지 말해 줄 수 없다
 * (모집 글의 {@code game} 과 같은 이유다).
 *
 * @param targetUserId 신고할 사람의 사용자 번호. 숫자가 아니면 없는 사용자와 같은 404 다
 * @param detail       1000자까지. 없어도 된다 — {@code reason} 이 {@code OTHER} 면 필수다
 * @param contextId    글의 id(숫자). 없어도 된다. 있는지 확인하지 않는다
 */
public record ReportRequest(
        @NotBlank(message = "필요합니다") String targetUserId,

        @NotBlank(message = "필요합니다") String reason,

        @Size(max = 1000, message = "1000자를 넘을 수 없습니다") String detail,

        String contextId
) {
}
