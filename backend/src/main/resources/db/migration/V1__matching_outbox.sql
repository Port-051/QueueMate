-- matching_outbox — 확정된 제안(ProposalConfirmed)을 SQS 로 보내기 전에 먼저 적어 두는 표 (transactional outbox).
--
-- 왜 있나. 확정 순간에 SQS 를 곧장 부르면 "확정했다"와 "알렸다"가 따로 성공·실패해 어긋난다 — 호출이 실패하면
-- 파티는 확정됐는데 platform 은 영영 모르고, 응답을 잃고 재시도하면 두 번 간다. 그래서 확정과 같은 자리에서
-- 이 표에 한 줄을 넣고, 배달원(스케줄러)이 sent_at 이 비어 있는 줄을 꺼내 보낸 뒤 표시한다.
-- "적혔으면 언젠가는 반드시 전달된다"가 이 표가 주는 보장이다 (docs/11 #18 · #21).
--
-- 소유. 이 앱의 것이다 — 이 앱이 DB 에 쓰는 유일한 표이고, 이 Flyway 가 관리하는 표도 이것뿐이다.
-- blocks 는 platform 의 Flyway 것이라 여기서 만들지 않는다 (CLAUDE.md §3 · D-34). 진행 중인 매칭 상태는
-- 여전히 Redis 가 원본이다 — 이 표는 "확정된 것"만 안다 (docs/11 #27 — PostgreSQL 은 확정된 것만 안다).
--
-- 한 줄 = 이벤트 하나. 같은 제안이 두 번 적히지 않게 (event_type, aggregate_id) 가 UNIQUE 다 —
-- INSERT 성공 뒤 앱이 죽어 재시도가 같은 줄을 또 넣으려 하면 위반이 나고, 부르는 쪽은 그것을 "이미 됐음"으로 읽는다.
--
-- 이식성. Postgres 가 운영, H2(MODE=PostgreSQL) 가 로컬·테스트다. 둘 다 받는 문법만 쓴다 —
-- 부분 인덱스(WHERE sent_at IS NULL)는 H2 가 없어 (sent_at, id) 일반 인덱스로 대신한다.
-- payload 는 jsonb 가 아니라 text 다 — 이 앱은 그 안을 질의하지 않고 그대로 실어 보내기만 한다.
CREATE TABLE matching_outbox (
    id           bigint                   GENERATED ALWAYS AS IDENTITY,
    event_type   varchar(64)              NOT NULL,   -- OutboxEventType 의 이름 ('PROPOSAL_CONFIRMED'). 계약 이름 ProposalConfirmed 는 그 enum 이 준다
    aggregate_id varchar(64)              NOT NULL,   -- partyId (= proposalId). FIFO 면 MessageGroupId 로도 쓴다
    payload      text                     NOT NULL,   -- JSON 문자열. 받는 쪽(platform)과의 계약은 contracts/events.md
    created_at   timestamp with time zone NOT NULL DEFAULT now(),
    sent_at      timestamp with time zone,            -- 배달원이 SQS 에 보낸 시각. NULL 이면 아직 안 보냈다
    attempts     integer                  NOT NULL DEFAULT 0,   -- 보내기를 시도한 횟수 (실패 진단용)
    last_error   text,                                -- 마지막 실패 이유. 성공하면 비운다
    CONSTRAINT matching_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT matching_outbox_event_key UNIQUE (event_type, aggregate_id)
);

-- 배달원의 질의 — WHERE sent_at IS NULL ORDER BY id LIMIT n — 가 타는 인덱스
CREATE INDEX matching_outbox_unsent_idx ON matching_outbox (sent_at, id);
