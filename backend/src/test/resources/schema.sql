-- 테스트용 H2 에만 만드는 blocks.
--
-- 운영 테이블은 app:platform 이 소유하고 그쪽 Flyway(V1__schema.sql)가 public 스키마에 만든다
-- (2026-09-26 에 social.blocks 에서 public.blocks 가 됐다 — docs/11 D-34). 이 파일은 그 테이블을
-- 흉내만 낸다 — 없으면 배정 경로가 차단 조회에서 통째로 죽는다.
-- 그러면 파티에 두 번째 사람이 들어가는 구간을 테스트가 아예 못 밟는다.
--
-- 컬럼은 Block 엔티티에 맞춘다. 두 칸은 운영과 같은 bigint(사용자 번호)다 — 타입이 어긋나면
-- 엔티티를 잘못 고쳐도 테스트가 못 잡는다. 채번 · UNIQUE · FK · created_at 은 저쪽 몫이라 여기서 걸지 않는다.
create table if not exists blocks (
    id         bigint not null primary key,
    blocker_id bigint not null,
    blocked_id bigint not null
);
