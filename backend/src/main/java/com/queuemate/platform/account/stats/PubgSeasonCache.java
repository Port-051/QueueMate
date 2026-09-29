package com.queuemate.platform.account.stats;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * <b>PUBG 의 현재 시즌 번호 캐시</b>(2026-09-29 — P-36). 키는 <b>{@code qm:pubg:season:{shard}}</b> 이고 값은 시즌 id(예 {@code division.bro.official.pc-2018-43}),
 * 수명은 30일({@link PubgProperties#seasonCacheTtl()} — 문서의 "한 달에 한 번보다 자주 묻지 마라". 시즌이 바뀐 뒤 최대 30일은 지난 시즌을 본다 — 감수한다).
 * 접두사 {@code qm:pubg:*} 는 이 앱의 것이다({@code qm:riot:*} · {@code qm:auth:*} 와 같은 자리).
 *
 * <p><b>왜 캐시하나</b> — 한도가 키 하나에 분당 10회라 연결 한 번에 한 번씩 시즌 목록을 부르면 아깝고, 문서가 자주 부르지 말라고 한다(시즌은 두 달쯤에 한 번 바뀐다).
 * <b>프로세스 로컬 캐시는 쓰지 않는다</b> — 이 앱은 stateless 다(CLAUDE.md §5). 여러 태스크가 한 번 읽은 것을 같이 쓴다.
 *
 * <p><b>Redis 가 죽으면 캐시 없이 간다</b> — 읽기 실패는 "없다" 로, 쓰기 실패는 WARN 한 줄로 끝낸다(그러면 매번 시즌 목록을 부른다). 전적 때문에 기능이 멈추면 안 된다.
 */
@Slf4j
@Component
public class PubgSeasonCache {

    /** 접두사는 이 한 곳에만 둔다. 계약에 적힌 이름이다 */
    static final String SEASON_KEY_PREFIX = "qm:pubg:season:";

    private final StringRedisTemplate redis;
    private final PubgProperties properties;

    public PubgSeasonCache(StringRedisTemplate redis, PubgProperties properties)
    {
        this.redis = redis;
        this.properties = properties;
    }

    /** 캐시된 현재 시즌 id. 없거나 Redis 에 묻지 못했으면 비어 있다 */
    public Optional<String> get(String shard)
    {
        try
        {
            String seasonId = redis.opsForValue().get(key(shard));
            return (seasonId == null || seasonId.isBlank()) ? Optional.empty() : Optional.of(seasonId);
        }
        catch(RuntimeException e)
        {
            log.warn("PUBG 시즌 캐시를 읽지 못했다 — 시즌 목록을 부른다 shard={}: {}", shard, e.toString());
            return Optional.empty();
        }
    }

    public void put(String shard, String seasonId)
    {
        try
        {
            redis.opsForValue().set(key(shard), seasonId, properties.seasonCacheTtl());
        }
        catch(RuntimeException e)
        {
            log.warn("PUBG 시즌 캐시를 적지 못했다 — 다음에 또 부른다 shard={}: {}", shard, e.toString());
        }
    }

    static String key(String shard)
    {
        return SEASON_KEY_PREFIX + shard;
    }
}
