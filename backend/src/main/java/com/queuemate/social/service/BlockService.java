package com.queuemate.social.service;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.social.domain.Block;
import com.queuemate.social.domain.Friendship;
import com.queuemate.social.repository.BlockRepository;
import com.queuemate.social.repository.FriendRequestRepository;
import com.queuemate.social.repository.FriendshipRepository;
import com.queuemate.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class BlockService {

    private final BlockRepository blocks;
    private final FriendshipRepository friendships;
    private final FriendRequestRepository friendRequests;
    private final UserRepository users;
    private final CachedBlockLookupAdapter blockLookup;

    public BlockService(BlockRepository blocks, FriendshipRepository friendships,
                        FriendRequestRepository friendRequests, UserRepository users,
                        CachedBlockLookupAdapter blockLookup) {
        this.blocks = blocks;
        this.friendships = friendships;
        this.friendRequests = friendRequests;
        this.users = users;
        this.blockLookup = blockLookup;
    }

    /** 차단하면 친구 관계와 대기 중 요청이 함께 정리된다 (docs/01 Block flow). */
    @Transactional
    public Block block(UUID blockerId, UUID targetId) {
        if (blockerId.equals(targetId)) {
            throw new ConflictException("SELF_BLOCK", "자기 자신을 차단할 수 없다");
        }
        if (!users.existsById(targetId)) {
            throw new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없다");
        }
        Block.BlockId id = Block.BlockId.of(blockerId, targetId);
        if (blocks.existsById(id)) {
            throw new ConflictException("ALREADY_BLOCKED", "이미 차단한 사용자다");
        }

        friendships.deleteById(Friendship.FriendshipId.of(blockerId, targetId));
        friendRequests.cancelPendingBetween(blockerId, targetId);
        try {
            Block saved = blocks.saveAndFlush(Block.of(blockerId, targetId));
            invalidateAfterCommit(blockerId, targetId);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("ALREADY_BLOCKED", "이미 차단한 사용자다");
        }
    }

    @Transactional
    public void unblock(UUID blockerId, UUID targetId) {
        Block.BlockId id = Block.BlockId.of(blockerId, targetId);
        if (!blocks.existsById(id)) {
            throw new NotFoundException("BLOCK_NOT_FOUND", "차단하지 않은 사용자다");
        }
        blocks.deleteById(id);
        invalidateAfterCommit(blockerId, targetId);
    }

    @Transactional(readOnly = true)
    public List<Block> list(UUID blockerId) {
        return blocks.findAllByIdBlockerIdOrderByCreatedAtDesc(blockerId);
    }

    /**
     * 커밋 전에 캐시를 지우면 아직 보이지 않는 상태를 다시 캐시할 수 있다.
     * 커밋 이후에 지워야 다음 조회가 확정된 결과를 읽는다.
     */
    private void invalidateAfterCommit(UUID one, UUID other) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            blockLookup.invalidate(one, other);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                blockLookup.invalidate(one, other);
            }
        });
    }
}
