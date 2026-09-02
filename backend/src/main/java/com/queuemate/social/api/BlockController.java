package com.queuemate.social.api;

import com.queuemate.common.security.CurrentUser;
import com.queuemate.social.api.SocialDtos.BlockView;
import com.queuemate.social.api.SocialDtos.CreateBlockRequest;
import com.queuemate.social.service.BlockService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/blocks")
public class BlockController {

    private final BlockService blockService;
    private final SocialViewAssembler assembler;

    public BlockController(BlockService blockService, SocialViewAssembler assembler) {
        this.blockService = blockService;
        this.assembler = assembler;
    }

    @GetMapping
    public List<BlockView> blocks(CurrentUser currentUser) {
        return assembler.toBlockViews(blockService.list(currentUser.userId()));
    }

    @PostMapping
    public ResponseEntity<BlockView> block(
            CurrentUser currentUser, @Valid @RequestBody CreateBlockRequest request) {
        BlockView view = assembler.toBlockView(
                blockService.block(currentUser.userId(), request.targetUserId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> unblock(CurrentUser currentUser, @PathVariable UUID userId) {
        blockService.unblock(currentUser.userId(), userId);
        return ResponseEntity.noContent().build();
    }
}
