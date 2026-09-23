package com.queuemate.platform.social.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 차단 한 건. <b>방향이 있다</b> — {@code blockerId} 가 {@code blockedId} 를 차단했다.
 *
 * <p><b>이 테이블은 {@code matching} 과의 약속이다</b>(CLAUDE.md §3.5 · docs/11 D-1 · D-4). {@code matching} 이 {@code block/Block.java} 로
 * 이 테이블을 이미 읽고 있다 — 이름이나 자료형을 바꾸면 그쪽이 런타임에 깨진다. 쓰는 것은 이 앱뿐이고 {@code id} 도 이 앱이 채번한다(IDENTITY).
 * <b>2026-09-22 소유자 결정으로 {@code blocker_id} · {@code blocked_id} 가 사용자 번호(bigint)가 됐다</b> — {@code matching} 의 {@code Block.java} 는
 * 아직 {@code String} 으로 읽는다. 그쪽을 {@code Long} 으로 같이 바꿔야 한다(아직 안 바꿨다 — {@code matching} 폴더의 일이다. V4 의 머리 주석).
 *
 * <p>사용자 번호는 {@code account.users.id} 의 것이지만 <b>FK 가 없다</b>(크로스 스키마 FK 금지) — 엔티티 연관도 두지 않고 숫자로만 든다.
 * id 가 {@code null} 인 새 엔티티라 {@code save()} 가 {@code persist} 로 간다 — 반드시 INSERT 가 나가고 중복은 UNIQUE 가 막는다.
 */
@Entity
@Table(schema = "social", name = "blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 차단한 사람 */
    @Column(name = "blocker_id", nullable = false, updatable = false)
    private Long blockerId;

    /** 차단당한 사람 */
    @Column(name = "blocked_id", nullable = false, updatable = false)
    private Long blockedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Block(Long blockerId, Long blockedId, Instant now)
    {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.createdAt = now;
    }
}
