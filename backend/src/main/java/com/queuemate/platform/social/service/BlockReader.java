package com.queuemate.platform.social.service;

import com.queuemate.platform.social.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * <b>{@code social} 밖에서 차단 관계를 읽는 창구</b> — 모집 글 목록과 입장권 발급이 "방 안의 누구와든 차단 관계인가"를 볼 때 쓴다
 * (CLAUDE.md §7.1 · docs/11 D-20). 다른 도메인은 {@code social.blocks} 를 JOIN 하지 않고 리포지토리도 직접 쓰지 않는다 — 여기만 부른다.
 */
@Component
@RequiredArgsConstructor
public class BlockReader {

    private final BlockRepository blockRepository;

    /**
     * {@code others} 가운데 나와 <b>어느 방향으로든</b> 차단 관계인 사람 — 내가 차단했든 나를 차단했든 같다(D-20). <b>쿼리 한 번이다.</b>
     *
     * <p>결과가 비어 있지 않으면 그 방을 목록에서 빼고 입장권도 내주지 않는다. <b>결과를 그대로 응답에 싣지 마라</b> —
     * 누가 나를 차단했는지가 새어 나간다.
     */
    @Transactional(readOnly = true)
    public Set<Long> findBlockedEitherWay(Long me, Collection<Long> others)
    {
        if(me == null || others == null || others.isEmpty())
        {
            return Set.of();
        }
        return new HashSet<>(blockRepository.findCounterpartsEitherWay(me, new HashSet<>(others)));
    }
}
