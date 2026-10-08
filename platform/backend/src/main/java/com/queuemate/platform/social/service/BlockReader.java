package com.queuemate.platform.social.service;

import com.queuemate.platform.social.repository.BlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * <b>{@code social} 밖에서 차단 관계를 읽는 창구</b> — 모집 글 목록과 방의 입장({@code party.service.PostEntryGate})이 "방 안의 누구와든 차단 관계인가"를 볼 때 쓴다
 * (CLAUDE.md §7.1 · docs/11 D-20). 묻는 사용자 번호가 DB 가 아니라 <b>Redis 의 멤버 HASH</b> 에서 오므로 JOIN 할 짝이 없어 창구로 남긴다 — 어차피 {@code IN} 한 번이다.
 * 다른 패키지는 {@code BlockRepository} 를 직접 쓰지 않고 여기를 부른다. 회원 탈퇴도 여기서 상대 목록을 읽는다({@link #counterpartsOf}).
 */
@Component
@RequiredArgsConstructor
public class BlockReader {

    private final BlockRepository blockRepository;

    /**
     * {@code others} 가운데 나와 <b>어느 방향으로든</b> 차단 관계인 사람 — 내가 차단했든 나를 차단했든 같다(D-20). <b>쿼리 한 번이다.</b>
     *
     * <p>결과가 비어 있지 않으면 그 방을 목록에서 빼고 입장도 거절한다. <b>결과를 그대로 응답에 싣지 마라</b> —
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

    /**
     * 나와 <b>어느 방향으로든</b> 차단 관계인 사람 전부. <b>회원 탈퇴</b>({@code account.service.AccountDeletionService})가 사용자 줄을 지우기 전에, 같은 트랜잭션에서
     * 사용자 줄을 잠근 뒤 읽는다 — 지우면 CASCADE 가 그 줄들을 지우므로, 커밋 뒤 차단 관계 사본에서 탈퇴자 번호를 뺄 상대를 미리 들고 있어야 한다
     * ({@link BlockRelationRedis#removeUser} — 2026-10-02 · P-52). 사용자 줄을 잠근 뒤라 그 사이에 이 사람을 향한 새 차단이 커밋되지 않는다(FK 검사가 기다린다).
     */
    @Transactional(readOnly = true)
    public Set<Long> counterpartsOf(Long me)
    {
        return new HashSet<>(blockRepository.findAllCounterparts(me));
    }
}
