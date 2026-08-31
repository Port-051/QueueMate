package com.queuemate.social.repository;

import com.queuemate.social.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {
}
