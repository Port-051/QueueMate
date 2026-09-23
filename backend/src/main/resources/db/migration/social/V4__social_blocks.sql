-- social 스키마 — 차단 (CLAUDE.md §3.5 · contracts/platform-api.md "차단").
--
-- 이 테이블은 matching 이 이미 읽고 있다(matching 의 block/Block.java · docs/11 D-4) — id · blocker_id · blocked_id 의 이름과 자료형이
-- 그쪽과 맞아야 한다. 바꿀 때는 matching 과 같이 바꾼다.
--
-- ** 2026-09-22 소유자 결정 — 사용자 id 가 로그인 아이디(문자열)에서 사용자 번호(bigint)로 바뀌었다(account/V2 의 머리 주석). 그래서 blocker_id · blocked_id 가
--    varchar(20) 에서 bigint 가 됐다. matching 의 Block.java 는 아직 이 두 컬럼을 String 으로 읽는다 — 그쪽을 Long 으로 같이 바꿔야 한다(아직 안 바꿨다.
--    matching 폴더의 일이다). 바꾸기 전까지 matching 은 이 테이블을 읽다가 런타임에 깨진다.
--
-- 스키마별 DB 롤은 두지 않는다(2026-09-22 소유자 결정 — CLAUDE.md §3.5). 앱 하나가 롤 하나로 붙고, matching 도 별도 롤 없이 이 테이블을 읽는다.
-- 그래서 이 파일은 롤을 만들지도 GRANT 를 하지도 않는다 — 스키마 분리와 크로스 스키마 FK · JOIN 금지는 그대로다.

CREATE SCHEMA social;

-- 방향이 있는 한 줄이다 — blocker_id 가 blocked_id 를 차단했다. matching 은 양방향으로 조회한다.
-- account.users 로 FK 를 걸지 않는다 — 크로스 스키마 FK 금지다 (CLAUDE.md §3.5). 대상이 있는 사용자인지는 앱이 account 의 창구로 확인한다.
-- "같은 사람 두 번 차단 금지"는 UNIQUE 가 지킨다 — 앱은 조회하지 않고 INSERT 의 위반을 409 ALREADY_BLOCKED 로 옮긴다.
CREATE TABLE social.blocks (
    id         bigint GENERATED ALWAYS AS IDENTITY,
    blocker_id bigint      NOT NULL,
    blocked_id bigint      NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT blocks_pkey PRIMARY KEY (id),
    CONSTRAINT blocks_blocker_blocked_key UNIQUE (blocker_id, blocked_id),
    CONSTRAINT blocks_not_self CHECK (blocker_id <> blocked_id)
);

-- "나를 차단한 사람" 방향의 조회용이다(목록의 차단 거르기 · matching 의 양방향 조회). 반대 방향은 UNIQUE 의 인덱스가 받는다.
CREATE INDEX blocks_blocked_id_idx ON social.blocks (blocked_id);
