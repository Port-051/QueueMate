package com.queuemate.auth.oauth;

import com.queuemate.auth.domain.OAuthProvider;
import com.queuemate.common.error.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 제공자 인가 코드를 QueueMate 사용자 id로 바꾼다. 토큰 발급은 호출자가 한다. */
@Service
public class OAuthLoginService {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginService.class);

    private final OAuthClients clients;
    private final OAuthAccountLinker linker;

    public OAuthLoginService(OAuthClients clients, OAuthAccountLinker linker) {
        this.clients = clients;
        this.linker = linker;
    }

    public UUID authenticate(OAuthProvider provider, String code, String state) {
        // 외부 호출은 트랜잭션 밖에서 한다. 제공자가 느릴 때 DB 커넥션을 붙들지 않는다.
        OAuthUserInfo info = clients.require(provider).fetchUser(code, state);

        return linker.findLinkedUser(provider, info.providerUserId())
                .map(userId -> {
                    linker.requireActiveUser(userId);
                    return userId;
                })
                .orElseGet(() -> createOrRecoverFromRace(provider, info));
    }

    private UUID createOrRecoverFromRace(OAuthProvider provider, OAuthUserInfo info) {
        try {
            return linker.linkOrCreate(provider, info);
        } catch (DataIntegrityViolationException e) {
            // 같은 사람이 두 창에서 동시에 눌렀다. unique 제약이 하나만 통과시켰으므로
            // 진 쪽은 이긴 쪽이 만든 신원을 읽어 같은 계정으로 들어간다.
            log.info("소셜 신원 생성 경합 provider={}", provider);
            return linker.findLinkedUser(provider, info.providerUserId())
                    .orElseThrow(() -> new ConflictException("OAUTH_LINK_FAILED", "계정을 연결하지 못했다"));
        }
    }
}
