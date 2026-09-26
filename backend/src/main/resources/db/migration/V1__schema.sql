-- platform 의 DB 스키마 전부 — 2026-09-26 소유자 결정: 스키마 하나(public) · 테이블 사이의 JOIN · FK 허용.
-- 옛 V1~V8(account/ · social/ · party/ 로 나뉘어 있던 것)을 합친 것이다. 최종 모양은 옛 V8 까지 적용한 결과와 같고,
-- 다른 점은 둘이다 — ① 스키마가 public 하나다 ② 사용자 번호를 담는 칸에 전부 users(id) 로 가는 FK 를 건다(ON DELETE CASCADE).
--
-- 왜 — DB 를 보는 앱이 사실상 이 앱 하나다. 스키마를 나누고 JOIN · FK 를 막은 탓에 닉네임을 따로 읽어 자바에서 정렬하고,
-- 사용자가 있는지를 앱이 조회로 확인했다. 이제 그것을 JOIN 과 FK 가 한다. 패키지 나누기(account · social · party · room)는 그대로다.
--
-- 운영 DB 가 아직 없고 로컬 · 테스트 DB 는 매번 빈 채로 뜨므로 옛 파일을 고치지 않고 통째로 갈아 끼웠다.
-- 이 파일부터는 다시 "이미 적용된 파일은 고치지 않는다"(체크섬이 달라져 기동이 막힌다) — 바꿀 것은 새 버전(V2 …)으로 쓴다.
--
-- 제약 · 인덱스에 전부 이름을 붙였다 — 앱이 위반을 그 이름으로 가려 에러 코드로 옮긴다
-- (users_login_id_key → LOGIN_ID_TAKEN · users_nickname_key → NICKNAME_TAKEN · blocks_blocker_blocked_key → ALREADY_BLOCKED ·
--  friend_requests_one_pending → FRIEND_REQUEST_ALREADY_SENT · recruit_posts_one_recruiting_per_host → ALREADY_RECRUITING ·
--  사용자 번호로 가는 FK → USER_NOT_FOUND 등). 이름을 바꾸면 앱의 상수도 같이 바꾼다.
--
-- 모든 테이블의 PK 는 bigint GENERATED ALWAYS AS IDENTITY 다(2026-09-22 소유자 결정). 사용자의 식별자는 둘로 갈린다 —
--   · users.id       : 사용자 번호. 시스템 안팎에서 쓰는 userId 가 이것이다 — JWT 의 sub(숫자를 문자열로), 알림 채널 qm:pubsub:push:{userId},
--                      방 키 · 멤버 SET, URL 의 {userId}, 요청 · 응답 본문의 userId, 다른 테이블의 *_id 컬럼 전부
--   · users.login_id : 가입할 때 정한 로그인 아이디. 로그인할 때만 쓴다. 바꾸는 API 는 없다
--
-- 롤 · GRANT 는 없다 — 스키마별 DB 롤은 두지 않는다(2026-09-22 소유자 결정). matching 은 별도 롤 없이 blocks 를 읽는다.
-- reservation 의 테이블은 여기 넣지 않는다(app:reservation 의 것이다).


-- ============================================================================================
-- 계정 (account 패키지)
-- ============================================================================================

