-- party 스키마 — 파티 모집 게시판의 글과, 방장이 확정한 파티의 기록 (CLAUDE.md §3.5 · §7.1 · contracts/platform-api.md "모집 글 · 목록 · 입장권").
--
-- 이 앱이 다루는 것은 "오래 남는 것"이다 — 글 · 글의 상태 · 확정된 파티원. 지금 방에 누가 있는지는 room(Redis)의 것이고 여기 없다 (docs/11 D-16).
-- 사용자 번호(host_id · user_id)는 account.users.id 의 것이지만 FK 를 걸지 않는다 — 크로스 스키마 FK 금지다 (CLAUDE.md §3.5).
-- party.outbox 는 만들지 않는다 — SQS 배선이 미정이다 (CLAUDE.md §7 "SQS 배선 시점").
--
-- 모든 PK 는 bigint GENERATED ALWAYS AS IDENTITY 다 (2026-09-22 소유자 결정). 글의 id 가 곧 roomId 다 — room 은 roomId 를 문자열로 받으므로
-- 브라우저 · 입장권에는 숫자를 문자열로 준다. source = 'MATCH' 인 파티가 matching 의 partyId(UUID 문자열)를 어디에 들지는 6단계(SQS 배선)에서 정한다 —
-- 여기서는 정하지 않는다.

CREATE SCHEMA party;

-- 모집 글. id 가 곧 roomId 다 — 브라우저가 이 값으로 room 의 방 만들기를 부른다.
-- 글은 지우지 않는다 — 방장이 지우거나 방이 사라지면 EXPIRED, 방장이 확정하면 CONFIRMED 로 남는다.
--
-- room_seen_at — "아직 안 만들어진 방"과 "사라진 방"을 가르는 자리다. room 의 방장 키를 처음 본 순간 적는다.
--   이 값이 있는데 방장 키가 없으면 방이 사라진 것이고, 이 값이 없으면 방 만들기를 아직 안 부른 글이다(쓴 지 10분이 지나야 만료시킨다).
-- conditions — 게임마다 다른 조건(PUBG 의 시점 등). 모양은 앱이 검증한다.
CREATE TABLE party.recruit_posts (
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
    room_seen_at timestamptz,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    confirmed_at timestamptz,
    expired_at   timestamptz,
    CONSTRAINT recruit_posts_pkey PRIMARY KEY (id),
    CONSTRAINT recruit_posts_game_check CHECK (game IN ('LOL', 'VALORANT', 'PUBG')),
    CONSTRAINT recruit_posts_voice_check CHECK (voice IN ('REQUIRED', 'NO_VOICE')),
    CONSTRAINT recruit_posts_purpose_check CHECK (purpose IN ('RANK_UP', 'NORMAL', 'FUN')),
    CONSTRAINT recruit_posts_status_check CHECK (status IN ('RECRUITING', 'CONFIRMED', 'EXPIRED')),
    -- 상태와 시각이 어긋난 줄을 DB 가 받지 않는다 — CONFIRMED 면 confirmed_at 이 있고, 아니면 없다. EXPIRED 도 같다
    CONSTRAINT recruit_posts_confirmed_at_check CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL)),
    CONSTRAINT recruit_posts_expired_at_check CHECK ((status = 'EXPIRED') = (expired_at IS NOT NULL))
);

-- "모집 중인 글은 한 사람에 하나"를 DB 가 지킨다 — 앱은 조회하지 않고 INSERT 의 위반을 409 ALREADY_RECRUITING 으로 옮긴다 (CLAUDE.md §5).
-- 만료 · 확정된 글은 몇 개든 남을 수 있어 부분 인덱스다.
CREATE UNIQUE INDEX recruit_posts_one_recruiting_per_host ON party.recruit_posts (host_id) WHERE status = 'RECRUITING';

-- 게시판 목록의 조회용 — 게임별로 · 모집 중인 글 먼저 · 새 글 먼저.
CREATE INDEX recruit_posts_game_status_created_idx ON party.recruit_posts (game, status, created_at DESC);

-- 글의 "찾는 포지션". 그 게임의 포지션 이름인지는 앱이 검증한다(목록의 원본이 앱의 Game 이다). PUBG 는 줄이 없다.
CREATE TABLE party.recruit_post_positions (
    post_id  bigint      NOT NULL,
    position varchar(20) NOT NULL,
    CONSTRAINT recruit_post_positions_pkey PRIMARY KEY (post_id, position),
    CONSTRAINT recruit_post_positions_post_id_fkey FOREIGN KEY (post_id) REFERENCES party.recruit_posts (id) ON DELETE CASCADE
);

-- 확정된 파티. source 가 BOARD 면 게시판의 방장 확정으로 생긴 것이고 post_id 가 글의 id(= roomId)다 —
-- "한 글에 파티 하나"는 UNIQUE (post_id) 가 지킨다(방장 확정의 기록은 INSERT … ON CONFLICT (post_id) DO NOTHING 으로 멱등하다).
-- MATCH 는 자동 매칭(ProposalConfirmed.fifo)의 자리다 — 아직 만드는 코드가 없고, matching 의 partyId 를 어디에 들지는 6단계에서 정한다(머리 주석).
CREATE TABLE party.parties (
    id         bigint      GENERATED ALWAYS AS IDENTITY,
    source     varchar(8)  NOT NULL,
    post_id    bigint,
    game       varchar(10) NOT NULL,
    status     varchar(8)  NOT NULL,
    created_at timestamptz NOT NULL,
    closed_at  timestamptz,
    CONSTRAINT parties_pkey PRIMARY KEY (id),
    CONSTRAINT parties_post_id_key UNIQUE (post_id),
    CONSTRAINT parties_post_id_fkey FOREIGN KEY (post_id) REFERENCES party.recruit_posts (id),
    CONSTRAINT parties_source_check CHECK (source IN ('BOARD', 'MATCH')),
    CONSTRAINT parties_game_check CHECK (game IN ('LOL', 'VALORANT', 'PUBG')),
    CONSTRAINT parties_status_check CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT parties_board_has_post_check CHECK ((source = 'BOARD') = (post_id IS NOT NULL)),
    CONSTRAINT parties_closed_at_check CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

-- 파티원. 게시판 파티는 확정 기록을 하던 순간 room 의 멤버 SET 에 있던 전원이다. is_host 는 글을 쓴 사람이다.
CREATE TABLE party.party_members (
    party_id  bigint      NOT NULL,
    user_id   bigint      NOT NULL,
    is_host   boolean     NOT NULL,
    joined_at timestamptz NOT NULL,
    CONSTRAINT party_members_pkey PRIMARY KEY (party_id, user_id),
    CONSTRAINT party_members_party_id_fkey FOREIGN KEY (party_id) REFERENCES party.parties (id) ON DELETE CASCADE
);

-- "이 사람이 함께한 파티" 방향의 조회용이다(최근 함께한 사람 등). 반대 방향은 PK 의 인덱스가 받는다.
CREATE INDEX party_members_user_id_idx ON party.party_members (user_id);
