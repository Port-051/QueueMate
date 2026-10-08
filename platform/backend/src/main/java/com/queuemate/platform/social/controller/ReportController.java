package com.queuemate.platform.social.controller;

import com.queuemate.platform.common.security.CurrentUserId;
import com.queuemate.platform.social.dto.ReportRequest;
import com.queuemate.platform.social.dto.ReportResponse;
import com.queuemate.platform.social.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 신고 — <b>접수만 있다.</b> 읽는 요청이 없다(처리 화면이 없다). 원본은 {@code contracts/platform-api.md} 다 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ResponseEntity<ReportResponse> report(@CurrentUserId Long userId, @Valid @RequestBody ReportRequest request)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.report(userId, request));
    }
}
