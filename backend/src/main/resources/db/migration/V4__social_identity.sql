-- 소셜 로그인. 한 사용자는 비밀번호와 여러 제공자 신원을 함께 가질 수 있다.

-- 소셜로만 가입한 사용자는 비밀번호가 없다. NOT NULL을 유지하면 빈 문자열이나
-- 무작위 해시를 넣게 되는데, 둘 다 비밀번호가 있는 계정과 구분되지 않아 위험하다.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- 제공자가 이메일을 주지 않거나 사용자가 이메일 제공에 동의하지 않을 수 있다.
-- UNIQUE 제약은 그대로 둔다. Postgres는 NULL을 서로 다른 값으로 보므로 충돌하지 않는다.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

CREATE TABLE user_identities (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(20)  NOT NULL,
    -- 제공자가 주는 식별자. 카카오는 숫자, 네이버는 문자열이다. 길이는 넉넉히 잡는다.
    provider_user_id VARCHAR(191) NOT NULL,
    -- 연결 시점의 이메일. 로그인 판정에 쓰지 않고 어떤 계정으로 붙었는지 추적용이다.
    email            VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT user_identities_provider_check CHECK (provider IN ('KAKAO', 'NAVER', 'DEV')),
    -- 같은 제공자 계정이 두 QueueMate 계정에 붙으면 어느 쪽으로 로그인될지 알 수 없다.
    CONSTRAINT user_identities_provider_unique UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_user_identities_user ON user_identities (user_id);
