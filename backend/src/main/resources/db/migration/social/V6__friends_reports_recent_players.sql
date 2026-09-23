-- social 스키마 — 친구 요청 · 친구 · 신고 · 최근 함께한 사람 (CLAUDE.md §3.5 · contracts/platform-api.md "친구 · 신고 · 최근 함께한 사람").
--
-- 제약과 인덱스에 전부 이름을 붙였다 — 앱이 위반을 에러 코드로 옮길 때 그 이름으로 가른다
-- (friend_requests_one_pending → FRIEND_REQUEST_ALREADY_SENT). 이름을 바꾸면 앱의 상수(FriendService)도 같이 바꾼다.
-- 사용자 번호는 account.users.id 의 것이고 last_party_id · context_id 는 party 의 것이지만 FK 를 걸지 않는다 — 크로스 스키마 FK 금지다 (CLAUDE.md §3.5).
-- 사용자 번호 · 글 · 파티의 id 는 전부 bigint 다 (2026-09-22 소유자 결정 — account/V2 의 머리 주석).
--
-- 롤 · GRANT 는 없다 — 스키마별 DB 롤은 두지 않는다 (2026-09-22 소유자 결정 · CLAUDE.md §3.5. V4 도 같다).

-- 친구 요청. 방향이 있는 한 줄이다 — requester_id 가 receiver_id 에게 보냈다. 처리된 줄은 지우지 않고 status 로 닫는다.
-- responded_at 은 "대기 중이 아니게 된 시각"이다 — 수락 · 거절 · 거두기 어느 쪽이든 적는다. PENDING 과 NULL 은 늘 같이 간다.
CREATE TABLE social.friend_requests (
    id           bigint GENERATED ALWAYS AS IDENTITY,
    requester_id bigint      NOT NULL,
    receiver_id  bigint      NOT NULL,
    status       varchar(10) NOT NULL,
    created_at   timestamptz NOT NULL,
    responded_at timestamptz,
    CONSTRAINT friend_requests_pkey PRIMARY KEY (id),
    CONSTRAINT friend_requests_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED')),
    CONSTRAINT friend_requests_not_self CHECK (requester_id <> receiver_id),
    CONSTRAINT friend_requests_responded_at_check CHECK ((status = 'PENDING') = (responded_at IS NULL))
);

-- "같은 방향의 대기 중 요청은 하나"는 이 인덱스가 지킨다 — 앱은 조회하지 않고 INSERT 의 위반을 409 FRIEND_REQUEST_ALREADY_SENT 로 옮긴다.
-- 처리된 줄(거절 · 거두기 · 수락)은 인덱스에 없어서 그 뒤에 다시 요청할 수 있다.
-- 반대 방향은 다른 줄이다 — 서로 동시에 보내면 양방향 PENDING 이 둘 생길 수 있고, 수락할 때 앱이 둘 다 닫는다.
-- "내가 보낸 대기 중 요청" 조회도 이 인덱스가 받는다(requester_id 가 앞이다).
CREATE UNIQUE INDEX friend_requests_one_pending ON social.friend_requests (requester_id, receiver_id) WHERE status = 'PENDING';

-- "내가 받은 대기 중 요청" 조회용이다.
CREATE INDEX friend_requests_receiver_pending_idx ON social.friend_requests (receiver_id) WHERE status = 'PENDING';

-- 친구. 방향이 없는 한 줄이다 — 두 사용자 번호를 (작은 쪽, 큰 쪽)으로 정규화해 넣고 PK 가 중복을 막는다.
-- "작은 쪽"은 앱(자바의 Long 비교)이 고른다 — 숫자라 DB 의 비교와 늘 같다(문자열이던 때의 COLLATE "C" 는 필요 없어졌다).
CREATE TABLE social.friendships (
    user_low_id  bigint      NOT NULL,
    user_high_id bigint      NOT NULL,
    created_at   timestamptz NOT NULL,
    CONSTRAINT friendships_pkey PRIMARY KEY (user_low_id, user_high_id),
    CONSTRAINT friendships_ordered CHECK (user_low_id < user_high_id)
);

-- 친구 목록은 "내가 어느 칸에 있든" 찾는다 — 작은 쪽은 PK 의 인덱스가, 큰 쪽은 이 인덱스가 받는다.
CREATE INDEX friendships_user_high_id_idx ON social.friendships (user_high_id);

-- 신고. 접수만 받는다 — 처리 화면 · 제재는 없다. 같은 사람을 여러 번 신고할 수 있어 UNIQUE 가 없다.
-- context_id 는 글(모집 글)의 id 다. 있는지 확인하지 않는다(FK 도 없다).
CREATE TABLE social.reports (
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
    CONSTRAINT reports_not_self CHECK (reporter_id <> target_user_id)
);

-- 최근 함께한 사람. 방향이 있는 한 줄이다 — user_id 의 목록에 other_user_id 가 있다. 읽는 쪽은 자기 user_id 의 줄만 본다.
-- 한 사람에 한 줄이다 — PK 가 (user_id, other_user_id) 라서 같은 사람과 또 해도 줄이 늘지 않는다(채우는 쪽이 마지막 것으로 덮는다).
-- 채우는 것은 PartyClosed.fifo 의 소비인데 SQS 배선이 미정이라 아직 아무도 채우지 않는다 (CLAUDE.md §3.4 · §7).
CREATE TABLE social.recent_players (
    user_id        bigint      NOT NULL,
    other_user_id  bigint      NOT NULL,
    last_party_id  bigint      NOT NULL,
    last_played_at timestamptz NOT NULL,
    CONSTRAINT recent_players_pkey PRIMARY KEY (user_id, other_user_id),
    CONSTRAINT recent_players_not_self CHECK (user_id <> other_user_id)
);

-- 목록은 "내 것을 최근순으로 50명"이다.
CREATE INDEX recent_players_user_recent_idx ON social.recent_players (user_id, last_played_at DESC);
