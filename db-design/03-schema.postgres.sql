-- =====================================================================
--  LoL 파티 매칭 서비스 — PostgreSQL 16 스키마
--  기준: 기능 명세서 대주제 1~5
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;   -- EXCLUDE 제약(INV-2)에 필요

-- ---------------------------------------------------------------------
-- ENUM
-- ---------------------------------------------------------------------
CREATE TYPE voice_mode    AS ENUM ('REQUIRED','AVAILABLE','NONE');            -- 1.2.2
CREATE TYPE play_purpose  AS ENUM ('CASUAL','WIN','LEARN','SKILLED');         -- 1.2.3
CREATE TYPE play_mood     AS ENUM ('RELAXED','FOCUSED','INTENSE');            -- 1.2.4
CREATE TYPE lol_position  AS ENUM ('TOP','JUNGLE','MID','BOT','SUPPORT');     -- 1.2.6
CREATE TYPE queue_type    AS ENUM ('SOLO_DUO','FLEX','NORMAL');               -- 1.4.6
CREATE TYPE match_kind    AS ENUM ('IMMEDIATE','SCHEDULED');                  -- 1.4.1
CREATE TYPE request_status AS ENUM
    ('RESERVED','QUEUED','PROPOSED','REFILLING','CONFIRMED','CANCELED','FAILED'); -- 1.5.2~1.5.8
CREATE TYPE offer_status  AS ENUM ('PENDING','REFILLING','CONFIRMED','FAILED');
CREATE TYPE seat_status   AS ENUM ('OPEN','PENDING','FILLED');
CREATE TYPE seat_response AS ENUM ('PENDING','ACCEPTED_LOCKED','DECLINED','TIMED_OUT'); -- 1.6.11
CREATE TYPE ready_state   AS ENUM ('NONE','READY','LATE');                    -- 1.7.7 / 1.7.8
CREATE TYPE riot_link_status AS ENUM ('OK','NEEDS_CHECK','FAILED');           -- 1.3.8
CREATE TYPE busy_source   AS ENUM ('REQUEST','PARTY');
CREATE TYPE discord_channel_status AS ENUM ('REQUESTED','CREATED','SKIPPED','FAILED','DELETED'); -- 5.2.6
CREATE TYPE discord_member_status  AS ENUM ('PENDING','GRANTED','NOT_JOINED','FAILED');          -- 5.3.4
CREATE TYPE outbox_topic  AS ENUM ('NOTIFICATION','DISCORD');
CREATE TYPE delivery_status AS ENUM ('SENT','FAILED','EXPIRED_SUBSCRIPTION');

-- ---------------------------------------------------------------------
-- 게임 (멀티게임 확장 탈출구)
-- ---------------------------------------------------------------------
CREATE TABLE game (
    id        smallserial PRIMARY KEY,
    code      text UNIQUE NOT NULL,
    name      text NOT NULL,
    is_active boolean NOT NULL DEFAULT true
);
INSERT INTO game (code, name) VALUES ('LOL', '리그 오브 레전드');

-- ---------------------------------------------------------------------
-- 사용자
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id                 bigserial PRIMARY KEY,
    discord_id         text UNIQUE NOT NULL,                      -- 1.1.1 / 1.1.3
    discord_username   text NOT NULL,                             -- 1.7.4 확정 후 공개
    nickname           text NOT NULL,                             -- 1.2.1
    voice_mode         voice_mode   NOT NULL DEFAULT 'AVAILABLE', -- 1.2.2
    purpose            play_purpose NOT NULL DEFAULT 'CASUAL',    -- 1.2.3
    mood               play_mood    NOT NULL DEFAULT 'RELAXED',   -- 1.2.4 (표시 전용)
    primary_position   lol_position,                              -- 1.2.6
    discord_dm_enabled boolean NOT NULL DEFAULT false,            -- 5.4.2 기본 꺼짐
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE user_sub_position (                                  -- 1.2.7
    user_id  bigint       NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    position lol_position NOT NULL,
    PRIMARY KEY (user_id, position)
);

CREATE TABLE riot_account (                                       -- 1.3
    user_id            bigint PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    game_name          text NOT NULL,                             -- 1.3.1
    tag_line           text NOT NULL,
    puuid              text UNIQUE,                               -- 1.3.3
    solo_tier          text,                                      -- 1.3.5
    solo_tier_order    smallint,                                  -- 비교용 (2.2.4)
    solo_division      text,
    solo_lp            int,
    solo_wins          int,
    solo_losses        int,
    solo_in_placement  boolean NOT NULL DEFAULT false,            -- 1.3.7
    flex_tier          text,                                      -- 1.3.6
    flex_tier_order    smallint,
    flex_division      text,
    flex_lp            int,
    flex_wins          int,
    flex_losses        int,
    flex_in_placement  boolean NOT NULL DEFAULT false,
    link_status        riot_link_status NOT NULL DEFAULT 'NEEDS_CHECK', -- 1.3.8
    refreshed_at       timestamptz,                               -- 1.3.9
    UNIQUE (game_name, tag_line)
);

