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
 * <p><b>스키마 분리의 유일한 예외다.</b> 스키마는 앱별로 나누고 크로스 스키마 접근을
 * 금지하는데, 이 테이블만 {@code matching} 롤에 SELECT 권한을 준다 (docs/11 D-1).
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
@Table(schema = "social", name = "blocks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block {

    /** app:platform 이 채번한다. 우리는 읽기만 한다. */
    @Id
    private Long id;

    /** 차단한 사람 */
    @Column(name = "blocker_id", nullable = false, updatable = false)
    private String blockerId;

    /** 차단당한 사람 */
    @Column(name = "blocked_id", nullable = false, updatable = false)
    private String blockedId;
}
