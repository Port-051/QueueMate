package com.queuemate.platform.account.service;

import com.queuemate.platform.account.domain.Game;
import com.queuemate.platform.account.dto.GameProfileResponse;
import com.queuemate.platform.account.domain.GameAccount;
import com.queuemate.platform.account.dto.UserGameProfile;
import com.queuemate.platform.account.repository.GameAccountRepository;
import com.queuemate.platform.account.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <b>{@code account} 밖에서 게임 프로필을 읽는 창구</b> — 모집 글 목록이 방 안 사람들의 카드를 그릴 때 쓴다
 * ({@code contracts/platform-api.md} "글 한 줄"의 {@code host} · {@code members[].profile}).
 * 2026-09-26 부터 테이블을 패키지 너머로 JOIN 해도 되지만 이것은 남긴다 — 묻는 사용자 번호가 DB 가 아니라 <b>Redis 의 멤버 SET</b> 에서 오므로
 * JOIN 할 짝이 없고, 어차피 {@code IN} 한 번이다.
 *
 * <p><b>게임사 API 를 부르지 않는다</b> — DB 의 스냅숏({@code game_account_stats})만 읽는다. 그 스냅숏을 채우는 것은
 * {@code account.stats} 이고 목록을 그리는 길과 따로 돈다(게임 계정 연결 · 전적 갱신 — {@code contracts/platform-api.md} "전적을 긁는 것").
 */
@Component
@RequiredArgsConstructor
public class GameProfileReader {

    private final UserRepository userRepository;
    private final GameAccountRepository gameAccountRepository;

    /**
     * 여러 사용자의 닉네임과 그 게임의 게임 프로필. <b>쿼리 한 번이다</b> — 사람 수만큼 되풀이하지 않는다.
     *
     * @return 사용자 번호 → 프로필. <b>없는 사용자는 결과에 없다.</b> 있는 사용자인데 그 게임에 연결한 게임 계정이 없으면
     *         {@link UserGameProfile#profile()} 이 {@code null} 이다
     */
    @Transactional(readOnly = true)
    public Map<Long, UserGameProfile> findProfiles(Collection<Long> userIds, Game game)
    {
        if(userIds == null || userIds.isEmpty())
        {
            return Map.of();
        }
        // 같은 번호가 두 번 들어와도 IN 절은 한 줄만 돌려준다 — 그래도 모아 담을 때 겹치지 않게 미리 걷어 낸다
        return userRepository.findGameProfileRows(new HashSet<>(userIds), game).stream()
                .collect(Collectors.toMap(row -> row.userId(), row -> new UserGameProfile(row.userId(), row.nickname(),
                        row.account() == null ? null : GameProfileResponse.of(row.account(), row.stats()))));
    }

    /**
     * 여러 사용자의 그 게임 계정 <b>티어</b>만 — <b>쿼리 한 번이다.</b> 게시판 방 먼저 합류(2026-09-28 · P-28)가 후보 글들의 방장 티어로 "내 티어가 그 방의 허용 범위 안인가" 를
     * 볼 때 쓴다({@code party.service.AutoJoinService}). 사용자 번호가 글({@code recruit_posts.host_id})에서 오므로 JOIN 으로도 되지만 글 쿼리를 무겁게 하지 않으려고 창구로 둔다.
     *
     * @return 사용자 번호 → 티어. <b>그 게임에 계정이 없는 사용자는 결과에 없고</b>, 계정은 있는데 티어를 안 적은 사용자는 값이 {@code null} 이다
     */
    @Transactional(readOnly = true)
    public Map<Long, String> findTiers(Collection<Long> userIds, Game game)
    {
        if(userIds == null || userIds.isEmpty())
        {
            return Map.of();
        }
        Map<Long, String> tiers = new HashMap<>();
        for(GameAccount account : gameAccountRepository.findByUserIdInAndGame(new HashSet<>(userIds), game))
        {
            tiers.put(account.getUserId(), account.getTier());
        }
        return tiers;
    }
}
