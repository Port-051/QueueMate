package com.queuemate.party.api;

import com.queuemate.party.api.PartyDtos.MemberView;
import com.queuemate.party.api.PartyDtos.PartyView;
import com.queuemate.party.domain.PartyMember;
import com.queuemate.party.service.PartyService.PartyDetail;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 닉네임을 한 번에 읽어 멤버 수만큼 조회가 늘어나지 않게 한다. */
@Component
public class PartyViewAssembler {

    private final UserRepository users;

    public PartyViewAssembler(UserRepository users) {
        this.users = users;
    }

    public PartyView toView(PartyDetail detail) {
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
                                    member.isReady());
                        })
                        .toList());
    }
}
