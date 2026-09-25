package com.queuemate.platform.party.domain;

import com.queuemate.platform.account.domain.Game;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 모집 글. <b>{@code id} 가 곧 {@code roomId} 다</b> — DB 가 매기고(bigint identity), 글을 쓰는 그 트랜잭션에서 그 값(숫자를 문자열로)으로 방이 만들어진다({@code PostStore#create} — 2026-09-25 2단계)
 * ({@code contracts/platform-api.md} "모집 글 · 목록").
 *
 * <p>{@code hostId} 는 사용자 번호({@code account.users.id})지만 <b>FK 도 엔티티 연관도 없다</b>(크로스 스키마 FK 금지 — CLAUDE.md §3.5). 숫자로만 든다.
 * {@link Game} 은 {@code account} 의 <b>도메인 enum</b> 이다 — 테이블을 JOIN 하는 것이 아니라 이름의 목록을 같이 쓰는 것이다.
 *
 * <p><b>상태는 엔티티로 바꾸지 않는다</b> — {@code RecruitPostRepository} 의 조건부 UPDATE({@code … WHERE status = 'RECRUITING'})로만 바꾼다.
 * 그래야 만료 · 확정 · 방장의 삭제가 동시에 와도 한 번만 바뀐다. 그래서 여기에 {@code expire()} 같은 메서드가 없다.
 *
 * <p>id 를 DB 가 채번하므로({@code IDENTITY}) 새 엔티티는 id 가 {@code null} 이고 {@code save()} 가 {@code persist} 로 간다 — 반드시 INSERT 가 나가고
 * "모집 중인 글은 한 사람에 하나"는 DB 의 부분 UNIQUE 인덱스가 막는다.
 */
@Entity
@Table(schema = "party", name = "recruit_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 글을 쓴 사람(사용자 번호) — 방장. 바뀌지 않는다(확정한 방에서 {@code room} 의 방장 키의 값이 바뀌어도 이 값은 그대로다 — D-23) */
    @Column(name = "host_id", nullable = false, updatable = false)
    private Long hostId;

    @Enumerated(EnumType.STRING)
    @Column(name = "game", nullable = false, updatable = false, length = 10)
    private Game game;

    /** 자유 문자열 — 모드의 목록은 {@code matching} 의 gameconfig 가 원본이라 여기서 검증하지 않는다 */
    @Column(name = "mode", length = 30)
    private String mode;

    @Column(name = "title", nullable = false, length = 60)
    private String title;

    @Column(name = "description", length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "voice", nullable = false, length = 10)
    private VoicePreference voice;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 10)
    private PlayPurpose purpose;

    /** 게임마다 다른 조건. <b>jsonb 의 글자 그대로</b> 들고 있다가 응답에 그대로 싣는다. 모양은 쓸 때 검증한다({@code PostConditions}) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions", nullable = false)
    private String conditions;

    /** 조건부 UPDATE 로만 바뀐다 — 엔티티를 저장할 때 덮어쓰지 않게 {@code updatable = false} 다 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 12)
    private PostStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "confirmed_at", updatable = false)
    private Instant confirmedAt;

    @Column(name = "expired_at", updatable = false)
    private Instant expiredAt;

    /**
     * 찾는 포지션. 줄에 순서가 없다 — 내려 줄 때 그 게임의 포지션 순서로 세운다.
     *
     * <p><b>EAGER + SUBSELECT</b> — 글 N개를 읽으면 포지션은 <b>쿼리 한 번</b>으로 같이 온다(N번이 아니다). 목록은 게시판 신호가 올 때마다
     * 다시 불리고, 글은 트랜잭션 밖(방 키를 읽는 동안)에서도 쓰이므로 LAZY 로 둘 수 없다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(schema = "party", name = "recruit_post_positions", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "position", nullable = false, length = 20)
    private Set<String> wantedPositions = new LinkedHashSet<>();

    public RecruitPost(Long hostId, Game game, String mode, String title, String description,
                       VoicePreference voice, PlayPurpose purpose, String conditions, Set<String> wantedPositions,
                       Instant now)
    {
        this.hostId = hostId;
        this.game = game;
        this.mode = mode;
        this.title = title;
        this.description = description;
        this.voice = voice;
        this.purpose = purpose;
        this.conditions = conditions;
        this.wantedPositions = new LinkedHashSet<>(wantedPositions);
        this.status = PostStatus.RECRUITING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 글의 내용을 고친다. 상태 · 방장 · 게임은 바뀌지 않는다. 부르는 쪽이 "준 것만" 골라 넘긴다 — 여기는 받은 대로 적는다 */
    public void edit(String mode, String title, String description, VoicePreference voice, PlayPurpose purpose,
                     String conditions, Set<String> wantedPositions, Instant now)
    {
        this.mode = mode;
        this.title = title;
        this.description = description;
        this.voice = voice;
        this.purpose = purpose;
        this.conditions = conditions;
        if(!this.wantedPositions.equals(wantedPositions))
        {
            // 같은 컬렉션을 비우고 다시 채운다 — 새 컬렉션으로 갈아 끼우면 Hibernate 가 줄을 전부 지우고 다시 넣는다
            this.wantedPositions.retainAll(wantedPositions);
            this.wantedPositions.addAll(wantedPositions);
        }
        this.updatedAt = now;
    }

    public boolean isHost(Long userId)
    {
        return hostId.equals(userId);
    }
}
