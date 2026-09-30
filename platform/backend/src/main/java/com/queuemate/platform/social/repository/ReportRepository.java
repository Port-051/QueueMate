package com.queuemate.platform.social.repository;

import com.queuemate.platform.social.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

/** 신고 — 접수({@code save})만 한다. 읽는 화면이 없다 */
public interface ReportRepository extends JpaRepository<Report, Long> {
}
