package com.queuemate.matching.rule.pubg;

import com.queuemate.matching.dto.CreateMatchRequestCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class PubgTieredAssigner {

    public void assign(CreateMatchRequestCommand command, PubgModeConfig config, Set<String> blockedUserIds) {

    }
}
