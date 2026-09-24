-- 게시판 목록의 정렬이 "최신순" 하나, 그것도 id 내림차순이 됐다 (2026-09-24 소유자 결정 · CLAUDE.md §7.1 · contracts/platform-api.md "모집 글 · 목록 · 입장권").
--
-- 왜 — 옛 정렬은 (모집 중인가, created_at DESC, id) 였고 커서 페이지 나누기가 그 셋을 담았다. 그런데 "모집 중인가" 는 변하고,
-- 그것도 목록 조회 자신이 바꾼다(방이 사라진 글을 그 자리에서 만료로 옮겨 적는다). 1쪽에서 모집 중으로 나간 글이 그 사이 만료되면
-- 2쪽의 조건에 다시 걸려 같은 글이 두 번 보였다.
-- 커서가 요구하는 것은 ① 순서대로 커지고 ② 겹치지 않고 ③ 변하지 않는 키인데, id 가 identity 라 혼자 셋을 다 만족한다 —
-- 그래서 created_at 도 정렬에서 뺐다(같은 뜻의 값을 둘로 들 필요가 없고, 앱이 넣는 값과 DB 가 매기는 값이라 동시에 들어온 둘에서 어긋날 수 있다).
-- created_at 컬럼은 그대로 둔다 — 화면의 "몇 분 전" 이 그 값이다. 정렬과 커서에서만 안 쓴다.
--
-- 그래서 order by 가 id DESC 가 됐다. 게임으로 좁힌 뒤 그 순서로 이어 읽을 수 있는 인덱스라야 목록 조회가 따로 정렬하지 않는다
-- (커서의 id < ? 도 같은 인덱스를 탄다).
CREATE INDEX recruit_posts_game_id_idx ON party.recruit_posts (game, id DESC);

-- 옛 인덱스를 지운다 — 남겨 둘 쓸모가 없다.
--   ① order by 와 맞지 않는다(가운데가 status 이고 뒤가 created_at 이라 id 순으로 이어 읽을 수 없다).
--   ② status 를 거르는 데도 못 쓴다 — 목록의 조건은 "모집 중이거나 확정 · 만료된 지 얼마 안 됐다" 라는 세 컬럼에 걸친 OR 이고, 등호가 아니다.
--   ③ game 하나로 좁히는 몫은 위 새 인덱스의 앞 컬럼이 그대로 한다.
-- status 를 등호로 보는 곳은 "모집 중인 글은 한 사람에 하나" 뿐이고 그것은 부분 UNIQUE 인덱스(recruit_posts_one_recruiting_per_host)가 맡는다.
-- 상태를 바꾸는 UPDATE 와 글 한 건 조회는 전부 id 기준이라 PK 를 탄다.
DROP INDEX party.recruit_posts_game_status_created_idx;
