-- QueueMate 초기 스키마
-- 근거: docs/06_DATA_MODEL.md, contracts/openapi.yaml
-- enum은 PostgreSQL enum type 대신 varchar + CHECK로 둔다.
-- JPA EnumType.STRING과 그대로 맞고, 값 추가 시 type 변경 없이 migration 하나로 끝난다.

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- updated_at 자동 갱신.
-- now()는 트랜잭션 시각이라 한 트랜잭션 내 연속 갱신을 구분하지 못하므로 clock_timestamp를 쓴다.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================
-- 계정 / 프로필
-- ============================================================

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    nickname      VARCHAR(16)  NOT NULL,
    avatar_url    VARCHAR(512),
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_email_unique UNIQUE (email),
    CONSTRAINT users_nickname_unique UNIQUE (nickname),
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT users_nickname_length_check CHECK (char_length(nickname) BETWEEN 2 AND 16)
);

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 외부 게임 ID는 필요한 범위만 저장한다 (docs/13 PII).
CREATE TABLE game_accounts (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider_game    VARCHAR(20) NOT NULL,
    external_game_id VARCHAR(128) NOT NULL,
    region           VARCHAR(20),
    rank_code        VARCHAR(40),
    verified_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT game_accounts_unique UNIQUE (user_id, provider_game, external_game_id),
    CONSTRAINT game_accounts_provider_game_check CHECK (provider_game IN ('LOL', 'VALORANT', 'PUBG'))
);

CREATE INDEX game_accounts_user_idx ON game_accounts (user_id);

CREATE TRIGGER game_accounts_set_updated_at
    BEFORE UPDATE ON game_accounts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- 게임 설정 (owner: Member 2, 테이블만 Member 3가 만든다)
-- ============================================================

CREATE TABLE game_mode_configs (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_key          VARCHAR(20) NOT NULL,
    mode_key          VARCHAR(40) NOT NULL,
    target_party_size INTEGER     NOT NULL,
    role_uniqueness   BOOLEAN     NOT NULL DEFAULT FALSE,
    active            BOOLEAN     NOT NULL DEFAULT TRUE,
    policy_json       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT game_mode_configs_unique UNIQUE (game_key, mode_key),
    CONSTRAINT game_mode_configs_game_key_check CHECK (game_key IN ('LOL', 'VALORANT', 'PUBG')),
    -- INV-3: target party size는 최소 2인 팀이다.
    CONSTRAINT game_mode_configs_target_size_check CHECK (target_party_size > 1)
);

CREATE TRIGGER game_mode_configs_set_updated_at
    BEFORE UPDATE ON game_mode_configs FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- 매칭 제안 / 요청
-- ============================================================

