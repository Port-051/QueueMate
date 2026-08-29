package com.queuemate.reservation.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> request) {
        // TODO Member 2: replace Map with contract DTO after C0 freeze.
        return ResponseEntity.status(501).body(Map.of("code", "NOT_IMPLEMENTED"));
    }
}
