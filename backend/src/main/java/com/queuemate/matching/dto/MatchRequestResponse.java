package com.queuemate.matching.dto;

import com.queuemate.matching.domain.MatchRequestStatus;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class MatchRequestResponse {
    private final String requestId;
    private final MatchRequestStatus status;
}
