-- 로컬 개발용 H2 에만 만드는 blocks — 운영 테이블의 흉내다.
--
-- 이 파일은 **임베디드(H2 인메모리) 데이터소스일 때만** 실행된다. Spring Boot 의
-- spring.sql.init.mode 기본값이 embedded 이고 application.yaml 에도 그렇게 못 박아 두었다.
-- DB_URL 로 Postgres 에 붙으면 실행되지 않는다 — 그래야 한다. 운영 테이블은 app:platform 이
-- 소유하고 그쪽 Flyway(../platform/backend/src/main/resources/db/migration/V1__schema.sql)가
-- public 스키마에 만든다(2026-09-26 에 social.blocks 에서 public.blocks 가 됐다 — docs/11 D-34).
-- 이 앱은 Flyway 를 두지 않고 그 테이블을 Postgres 에 만들지도 않는다.
--
-- 왜 main 에 두나. 기본 실행(H2 · ddl-auto: none)에 이 테이블이 없으면 배정 경로가 차단 조회
-- (BlockRepository)에서 죽는다. 배정은 @Async 안이라 요청은 201 로 나가고 배정만 조용히 실패해
-- 파티가 하나도 안 생긴다. 예전에는 테스트 리소스에만 있어 bootRun 이 그 상태였다.
--
-- 매칭 상태가 아니다. 이 앱이 DB 를 치는 자리는 INV-6 의 blocks 조회 하나뿐이고, 진행 중인
-- 매칭 상태는 Redis 가 원본이다 — 여기에 다른 테이블을 더하지 마라 (CLAUDE.md §3).
--
-- 컬럼은 Block 엔티티에 맞춘다. 두 칸은 운영과 같은 bigint(사용자 번호)다 — 타입이 어긋나면
-- 엔티티를 잘못 고쳐도 로컬에서 못 잡는다. 채번 · UNIQUE · FK · created_at 은 저쪽 몫이라 여기서 걸지 않는다.
-- 테스트(ConcurrencyTestSupport)는 ddl-auto=create-drop 을 겹쳐 쓰는데 if not exists 라 충돌하지 않는다.
create table if not exists blocks (
    id         bigint not null primary key,
    blocker_id bigint not null,
    blocked_id bigint not null
);
