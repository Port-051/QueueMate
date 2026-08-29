package com.queuemate.social.service;

import com.queuemate.common.error.ConflictException;
import com.queuemate.common.error.NotFoundException;
import com.queuemate.social.domain.Report;
import com.queuemate.social.domain.ReportReason;
import com.queuemate.social.repository.ReportRepository;
import com.queuemate.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository reports;
    private final UserRepository users;

    public ReportService(ReportRepository reports, UserRepository users) {
        this.reports = reports;
        this.users = users;
    }

    @Transactional
    public Report report(UUID reporterId, UUID targetUserId, UUID partyId,
                         ReportReason reason, String description) {
        if (reporterId.equals(targetUserId)) {
            throw new ConflictException("SELF_REPORT", "자기 자신을 신고할 수 없다");
        }
        if (!users.existsById(targetUserId)) {
            throw new NotFoundException("USER_NOT_FOUND", "사용자를 찾을 수 없다");
        }
        Report saved = reports.save(
                Report.create(reporterId, targetUserId, partyId, reason, description));
        // 신고 본문은 남기지 않는다. 식별자와 category만 로그에 남긴다 (docs/09).
        log.info("report 접수 reportId={} reason={} partyId={}", saved.getId(), reason, partyId);
        return saved;
    }
}
