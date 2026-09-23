-- 게임 프로필(게임 계정의 읽기 전용 칸 · 전적 스냅숏)과 소셜 로그인의 연결
-- (contracts/platform-api.md "게임 프로필" · "소셜 로그인").
--
-- 버전 번호는 스키마 폴더 사이에서 하나의 순서다 — V2 account, 이 파일이 V3, 다음이 V4 social.
-- 이미 적용된 파일(V1 · V2)은 고치지 않는다. 제약에 전부 이름을 붙인다 — 앱이 위반을 에러 코드로 옮길 때 그 이름으로 가른다.

-- external_id : 게임사 쪽 계정 식별자(Riot 의 puuid 등). 게임사 API 연동이 생기면 채운다. 지금은 늘 NULL 이다.
-- verified    : 게임사 인증(RSO 등)으로 본인 계정임을 확인했는가. 사용자의 요청으로는 바뀌지 않는다 — 켜는 법은 미정이다.
-- server      : PUBG 만 쓴다(스팀 · 카카오). 다른 게임은 NULL 만 들어온다 — 앱의 검증과 같은 것을 DB 도 건다.
ALTER TABLE account.game_accounts
    ADD COLUMN external_id varchar(100),
    ADD COLUMN verified    boolean NOT NULL DEFAULT false,
    ADD COLUMN server      varchar(10),
    ADD CONSTRAINT game_accounts_server_check
        CHECK (server IS NULL OR (game = 'PUBG' AND server IN ('STEAM', 'KAKAO')));

-- 게임사 API 에서 가져온 전적의 스냅숏. 게임 계정과 1:1 이다. 목록을 그릴 때 게임사 API 를 부르지 않고 이 테이블만 읽는다.
-- 가져오는 기능은 아직 없다 — 줄이 없으면 응답의 stats 가 null 이다. winRate · kda 는 저장하지 않고 앱이 계산해 내려 준다.
--
-- 세 게임이 보여 주는 것이 다르다(2026-09-22 소유자 결정) —
--   승/패   : LoL 180승 184패 · VALORANT 8승 5패. PUBG 는 없다 — 100명 중 순위 싸움이라 "승"이 치킨(1위)이다.
--   KDA    : LoL · VALORANT 는 킬/데스/어시스트. PUBG 는 K/D 만이다 — 어시스트를 보여 주지 않는다.
--   연승   : LoL · VALORANT 는 최근 연승. PUBG 는 개념이 약하다.
--   고유   : 모스트 챔피언 3 / 모스트 요원 3 · 주 무기 · 헤드샷률 / 평균 데미지 · 치킨률.
-- 그래서 세 게임 모두에 있는 판 수(games)만 NOT NULL 이고, wins · losses · win_streak 은 nullable 이다 —
-- PUBG 를 넣으려고 "치킨 수 = 승"으로 우기면 데이터가 거짓말을 한다. 게임마다 다른 나머지는 detail(jsonb)이다.
--
-- 검토하고 버린 대안 둘 —
--   ① 게임마다 테이블 하나: 모양은 정확하지만 프로필 한 번 읽는 데 테이블 셋을 봐야 한다. 목록이 핫 패스라 쿼리 한 번을 지킨다.
--   ② 전부 jsonb: DB 가 아무것도 검증하지 못하고 나중에 SQL 로 정렬을 못 한다.
-- 받은 것은 ③ 진짜 공통인 것만 컬럼 + 나머지는 jsonb 다.
--
-- wins 와 losses 는 같이 있거나 같이 없다(together_check) — 한쪽만 있으면 승률을 계산할 수 없다.
CREATE TABLE account.game_account_stats (
    game_account_id bigint       NOT NULL,
    games           integer      NOT NULL,
    avg_kills       numeric(4, 1),
    avg_deaths      numeric(4, 1),
    avg_assists     numeric(4, 1),
    wins            integer,
    losses          integer,
    win_streak      integer,
    detail          jsonb        NOT NULL DEFAULT '{}',
    source          varchar(6)   NOT NULL,
    synced_at       timestamptz  NOT NULL,
    CONSTRAINT game_account_stats_pkey PRIMARY KEY (game_account_id),
    CONSTRAINT game_account_stats_game_account_id_fkey FOREIGN KEY (game_account_id)
        REFERENCES account.game_accounts (id) ON DELETE CASCADE,
    CONSTRAINT game_account_stats_source_check CHECK (source IN ('SELF', 'API')),
    CONSTRAINT game_account_stats_counts_check
        CHECK (games >= 0
           AND (wins IS NULL OR wins >= 0)
           AND (losses IS NULL OR losses >= 0)
           AND (win_streak IS NULL OR win_streak >= 0)),
    CONSTRAINT game_account_stats_wins_losses_together_check CHECK ((wins IS NULL) = (losses IS NULL))
);

-- 소셜 계정과 사용자의 연결. 제공자 쪽 회원 번호 하나는 사용자 하나에만 붙는다(PK) — 같은 소셜 계정으로 두 번 가입하는 것을 DB 가 막는다.
-- 한 사용자는 제공자마다 하나만 잇는다(UNIQUE). 제공자의 access token 은 저장하지 않는다 — 회원 번호를 얻고 나면 쓸 일이 없다.
-- user_id 는 사용자 번호(account.users.id)다 — 제공자의 회원 번호는 provider_user_id 에만 있고 사용자 번호가 되지 않는다.
CREATE TABLE account.social_identities (
    provider         varchar(10) NOT NULL,
    provider_user_id varchar(64) NOT NULL,
    user_id          bigint      NOT NULL,
    created_at       timestamptz NOT NULL,
    CONSTRAINT social_identities_pkey PRIMARY KEY (provider, provider_user_id),
    CONSTRAINT social_identities_user_id_provider_key UNIQUE (user_id, provider),
    CONSTRAINT social_identities_user_id_fkey FOREIGN KEY (user_id) REFERENCES account.users (id) ON DELETE CASCADE,
    CONSTRAINT social_identities_provider_check CHECK (provider IN ('KAKAO', 'DISCORD'))
);
