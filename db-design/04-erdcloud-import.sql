-- =====================================================================
--  ERDCloud 임포트용 DDL (MySQL 문법)
--  * ERDCloud 파서가 못 읽는 PostgreSQL 전용 문법을 걷어낸 버전입니다.
--    - ENUM 타입      -> VARCHAR
--    - tstzrange      -> DATETIME 2개 (busy_start / busy_end)
--    - EXCLUDE 제약   -> 주석으로만 표기
--    - 부분 유니크    -> 주석으로만 표기
--    - jsonb          -> JSON
--  * 실제 구축에는 03-schema.postgres.sql 을 사용하세요.
--  사용법: ERDCloud > 새 ERD > Import > DDL 붙여넣기
-- =====================================================================

CREATE TABLE game (
  id        SMALLINT     NOT NULL AUTO_INCREMENT COMMENT '게임 ID',
  code      VARCHAR(20)  NOT NULL COMMENT 'LOL / VALORANT 등',
  name      VARCHAR(50)  NOT NULL COMMENT '게임 이름',
  is_active TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uq_game_code (code)
) COMMENT '게임 마스터 - 멀티게임 확장 탈출구';

CREATE TABLE app_user (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  discord_id         VARCHAR(32)  NOT NULL COMMENT 'OAuth2 신원 1.1.1',
  discord_username   VARCHAR(64)  NOT NULL COMMENT '확정 후 공개 1.7.4',
  nickname           VARCHAR(32)  NOT NULL COMMENT '초기값 Discord 표시명 1.2.1',
  voice_mode         VARCHAR(16)  NOT NULL COMMENT 'REQUIRED/AVAILABLE/NONE 1.2.2',
  purpose            VARCHAR(16)  NOT NULL COMMENT 'CASUAL/WIN/LEARN/SKILLED 1.2.3',
  mood               VARCHAR(16)  NOT NULL COMMENT '표시 전용 1.2.5',
  primary_position   VARCHAR(16)  NULL COMMENT 'TOP/JUNGLE/MID/BOT/SUPPORT 1.2.6',
  discord_dm_enabled TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '기본 꺼짐 5.4.2',
  created_at         DATETIME     NOT NULL,
  updated_at         DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_discord (discord_id)
) COMMENT '서비스 계정';

CREATE TABLE user_sub_position (
  user_id  BIGINT      NOT NULL,
  position VARCHAR(16) NOT NULL COMMENT '기본 부 포지션 1.2.7',
  PRIMARY KEY (user_id, position),
  CONSTRAINT fk_usp_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) COMMENT '프로필 부 포지션 (다중값)';

CREATE TABLE riot_account (
  user_id           BIGINT      NOT NULL,
  game_name         VARCHAR(32) NOT NULL COMMENT 'Riot ID 앞부분 1.3.1',
  tag_line          VARCHAR(16) NOT NULL COMMENT 'Riot ID 태그',
  puuid             VARCHAR(80) NULL COMMENT 'account-v1 결과 1.3.3',
  solo_tier         VARCHAR(16) NULL COMMENT '솔로듀오 랭크 1.3.5',
  solo_tier_order   SMALLINT    NULL COMMENT '비교용 정수 2.2.4',
  solo_division     VARCHAR(4)  NULL,
  solo_lp           INT         NULL,
  solo_wins         INT         NULL,
  solo_losses       INT         NULL,
  solo_in_placement TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '배치 진행 중 1.3.7',
  flex_tier         VARCHAR(16) NULL COMMENT '자유 랭크 1.3.6',
  flex_tier_order   SMALLINT    NULL,
  flex_division     VARCHAR(4)  NULL,
  flex_lp           INT         NULL,
  flex_wins         INT         NULL,
  flex_losses       INT         NULL,
  flex_in_placement TINYINT(1)  NOT NULL DEFAULT 0,
  link_status       VARCHAR(16) NOT NULL COMMENT 'OK/NEEDS_CHECK/FAILED 1.3.8',
  refreshed_at      DATETIME    NULL COMMENT '마지막 갱신 1.3.9',
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_riot_puuid (puuid),
  UNIQUE KEY uq_riot_id (game_name, tag_line),
  CONSTRAINT fk_riot_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) COMMENT 'Riot 계정 연동 1:1';

CREATE TABLE push_subscription (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  user_id    BIGINT       NOT NULL,
  endpoint   VARCHAR(512) NOT NULL COMMENT 'Web Push 1.9.1',
  p256dh     VARCHAR(255) NOT NULL,
  auth       VARCHAR(255) NOT NULL,
  created_at DATETIME     NOT NULL,
  revoked_at DATETIME     NULL COMMENT '410 Gone 감지 4.2.3',
  PRIMARY KEY (id),
  UNIQUE KEY uq_push_endpoint (endpoint),
  CONSTRAINT fk_push_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) COMMENT 'Web Push 구독';

