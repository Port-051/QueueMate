package com.queuemate.party.api;

import com.queuemate.party.api.PartyDtos.MemberView;
import com.queuemate.party.api.PartyDtos.PartyView;
import com.queuemate.party.domain.PartyMember;
import com.queuemate.party.service.PartyService.PartyDetail;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import com.queuemate.user.repository.GameAccountRepository;
import com.queuemate.user.domain.GameAccount;
import com.queuemate.common.domain.GameKey;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 닉네임을 한 번에 읽어 멤버 수만큼 조회가 늘어나지 않게 한다. */
@Component
public class PartyViewAssembler {

    private final UserRepository users;
    private final GameAccountRepository gameAccounts;

    public PartyViewAssembler(UserRepository users, GameAccountRepository gameAccounts) {
        this.users = users;
        this.gameAccounts = gameAccounts;
    }

    public PartyView toView(PartyDetail detail) {
        Map<UUID, List<String>> gameIds = gameAccounts.findAllByUserIdInAndProviderGame(
                        detail.members().stream().map(PartyMember::getUserId).toList(),
                        GameKey.valueOf(detail.party().getGameKey()))
                .stream().collect(Collectors.groupingBy(GameAccount::getUserId,
                        Collectors.mapping(GameAccount::getExternalGameId, Collectors.toList())));
        Map<UUID, User> profiles = users
                .findAllById(detail.members().stream().map(PartyMember::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new PartyView(
                detail.party().getId(),
                detail.party().getGameKey(),
                detail.party().getModeKey(),
                detail.party().getTargetSize(),
                detail.party().getStatus().name(),
                detail.members().stream()
                        .map(member -> {
                            User profile = profiles.get(member.getUserId());
                            return new MemberView(
                                    member.getUserId(),
                                    profile == null ? null : profile.getNickname(),
                                    member.isReady(),
                                    gameIds.getOrDefault(member.getUserId(), List.of()).stream().sorted().toList());
                        })
                        .toList());
    }
}
