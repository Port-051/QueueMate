package com.queuemate.matching.block;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 차단 한 건. INV-6 검증에 쓴다.
 *
 * <p><b>{@code app:platform} 의 테이블을 읽는 유일한 자리다.</b> 스키마는 {@code public} 하나이고
 * 스키마별 DB 롤도 없다 (docs/11 D-34 — 옛 {@code social.blocks} 와 {@code matching} 롤의 SELECT 권한(D-1)은
 * 없어졌다). 이 앱이 읽는 {@code platform} 의 테이블을 이것 밖으로 늘리지 않는 것은 권한이 아니라 약속이다.
 *
 * <p><b>두 칸은 사용자 번호({@code bigint})다</b> ({@code platform} 의 P-11 — D-4 의 {@code String} 을 개정).
 * 이 앱의 나머지(Redis 키 · 파티 HASH 의 {@code member:{userId}} · 요청 파라미터)는 {@code userId} 를
 * 숫자 문자열({@code "42"})로 다루므로, 이 엔티티만 {@code Long} 이고 문자열로 되돌리는 것은 부르는 쪽
 * ({@link BlockedUsers}) 이 한다.
 *
 * <p><b>쓰기는 {@code app:platform} 만 한다.</b> 우리는 읽기만 하므로 {@code @Immutable} 로
 * Hibernate 가 UPDATE/INSERT 를 아예 만들지 않게 한다. {@code id} 채번도 저쪽 몫이라
 * {@code @GeneratedValue} 를 붙이지 않았다 — 동작하지도 않으면서 "여기서 만든다"고
 * 읽히기만 한다.
 *
 * <p><b>방향이 있다.</b> {@code blockerId} 가 {@code blockedId} 를 차단했다는 뜻이다.
 * 매칭에서는 누가 먼저 차단했는지가 의미 없으므로 조회할 때 양방향을 함께 본다
 * (BlockRepository 참고).
 *
 * <p><b>(blockerId, blockedId) 조합은 유일해야 한다.</b> 같은 사람을 두 번 차단할 수 없기
 * 때문이다. 다만 그 UNIQUE 제약은 이 테이블을 소유한 {@code app:platform} 이 건다.
 */
@Entity
@Immutable
@Table(name = "blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    /** app:platform 이 채번한다. 우리는 읽기만 한다. */
    @Id
    private Long id;

    /** 차단한 사람 */
    @Column(name = "blocker_id", nullable = false, updatable = false)
    private Long blockerId;

    /** 차단당한 사람 */
    @Column(name = "blocked_id", nullable = false, updatable = false)
    private Long blockedId;
}
