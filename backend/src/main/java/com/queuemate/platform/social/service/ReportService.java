package com.queuemate.platform.social.service;

import com.queuemate.platform.common.error.ApiException;
import com.queuemate.platform.common.error.ConstraintViolations;
import com.queuemate.platform.common.web.Ids;
import com.queuemate.platform.social.domain.Report;
import com.queuemate.platform.social.domain.ReportReason;
import com.queuemate.platform.social.dto.ReportRequest;
import com.queuemate.platform.social.dto.ReportResponse;
import com.queuemate.platform.social.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

/**
 * 신고 — <b>접수만 받는다</b>({@code contracts/platform-api.md} "친구 · 신고 · 최근 함께한 사람"). 처리 화면 · 제재는 없다.
 * 같은 사람을 여러 번 신고할 수 있다. 신고당한 사람에게 알리지 않는다.
 *
 * <p><b>차단 관계를 보지 않는다</b> — 나를 차단한 사람도, 내가 차단한 사람도 신고할 수 있어야 한다(친구 요청과 다르다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    /** 마이그레이션(V1__schema.sql)이 붙인 FK 의 이름이다 — 신고한 사람(나)의 칸 */
    static final String REPORTER_FKEY = "reports_reporter_id_fkey";

    private final ReportRepository reportRepository;

    /**
     * 대상이 있는 사용자인지는 DB 가 본다 — {@code reports_target_user_id_fkey} 의 위반이 404 {@code USER_NOT_FOUND} 다(조회하지 않는다).
     * 내 쪽({@code reports_reporter_id_fkey})이 깨지면 토큰은 멀쩡한데 내가 DB 에 없는 것이라 401 이다.
     * {@code contextId} 는 모양(숫자)만 본다 — 그 글이 있는지는 확인하지 않는다(계약). {@code targetUserId} 가 숫자가 아니면 있을 수 없는 사용자라
     * 없는 사용자와 같은 404 다.
     */
    @Transactional
    public ReportResponse report(Long me, ReportRequest request)
    {
        ReportReason reason = parseReason(request.reason());
        Long contextId = parseContextId(request.contextId());
        String detail = (request.detail() == null || request.detail().isBlank()) ? null : request.detail();
        if(reason == ReportReason.OTHER && detail == null)
        {
            throw ApiException.validationFailed("detail", "사유가 OTHER 면 필요합니다");
        }
        Long targetUserId = Ids.parse(request.targetUserId()).orElseThrow(ReportService::userNotFound);
        if(me.equals(targetUserId))
        {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_SELF", "자기 자신은 신고할 수 없습니다");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Report report;
        try
        {
            report = reportRepository.saveAndFlush(new Report(me, targetUserId, reason, detail, contextId, now));
        }
        catch(DataIntegrityViolationException e)
        {
            String constraint = ConstraintViolations.nameOf(e);
            if(REPORTER_FKEY.equals(constraint))
            {
                throw ApiException.unauthenticated();
            }
            if(ConstraintViolations.isMissingUser(constraint))
            {
                throw userNotFound();
            }
            throw e;
        }
        // detail 은 로그에 남기지 않는다 — 사람이 쓴 글이라 무엇이 들어 있을지 모른다
        log.info("신고 접수 reportId={} reporterId={} targetUserId={} reason={}", report.getId(), me, targetUserId, reason);
        return new ReportResponse(report.getId(), now);
    }

    private static ApiException userNotFound()
    {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "없는 사용자입니다");
    }

    private static ReportReason parseReason(String value)
    {
        return Arrays.stream(ReportReason.values())
                .filter(reason -> reason.name().equals(value))
                .findFirst()
                .orElseThrow(() -> ApiException.validationFailed("reason",
                        "ABUSE · CHEATING · SPAM · NO_SHOW · OTHER 가운데 하나여야 합니다"));
    }

    private static Long parseContextId(String value)
    {
        if(value == null || value.isBlank())
        {
            return null;
        }
        return Ids.parse(value).orElseThrow(() -> ApiException.validationFailed("contextId", "글의 id(숫자)여야 합니다"));
    }
}