-- login_id 의 형식은 앱의 검증과 같은 CHECK 를 DB 에도 건다 — 앱을 거치지 않은 INSERT 도 막는다.
CREATE TABLE users (
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
CREATE TABLE credentials (
    user_id       bigint       NOT NULL,
    password_hash varchar(100) NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT credentials_pkey PRIMARY KEY (user_id),
    CONSTRAINT credentials_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 게임마다 계정 하나다. tier 는 자기신고 문자열이고(값은 gameconfig 의 티어 사다리로 앱이 검증한다) main_position 은 방 안 사람 카드의 출처다.
-- 포지션 이름의 검증은 앱이 한다(게임마다 목록이 다르다). PUBG 는 포지션이 없어 NULL 만 들어온다.
-- external_id : 게임사 쪽 계정 식별자(Riot 의 puuid 등). 전적을 긁을 때 알게 된다.
-- verified    : 게임사 인증(RSO 등)으로 본인 계정임을 확인했는가. 사용자의 요청으로는 바뀌지 않는다 — 켜는 길은 아직 없다.
-- server      : PUBG 만 쓴다(스팀 · 카카오). 다른 게임은 NULL 만 들어온다 — 앱의 검증과 같은 것을 DB 도 건다.
CREATE TABLE game_accounts (
    id            bigint GENERATED ALWAYS AS IDENTITY,
    user_id       bigint       NOT NULL,
    game          varchar(10)  NOT NULL,
    game_nickname varchar(40)  NOT NULL,
    tier          varchar(20),
    main_position varchar(20),
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    external_id   varchar(100),
    verified      boolean      NOT NULL DEFAULT false,
    server        varchar(10),
    CONSTRAINT game_accounts_pkey PRIMARY KEY (id),
    CONSTRAINT game_accounts_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT game_accounts_user_id_game_key UNIQUE (user_id, game),
    CONSTRAINT game_accounts_game_check CHECK (game IN ('LOL', 'VALORANT', 'PUBG')),
    CONSTRAINT game_accounts_server_check
        CHECK (server IS NULL OR (game = 'PUBG' AND server IN ('STEAM', 'KAKAO')))
);

-- 게임사 API 에서 가져온 전적의 스냅숏. 게임 계정과 1:1 이다. 목록을 그릴 때 게임사 API 를 부르지 않고 이 테이블만 읽는다.
-- 줄이 없으면 응답의 stats 가 null 이다. winRate · kda 는 저장하지 않고 앱이 계산해 내려 준다.
--
-- 세 게임이 보여 주는 것이 다르다(2026-09-22 소유자 결정) —
--   승/패   : LoL · VALORANT 는 있다. PUBG 는 없다 — 100명 중 순위 싸움이라 "승"이 치킨(1위)이다.
--   KDA    : LoL · VALORANT 는 킬/데스/어시스트. PUBG 는 K/D 만이다 — 어시스트를 보여 주지 않는다.
--   연승   : LoL · VALORANT 는 최근 연승. PUBG 는 개념이 약하다.
--   고유   : 모스트 챔피언 3 / 모스트 요원 3 · 주 무기 · 헤드샷률 / 평균 데미지 · 치킨률.
-- 그래서 세 게임 모두에 있는 판 수(games)만 NOT NULL 이고, wins · losses · win_streak 은 nullable 이다 —
-- PUBG 를 넣으려고 "치킨 수 = 승"으로 우기면 데이터가 거짓말을 한다. 게임마다 다른 나머지는 detail(jsonb)이다.
-- (게임마다 테이블 하나 · 전부 jsonb 는 검토하고 버렸다 — 목록이 핫 패스라 쿼리 한 번을 지키고, DB 가 검증할 수 있는 것은 컬럼으로 둔다.)
--
-- wins 와 losses 는 같이 있거나 같이 없다(together_check) — 한쪽만 있으면 승률을 계산할 수 없다.
CREATE TABLE game_account_stats (
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
        REFERENCES game_accounts (id) ON DELETE CASCADE,
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
-- 제공자의 회원 번호는 provider_user_id 에만 있고 사용자 번호가 되지 않는다.
CREATE TABLE social_identities (
    provider         varchar(10) NOT NULL,
    provider_user_id varchar(64) NOT NULL,
    user_id          bigint      NOT NULL,
    created_at       timestamptz NOT NULL,
    CONSTRAINT social_identities_pkey PRIMARY KEY (provider, provider_user_id),
    CONSTRAINT social_identities_user_id_provider_key UNIQUE (user_id, provider),
    CONSTRAINT social_identities_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT social_identities_provider_check CHECK (provider IN ('KAKAO', 'DISCORD'))
);


-- ============================================================================================
-- 소셜 (social 패키지)
-- ============================================================================================

-- 차단. 방향이 있는 한 줄이다 — blocker_id 가 blocked_id 를 차단했다. matching 은 양방향으로 조회한다.
-- ** 이 테이블은 matching 이 직접 읽는다(그쪽의 block/Block.java · docs/11 D-4) — id · blocker_id · blocked_id 의 이름과 자료형이 그쪽과 맞아야 한다.
--    matching 은 테이블을 스키마 붙인 이름(social.blocks)으로 읽고 있다 — 2026-09-26 에 public 으로 옮겼으므로 그쪽도 고쳐야 한다(matching 폴더의 일).
-- "같은 사람 두 번 차단 금지"는 UNIQUE 가 지킨다 — 앱은 조회하지 않고 INSERT 의 위반을 409 ALREADY_BLOCKED 로 옮긴다.
-- 대상이 있는 사용자인지는 FK 가 지킨다 — 위반을 404 USER_NOT_FOUND 로 옮긴다.
CREATE TABLE blocks (
    id         bigint GENERATED ALWAYS AS IDENTITY,
    blocker_id bigint      NOT NULL,
    blocked_id bigint      NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT blocks_pkey PRIMARY KEY (id),
    CONSTRAINT blocks_blocker_blocked_key UNIQUE (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self CHECK (blocker_id <> blocked_id),
    CONSTRAINT blocks_blocker_id_fkey FOREIGN KEY (blocker_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT blocks_blocked_id_fkey FOREIGN KEY (blocked_id) REFERENCES users (id) ON DELETE CASCADE
);

-- "나를 차단한 사람" 방향의 조회용이다(목록의 차단 거르기 · matching 의 양방향 조회). 반대 방향은 UNIQUE 의 인덱스가 받는다.
CREATE INDEX blocks_blocked_id_idx ON blocks (blocked_id);

-- 친구 요청. 방향이 있는 한 줄이다 — requester_id 가 receiver_id 에게 보냈다. 처리된 줄은 지우지 않고 status 로 닫는다.
-- responded_at 은 "대기 중이 아니게 된 시각"이다 — 수락 · 거절 · 거두기 어느 쪽이든 적는다. PENDING 과 NULL 은 늘 같이 간다.
CREATE TABLE friend_requests (
    id           bigint GENERATED ALWAYS AS IDENTITY,
    requester_id bigint      NOT NULL,
    receiver_id  bigint      NOT NULL,
    status       varchar(10) NOT NULL,
    created_at   timestamptz NOT NULL,
    responded_at timestamptz,
    CONSTRAINT friend_requests_pkey PRIMARY KEY (id),
    CONSTRAINT friend_requests_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED')),
    CONSTRAINT friend_requests_not_self CHECK (requester_id <> receiver_id),
    CONSTRAINT friend_requests_responded_at_check CHECK ((status = 'PENDING') = (responded_at IS NULL)),
    CONSTRAINT friend_requests_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT friend_requests_receiver_id_fkey FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
);

-- "같은 방향의 대기 중 요청은 하나"는 이 인덱스가 지킨다 — 앱은 조회하지 않고 INSERT 의 위반을 409 FRIEND_REQUEST_ALREADY_SENT 로 옮긴다.
-- 처리된 줄(거절 · 거두기 · 수락)은 인덱스에 없어서 그 뒤에 다시 요청할 수 있다.
-- 반대 방향은 다른 줄이다 — 서로 동시에 보내면 양방향 PENDING 이 둘 생길 수 있고, 수락할 때 앱이 둘 다 닫는다.
-- "내가 보낸 대기 중 요청" 조회도 이 인덱스가 받는다(requester_id 가 앞이다).
CREATE UNIQUE INDEX friend_requests_one_pending ON friend_requests (requester_id, receiver_id) WHERE status = 'PENDING';

-- "내가 받은 대기 중 요청" 조회용이다.
CREATE INDEX friend_requests_receiver_pending_idx ON friend_requests (receiver_id) WHERE status = 'PENDING';

-- 친구. 방향이 없는 한 줄이다 — 두 사용자 번호를 (작은 쪽, 큰 쪽)으로 정규화해 넣고 PK 가 중복을 막는다.
-- "작은 쪽"은 앱(자바의 Long 비교)이 고른다 — 숫자라 DB 의 비교와 늘 같다.
CREATE TABLE friendships (
    user_low_id  bigint      NOT NULL,
    user_high_id bigint      NOT NULL,
    created_at   timestamptz NOT NULL,
    CONSTRAINT friendships_pkey PRIMARY KEY (user_low_id, user_high_id),
    CONSTRAINT friendships_ordered CHECK (user_low_id < user_high_id),
    CONSTRAINT friendships_user_low_id_fkey FOREIGN KEY (user_low_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT friendships_user_high_id_fkey FOREIGN KEY (user_high_id) REFERENCES users (id) ON DELETE CASCADE
);

-- 친구 목록은 "내가 어느 칸에 있든" 찾는다 — 작은 쪽은 PK 의 인덱스가, 큰 쪽은 이 인덱스가 받는다.
CREATE INDEX friendships_user_high_id_idx ON friendships (user_high_id);

-- 신고. 접수만 받는다 — 처리 화면 · 제재는 없다. 같은 사람을 여러 번 신고할 수 있어 UNIQUE 가 없다.
-- context_id 는 글(모집 글)의 id 다. 있는지 확인하지 않는다(FK 도 없다 — 계약이 모양만 본다고 정했다).
CREATE TABLE reports (
    id             bigint GENERATED ALWAYS AS IDENTITY,
    reporter_id    bigint      NOT NULL,
    target_user_id bigint      NOT NULL,
    reason         varchar(20) NOT NULL,
    detail         varchar(1000),
    context_id     bigint,
    status         varchar(10) NOT NULL DEFAULT 'RECEIVED',
    created_at     timestamptz NOT NULL,
    CONSTRAINT reports_pkey PRIMARY KEY (id),
    CONSTRAINT reports_reason_check CHECK (reason IN ('ABUSE', 'CHEATING', 'SPAM', 'NO_SHOW', 'OTHER')),
    CONSTRAINT reports_status_check CHECK (status IN ('RECEIVED', 'REVIEWED')),
    CONSTRAINT reports_not_self CHECK (reporter_id <> target_user_id),
    CONSTRAINT reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT reports_target_user_id_fkey FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE CASCADE
);


-- ============================================================================================
-- 파티 모집 게시판 (party 패키지)
-- ============================================================================================
-- 여기 있는 것은 "오래 남는 것"이다 — 글 · 글의 상태 · 확정된 파티원. 지금 방에 누가 있는지는 Redis(room 패키지)의 것이고 여기 없다.
-- outbox 는 만들지 않는다 — SQS 배선이 미정이다.

-- 모집 글. id 가 곧 roomId 다 — 글 쓰기가 같은 번호의 방을 같이 만든다(2026-09-25 소유자 결정 C). 방 키에는 숫자가 십진 문자열로 들어간다.
-- 글은 지우지 않는다 — 방장이 지우거나 방이 사라지면 EXPIRED, 방장이 확정하면 CONFIRMED 로 남는다.
-- mode 는 앱이 필수로 받고 gameconfig 의 모드 이름으로 검증한다 — 값의 목록이 Redis 에 있어 DB 로는 강제하지 않는다(NULL 허용 그대로).
-- conditions — 게임마다 다른 조건(PUBG 의 시점 등). 모양은 앱이 검증한다.
CREATE TABLE recruit_posts (
    id           bigint       GENERATED ALWAYS AS IDENTITY,
    host_id      bigint       NOT NULL,
    game         varchar(10)  NOT NULL,
    mode         varchar(30),
    title        varchar(60)  NOT NULL,
    description  varchar(300),
    voice        varchar(10)  NOT NULL,
    purpose      varchar(10)  NOT NULL,
    conditions   jsonb        NOT NULL DEFAULT '{}',
    status       varchar(12)  NOT NULL,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    confirmed_at timestamptz,
    expired_at   timestamptz,
    CONSTRAINT recruit_posts_pkey PRIMARY KEY (id),
    CONSTRAINT recruit_posts_host_id_fkey FOREIGN KEY (host_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT recruit_posts_game_check CHECK (game IN ('LOL', 'VALORANT', 'PUBG')),
    CONSTRAINT recruit_posts_voice_check CHECK (voice IN ('REQUIRED', 'NO_VOICE')),
    CONSTRAINT recruit_posts_purpose_check CHECK (purpose IN ('RANK_UP', 'NORMAL', 'FUN')),
    CONSTRAINT recruit_posts_status_check CHECK (status IN ('RECRUITING', 'CONFIRMED', 'EXPIRED')),
    -- 상태와 시각이 어긋난 줄을 DB 가 받지 않는다 — CONFIRMED 면 confirmed_at 이 있고, 아니면 없다. EXPIRED 도 같다
    CONSTRAINT recruit_posts_confirmed_at_check CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CONSTRAINT recruit_posts_expired_at_check CHECK ((status = 'EXPIRED') = (expired_at IS NOT NULL))
);

-- "모집 중인 글은 한 사람에 하나"를 DB 가 지킨다 — 앱은 조회하지 않고 INSERT 의 위반을 409 ALREADY_RECRUITING 으로 옮긴다.
-- 만료 · 확정된 글은 몇 개든 남을 수 있어 부분 인덱스다.
CREATE UNIQUE INDEX recruit_posts_one_recruiting_per_host ON recruit_posts (host_id) WHERE status = 'RECRUITING';

-- 게시판 목록의 조회용 — 정렬이 id 내림차순 하나이고(2026-09-24 소유자 결정) 목록의 game 은 필수다(2026-09-25).
-- 게임으로 좁힌 뒤 그 순서로 이어 읽으므로 목록 조회가 따로 정렬하지 않고, 커서의 id < ? 도 같은 인덱스를 탄다.
-- (id 가 identity 라 순증가 · 유일 · 불변을 혼자 만족한다 — 커서가 요구하는 셋이다. created_at 은 화면의 "몇 분 전"에만 쓴다.)
CREATE INDEX recruit_posts_game_id_idx ON recruit_posts (game, id DESC);

-- 글의 "찾는 포지션". 그 게임의 포지션 이름인지는 앱이 검증한다(목록의 원본이 앱의 Game 이다). PUBG 는 줄이 없다.
CREATE TABLE recruit_post_positions (
    post_id  bigint      NOT NULL,
    position varchar(20) NOT NULL,
    CONSTRAINT recruit_post_positions_pkey PRIMARY KEY (post_id, position),
    CONSTRAINT recruit_post_positions_post_id_fkey FOREIGN KEY (post_id) REFERENCES recruit_posts (id) ON DELETE CASCADE
);

-- 확정된 파티. source 가 BOARD 면 게시판의 방장 확정으로 생긴 것이고 post_id 가 글의 id(= roomId)다 —
-- "한 글에 파티 하나"는 UNIQUE (post_id) 가 지킨다(방장 확정의 기록은 INSERT … ON CONFLICT (post_id) DO NOTHING 으로 멱등하다).
-- MATCH 는 자동 매칭(ProposalConfirmed.fifo)의 자리다 — 아직 만드는 코드가 없고, matching 의 partyId 를 어디에 들지는 6단계에서 정한다.
-- post_id 의 FK 는 ON DELETE CASCADE 다 — 방장(사용자)을 지우면 글이 딸려 지워지고, 그 글의 파티 · 파티원도 같이 지워진다.
CREATE TABLE parties (
    id         bigint      GENERATED ALWAYS AS IDENTITY,
    source     varchar(8)  NOT NULL,
    post_id    bigint,
    game       varchar(10) NOT NULL,
    status     varchar(8)  NOT NULL,
    created_at timestamptz NOT NULL,
    closed_at  timestamptz,
    CONSTRAINT parties_pkey PRIMARY KEY (id),
    CONSTRAINT parties_post_id_key UNIQUE (post_id),
    CONSTRAINT parties_post_id_fkey FOREIGN KEY (post_id) REFERENCES recruit_posts (id) ON DELETE CASCADE,
    CONSTRAINT parties_source_check CHECK (source IN ('BOARD', 'MATCH')),
    CONSTRAINT parties_game_check CHECK (game IN ('LOL', 'VALORANT', 'PUBG')),
    CONSTRAINT parties_status_check CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT parties_board_has_post_check CHECK ((source = 'BOARD') = (post_id IS NOT NULL)),
    CONSTRAINT parties_closed_at_check CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

-- 파티원. 게시판 파티는 확정하던 순간 방의 멤버 SET 에 있던 사람 가운데 가입한 사용자다(FK 가 있어 가입하지 않은 번호는 적지 않는다).
-- is_host 는 글을 쓴 사람이다.
CREATE TABLE party_members (
    party_id  bigint      NOT NULL,
    user_id   bigint      NOT NULL,
    is_host   boolean     NOT NULL,
    joined_at timestamptz NOT NULL,
    CONSTRAINT party_members_pkey PRIMARY KEY (party_id, user_id),
    CONSTRAINT party_members_party_id_fkey FOREIGN KEY (party_id) REFERENCES parties (id) ON DELETE CASCADE,
    CONSTRAINT party_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- "이 사람이 함께한 파티" 방향의 조회용이다(최근 함께한 사람 등). 반대 방향은 PK 의 인덱스가 받는다.
CREATE INDEX party_members_user_id_idx ON party_members (user_id);


-- ============================================================================================
-- 최근 함께한 사람 (social 패키지 — parties 를 FK 로 가리키므로 파티 다음에 만든다)
-- ============================================================================================

-- 방향이 있는 한 줄이다 — user_id 의 목록에 other_user_id 가 있다. 읽는 쪽은 자기 user_id 의 줄만 본다.
-- 한 사람에 한 줄이다 — PK 가 (user_id, other_user_id) 라서 같은 사람과 또 해도 줄이 늘지 않는다(채우는 쪽이 마지막 것으로 덮는다).
-- last_party_id 는 그 파티가 지워지면 NULL 이 된다(그래서 nullable 이다). 사람은 남는다.
-- 채우는 것은 PartyClosed.fifo 의 소비인데 SQS 배선이 미정이라 아직 아무도 채우지 않는다.
CREATE TABLE recent_players (
    user_id        bigint      NOT NULL,
    other_user_id  bigint      NOT NULL,
    last_party_id  bigint,
    last_played_at timestamptz NOT NULL,
    CONSTRAINT recent_players_pkey PRIMARY KEY (user_id, other_user_id),
    CONSTRAINT recent_players_not_self CHECK (user_id <> other_user_id),
    CONSTRAINT recent_players_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT recent_players_other_user_id_fkey FOREIGN KEY (other_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT recent_players_last_party_id_fkey FOREIGN KEY (last_party_id) REFERENCES parties (id) ON DELETE SET NULL
);

-- 목록은 "내 것을 최근순으로 50명"이다.
CREATE INDEX recent_players_user_recent_idx ON recent_players (user_id, last_played_at DESC);
