package com.queuemate.party.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/parties")
public class PartyController {
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        // TODO Member 3
        return ResponseEntity.status(501).body(Map.of("code", "NOT_IMPLEMENTED"));
    }
}
