package com.queuemate.platform.account.service;

import com.queuemate.platform.account.dto.AuthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * TEMP-DEV-LOGIN — 개발용 로그인의 DB 쪽: <b>"그 닉네임의 사용자가 있으면 그 사람, 없으면 만든다."</b> 걷어낼 때 이 파일째 지운다
 * ({@code contracts/platform-api.md} "개발용 로그인" · P-34 — 2026-09-29 소유자 결정). 쿠키를 싣는 것은 컨트롤러다({@code DevLoginController}).
 *
 * <p><b>설정 {@value #ENABLED_PROPERTY} 가 켜져 있을 때만 빈이 생긴다</b>(기본 꺼짐 — 컨트롤러와 같은 조건이다).
 *
 * <p><b>한 문장이다</b> — {@code INSERT … ON CONFLICT (닉네임) DO NOTHING RETURNING} 과 같은 닉네임의 {@code SELECT} 를 한 번에 돈다.
 * {@code 조회 → 판단 → 삽입} 이 아니다(CLAUDE.md §5 — 닉네임의 UNIQUE 가 중복을 막는다). <b>소셜 연결({@code social_identities})은 만들지 않는다</b> —
 * 그래서 {@code users/me} 의 {@code socialProviders} 가 {@code []} 다.
 *
 * <p><b>한 번 더 도는 이유</b> — 같은 새 닉네임을 다른 요청이 <b>동시에</b> 넣고 있으면 {@code ON CONFLICT} 는 그 커밋을 기다렸다가 아무것도 넣지 않는데,
 * 같은 문장의 {@code SELECT} 는 문장이 시작할 때의 스냅숏이라 방금 커밋된 그 줄을 보지 못한다(READ COMMITTED — 빈 결과). 다음 문장은 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBooleanProperty(DevLoginService.ENABLED_PROPERTY)
public class DevLoginService {

    /** 환경변수 {@code DEV_LOGIN_ENABLED}(기본 {@code false}) — {@code application.yaml} */
    public static final String ENABLED_PROPERTY = "platform.auth.dev-login-enabled";

    private static final int ATTEMPTS = 2;

    /** 넣었으면 넣은 줄이, 이미 있었으면 있던 줄이 온다 — 한 줄이다(닉네임의 UNIQUE). 제약 이름은 V1 의 것이다 */
    private static final String FIND_OR_CREATE = """
            with inserted as (
                insert into users (nickname, created_at, updated_at) values (?, ?, ?)
                on conflict on constraint users_nickname_key do nothing
                returning id, nickname, true as created
            )
            select id, nickname, created from inserted
            union all
            select id, nickname, false from users where nickname = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    private record Row(long userId, String nickname, boolean created) {
    }

    /** 트랜잭션이 없다 — 문장 하나가 제 트랜잭션(autocommit)이다. 두 번 다 비면 500 이다(그 사이에 사용자가 지워지는 드문 경우) */
    public AuthResponse login(String nickname)
    {
        // 소셜 가입(User 엔티티)과 같이 밀리초로 자른다 — 응답의 createdAt 모양이 같게
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now().truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
        for(int attempt = 1; attempt <= ATTEMPTS; attempt++)
        {
            List<Row> rows = jdbcTemplate.query(FIND_OR_CREATE,
                    (rs, rowNum) -> new Row(rs.getLong("id"), rs.getString("nickname"), rs.getBoolean("created")),
                    nickname, now, now, nickname);
            if(!rows.isEmpty())
            {
                Row row = rows.get(0);
                // 요청마다 한 줄 — 운영 로그에 이것이 보이면 설정이 잘못 들어간 것이다
                log.warn("개발용 로그인 — 운영에서는 DEV_LOGIN_ENABLED 를 두지 마라 userId={} created={}", row.userId(), row.created());
                return new AuthResponse(row.userId(), row.nickname());
            }
        }
        throw new IllegalStateException("개발용 로그인 — 그 닉네임의 사용자를 찾지도 만들지도 못했다");
    }
}
