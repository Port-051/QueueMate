package com.queuemate.party.api;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.security.CurrentUser;
import com.queuemate.party.api.PartyDtos.PartyView;
import com.queuemate.party.api.PartyDtos.ReadyRequest;
import com.queuemate.party.service.PartyDepartureService;
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
    private final PartyDepartureService departureService;
    private final PartyViewAssembler assembler;

    public PartyController(PartyService partyService, PartyDepartureService departureService,
                           PartyViewAssembler assembler) {
        this.partyService = partyService;
        this.departureService = departureService;
        this.assembler = assembler;
    }

    @GetMapping("/{id}")
    public ResponseEntity<PartyView> get(@PathVariable UUID id, CurrentUser currentUser) {
        return ResponseEntity.ok(assembler.toView(partyService.detail(id, currentUser.userId())));
    }

    /**
     * 명시적 이탈은 유예 없이 즉시 처리한다. 연결 끊김은 재접속을 기다리지만
     * 본인이 나가겠다고 한 경우는 기다릴 이유가 없다.
     */
    @PostMapping("/{id}/leave")
    public ResponseEntity<Void> leave(@PathVariable UUID id, CurrentUser currentUser) {
        // 멤버가 아니면 파티의 존재 자체를 알리지 않는다. 조회와 같은 규칙이다.
        partyService.detail(id, currentUser.userId());
        if (!departureService.leave(id, currentUser.userId())) {
            throw new ConflictException("ALREADY_LEFT", "이미 나갔거나 종료된 파티다");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ready")
    public ResponseEntity<PartyView> ready(@PathVariable UUID id, CurrentUser currentUser,
                                           @Valid @RequestBody ReadyRequest request) {
        return ResponseEntity.ok(assembler.toView(
                partyService.changeReady(id, currentUser.userId(), request.ready())));
    }
}
