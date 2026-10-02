package com.queuemate.platform.account.controller;

import com.queuemate.platform.account.service.VerificationService;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public badge status for authenticated social screens, without game nicknames or account details. */
@RestController
@RequiredArgsConstructor
public class VerificationController {
    private final VerificationService service;

    @GetMapping("/api/v1/users/verification")
    public List<VerificationService.Status> statuses(@RequestParam @Size(min = 1, max = 100) List<@Positive Long> userIds) {
        return service.statuses(userIds);
    }
}