CREATE TABLE match_request (
  id                     BIGINT      NOT NULL AUTO_INCREMENT,
  user_id                BIGINT      NOT NULL,
  game_id                SMALLINT    NOT NULL,
  kind                   VARCHAR(16) NOT NULL COMMENT 'IMMEDIATE/SCHEDULED 1.4.1',
  queue                  VARCHAR(16) NOT NULL COMMENT 'SOLO_DUO/FLEX/NORMAL 1.4.6',
  target_size            SMALLINT    NOT NULL COMMENT '큐별 허용값만 1.4.7',
  status                 VARCHAR(16) NOT NULL COMMENT 'RESERVED/QUEUED/PROPOSED/REFILLING/CONFIRMED/CANCELED/FAILED 1.5',
  requested_at           DATETIME    NOT NULL COMMENT 'FCFS 1차 정렬키 2.4.1',
  play_minutes           INT         NOT NULL COMMENT '예상 플레이시간 1.4.16',
  max_wait_minutes       INT         NULL COMMENT '즉시 전용 1.4.2',
  play_date              DATE        NULL COMMENT '예약 전용 1.4.13',
  window_start           DATETIME    NULL COMMENT '시작 가능 범위 1.4.15',
  window_end             DATETIME    NULL,
  tier_snapshot          VARCHAR(16) NULL COMMENT '요청 시점 고정 1.4.19',
  tier_snapshot_order    SMALLINT    NULL COMMENT '양방향 판정용 2.2.4',
  tier_snapshot_at       DATETIME    NULL COMMENT '신선도 검증 1.3.11',
  allowed_tier_min_order SMALLINT    NOT NULL COMMENT '허용 티어 범위 1.4.10',
  allowed_tier_max_order SMALLINT    NOT NULL,
  primary_position       VARCHAR(16) NOT NULL COMMENT '2.5.1',
  purpose                VARCHAR(16) NOT NULL COMMENT '2.2.8',
  voice_mode             VARCHAR(16) NOT NULL COMMENT '2.3',
  conditions             JSON        NULL COMMENT '게임 확장 여지',
  version                INT         NOT NULL DEFAULT 0 COMMENT '낙관적 락 1.5.9',
  created_at             DATETIME    NOT NULL,
  updated_at             DATETIME    NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_req_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_req_game FOREIGN KEY (game_id) REFERENCES game (id)
) COMMENT 'INV-1 진행중 요청 1건은 부분유니크 / INV-9 FLEX 4인차단은 CHECK';

CREATE TABLE request_sub_position (
  request_id BIGINT      NOT NULL,
  position   VARCHAR(16) NOT NULL COMMENT '상관없음 전개 후 1.4.18',
  PRIMARY KEY (request_id, position),
  CONSTRAINT fk_rsp_req FOREIGN KEY (request_id) REFERENCES match_request (id)
) COMMENT '요청별 부 포지션';

CREATE TABLE user_busy_interval (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  busy_start  DATETIME    NOT NULL COMMENT 'PG에서는 tstzrange 단일 컬럼',
  busy_end    DATETIME    NOT NULL,
  source_type VARCHAR(16) NOT NULL COMMENT 'REQUEST/PARTY',
  request_id  BIGINT      NULL,
  party_id    BIGINT      NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_busy_user FOREIGN KEY (user_id) REFERENCES app_user (id),
  CONSTRAINT fk_busy_req  FOREIGN KEY (request_id) REFERENCES match_request (id)
) COMMENT 'INV-2 시간 겹침 금지 - PG EXCLUDE USING gist 로 강제 1.4.17';

CREATE TABLE match_offer (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  game_id     SMALLINT    NOT NULL,
  queue       VARCHAR(16) NOT NULL COMMENT '합의 조건 2.7.5',
  target_size SMALLINT    NOT NULL,
  purpose     VARCHAR(16) NOT NULL,
  voice_party TINYINT(1)  NOT NULL COMMENT '2.3.5 / 2.3.6',
  combo_hash  VARCHAR(64) NOT NULL COMMENT '중복 제안 방지',
  status      VARCHAR(16) NOT NULL COMMENT 'PENDING/REFILLING/CONFIRMED/FAILED',
  created_at  DATETIME    NOT NULL,
  respond_by  DATETIME    NOT NULL COMMENT '최초 응답 만료 1.6.3',
  refill_by   DATETIME    NOT NULL COMMENT '전체 충원 제한 1.6.21',
  closed_at   DATETIME    NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_offer_game FOREIGN KEY (game_id) REFERENCES game (id)
) COMMENT '즉시 매칭 파티 제안';

