package com.queuemate.platform.social.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.common.web.Ids;
import com.queuemate.platform.social.domain.Block;
import com.queuemate.platform.social.domain.BlockRelationUnavailableException;
import com.queuemate.platform.social.dto.BlockListResponse;
import com.queuemate.platform.social.dto.BlockResponse;
import com.queuemate.platform.social.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 차단 · 해제 · 내가 차단한 사람의 목록 ({@code contracts/platform-api.md} "차단").
 *
 * <p><b>차단은 DB 에 저장하고, 같은 트랜잭션에서 Redis 의 차단 관계 사본({@code qm:user:block-rel:*})에도 적는다</b>(2026-10-02 소유자 결정
 * "차단 관계를 Redis 로 — matching 이 DB 를 안 쓰게" · docs/11 D-57 · P-52). {@code matching} 은 합류 스크립트에서 그 사본을 읽는다 — {@code blocks} 를 읽지 않는다.
 * 이벤트({@code BlockChanged.fifo} 같은 것)는 여전히 내지 않는다(docs/11 D-12). 이미 맺은 친구 관계 · 이미 같은 방에 있는 상태도 건드리지 않는다
 * (미정 그대로 — CLAUDE.md §7.1).
 *
 * <p><b>쓰는 순서 — DB 먼저, Redis 다음, 그리고 커밋</b>({@link BlockRelationRedis}). 사본을 못 고치면 503 {@code BLOCK_STATE_UNAVAILABLE} 로 트랜잭션째 되돌린다 —
 * 그래서 "DB 에는 있는데 사본에는 없는 차단" 이 생기지 않는다. 반대(사본에는 넣었는데 커밋이 실패)는 더 막는 쪽이라 안전하고 재구성이 지운다.
 * 해제는 거꾸로 커밋이 실패하면 사본에서 뺀 것을 되돌려 넣는다({@link #unblock}). 차단 · 해제 · 재구성은 한 줄로 선다({@code BlockRepository#lockRelationCopy}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    /** 마이그레이션(V1__schema.sql)이 붙인 제약의 이름이다. 거기서 바꾸면 여기도 바꾼다 */
    static final String BLOCKS_UNIQUE = "blocks_blocker_blocked_key";
    static final String BLOCKS_NOT_SELF = "blocks_not_self";
    static final String BLOCKER_FKEY = "blocks_blocker_id_fkey";

    /**
     * 차단 관계 사본(Redis)을 못 고쳐 차단 · 해제를 되돌렸다 — 503 + {@code Retry-After: 5}(2026-10-02 · P-52). 이름과 글귀는 Claude 가 정했다 —
     * {@code ROOM_STATE_UNAVAILABLE} 은 방의 상태라는 뜻이라 따로 뒀다
     */
    public static final String BLOCK_STATE_UNAVAILABLE = "BLOCK_STATE_UNAVAILABLE";

    /** 503 에 싣는 {@code Retry-After}(초) — 방의 503 과 같은 값이다 */
    static final long RETRY_AFTER_SECONDS = 5;

    private final BlockRepository blockRepository;
    private final BlockRelationRedis blockRelationRedis;

    /**
     * 차단한다. <b>같은 사람 두 번 차단은 DB 가 막는다</b> — 있는지 먼저 조회하지 않고 INSERT 한 뒤 UNIQUE 위반을 409 로 옮긴다.
     * 같은 차단이 동시에 여러 번 와도 하나만 통과한다(CLAUDE.md §5).
     *
     * <p><b>대상이 있는 사용자인지도 DB 가 본다</b> — {@code blocks_blocked_id_fkey} 의 위반이 404 {@code USER_NOT_FOUND} 다(조회하지 않는다).
     * 내 쪽({@code blocks_blocker_id_fkey})이 깨지면 토큰은 멀쩡한데 내가 DB 에 없는 것이라 401 이다.
     *
     * <p><b>DB 에 넣은 뒤 커밋 전에 차단 관계 사본의 양쪽 집합에 넣는다</b>(P-52). Redis 에 닿지 못하면 503 {@code BLOCK_STATE_UNAVAILABLE} 이고 넣은 줄도 되돌린다.
     * 거절(409 · 400 · 404 · 401)이면 사본을 건드리지 않는다.
     *
     * @param target 사용자 번호(요청 본문의 글자 그대로). 숫자가 아니면 있을 수 없는 사용자라 없는 사용자와 같은 404 다
     */
    @Transactional
    public BlockResponse block(Long me, String target)
    {
        Long targetUserId = Ids.parse(target).orElseThrow(BlockService::userNotFound);
        if(me.equals(targetUserId))
        {
            throw cannotBlockSelf();
        }
        blockRepository.lockRelationCopy();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        try
        {
            blockRepository.saveAndFlush(new Block(me, targetUserId, now));
        }
        catch(DataIntegrityViolationException e)
        {
            String constraint = ConstraintViolations.nameOf(e);
            if(BLOCKS_UNIQUE.equals(constraint))
            {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_BLOCKED", "이미 차단한 사용자입니다");
            }
            if(BLOCKS_NOT_SELF.equals(constraint))
            {
                throw cannotBlockSelf();
            }
            if(BLOCKER_FKEY.equals(constraint))
            {
                throw ApiException.unauthenticated();
            }
            if(ConstraintViolations.isMissingUser(constraint))
            {
                throw userNotFound();
            }
            throw e;
        }
        try
        {
            blockRelationRedis.add(me, targetUserId);
        }
        catch(BlockRelationUnavailableException e)
        {
            log.warn("차단을 되돌린다 — 차단 관계 사본(Redis)에 넣지 못했다 blockerId={} blockedId={}: {}", me, targetUserId, e.getCause().toString());
            throw stateUnavailable();
        }
        log.info("차단 blockerId={} blockedId={}", me, targetUserId);
        // 방금 이 트랜잭션이 넣은 줄이다 — 반드시 있다(대상이 있는 사용자라는 것도 FK 가 확인했다)
        return blockRepository.findResponse(me, targetUserId)
                .orElseThrow(() -> new IllegalStateException("방금 넣은 차단이 없다 blockedId=" + targetUserId));
    }

    /**
     * 차단을 푼다. 차단한 적이 없어도 성공이다 — 두 번 눌러도 결과가 같다(지운 줄이 없으면 사본도 건드리지 않는다).
     *
     * <p><b>줄을 지운 뒤 같은 트랜잭션에서 반대 방향 줄(상대가 나를 차단한 것)이 있는지 본다</b> — 있으면 두 사람은 여전히 차단 관계라 사본을 그대로 두고,
     * 없을 때만 양쪽 집합에서 뺀다(P-52). Redis 에 닿지 못하면 503 {@code BLOCK_STATE_UNAVAILABLE} 이고 지운 줄도 되돌린다.
     *
     * <p><b>뺀 뒤 커밋이 실패하면 사본에 되돌려 넣는다</b>(롤백 뒤 — Claude 세부). 그대로 두면 DB 에는 남은 차단이 사본에서 빠진 채(덜 막기) 다음 재구성까지 간다.
     * 되돌려 넣기는 늦게 돌아도 더 막는 쪽으로만 틀린다(그 사이 다시 풀렸어도 재구성이 지운다). 503 으로 되돌릴 때도 같이 돈다 — 스크립트가 실은 돌았는데 응답만
     * 못 받은 경우를 덮는다. 되돌려 넣기도 실패하면 WARN 한 줄 — 재구성이 맞춘다.
     */
    @Transactional
    public void unblock(Long me, Long targetUserId)
    {
        blockRepository.lockRelationCopy();
        int deleted = blockRepository.deleteByBlockerIdAndBlockedId(me, targetUserId);
        if(deleted == 0)
        {
            return;
        }
        log.info("차단 해제 blockerId={} blockedId={}", me, targetUserId);
        if(blockRepository.existsByBlockerIdAndBlockedId(targetUserId, me))
        {
            // 상대가 나를 차단한 줄이 남았다 — 관계가 이어지니 사본은 그대로다
            return;
        }
        restoreIfNotCommitted(me, targetUserId);
        try
        {
            blockRelationRedis.remove(me, targetUserId);
        }
        catch(BlockRelationUnavailableException e)
        {
            log.warn("차단 해제를 되돌린다 — 차단 관계 사본(Redis)에서 빼지 못했다 blockerId={} blockedId={}: {}", me, targetUserId, e.getCause().toString());
            throw stateUnavailable();
        }
    }

    /** 이 트랜잭션이 커밋되지 않고 끝나면 그 쌍을 사본에 다시 넣는다. 트랜잭션 밖에서 불렸으면(동기화가 없다) 아무것도 걸지 않는다 */
    private void restoreIfNotCommitted(Long me, Long targetUserId)
    {
        if(!TransactionSynchronizationManager.isSynchronizationActive())
        {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status)
            {
                if(status == STATUS_COMMITTED)
                {
                    return;
                }
                try
                {
                    blockRelationRedis.add(me, targetUserId);
                }
                catch(BlockRelationUnavailableException e)
                {
                    log.warn("커밋되지 않은 차단 해제를 사본에 되돌려 넣지 못했다 — 재구성이 맞춘다 blockerId={} blockedId={}: {}",
                            me, targetUserId, e.getCause().toString());
                }
            }
        });
    }

    /** 내가 차단한 사람만. <b>나를 차단한 사람은 보여 주지 않는다.</b> 쿼리 한 번이다 — 닉네임은 JOIN 으로 붙인다 */
    @Transactional(readOnly = true)
    public BlockListResponse list(Long me)
    {
        return new BlockListResponse(blockRepository.findResponsesOf(me));
    }

    private static ApiException stateUnavailable()
    {
        return ApiException.retryAfter(HttpStatus.SERVICE_UNAVAILABLE, BLOCK_STATE_UNAVAILABLE,
                "차단을 지금 처리할 수 없습니다. 잠시 뒤에 다시 시도해 주세요", RETRY_AFTER_SECONDS);
    }

    private static ApiException cannotBlockSelf()
    {
        return new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_BLOCK_SELF", "자기 자신은 차단할 수 없습니다");
    }

    private static ApiException userNotFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "없는 사용자입니다");
    }
}
