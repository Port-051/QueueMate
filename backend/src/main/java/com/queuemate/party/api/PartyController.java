package com.queuemate.party.api;

import com.queuemate.common.security.CurrentUser;
import com.queuemate.party.api.PartyDtos.PartyView;
import com.queuemate.party.api.PartyDtos.ReadyRequest;
import com.queuemate.party.service.PartyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * party 생성 엔드포인트는 없다. 파티는 proposal 확정에서만 만들어진다
 * (PartyCreationPort). 계약에도 POST /parties가 없다.
 */
@RestController
@RequestMapping("/api/v1/parties")
public class PartyController {

    private final PartyService partyService;
    private final PartyViewAssembler assembler;

    public PartyController(PartyService partyService, PartyViewAssembler assembler) {
        this.partyService = partyService;
        this.assembler = assembler;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartyView> get(@PathVariable UUID id, CurrentUser currentUser) {
        return ResponseEntity.ok(assembler.toView(partyService.detail(id, currentUser.userId())));
    }

    @PostMapping("/{id}/ready")
    public ResponseEntity<PartyView> ready(@PathVariable UUID id, CurrentUser currentUser,
                                           @Valid @RequestBody ReadyRequest request) {
        return ResponseEntity.ok(assembler.toView(
                partyService.changeReady(id, currentUser.userId(), request.ready())));
    }
}
