package com.queuemate.platform.social.controller;

import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.social.dto.BlockListResponse;
import com.queuemate.platform.social.dto.BlockRequest;
import com.queuemate.platform.social.dto.BlockResponse;
import com.queuemate.platform.social.service.BlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 차단. <b>"나"는 access 토큰에서 온다</b>({@link CurrentUserId}) — 남의 이름으로 차단하거나 남의 차단 목록을 볼 길이 없다.
 * 원본은 {@code contracts/platform-api.md} "차단" 이다.
 */
@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping
    public ResponseEntity<BlockResponse> block(@CurrentUserId Long userId, @Valid @RequestBody BlockRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(blockService.block(userId, request.userId()));
    }

    /** 차단한 적이 없어도 · 없는 사용자여도 204 다 — 해제는 멱등이다. 경로의 {@code userId} 가 숫자가 아니면 400 이다 */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unblock(@CurrentUserId Long userId, @PathVariable("userId") Long targetUserId)
    {
        blockService.unblock(userId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public BlockListResponse list(@CurrentUserId Long userId)
    {
        return blockService.list(userId);
    }
}
