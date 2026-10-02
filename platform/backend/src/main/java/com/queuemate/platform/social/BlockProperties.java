package com.queuemate.platform.social;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 차단의 설정값. {@code application.yaml} 의 {@code platform.block.*} 이고 환경변수로 바꿀 수 있다(이름과 기본값은 {@code contracts/platform-api.md} "차단" · P-52).
 *
 * @param redisSyncInterval 차단 관계 사본(Redis {@code qm:user:block-rel:*})을 {@code blocks} 표에서 다시 만드는 주기 — 환경변수
 *                          {@code BLOCK_REDIS_SYNC_INTERVAL}, 기본 {@code PT5M}. <b>{@code PT0S}(또는 음수)면 주기적 재구성을 끈다</b> — 기동 때 한 번은 그대로 돈다.
 *                          사본이 원본과 어긋날 수 있는 길(Redis 장애 조치로 마지막 몇 ms 의 쓰기를 잃는 것 · 커밋하지 못한 차단의 과잉 · 탈퇴의 정리 실패)이
 *                          길어야 이만큼 남는다. 짧을수록 그 창이 줄고 대신 DB · Redis 를 그만큼 자주 훑는다
 */
@ConfigurationProperties(prefix = "platform.block")
public record BlockProperties(@DefaultValue("PT5M") Duration redisSyncInterval) {
}
