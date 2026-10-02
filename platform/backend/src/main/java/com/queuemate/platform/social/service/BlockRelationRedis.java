package com.queuemate.platform.social.service;

import com.queuemate.platform.social.domain.BlockPair;
import com.queuemate.platform.social.domain.BlockRelationUnavailableException;
import com.queuemate.platform.social.redisKeys.BlockKeys;
import com.queuemate.platform.social.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>차단 관계 사본</b> — Redis SET {@code qm:user:block-rel:{userId}} 를 쓰는 유일한 자리(2026-10-02 소유자 결정 "차단 관계를 Redis 로 — matching 이 DB 를 안 쓰게" ·
 * docs/11 D-57 · {@code contracts/platform-api.md} P-52). 키 원본은 {@link BlockKeys}.
 *
 * <p><b>원본은 DB 의 {@code blocks} 표이고 이것은 사본이다 — 어긋나면 DB 가 맞다.</b> {@code matching} 이 합류 스크립트에서 {@code SISMEMBER} 로 읽어
 * 차단 관계인 사람끼리 한 파티에 넣지 않는다(INV-6). 그래서 <b>"DB 에는 있는데 사본에는 없다"(덜 막기)가 위험한 쪽</b>이고, "사본에만 있다"(더 막기)는 안전한 쪽이다 —
 * 모든 순서를 덜 막기가 생기지 않는 쪽으로 맞췄다.
 * <ul>
 *   <li>{@link #add} · {@link #remove} — 차단 · 해제의 트랜잭션 안에서 DB 를 바꾼 뒤 커밋 전에 부른다({@code BlockService}). 실패하면
 *       {@link BlockRelationUnavailableException} — 부른 쪽이 503 으로 트랜잭션째 되돌린다</li>
 *   <li>{@link #removeUser} — 회원 탈퇴가 커밋 뒤에 부른다. 실패해도 탈퇴는 끝난다(남는 것은 없는 사람을 향한 더 막기 — 재구성이 치운다)</li>
 *   <li>{@link #rebuild} — 표 전체에서 다시 만든다. 기동 때 한 번과 주기적으로({@code BlockRelationSync})</li>
 * </ul>
 * <b>사본을 바꾸는 트랜잭션은 한 줄로 선다</b>({@link BlockRepository#lockRelationCopy} — 이유는 그 주석).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlockRelationRedis {

    /** 재구성의 SCAN 이 한 번에 훑는 수(힌트). 키가 수천 개라는 전제다 */
    private static final long SCAN_COUNT = 1000;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> addBlockRelScript;
    private final RedisScript<Long> removeBlockRelScript;
    private final RedisScript<Long> replaceBlockRelScript;
    private final BlockRepository blockRepository;

    /**
     * 재구성의 결과 — 로그 한 줄과 테스트가 쓴다.
     *
     * @param users       집합을 갈아 끼운 사용자 수(차단 관계가 하나라도 있는 사람)
     * @param removedKeys 표에 없는 사용자라 지운 키의 수. 0 이 아니면 사본이 원본과 어긋나 있었다는 뜻이다(탈퇴의 정리 실패 · 테스트가 SQL 로 지운 사용자 등)
     */
    public record Rebuilt(int users, int removedKeys) {
    }

    /**
     * {@code a} 와 {@code b} 를 서로의 집합에 넣는다(대칭 · 한 스크립트 — {@code add-block-rel.lua}). 이미 있어도 그대로다.
     *
     * @throws BlockRelationUnavailableException Redis 에 닿지 못했다
     */
    public void add(long a, long b)
    {
        runPair(addBlockRelScript, a, b);
    }

    /**
     * {@code a} 와 {@code b} 를 서로의 집합에서 뺀다(대칭 · 한 스크립트 — {@code remove-block-rel.lua}). <b>반대 방향의 줄이 DB 에 없을 때만 부른다</b> — 그 판단은 부른 쪽이 한다.
     *
     * @throws BlockRelationUnavailableException Redis 에 닿지 못했다
     */
    public void remove(long a, long b)
    {
        runPair(removeBlockRelScript, a, b);
    }

    private void runPair(RedisScript<Long> script, long a, long b)
    {
        try
        {
            redisTemplate.execute(script, List.of(BlockKeys.key(a), BlockKeys.key(b)), String.valueOf(a), String.valueOf(b));
        }
        catch(DataAccessException e)
        {
            throw new BlockRelationUnavailableException(e);
        }
    }

    /**
     * <b>회원 탈퇴</b> — 탈퇴자의 집합을 지우고 상대들의 집합에서 탈퇴자 번호를 뺀다. {@code counterparts} 는 사용자 줄을 지우기 전에 읽어 둔 것이다
     * ({@code BlockReader#counterpartsOf} — 지우면 CASCADE 가 {@code blocks} 의 줄을 지운다). 한 스크립트로 묶지 않았다 — 도중에 끊겨 남는 것은
     * 없는 사람(번호는 다시 쓰이지 않는다)을 향한 더 막기뿐이고 재구성이 치운다.
     *
     * @throws BlockRelationUnavailableException Redis 에 닿지 못했다 — 탈퇴는 받아 넘긴다
     */
    public void removeUser(long userId, Collection<Long> counterparts)
    {
        String member = String.valueOf(userId);
        try
        {
            redisTemplate.delete(BlockKeys.key(userId));
            for(Long other : new LinkedHashSet<>(counterparts))
            {
                redisTemplate.opsForSet().remove(BlockKeys.key(other), member);
            }
        }
        catch(DataAccessException e)
        {
            throw new BlockRelationUnavailableException(e);
        }
    }

    /**
     * <b>재구성</b> — {@code blocks} 표 전체를 읽어 사용자별 집합을 계산하고, 키마다 한 스크립트로 갈아 끼운 뒤({@code replace-block-rel.lua} — {@code DEL} + {@code SADD}),
     * {@code SCAN qm:user:block-rel:*} 로 <b>표에 없는 사용자의 키를 지운다.</b> 여러 태스크가 같이 불러도 멱등이다(같은 표에서 같은 결과).
     *
     * <p><b>표가 크지 않다는 전제다</b>(수천 줄 · 키 수천 개) — 표를 한 번에 읽고 키마다 스크립트를 한 번씩 부른다. 수십만이 되면 나눠 읽고 파이프라인으로 바꾼다.
     *
     * <p><b>한 트랜잭션이고 맨 앞에서 줄을 선다</b>({@link BlockRepository#lockRelationCopy}) — 차단 · 해제가 그 사이 커밋되면 이 계산이 그것을 덮어 지운다.
     * 줄을 선 동안(표를 읽고 Redis 를 다 쓸 때까지) 차단 · 해제는 기다린다. DB 에는 쓰지 않는다.
     *
     * @throws DataAccessException DB · Redis 에 닿지 못했다 — 부른 쪽({@code BlockRelationSync})이 WARN 으로 넘긴다. 도중에 끊기면 일부 키만 새것이다(더 나빠지지는 않는다)
     */
    @Transactional
    public Rebuilt rebuild()
    {
        blockRepository.lockRelationCopy();
        Map<Long, Set<String>> relations = new HashMap<>();
        for(BlockPair pair : blockRepository.findAllPairs())
        {
            relations.computeIfAbsent(pair.blockerId(), id -> new HashSet<>()).add(String.valueOf(pair.blockedId()));
            relations.computeIfAbsent(pair.blockedId(), id -> new HashSet<>()).add(String.valueOf(pair.blockerId()));
        }
        Set<String> expected = new HashSet<>();
        for(Map.Entry<Long, Set<String>> entry : relations.entrySet())
        {
            String key = BlockKeys.key(entry.getKey());
            expected.add(key);
            redisTemplate.execute(replaceBlockRelScript, List.of(key), entry.getValue().toArray());
        }
        List<String> stale = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(BlockKeys.BLOCK_REL_PREFIX + "*").count(SCAN_COUNT).build();
        try(Cursor<String> cursor = redisTemplate.scan(options))
        {
            // SCAN 은 같은 키를 두 번 줄 수 있다 — 집합으로 거르지 않아도 DEL 은 멱등이다
            cursor.forEachRemaining(key -> {
                if(!expected.contains(key))
                {
                    stale.add(key);
                }
            });
        }
        if(!stale.isEmpty())
        {
            redisTemplate.delete(stale);
        }
        return new Rebuilt(relations.size(), stale.size());
    }
}