CREATE TABLE offer_seat (
  id                   BIGINT      NOT NULL AUTO_INCREMENT,
  offer_id             BIGINT      NOT NULL,
  position             VARCHAR(16) NOT NULL COMMENT '좌석은 포지션 단위 1.6.14',
  status               VARCHAR(16) NOT NULL COMMENT 'OPEN/PENDING/FILLED',
  filled_by_request_id BIGINT      NULL COMMENT '수락 확정자 2.8.17',
  opened_at            DATETIME    NOT NULL,
  closed_at            DATETIME    NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_seat_position (offer_id, position),
  UNIQUE KEY uq_seat_filled (filled_by_request_id),
  CONSTRAINT fk_seat_offer FOREIGN KEY (offer_id) REFERENCES match_offer (id),
  CONSTRAINT fk_seat_req   FOREIGN KEY (filled_by_request_id) REFERENCES match_request (id)
) COMMENT '제안 좌석 - 거절시 OPEN 으로 재개방 2.8.18';

CREATE TABLE offer_participant (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  offer_id     BIGINT      NOT NULL,
  seat_id      BIGINT      NOT NULL,
  request_id   BIGINT      NOT NULL,
  user_id      BIGINT      NOT NULL,
  round        SMALLINT    NOT NULL DEFAULT 1 COMMENT '충원 회차 2.8.19',
  response     VARCHAR(20) NOT NULL COMMENT 'PENDING/ACCEPTED_LOCKED/DECLINED/TIMED_OUT 1.6.11',
  offered_at   DATETIME    NOT NULL,
  expires_at   DATETIME    NOT NULL COMMENT '1.6.8',
  responded_at DATETIME    NULL COMMENT '조건부 전이로 멱등 1.6.9',
  PRIMARY KEY (id),
  UNIQUE KEY uq_seat_round (seat_id, round),
  UNIQUE KEY uq_offer_request (offer_id, request_id),
  CONSTRAINT fk_op_offer FOREIGN KEY (offer_id) REFERENCES match_offer (id),
  CONSTRAINT fk_op_seat  FOREIGN KEY (seat_id) REFERENCES offer_seat (id),
  CONSTRAINT fk_op_req   FOREIGN KEY (request_id) REFERENCES match_request (id),
  CONSTRAINT fk_op_user  FOREIGN KEY (user_id) REFERENCES app_user (id)
) COMMENT 'INV-7 좌석당 PENDING 1명은 부분유니크 / INV-8 재등장 금지 2.8.4';

CREATE TABLE party (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  game_id      SMALLINT    NOT NULL,
  offer_id     BIGINT      NULL COMMENT 'INV-6 / 예약은 NULL',
  queue        VARCHAR(16) NOT NULL COMMENT '합의 스냅샷 1.7.6',
  target_size  SMALLINT    NOT NULL,
  purpose      VARCHAR(16) NOT NULL,
  voice_party  TINYINT(1)  NOT NULL COMMENT '1.7.5',
  start_at     DATETIME    NOT NULL COMMENT '1.7.1 / 3.3.4',
  end_at       DATETIME    NOT NULL COMMENT '3.3.5',
  confirmed_at DATETIME    NOT NULL COMMENT '1.6.25',
  PRIMARY KEY (id),
  UNIQUE KEY uq_party_offer (offer_id),
  CONSTRAINT fk_party_game  FOREIGN KEY (game_id) REFERENCES game (id),
  CONSTRAINT fk_party_offer FOREIGN KEY (offer_id) REFERENCES match_offer (id)
) COMMENT '확정 파티 - 확정 후 취소/해체/구성원 변경 없음 1.7.13~1.7.16';

CREATE TABLE party_member (
  request_id  BIGINT      NOT NULL COMMENT 'PK 자체가 INV-3',
  party_id    BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  position    VARCHAR(16) NOT NULL COMMENT '2.5.8',
  ready_state VARCHAR(16) NOT NULL COMMENT 'NONE/READY/LATE 1.7.7 1.7.8',
  ready_at    DATETIME    NULL,
  joined_at   DATETIME    NOT NULL,
  PRIMARY KEY (request_id),
  UNIQUE KEY uq_pm_position (party_id, position),
  UNIQUE KEY uq_pm_user (party_id, user_id),
  CONSTRAINT fk_pm_req   FOREIGN KEY (request_id) REFERENCES match_request (id),
  CONSTRAINT fk_pm_party FOREIGN KEY (party_id) REFERENCES party (id),
  CONSTRAINT fk_pm_user  FOREIGN KEY (user_id) REFERENCES app_user (id)
) COMMENT 'INV-3 요청당 파티 1개 / INV-4 파티내 포지션 중복 금지';

