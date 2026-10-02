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
 * <p>{@code hostId} 는 사용자 번호({@code users.id})이고 FK 가 걸려 있다. 엔티티 연관은 두지 않고 숫자로만 든다.
 * <b>방장이 탈퇴하면 확정된 글은 남고 이 칸만 빈다</b>(2026-10-02 소유자 결정 — "확정된 파티 기록은 남긴다", V9 의 {@code ON DELETE SET NULL}).
 * 모집 중 · 만료된 글은 탈퇴가 먼저 지우므로 <b>{@code null} 인 글은 늘 {@code CONFIRMED}</b> 다(DB 의 CHECK {@code recruit_posts_host_id_check}).
 * {@link Game} 은 {@code account} 의 <b>도메인 enum</b> 이다 — 테이블을 JOIN 하는 것이 아니라 이름의 목록을 같이 쓰는 것이다.
 *
 * <p><b>상태는 엔티티로 바꾸지 않는다</b> — {@code RecruitPostRepository} 의 조건부 UPDATE({@code … WHERE status = 'RECRUITING'})로만 바꾼다.
 * 그래야 만료 · 확정 · 방장의 삭제가 동시에 와도 한 번만 바뀐다. 그래서 여기에 {@code expire()} 같은 메서드가 없다.
 *
 * <p>id 를 DB 가 채번하므로({@code IDENTITY}) 새 엔티티는 id 가 {@code null} 이고 {@code save()} 가 {@code persist} 로 간다 — 반드시 INSERT 가 나가고
 * "모집 중인 글은 한 사람에 하나"는 DB 의 부분 UNIQUE 인덱스가 막는다.
 */
@Entity
@Table(name = "recruit_posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecruitPost {

    /** 방 정원의 상한이자 <b>정원을 모르는 글의 정원</b> — 방장 포함 5명(docs/11 D-11 10번). V8 전에 쓴 글({@code capacity} 가 {@code NULL})과
     *  gameconfig 를 못 읽은 채 쓴 글이 이 값이다(2026-09-30 — P-41) */
    public static final int MAX_CAPACITY = 5;

    /** 방 정원의 하한 — 방장 + 한 명. 모드의 인원이 이보다 작으면 모르는 값으로 친다(DB 의 CHECK 도 2 ~ 5 다 — V8) */
    public static final int MIN_CAPACITY = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * 글을 쓴 사람(사용자 번호) — 방장. 바뀌지 않는다(확정한 방에서 {@code room} 의 방장 키의 값이 바뀌어도 이 값은 그대로다 — D-23).
     * <b>방장이 탈퇴한 확정된 글은 {@code null}</b> 이다(2026-10-02 — V9 · P-48) — 읽는 쪽은 {@code null} 을 견뎌야 한다({@link #isHost} 는 견딘다)
     */
    @Column(name = "host_id", updatable = false)
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

    @Column(name = "auto_confirm_at")
    private Instant autoConfirmAt;

    public void setAutoConfirmAt(Instant deadline) { this.autoConfirmAt = deadline; }

    /**
     * 찾는 포지션. 줄에 순서가 없다 — 내려 줄 때 그 게임의 포지션 순서로 세운다.
     *
     * <p><b>EAGER + SUBSELECT</b> — 글 N개를 읽으면 포지션은 <b>쿼리 한 번</b>으로 같이 온다(N번이 아니다). 목록은 게시판 신호가 올 때마다
     * 다시 불리고, 글은 트랜잭션 밖(방 키를 읽는 동안)에서도 쓰이므로 LAZY 로 둘 수 없다.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @CollectionTable(name = "recruit_post_positions", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "position", nullable = false, length = 20)
    private Set<String> wantedPositions = new LinkedHashSet<>();

    /**
     * 방장 자신의 포지션(2026-09-30 소유자 결정 — P-38). 그 게임의 포지션 이름이고 {@link #wantedPositions} 에 들지 않는다 — 검증은 쓸 때 한다
     * ({@code PostValidation#hostPosition}). 포지션이 없는 모드 · 그 전에 쓴 글은 {@code null} 이다. 값의 목록이 코드와 gameconfig 에 있어 DB 는 CHECK 를 걸지 않는다(V6)
     */
    @Column(name = "host_position", length = 20)
    private String hostPosition;

    /**
     * 방의 정원(2026-09-30 소유자 결정 — P-41) — 그 모드의 인원(gameconfig 모드 HASH 의 {@code targetPartySize})이고 방장을 포함한다.
     * 글을 쓸 때 {@code PostService} 가 트랜잭션 밖에서 읽어 넘긴다(글은 고칠 수 없다 — 2026-10-01 소유자 결정). <b>V8 전에 쓴 글은 {@code NULL}</b> 이고 {@link #getCapacity()} 가 5 로 읽는다
     */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "capacity")
    private Integer capacity;

    /**
     * 빠른매치로 들어오는 것을 허용하는가(2026-10-02 소유자 결정 — P-50). {@code false} 면 게시판 방 먼저 합류의 후보에서 빠진다({@code RecruitPostRepository#findAutoJoinCandidates}) —
     * 직접 입장은 그대로 된다. 글을 쓸 때 반드시 고르고(요청의 필수 칸) 글은 고칠 수 없어 바뀌지 않는다. V10 전에 쓴 글은 {@code true} 다(그때까지 모두 합류 대상이었다)
     */
    @Column(name = "allow_auto_join", nullable = false, updatable = false)
    private boolean allowAutoJoin;

    public RecruitPost(Long hostId, Game game, String mode, String title, String description,
                       VoicePreference voice, String conditions, Set<String> wantedPositions,
                       String hostPosition, int capacity, boolean allowAutoJoin, Instant now)
    {
        this.hostId = hostId;
        this.game = game;
        this.mode = mode;
        this.title = title;
        this.description = description;
        this.voice = voice;
        this.conditions = conditions;
        this.wantedPositions = new LinkedHashSet<>(wantedPositions);
        this.hostPosition = hostPosition;
        this.capacity = capacity;
        this.allowAutoJoin = allowAutoJoin;
        this.status = PostStatus.RECRUITING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 방의 정원 — 적힌 값이고, <b>V8 전에 쓴 글({@code NULL})은 {@value #MAX_CAPACITY}</b> 다(그날까지의 정원). 입장 스크립트 · 응답의 {@code capacity} · {@code full} ·
     * 게시판 방 먼저 합류가 전부 이 값 하나를 본다(2026-09-30 — P-41)
     */
    public int getCapacity()
    {
        return capacity == null ? MAX_CAPACITY : capacity;
    }

    /** 이 사람이 글을 쓴 사람인가. 방장이 탈퇴한 글({@code hostId} 가 {@code null} — 2026-10-02 · P-48)은 누구의 글도 아니다 */
    public boolean isHost(Long userId)
    {
        return hostId != null && hostId.equals(userId);
    }
}
