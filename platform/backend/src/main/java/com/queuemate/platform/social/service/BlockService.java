package com.queuemate.platform.social.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.common.web.Ids;
import com.queuemate.platform.social.domain.Block;
import com.queuemate.platform.social.dto.BlockListResponse;
import com.queuemate.platform.social.dto.BlockResponse;
import com.queuemate.platform.social.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 차단 · 해제 · 내가 차단한 사람의 목록 ({@code contracts/platform-api.md} "차단").
 *
 * <p><b>차단은 DB 에 저장하는 것으로 끝난다</b> — {@code BlockChanged.fifo} 같은 이벤트를 내지 않는다(docs/11 D-12).
 * {@code matching} 은 확정 직전에 {@code blocks} 를 직접 읽는다(D-1 · D-2). 이미 맺은 친구 관계 · 이미 같은 방에 있는 상태도 건드리지 않는다
 * (미정 그대로 — CLAUDE.md §7.1).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    /** 마이그레이션(V1__schema.sql)이 붙인 제약의 이름이다. 거기서 바꾸면 여기도 바꾼다 */
    static final String BLOCKS_UNIQUE = "blocks_blocker_blocked_key";
    static final String BLOCKS_NOT_SELF = "blocks_not_self";
    static final String BLOCKER_FKEY = "blocks_blocker_id_fkey";

    private final BlockRepository blockRepository;

    /**
     * 차단한다. <b>같은 사람 두 번 차단은 DB 가 막는다</b> — 있는지 먼저 조회하지 않고 INSERT 한 뒤 UNIQUE 위반을 409 로 옮긴다.
     * 같은 차단이 동시에 여러 번 와도 하나만 통과한다(CLAUDE.md §5).
     *
     * <p><b>대상이 있는 사용자인지도 DB 가 본다</b> — {@code blocks_blocked_id_fkey} 의 위반이 404 {@code USER_NOT_FOUND} 다(조회하지 않는다).
     * 내 쪽({@code blocks_blocker_id_fkey})이 깨지면 토큰은 멀쩡한데 내가 DB 에 없는 것이라 401 이다.
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
        log.info("차단 blockerId={} blockedId={}", me, targetUserId);
        // 방금 이 트랜잭션이 넣은 줄이다 — 반드시 있다(대상이 있는 사용자라는 것도 FK 가 확인했다)
        return blockRepository.findResponse(me, targetUserId)
                .orElseThrow(() -> new IllegalStateException("방금 넣은 차단이 없다 blockedId=" + targetUserId));
    }

    /** 차단을 푼다. 차단한 적이 없어도 성공이다 — 두 번 눌러도 결과가 같다 */
    @Transactional
    public void unblock(Long me, Long targetUserId)
    {
        int deleted = blockRepository.deleteByBlockerIdAndBlockedId(me, targetUserId);
        if(deleted > 0)
        {
            log.info("차단 해제 blockerId={} blockedId={}", me, targetUserId);
        }
    }

    /** 내가 차단한 사람만. <b>나를 차단한 사람은 보여 주지 않는다.</b> 쿼리 한 번이다 — 닉네임은 JOIN 으로 붙인다 */
    @Transactional(readOnly = true)
    public BlockListResponse list(Long me)
    {
        return new BlockListResponse(blockRepository.findResponsesOf(me));
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
