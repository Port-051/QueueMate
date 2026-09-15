package com.queuemate.social.api;

import com.queuemate.common.security.CurrentUser;
import com.queuemate.social.api.SocialDtos.CreateReportRequest;
import com.queuemate.social.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    public ResponseEntity<Void> report(
            CurrentUser currentUser, @Valid @RequestBody CreateReportRequest request) {
        reportService.report(currentUser.userId(), request.targetUserId(), request.partyId(),
                request.reason(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
