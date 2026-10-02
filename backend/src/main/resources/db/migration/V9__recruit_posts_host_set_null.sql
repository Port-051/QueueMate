-- 회원 탈퇴가 확정된 파티 기록을 지우지 않게 한다 — 2026-10-02 소유자 결정(contracts/platform-api.md P-48).
-- 탈퇴(DELETE /api/v1/auth/account)는 users 의 줄을 지우고 딸린 줄은 FK 의 ON DELETE CASCADE 가 지운다. 그런데 V1 의 recruit_posts.host_id 가
-- CASCADE 라 방장이 탈퇴하면 글이 지워지고, 글의 CASCADE(parties.post_id · recruit_post_positions.post_id)를 타고 그 글의 파티와 남의 파티원 줄까지
-- 지워졌다. 소유자 결정 — "확정된 파티 기록은 남긴다". 그래서 방장의 칸만 비우고 글 · 파티 · 다른 파티원은 남긴다.
--
-- ① host_id 를 NULL 허용으로 바꾼다 — 방장이 탈퇴한 확정된 글은 작성자 칸만 빈 채 제목 · 설명 · 파티 기록이 그대로 남는다.
-- ② FK 를 ON DELETE SET NULL 로 다시 건다. 이름은 V1 의 것(recruit_posts_host_id_fkey) 그대로다 — 앱이 그 이름으로 "그런 사용자가 없다" 를
--    가른다(common.error.ConstraintViolations · party.service.PostStore#HOST_FKEY — 글 쓰기의 401). 한 문장 안에서 지우고 다시 거니 FK 가 없는 순간이 밖에 보이지 않는다.
-- ③ 방장이 비는 글은 확정된 글뿐이다 — CHECK 로 건다(recruit_posts_host_id_check). 모집 중 · 만료된 글은 탈퇴가 users 를 지우기 전에
--    같은 트랜잭션에서 지운다(party.service.PostStore#deleteUnconfirmedOf). 그 순서가 어긋나 비확정 글이 남은 채 users 를 지우면 SET NULL 이 이 CHECK 에 걸려
--    탈퇴가 통째로 되돌려진다 — 방장 없는 모집 중인 글(입장 · 확정 · 지우기의 주인이 없는 글)이 생기지 않는다.
--
-- 모집 중인 글의 부분 UNIQUE 인덱스(recruit_posts_one_recruiting_per_host)는 그대로다 — NULL 은 확정된 글에만 있어 그 인덱스에 들지 않는다.
-- 지금 있는 줄은 전부 host_id 가 있어 CHECK 를 거는 데 걸리는 줄이 없다.
--
-- V1 ~ V8 은 고치지 않는다 — 이미 적용된 파일은 체크섬이 달라져 기동이 막힌다(V1 머리의 약속).
-- V1 의 recruit_posts · parties 머리 주석이 "방장을 지우면 글 · 파티가 딸려 지워진다" 고 적은 것은 그 때의 기록으로 남는다.
ALTER TABLE recruit_posts ALTER COLUMN host_id DROP NOT NULL;

ALTER TABLE recruit_posts
    DROP CONSTRAINT recruit_posts_host_id_fkey,
    ADD CONSTRAINT recruit_posts_host_id_fkey FOREIGN KEY (host_id) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT recruit_posts_host_id_check CHECK (host_id IS NOT NULL OR status = 'CONFIRMED');
