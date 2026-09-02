package com.queuemate.matching.api;

import com.queuemate.common.security.CurrentUser;
import com.queuemate.matching.app.ProposalService;
import com.queuemate.matching.app.ProposalViewAssembler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 매칭 제안 응답 API (contracts/openapi.yaml /proposals). */
@RestController
@RequestMapping("/api/v1/proposals")
public class ProposalController {

    private final ProposalService service;
    private final ProposalViewAssembler assembler;

    public ProposalController(ProposalService service, ProposalViewAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping("/{id}")
    public ProposalService.ProposalView get(CurrentUser currentUser, @PathVariable UUID id) {
        return assembler.withNicknames(service.get(currentUser.userId(), id));
    }

    @PostMapping("/{id}/accept")
    public ProposalService.ProposalView accept(CurrentUser currentUser, @PathVariable UUID id) {
        return assembler.withNicknames(service.accept(currentUser.userId(), id));
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<Void> decline(CurrentUser currentUser, @PathVariable UUID id) {
        service.decline(currentUser.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
