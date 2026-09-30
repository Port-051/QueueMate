-- 구글 로그인 — 2026-09-29 소유자 결정(contracts/platform-api.md P-33). 소셜 제공자에 GOOGLE 을 더한다.
--
-- ① provider 의 CHECK 에 'GOOGLE' 을 더한다. 제약 이름은 V1 의 것(social_identities_provider_check) 그대로 다시 만든다 —
--    마이그레이션 테스트가 그 이름으로 거절을 본다. 한 문장이라 CHECK 가 없는 순간이 밖에 보이지 않는다.
-- ② provider_user_id 를 varchar(64) → varchar(255) 로 넓힌다 — 구글의 sub 는 "255자까지의 ASCII" 라고만 약속돼 있다
--    (카카오는 숫자 · 디스코드는 snowflake 라 64 로 충분했다). 이 칸은 PK 의 일부지만 길이만 늘리는 것이라 값은 그대로다.
--
-- V1 · V2 는 고치지 않는다 — 이미 적용된 파일은 체크섬이 달라져 기동이 막힌다(V1 머리의 약속).
ALTER TABLE social_identities
    DROP CONSTRAINT social_identities_provider_check,
    ADD CONSTRAINT social_identities_provider_check CHECK (provider IN ('KAKAO', 'DISCORD', 'GOOGLE'));

ALTER TABLE social_identities ALTER COLUMN provider_user_id TYPE varchar(255);
