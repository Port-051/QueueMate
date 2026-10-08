package com.queuemate.platform.account.repository;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.User;
import com.queuemate.platform.account.domain.UserGameProfileRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * <b>{@code existsByNickname} 으로 중복을 먼저 확인하지 마라</b> — 확인과 INSERT 사이에 다른 요청이 끼어든다.
 * 중복은 {@code saveAndFlush} 의 제약 위반으로 안다 ({@code SocialLoginService#signup}).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 사용자 줄을 {@code SELECT … FOR UPDATE} 로 잠근다 — 같은 사용자의 소셜 끊기를 줄 세운다({@code SocialLoginService#unlink}).
     * 트랜잭션 안에서만 부른다. 없으면 빈 값이다.
     */
    @Query(value = "select id from users where id = :userId for update", nativeQuery = true)
    Optional<Long> lockById(@Param("userId") Long userId);

    /**
     * <b>회원 탈퇴</b> — 사용자 줄을 지운다(2026-10-02 소유자 결정 · P-48). 딸린 줄은 DB 의 FK 가 정리한다 — 사용자 번호의 칸은 전부 {@code ON DELETE CASCADE}
     * (소셜 연결 · 게임 계정과 전적 · 차단 양방향 · 친구 요청과 친구 · 신고(낸 것 · 받은 것) · 최근 함께한 사람 · 파티원 줄)이고,
     * <b>글의 방장 칸만 {@code ON DELETE SET NULL}</b> 이다(V9 — 확정된 글은 남긴다). 확정되지 않은 글은 같은 트랜잭션에서 먼저 지웠어야 한다
     * ({@code AccountDeletionService}). 엔티티를 읽지 않고 한 문장으로 지운다.
     *
     * @return 지운 줄의 수(0 또는 1)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "delete from users where id = :userId", nativeQuery = true)
    int deleteUserRow(@Param("userId") Long userId);

    /**
     * 여러 사용자의 닉네임과, <b>어느 한 게임</b>에 연결한 게임 계정 · 전적을 <b>쿼리 한 번으로</b> 읽는다 — 목록의 카드가 쓴다
     * ({@code GameProfileReader}). 사람 수만큼 쿼리를 되풀이하지 않는다. 그 게임에 연결한 계정이 없는 사용자도 한 줄로 온다(LEFT JOIN).
     * {@code userIds} 가 비어 있으면 부르지 마라.
     */
    @Query("""
            select new com.queuemate.platform.account.domain.UserGameProfileRow(u.id, u.nickname, a, s)
              from User u
              left join GameAccount a on a.userId = u.id and a.game = :game
              left join GameAccountStats s on s.gameAccountId = a.id
             where u.id in :userIds
            """)
    List<UserGameProfileRow> findGameProfileRows(@Param("userIds") Collection<Long> userIds, @Param("game") Game game);
}
