-- 자동 매칭 파티의 id 칸 — 2026-09-27 소유자 결정(docs/11 D-42): matching 은 확정 뒤 SQS 를 보내지 않고 파티 HASH(qm:party:{partyId})를
-- Redis 에 남기며, 이 앱이 프런트의 요청(POST /api/v1/match-parties/{partyId}/room)을 받아 그 HASH 를 읽어 파티 · 방을 만든다.
--
-- 왜 칸이 필요한가 — matching 의 partyId 는 UUID 문자열이고 parties.id 는 bigint identity 다(2026-09-22 소유자 결정 — PK 는 전부 bigint identity).
-- 그래서 matching 의 id 를 PK 로 쓸 수 없고 따로 든다. "자동 매칭 파티의 id 를 어디에 둘지"(CLAUDE.md §3.3 · §7.2 (나))는 이 칸으로 닫혔다.
-- 자동 매칭 파티의 방(roomId)은 이 값(= matching 의 partyId)이다 — 방 키가 qm:room:{uuid}:host 꼴이 된다.
--
-- UNIQUE 가 멱등을 지킨다 — 파티원 전원이 MATCH_CONFIRMED 를 받고 동시에 이 앱을 부르므로 INSERT … ON CONFLICT (match_party_id) DO NOTHING 으로
-- 파티는 하나만 남는다(게시판 파티의 UNIQUE (post_id) 와 같은 방식이다).
-- CHECK 는 게시판 파티의 parties_board_has_post_check 와 짝이다 — MATCH 면 이 칸이 있고, 아니면 없다.
--
-- V1 은 고치지 않는다 — 이미 적용된 파일은 체크섬이 달라져 기동이 막힌다(V1 머리의 약속).
ALTER TABLE parties ADD COLUMN match_party_id varchar(36);
ALTER TABLE parties ADD CONSTRAINT parties_match_party_id_key UNIQUE (match_party_id);
ALTER TABLE parties ADD CONSTRAINT parties_match_has_party_id_check CHECK ((source = 'MATCH') = (match_party_id IS NOT NULL));
