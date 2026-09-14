-- 테스트용 H2 에만 만드는 social.blocks.
--
-- 운영 스키마는 app:platform 이 소유하고 Flyway 가 만든다. 이 파일은 그 테이블을
-- 흉내만 낸다 — 없으면 H2 가 "Schema social not found" 로 막아 배정 경로가 통째로 죽는다.
-- 그러면 파티에 두 번째 사람이 들어가는 구간을 테스트가 아예 못 밟는다.
--
-- 컬럼은 Block 엔티티에 맞춘다. 채번과 UNIQUE 제약은 저쪽 몫이라 여기서 걸지 않는다.
create schema if not exists social;

create table if not exists social.blocks (
    id         bigint       not null primary key,
    blocker_id varchar(255) not null,
    blocked_id varchar(255) not null
);