CREATE TABLE push_subscription (                                  -- 1.9.1
    id         bigserial PRIMARY KEY,
    user_id    bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    endpoint   text UNIQUE NOT NULL,
    p256dh     text NOT NULL,
    auth       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz                                        -- 4.2.3
);

-- ---------------------------------------------------------------------
-- 매칭 요청
-- ---------------------------------------------------------------------
CREATE TABLE match_request (
    id                     bigserial PRIMARY KEY,
    user_id                bigint   NOT NULL REFERENCES app_user(id),
    game_id                smallint NOT NULL REFERENCES game(id),
    kind                   match_kind     NOT NULL,               -- 1.4.1
    queue                  queue_type     NOT NULL,               -- 1.4.6
    target_size            smallint       NOT NULL,               -- 1.4.7
    status                 request_status NOT NULL,               -- 1.5
    requested_at           timestamptz    NOT NULL DEFAULT now(), -- 2.4.1 FCFS 1차 정렬키
    play_minutes           int            NOT NULL,               -- 1.4.16

    max_wait_minutes       int,                                   -- 1.4.2 (즉시 전용)
    play_date              date,                                  -- 1.4.13 (예약 전용)
    window_start           timestamptz,                           -- 1.4.15
    window_end             timestamptz,

    tier_snapshot          text,                                  -- 1.4.19
    tier_snapshot_order    smallint,                              -- 2.2.4 / 2.2.6
    tier_snapshot_at       timestamptz,                           -- 1.3.11 신선도
    allowed_tier_min_order smallint NOT NULL,                     -- 1.4.10
    allowed_tier_max_order smallint NOT NULL,

    primary_position       lol_position NOT NULL,                 -- 2.5.1
    purpose                play_purpose NOT NULL,                 -- 2.2.8
    voice_mode             voice_mode   NOT NULL,                 -- 2.3
    conditions             jsonb NOT NULL DEFAULT '{}',           -- 게임 확장 여지
    version                int NOT NULL DEFAULT 0,                -- 1.5.9 낙관적 락
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_target_size    CHECK (target_size BETWEEN 2 AND 5),
    CONSTRAINT ck_tier_range     CHECK (allowed_tier_min_order <= allowed_tier_max_order),
    -- INV-9 : 자유 랭크 4인 차단 (1.4.8)
    CONSTRAINT ck_flex_no_four   CHECK (queue <> 'FLEX' OR target_size <> 4),
    CONSTRAINT ck_kind_fields    CHECK (
        (kind = 'IMMEDIATE' AND max_wait_minutes IS NOT NULL)
     OR (kind = 'SCHEDULED' AND play_date IS NOT NULL
                            AND window_start IS NOT NULL
                            AND window_end   IS NOT NULL)
    )
);

-- INV-1 : 사용자당 진행 중 요청 1건 (1.5.1)
CREATE UNIQUE INDEX uq_active_request_per_user ON match_request (user_id)
    WHERE status IN ('RESERVED','QUEUED','PROPOSED','REFILLING');

-- 2.4.1 즉시 매칭 후보 스캔
CREATE INDEX idx_request_queued ON match_request (game_id, queue, target_size, requested_at)
    WHERE status = 'QUEUED';
-- 3.1.2 예약 배치 대상 조회
CREATE INDEX idx_request_reserved ON match_request (play_date, queue, target_size, requested_at)
    WHERE status = 'RESERVED';

CREATE TABLE request_sub_position (                               -- 1.4.18
    request_id bigint       NOT NULL REFERENCES match_request(id) ON DELETE CASCADE,
    position   lol_position NOT NULL,
    PRIMARY KEY (request_id, position)
);

-- ---------------------------------------------------------------------
-- 시간 점유  (INV-2 : 1.4.17 / 2.2.10 / 3.2.9)
-- ---------------------------------------------------------------------
CREATE TABLE user_busy_interval (
    id          bigserial PRIMARY KEY,
    user_id     bigint      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    during      tstzrange   NOT NULL,
    source_type busy_source NOT NULL,
    request_id  bigint REFERENCES match_request(id) ON DELETE CASCADE,
    party_id    bigint,                                    -- FK는 party 생성 후 추가
    CONSTRAINT ck_busy_source CHECK (num_nonnulls(request_id, party_id) = 1),
    -- ★ 이것이 INV-2. PK로는 "겹치는 구간"을 막지 못한다.
    EXCLUDE USING gist (user_id WITH =, during WITH &&)
);

