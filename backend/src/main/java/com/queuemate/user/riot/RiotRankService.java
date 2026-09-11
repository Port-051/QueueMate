package com.queuemate.user.riot;

import com.queuemate.common.domain.GameKey;
import com.queuemate.user.domain.GameAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 연결된 게임 계정의 티어를 채운다.
 *
 * <p><b>지금은 LoL만 된다.</b> 발로란트 개인 랭크를 주는 공개 엔드포인트가 Riot에 없다.
 * val-ranked-v1은 액트 리더보드만 주고, 개인 전적은 승인받은 프로덕션 키 전용이다.
 * 그래서 VALORANT와 PUBG는 조회하지 않고 티어를 비워 둔다.
 *
 * <p>티어는 부가 정보다. Riot이 죽어 있어도 계정 연결과 목록 조회는 그대로 되어야 한다.
 * 이 클래스가 밖으로 예외를 내보내지 않는 이유다.
 */
@Service
public class RiotRankService {

    private static final Logger log = LoggerFactory.getLogger(RiotRankService.class);

    private final RiotClient riot;
    private final RiotProperties properties;

    public RiotRankService(RiotClient riot, RiotProperties properties) {
        this.riot = riot;
        this.properties = properties;
    }

    /**
     * 오래된 계정의 티어를 다시 읽는다. 하나라도 바뀌었으면 true.
     *
     * <p>목록 조회 경로에서 불린다. 스케줄러를 따로 두지 않는 이유는, 아무도 보지 않는
     * 계정의 티어를 갱신하는 데 호출 한도를 쓸 이유가 없어서다.
     */
    public boolean refreshStale(List<GameAccount> accounts, OffsetDateTime now) {
        if (!properties.configured()) {
            return false;
        }
        boolean changed = false;
        for (GameAccount account : accounts) {
            if (isStale(account, now)) {
                changed |= sync(account, now);
            }
        }
        return changed;
    }

    /** 방금 연결한 계정의 티어를 읽는다. 실패해도 연결은 이미 끝났고 되돌리지 않는다. */
    public void syncNewlyLinked(GameAccount account, OffsetDateTime now) {
        if (properties.configured()) {
            sync(account, now);
        }
    }

    /**
     * @return 계정에 쓴 것이 있으면 true. Riot을 못 읽었으면 아무것도 쓰지 않고 false
     */
    private boolean sync(GameAccount account, OffsetDateTime now) {
        if (account.getProviderGame() != GameKey.LOL) {
            return false;
        }
        RiotRankService.RiotId riotId = RiotId.parse(account.getExternalGameId());
        if (riotId == null) {
            // 형식이 아니면 다시 물어도 결과가 같다. 시각을 남겨 매 조회마다 되묻지 않게 한다.
            account.applyRank(null, now);
            return true;
        }
        try {
            Optional<String> puuid = riot.findPuuid(riotId.gameName(), riotId.tagLine());
            if (puuid.isEmpty()) {
                // 없는 Riot ID다. 이것도 확인된 결과이므로 시각을 남긴다.
                account.applyRank(null, now);
                return true;
            }
            account.applyRank(riot.findSoloRank(puuid.get()).map(RiotRank::toRankCode).orElse(null), now);
            return true;
        } catch (RiotUnavailableException e) {
            // 다음 조회 때 다시 시도한다. 이미 알고 있던 티어는 그대로 둔다.
            log.debug("티어 갱신을 건너뛴다 game={}", account.getProviderGame());
            return false;
        }
    }

    private boolean isStale(GameAccount account, OffsetDateTime now) {
        if (account.getProviderGame() != GameKey.LOL) {
            return false;
        }
        OffsetDateTime synced = account.getRankSyncedAt();
        if (synced == null) {
            return true;
        }
        // 랭크가 있으면 자주, 없으면 드물게 본다. 언랭은 흔하고 잘 바뀌지 않는다.
        Duration ttl = account.getRankCode() == null ? properties.unrankedTtl() : properties.rankTtl();
        return synced.plus(ttl).isBefore(now);
    }

    /** `이름#태그`. 이름에는 공백이 들어갈 수 있고 태그에는 들어가지 않는다. */
    record RiotId(String gameName, String tagLine) {

        static RiotId parse(String externalGameId) {
            if (externalGameId == null) {
                return null;
            }
            int hash = externalGameId.lastIndexOf('#');
            if (hash <= 0 || hash == externalGameId.length() - 1) {
                return null;
            }
            String name = externalGameId.substring(0, hash).trim();
            String tag = externalGameId.substring(hash + 1).trim();
            return name.isEmpty() || tag.isEmpty() ? null : new RiotId(name, tag);
        }
    }
}
