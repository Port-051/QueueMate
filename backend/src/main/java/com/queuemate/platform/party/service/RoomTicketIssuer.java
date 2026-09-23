package com.queuemate.platform.party.service;

import com.queuemate.platform.common.security.JwtProperties;
import com.queuemate.platform.common.security.TokenClaims;
import com.queuemate.platform.party.dto.TicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 입장권을 찍는다. 클레임은 {@code contracts/platform-api.md} "입장권" 표 그대로다.
 *
 * <p><b>access 토큰과 같은 키로 서명한다</b> — 그래서 {@code token_use} 로 가른다. 입장권은 {@code room_ticket} 이고 access 토큰의 검증기
 * ({@code JwtConfig#jwtDecoder})는 {@code access} 만 받는다 — 입장권을 쿠키에 넣어 와도 로그인이 되지 않는다.
 *
 * <p><b>"내줘도 되는가"는 여기서 보지 않는다</b> — 글이 모집 중인지, 방 안의 누구와도 차단 관계가 아닌지는 {@code PostService#ticket} 이 본 뒤에 부른다.
 * {@code room} 은 서명만 검증하고 이 앱에 묻지 않는다(CLAUDE.md §3.3).
 */
@Component
@RequiredArgsConstructor
public class RoomTicketIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final BoardProperties boardProperties;

    /**
     * 세 클레임({@code sub} · {@code room_id} · {@code host_id})은 전부 <b>숫자를 십진 문자열로</b> 찍는다 — {@code room} 은 {@code roomId} · {@code userId} 를
     * 문자열로 다룬다. 응답 본문의 {@code roomId} · {@code hostId} 는 숫자다.
     *
     * @param userId 입장하려는 사람의 사용자 번호({@code sub}). 방장도 같은 길로 받는다({@code sub} = {@code host_id})
     * @param roomId 글의 id
     * @param hostId 글을 쓴 사람의 사용자 번호
     */
    public TicketResponse issue(Long userId, Long roomId, Long hostId)
    {
        // JWT 의 시각은 초 단위다 — 응답의 expiresAt 이 토큰의 exp 와 같은 값이 되게 미리 자른다
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(boardProperties.roomTicketTtl());
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(jwtProperties.keyId())
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(TokenClaims.ISSUER)
                .subject(Long.toString(userId))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim(TokenClaims.TOKEN_USE, TokenClaims.TOKEN_USE_ROOM_TICKET)
                .claim(TokenClaims.ROOM_ID, Long.toString(roomId))
                .claim(TokenClaims.HOST_ID, Long.toString(hostId))
                .build();
        String ticket = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TicketResponse(ticket, roomId, hostId, expiresAt);
    }
}