CREATE TABLE party_discord_channel (
  party_id     BIGINT      NOT NULL COMMENT 'PK 자체가 명령 멱등키 5.6.7',
  guild_id     VARCHAR(32) NULL COMMENT '5.1.5',
  category_id  VARCHAR(32) NULL,
  channel_id   VARCHAR(32) NULL COMMENT '5.2.5',
  status       VARCHAR(16) NOT NULL COMMENT 'REQUESTED/CREATED/SKIPPED/FAILED/DELETED 5.2.6',
  fail_reason  VARCHAR(64) NULL COMMENT '5.6.1~5.6.4',
  created_at   DATETIME    NULL,
  delete_after DATETIME    NOT NULL COMMENT 'end_at + 24h 5.5.1',
  deleted_at   DATETIME    NULL COMMENT '5.5.5',
  PRIMARY KEY (party_id),
  UNIQUE KEY uq_channel_id (channel_id),
  CONSTRAINT fk_pdc_party FOREIGN KEY (party_id) REFERENCES party (id)
) COMMENT 'INV-11 파티당 채널 1개';

CREATE TABLE party_discord_member (
  party_id    BIGINT      NOT NULL COMMENT 'FK는 party - 채널 실패해도 기록 유지',
  user_id     BIGINT      NOT NULL,
  status      VARCHAR(16) NOT NULL COMMENT 'PENDING/GRANTED/NOT_JOINED/FAILED 5.3.4',
  granted_at  DATETIME    NULL,
  fail_reason VARCHAR(64) NULL COMMENT '5.6.5',
  updated_at  DATETIME    NOT NULL,
  PRIMARY KEY (party_id, user_id),
  CONSTRAINT fk_pdm_party FOREIGN KEY (party_id) REFERENCES party (id),
  CONSTRAINT fk_pdm_user  FOREIGN KEY (user_id) REFERENCES app_user (id)
) COMMENT '5.6.8 중복 권한 설정 방지';

CREATE TABLE outbox_event (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  event_id     CHAR(36)     NOT NULL COMMENT 'INV-5 중복 발행 금지 1.9.9',
  topic        VARCHAR(16)  NOT NULL COMMENT 'NOTIFICATION/DISCORD',
  event_type   VARCHAR(40)  NOT NULL COMMENT '4.1.2',
  party_id     BIGINT       NULL,
  user_id      BIGINT       NULL,
  payload      JSON         NOT NULL,
  available_at DATETIME     NOT NULL COMMENT '리마인더 지연 4.3.11~13',
  published_at DATETIME     NULL,
  attempts     SMALLINT     NOT NULL DEFAULT 0 COMMENT '4.4.2',
  last_error   VARCHAR(255) NULL,
  created_at   DATETIME     NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_outbox_event (event_id),
  CONSTRAINT fk_outbox_party FOREIGN KEY (party_id) REFERENCES party (id),
  CONSTRAINT fk_outbox_user  FOREIGN KEY (user_id) REFERENCES app_user (id)
) COMMENT 'Outbox - 확정 트랜잭션과 알림 발행의 원자성 1.9';

CREATE TABLE push_delivery (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  event_id        CHAR(36)     NOT NULL,
  subscription_id BIGINT       NOT NULL,
  status          VARCHAR(24)  NOT NULL COMMENT 'SENT/FAILED/EXPIRED_SUBSCRIPTION',
  http_status     SMALLINT     NULL COMMENT '4.2.5',
  error           VARCHAR(255) NULL,
  sent_at         DATETIME     NOT NULL COMMENT '4.2.4',
  PRIMARY KEY (id),
  UNIQUE KEY uq_delivery (event_id, subscription_id),
  CONSTRAINT fk_pd_event FOREIGN KEY (event_id) REFERENCES outbox_event (event_id),
  CONSTRAINT fk_pd_sub   FOREIGN KEY (subscription_id) REFERENCES push_subscription (id)
) COMMENT 'INV-12 같은 이벤트를 같은 구독에 1회만 4.4.1';

CREATE TABLE reservation_batch_run (
  play_date       DATE         NOT NULL COMMENT 'INV-10 하루 1회 3.1.7',
  started_at      DATETIME     NOT NULL,
  cutoff_at       DATETIME     NOT NULL COMMENT '처리 경계 3.1.3',
  finished_at     DATETIME     NULL,
  processed_count INT          NOT NULL DEFAULT 0 COMMENT '3.5.6',
  party_count     INT          NOT NULL DEFAULT 0,
  failed_count    INT          NOT NULL DEFAULT 0,
  error           VARCHAR(255) NULL,
  PRIMARY KEY (play_date)
) COMMENT '예약 배치 실행 기록';