CREATE TABLE match_proposals (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type  VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ,
    CONSTRAINT match_proposals_source_type_check CHECK (source_type IN ('REALTIME', 'RESERVATION')),
    CONSTRAINT match_proposals_status_check
        CHECK (status IN ('PENDING', 'CONFIRMED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    -- INV-4/INV-5: CONFIRMED만 confirmed_at을 가진다.
    CONSTRAINT match_proposals_confirmed_at_check
        CHECK ((status = 'CONFIRMED') = (confirmed_at IS NOT NULL))
);

CREATE INDEX match_proposals_pending_expiry_idx
    ON match_proposals (expires_at) WHERE status = 'PENDING';

CREATE TABLE match_requests (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    match_type     VARCHAR(20) NOT NULL DEFAULT 'REALTIME',
    status         VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    condition_json JSONB       NOT NULL,
    queued_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    proposal_id    UUID        REFERENCES match_proposals (id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT match_requests_match_type_check CHECK (match_type = 'REALTIME'),
    CONSTRAINT match_requests_status_check
        CHECK (status IN ('QUEUED', 'PROPOSED', 'MATCHED', 'CANCELLED', 'EXPIRED'))
);

-- INV-1: 한 사용자는 활성 실시간 매칭 요청을 1개만 가진다.
CREATE UNIQUE INDEX match_requests_one_active_per_user_idx
    ON match_requests (user_id) WHERE status IN ('QUEUED', 'PROPOSED');

CREATE INDEX match_requests_proposal_idx ON match_requests (proposal_id);

CREATE TRIGGER match_requests_set_updated_at
    BEFORE UPDATE ON match_requests FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE reservations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    condition_json  JSONB       NOT NULL,
    available_from  TIMESTAMPTZ NOT NULL,
    available_to    TIMESTAMPTZ NOT NULL,
    play_amount     VARCHAR(20) NOT NULL,
    scheduled_start TIMESTAMPTZ,
    proposal_id     UUID        REFERENCES match_proposals (id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reservations_status_check
        CHECK (status IN ('ACTIVE', 'PROPOSED', 'MATCHED', 'CANCELLED', 'EXPIRED', 'COMPLETED')),
    CONSTRAINT reservations_play_amount_check CHECK (play_amount IN ('ONE_GAME', 'TWO_PLUS')),
    CONSTRAINT reservations_window_check CHECK (available_from < available_to),
    -- INV-9: 시간이 겹치는 활성 예약을 중복 등록할 수 없다.
    -- 30분 경계 정렬은 docs/06에 따라 service layer가 검증한다.
    CONSTRAINT reservations_no_active_overlap EXCLUDE USING gist (
        user_id WITH =,
        tstzrange(available_from, available_to) WITH &&
    ) WHERE (status IN ('ACTIVE', 'PROPOSED'))
);

CREATE INDEX reservations_active_window_idx
    ON reservations (available_from, available_to) WHERE status = 'ACTIVE';

CREATE INDEX reservations_proposal_idx ON reservations (proposal_id);

CREATE TRIGGER reservations_set_updated_at
    BEFORE UPDATE ON reservations FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- source_request_id는 source_type에 따라 match_requests 또는 reservations를 가리키므로
-- FK를 걸지 않는다. 참조 무결성은 service layer가 담당한다.
CREATE TABLE proposal_members (
    proposal_id       UUID        NOT NULL REFERENCES match_proposals (id) ON DELETE CASCADE,
    user_id           UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_request_id UUID        NOT NULL,
    acceptance        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- INV-7 계열: 동일 proposal 내 사용자 중복 금지.
    PRIMARY KEY (proposal_id, user_id),
    CONSTRAINT proposal_members_acceptance_check
        CHECK (acceptance IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);

-- INV-2는 Redis atomic claim + service layer가 강제한다.
-- proposal status가 다른 테이블에 있어 partial unique index로는 표현할 수 없다.
CREATE INDEX proposal_members_user_idx ON proposal_members (user_id);

CREATE TRIGGER proposal_members_set_updated_at
    BEFORE UPDATE ON proposal_members FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- 파티
-- ============================================================

CREATE TABLE parties (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proposal_id     UUID        NOT NULL REFERENCES match_proposals (id) ON DELETE RESTRICT,
    game_key        VARCHAR(20) NOT NULL,
    mode_key        VARCHAR(40) NOT NULL,
    target_size     INTEGER     NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    scheduled_start TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at       TIMESTAMPTZ,
    -- INV-4: proposal 하나에서 party는 하나만 확정된다.
    CONSTRAINT parties_proposal_unique UNIQUE (proposal_id),
    CONSTRAINT parties_game_key_check CHECK (game_key IN ('LOL', 'VALORANT', 'PUBG')),
    CONSTRAINT parties_status_check CHECK (status IN ('OPEN', 'READY', 'PLAYING', 'CLOSED')),
    CONSTRAINT parties_target_size_check CHECK (target_size > 1),
    CONSTRAINT parties_closed_at_check CHECK ((status = 'CLOSED') OR (closed_at IS NULL))
);

CREATE INDEX parties_status_idx ON parties (status);

CREATE TABLE party_members (
    party_id  UUID        NOT NULL REFERENCES parties (id) ON DELETE CASCADE,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    ready     BOOLEAN     NOT NULL DEFAULT FALSE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at   TIMESTAMPTZ,
    -- INV-7: 동일 사용자의 PartyMember 중복 금지.
    PRIMARY KEY (party_id, user_id),
    CONSTRAINT party_members_left_at_check CHECK (left_at IS NULL OR left_at >= joined_at)
);

-- recent players는 완료된 party history에서 조회한다 (docs/06).
CREATE INDEX party_members_user_joined_idx ON party_members (user_id, joined_at DESC);

-- ============================================================
-- 소셜
-- ============================================================

CREATE TABLE friend_requests (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    receiver_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT friend_requests_status_check
        CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT friend_requests_not_self_check CHECK (sender_id <> receiver_id),
    CONSTRAINT friend_requests_responded_at_check
        CHECK ((status = 'PENDING') = (responded_at IS NULL))
);

-- 같은 방향의 PENDING 요청은 하나만 허용한다.
CREATE UNIQUE INDEX friend_requests_one_pending_idx
    ON friend_requests (sender_id, receiver_id) WHERE status = 'PENDING';

CREATE INDEX friend_requests_receiver_pending_idx
    ON friend_requests (receiver_id) WHERE status = 'PENDING';

-- (user_low_id, user_high_id)로 정규화해 방향 중복을 제거한다.
CREATE TABLE friendships (
    user_low_id  UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_high_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_low_id, user_high_id),
    CONSTRAINT friendships_normalized_check CHECK (user_low_id < user_high_id)
);

CREATE INDEX friendships_high_idx ON friendships (user_high_id);

CREATE TABLE blocks (
    blocker_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    blocked_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self_check CHECK (blocker_id <> blocked_id)
);

-- INV-6: 양방향 조회가 모두 필요하다 (내가 차단한 사람 / 나를 차단한 사람).
CREATE INDEX blocks_blocked_idx ON blocks (blocked_id);

-- 서버는 음성/채팅을 저장하지 않으므로 category + description + 식별자만 남는다 (docs/13).
CREATE TABLE reports (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    target_user_id UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    party_id       UUID        REFERENCES parties (id) ON DELETE SET NULL,
    reason         VARCHAR(40) NOT NULL,
    description    VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reports_not_self_check CHECK (reporter_id <> target_user_id)
);

CREATE INDEX reports_target_idx ON reports (target_user_id, created_at DESC);
