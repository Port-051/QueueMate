package com.queuemate.matching.app;

import com.queuemate.matching.app.ProposalService.ProposalView;
import com.queuemate.user.domain.User;
import com.queuemate.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 제안 응답에 닉네임을 붙인다 (contracts/openapi ProposalMember).
 *
 * <p>matching은 사용자 표시 이름을 들고 있지 않다. 제안이 다루는 것은 userId뿐이다.
 * 그래서 화면에 나갈 때만 한 번 읽어 붙인다. 멤버 수만큼 조회가 늘어나지 않도록
 * 한 번에 가져온다. party 쪽 {@code PartyViewAssembler}와 같은 방식이다.
 *
 * <p>이 계층이 없던 동안 REST와 WebSocket 모두 nickname 없는 응답을 내보냈고,
 * 화면은 그 값을 그대로 써서 제안 화면이 렌더링 도중 죽었다. 계약에 있는 필드가
 * 빠져도 서버는 아무 오류를 내지 않는다는 것이 이 버그의 성질이다.
 */
@Component
public class ProposalViewAssembler {

    private final UserRepository users;

    public ProposalViewAssembler(UserRepository users) {
        this.users = users;
    }

    public ProposalView withNicknames(ProposalView view) {
        Map<UUID, User> profiles = users
                .findAllById(view.members().stream().map(ProposalView.Member::userId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return new ProposalView(
                view.id(), view.status(), view.expiresAt(),
                view.members().stream()
                        .map(member -> {
                            User profile = profiles.get(member.userId());
                            return new ProposalView.Member(
                                    member.userId(),
                                    profile == null ? null : profile.getNickname(),
                                    member.acceptance());
                        })
                        .toList(),
                view.partyId());
    }
}