-- ---------------------------------------------------------------------
-- 파티 제안 (즉시 매칭 전용)
-- ---------------------------------------------------------------------
CREATE TABLE match_offer (
    id          bigserial PRIMARY KEY,
    game_id     smallint     NOT NULL REFERENCES game(id),
    queue       queue_type   NOT NULL,                            -- 2.7.5 합의 조건
    target_size smallint     NOT NULL,
    purpose     play_purpose NOT NULL,
    voice_party boolean      NOT NULL,                            -- 2.3.5 / 2.3.6
    combo_hash  text         NOT NULL,                            -- 중복 제안 방지
    status      offer_status NOT NULL DEFAULT 'PENDING',
    created_at  timestamptz  NOT NULL DEFAULT now(),
    respond_by  timestamptz  NOT NULL,                            -- 1.6.3 / 2.7.6
    refill_by   timestamptz  NOT NULL,                            -- 1.6.21 / 2.7.7
    closed_at   timestamptz,
    CONSTRAINT ck_offer_deadline CHECK (refill_by >= respond_by)
);

CREATE UNIQUE INDEX uq_offer_combo_active ON match_offer (combo_hash)
    WHERE status IN ('PENDING','REFILLING');

CREATE TABLE offer_seat (                                         -- 1.6.14 / 2.7.3
    id                   bigserial PRIMARY KEY,
    offer_id             bigint       NOT NULL REFERENCES match_offer(id) ON DELETE CASCADE,
    position             lol_position NOT NULL,
    status               seat_status  NOT NULL DEFAULT 'PENDING',
    filled_by_request_id bigint REFERENCES match_request(id),     -- 2.8.17
    opened_at            timestamptz  NOT NULL DEFAULT now(),
    closed_at            timestamptz,
    UNIQUE (offer_id, position)                                   -- 2.5.2 포지션 중복 방지
);

CREATE UNIQUE INDEX uq_seat_filled_request ON offer_seat (filled_by_request_id)
    WHERE filled_by_request_id IS NOT NULL;

CREATE TABLE offer_participant (
    id           bigserial PRIMARY KEY,
    offer_id     bigint        NOT NULL REFERENCES match_offer(id) ON DELETE CASCADE,
    seat_id      bigint        NOT NULL REFERENCES offer_seat(id) ON DELETE CASCADE,
    request_id   bigint        NOT NULL REFERENCES match_request(id),
    user_id      bigint        NOT NULL REFERENCES app_user(id),
    round        smallint      NOT NULL DEFAULT 1,                -- 2.8.19 충원 회차
    response     seat_response NOT NULL DEFAULT 'PENDING',        -- 1.6.11
    offered_at   timestamptz   NOT NULL DEFAULT now(),
    expires_at   timestamptz   NOT NULL,                          -- 1.6.8
    responded_at timestamptz,                                     -- 1.6.9 멱등
    UNIQUE (seat_id, round),
    -- INV-8 : 같은 제안에 같은 요청 재등장 금지 (2.8.4 / 2.8.5)
    UNIQUE (offer_id, request_id)
);

-- INV-7 : 한 좌석에 동시에 응답 대기 후보는 1명 (2.8.14)
CREATE UNIQUE INDEX uq_seat_active_candidate ON offer_participant (seat_id)
    WHERE response = 'PENDING';

-- ---------------------------------------------------------------------
-- 확정 파티  (확정 후 이탈/취소/구성원 변경 없음 : 1.7.13~1.7.16)
-- ---------------------------------------------------------------------
CREATE TABLE party (
    id           bigserial PRIMARY KEY,
    game_id      smallint     NOT NULL REFERENCES game(id),
    offer_id     bigint UNIQUE REFERENCES match_offer(id),        -- INV-6, 예약은 NULL
    queue        queue_type   NOT NULL,                           -- 1.7.6 합의 스냅샷
    target_size  smallint     NOT NULL,
    purpose      play_purpose NOT NULL,
    voice_party  boolean      NOT NULL,                           -- 1.7.5
    start_at     timestamptz  NOT NULL,                           -- 1.7.1 / 3.3.4
    end_at       timestamptz  NOT NULL,                           -- 3.3.5
    confirmed_at timestamptz  NOT NULL DEFAULT now(),             -- 1.6.25
    CONSTRAINT ck_party_period CHECK (end_at > start_at)
);

ALTER TABLE user_busy_interval
    ADD CONSTRAINT fk_busy_party FOREIGN KEY (party_id) REFERENCES party(id) ON DELETE CASCADE;

