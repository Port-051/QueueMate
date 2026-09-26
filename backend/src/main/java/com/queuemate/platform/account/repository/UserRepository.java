package com.queuemate.platform.account.repository;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.domain.User;
import com.queuemate.platform.account.domain.UserGameProfileRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * <b>{@code existsByLoginId} 로 중복을 먼저 확인하지 마라</b> — 확인과 INSERT 사이에 다른 요청이 끼어든다.
 * 중복은 {@code saveAndFlush} 의 제약 위반으로 안다 ({@code AuthService#signup}).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 로그인할 때 — 로그인 아이디로 찾는 유일한 자리다. 그 밖의 모든 곳은 사용자 번호({@code id})로 찾는다 */
    Optional<User> findByLoginId(String loginId);

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
