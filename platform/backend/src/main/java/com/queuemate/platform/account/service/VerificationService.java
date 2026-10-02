package com.queuemate.platform.account.service;

import com.queuemate.platform.account.repository.GameAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class VerificationService {
    private final GameAccountRepository accounts;
    public record Status(Long userId, boolean verified) {}

    @Transactional(readOnly = true)
    public List<Status> statuses(List<Long> userIds) {
        var verified = new HashSet<>(accounts.findVerifiedUserIds(userIds));
        return userIds.stream().distinct().map(id -> new Status(id, verified.contains(id))).toList();
    }
}
