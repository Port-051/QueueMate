package com.queuemate.matching.api;

import com.queuemate.common.api.MatchConditionRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/match-requests")
public class MatchController {

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody MatchConditionRequest request) {
        // TODO Member 2: application service + Redis one-active-request guard.
        return ResponseEntity.status(501).body(Map.of("code", "NOT_IMPLEMENTED"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable UUID id) {
        return ResponseEntity.status(501).body(Map.of("code", "NOT_IMPLEMENTED"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        return ResponseEntity.status(501).build();
    }
}
