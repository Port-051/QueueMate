-- 원본 요청/예약 ID를 그대로 사용한다. 파티는 기존 proposal 전원 수락 이후에만 생성된다.
CREATE TABLE recruitments (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    type VARCHAR(16) NOT NULL CHECK (type IN ('REALTIME', 'RESERVATION')),
    game VARCHAR(16) NOT NULL,
    mode_key VARCHAR(64) NOT NULL,
    preferences JSONB NOT NULL,
    description VARCHAR(120) NOT NULL DEFAULT '',
    auto_match BOOLEAN NOT NULL DEFAULT FALSE,
    paused BOOLEAN NOT NULL DEFAULT FALSE,
    closed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    confirmed_at TIMESTAMPTZ NOT NULL,
    bumped_at TIMESTAMPTZ,
    parent_id UUID REFERENCES recruitments(id),
    requested_parent_id UUID REFERENCES recruitments(id),
    version BIGINT NOT NULL DEFAULT 0,
    last_exposed_at TIMESTAMPTZ,
    impressions BIGINT NOT NULL DEFAULT 0,
    impression_baseline BIGINT NOT NULL DEFAULT 0,
    alert_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CHECK (parent_id IS NULL OR requested_parent_id IS NULL),
    CHECK (id IS DISTINCT FROM parent_id AND id IS DISTINCT FROM requested_parent_id)
);
CREATE INDEX recruitments_board_idx ON recruitments(type, game, mode_key, confirmed_at DESC) WHERE NOT closed;
CREATE INDEX recruitments_owner_idx ON recruitments(user_id, created_at DESC);
CREATE INDEX recruitments_parent_idx ON recruitments(parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX recruitments_requested_idx ON recruitments(requested_parent_id) WHERE requested_parent_id IS NOT NULL;
CREATE TABLE recruitment_impressions (
    recruitment_id UUID NOT NULL REFERENCES recruitments(id),
    viewer_id UUID NOT NULL REFERENCES users(id),
    day DATE NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (recruitment_id, viewer_id, day)
);
CREATE TABLE recruitment_events (
    id BIGSERIAL PRIMARY KEY,
    recruitment_id UUID NOT NULL REFERENCES recruitments(id),
    event VARCHAR(40) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX recruitment_events_funnel_idx ON recruitment_events(recruitment_id, event, occurred_at);
