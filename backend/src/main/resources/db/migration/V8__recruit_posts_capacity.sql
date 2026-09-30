-- 게시판 방의 정원을 글에 담는다 — 2026-09-30 소유자 결정(contracts/platform-api.md P-41).
-- 그 전에는 모드와 상관없이 모든 게시판 방의 정원이 5였다(RoomMemberService 의 상수). 솔로 랭크(2인)의 글에 세 번째 사람이 직접 들어왔다("3/5").
-- 이제 게시판 방의 정원 = 그 모드의 인원(gameconfig 모드 HASH 의 targetPartySize)이다 — 글을 쓸 때(POST /api/v1/posts) · 모드를 고칠 때(PATCH) 앱이 읽어 적는다.
--
-- capacity : 방장을 포함해 그 방에 있을 수 있는 최대 인원. 입장 스크립트(enter-room.lua)가 이 값으로 만석을 가르고, 응답의 capacity · full 이 이것이다.
--            값의 원본은 gameconfig(Redis)라 여기서 값의 목록을 걸지 않는다 — 범위(2 ~ 5)만 건다. 5 는 방 정원의 상한이다(docs/11 D-11 10번).
--            gameconfig 를 못 읽었거나 안 심겼으면 앱이 5 를 적는다(글 쓰기의 fail-open — P-16 과 같은 쪽).
-- 그 전에 쓴 글은 NULL 로 남고 앱이 5 로 읽는다(옮겨 채울 값이 DB 에 없다 — 모드의 인원은 Redis 에 있다).
--
-- V1 ~ V7 은 고치지 않는다 — 이미 적용된 파일은 체크섬이 달라져 기동이 막힌다(V1 머리의 약속).
ALTER TABLE recruit_posts ADD COLUMN capacity smallint;
ALTER TABLE recruit_posts ADD CONSTRAINT recruit_posts_capacity_check CHECK (capacity IS NULL OR capacity BETWEEN 2 AND 5);