CREATE TABLE party_member (
    request_id  bigint       PRIMARY KEY REFERENCES match_request(id),  -- INV-3
    party_id    bigint       NOT NULL REFERENCES party(id) ON DELETE CASCADE,
    user_id     bigint       NOT NULL REFERENCES app_user(id),
    position    lol_position NOT NULL,                            -- 2.5.8
    ready_state ready_state  NOT NULL DEFAULT 'NONE',             -- 1.7.7 / 1.7.8 / 1.7.9
    ready_at    timestamptz,
    joined_at   timestamptz  NOT NULL DEFAULT now(),
    UNIQUE (party_id, position),                                  -- INV-4 (2.5.2)
    UNIQUE (party_id, user_id)
);

-- ---------------------------------------------------------------------
-- Discord
-- ---------------------------------------------------------------------
CREATE TABLE party_discord_channel (
    party_id     bigint PRIMARY KEY REFERENCES party(id) ON DELETE CASCADE, -- INV-11 (5.6.7)
    guild_id     text,                                            -- 5.1.5
    category_id  text,
    channel_id   text UNIQUE,                                     -- 5.2.5
    status       discord_channel_status NOT NULL DEFAULT 'REQUESTED',
    fail_reason  text,                                            -- 5.6.1~5.6.4
    created_at   timestamptz,
    delete_after timestamptz NOT NULL,                            -- 5.5.1 end_at + 24h
    deleted_at   timestamptz                                      -- 5.5.5
);

CREATE INDEX idx_channel_delete_due ON party_discord_channel (delete_after)
    WHERE status = 'CREATED';                                     -- 5.5.2

-- FK 대상이 party인 이유: 채널 생성이 실패/생략돼도 참가자 상태는 남아야 한다 (5.3.4~5.3.6)
CREATE TABLE party_discord_member (
    party_id    bigint NOT NULL REFERENCES party(id) ON DELETE CASCADE,
    user_id     bigint NOT NULL REFERENCES app_user(id),
    status      discord_member_status NOT NULL DEFAULT 'PENDING',
    granted_at  timestamptz,
    fail_reason text,                                             -- 5.6.5
    updated_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (party_id, user_id)                               -- 5.6.8 중복 권한 설정 방지
);

-- ---------------------------------------------------------------------
-- 비동기 발행 (Outbox)  — 1.9
-- ---------------------------------------------------------------------
CREATE TABLE outbox_event (
    id           bigserial PRIMARY KEY,
    event_id     uuid UNIQUE NOT NULL,                            -- INV-5 (1.9.9 / 4.4.1)
    topic        outbox_topic NOT NULL,                           -- SQS 큐 구분
    event_type   text NOT NULL,                                   -- 4.1.2
    party_id     bigint REFERENCES party(id) ON DELETE CASCADE,   -- nullable (매칭 실패 알림)
    user_id      bigint REFERENCES app_user(id) ON DELETE CASCADE,
    payload      jsonb NOT NULL,
    available_at timestamptz NOT NULL DEFAULT now(),              -- 4.3.11~4.3.13 리마인더
    published_at timestamptz,
    attempts     smallint NOT NULL DEFAULT 0,                     -- 4.4.2
    last_error   text,
    created_at   timestamptz NOT NULL DEFAULT now()
);

-- 발행 워커: SELECT ... FOR UPDATE SKIP LOCKED
CREATE INDEX idx_outbox_pending ON outbox_event (available_at)
    WHERE published_at IS NULL;

CREATE TABLE push_delivery (
    id              bigserial PRIMARY KEY,
    event_id        uuid   NOT NULL REFERENCES outbox_event(event_id),
    subscription_id bigint NOT NULL REFERENCES push_subscription(id) ON DELETE CASCADE,
    status          delivery_status NOT NULL,
    http_status     smallint,                                     -- 4.2.5
    error           text,
    sent_at         timestamptz NOT NULL DEFAULT now(),           -- 4.2.4
    UNIQUE (event_id, subscription_id)                            -- INV-12 (4.4.1)
);

-- ---------------------------------------------------------------------
-- 예약 배치 실행 기록  — 3.1.7 / 3.5.6
-- ---------------------------------------------------------------------
CREATE TABLE reservation_batch_run (
    play_date       date PRIMARY KEY,                             -- INV-10 (3.4.3)
    started_at      timestamptz NOT NULL DEFAULT now(),
    cutoff_at       timestamptz NOT NULL,                         -- 3.1.3 처리 경계
    finished_at     timestamptz,
    processed_count int NOT NULL DEFAULT 0,
    party_count     int NOT NULL DEFAULT 0,
    failed_count    int NOT NULL DEFAULT 0,
    error           text
);
