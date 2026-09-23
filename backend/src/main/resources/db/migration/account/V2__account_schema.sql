-- account 스키마 — 계정 · 비밀번호 해시 · 게임 계정 (CLAUDE.md §3.5 · contracts/platform-api.md "계정").
--
-- 버전 번호는 스키마 폴더 사이에서 하나의 순서다 — V1 은 baseline, V2 가 이 파일, 다음 단계(social)가 V3 이다.
-- 이미 적용된 파일은 고치지 않는다(체크섬이 달라져 기동이 막힌다). 바꿀 것은 새 버전으로 쓴다.
--
-- 제약에 전부 이름을 붙였다 — 앱이 제약 위반을 에러 코드로 옮길 때 그 이름으로 가른다
-- (users_login_id_key → LOGIN_ID_TAKEN, users_nickname_key → NICKNAME_TAKEN). 이름을 바꾸면 앱의 상수도 같이 바꾼다.
--
-- 모든 테이블의 PK 는 bigint GENERATED ALWAYS AS IDENTITY 다 (2026-09-22 소유자 결정). 사용자의 식별자는 둘로 갈린다 —
--   · id       : 사용자 번호. 시스템 안팎에서 쓰는 userId 가 이것이다 — JWT 의 sub(숫자를 문자열로), 알림 채널 qm:pubsub:push:{userId},
--                입장권의 sub · host_id, URL 의 {userId}, 요청 · 응답 본문의 userId, 다른 스키마의 *_id 컬럼 전부
--   · login_id : 가입할 때 정한 로그인 아이디. 로그인할 때만 쓴다. 바꾸는 API 는 없다

CREATE SCHEMA account;

-- login_id 의 형식은 앱의 검증과 같은 CHECK 를 DB 에도 건다 — 앱을 거치지 않은 INSERT 도 막는다.
CREATE TABLE account.users (
    id         bigint      GENERATED ALWAYS AS IDENTITY,
    login_id   varchar(20) NOT NULL,
    nickname   varchar(16) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_login_id_key UNIQUE (login_id),
    CONSTRAINT users_nickname_key UNIQUE (nickname),
    CONSTRAINT users_login_id_format CHECK (login_id ~ '^[a-z0-9_]{4,20}$')
);

-- 비밀번호 해시는 users 와 떼어 둔다 — 프로필을 읽는 조회가 해시를 같이 끌고 다니지 않게 한다.
-- {bcrypt} 접두사까지 68자다. 해시 방식을 바꿀 여유로 100 을 잡았다.
CREATE TABLE account.credentials (
    user_id       bigint       NOT NULL,
    password_hash varchar(100) NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT credentials_pkey PRIMARY KEY (user_id),
    CONSTRAINT credentials_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users (id) ON DELETE CASCADE
);

-- 게임마다 계정 하나다. tier 는 자기신고 문자열이고 main_position 은 방 안 사람 카드와 "찾는 포지션" 강조의 출처다 (CLAUDE.md §7.1).
-- 포지션 이름의 검증은 앱이 한다(게임마다 목록이 다르다). PUBG 는 포지션이 없어 NULL 만 들어온다.
CREATE TABLE account.game_accounts (
    id            bigint GENERATED ALWAYS AS IDENTITY,
    user_id       bigint      NOT NULL,
    game          varchar(10) NOT NULL,
    game_nickname varchar(40) NOT NULL,
    tier          varchar(20),
    main_position varchar(20),
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    CONSTRAINT game_accounts_pkey PRIMARY KEY (id),
    CONSTRAINT game_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users (id) ON DELETE CASCADE,
    CONSTRAINT game_accounts_user_id_game_key UNIQUE (user_id, game),
    CONSTRAINT game_accounts_game_check CHECK (game IN ('LOL', 'VALORANT', 'PUBG'))
);
