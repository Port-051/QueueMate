-- =====================================================================
--  [2026-09-19 개정 안내] 이 파일은 옛 설계의 스키마다. 현재 기준이 아니다.
-- =====================================================================
--
--  (a) 이 파일의 지위
--    프로젝트 초기(2026-08) 설계 — LoL 전용 / Discord 로그인 / 매칭 요청과 제안을 DB 에 저장 /
--    단일 스키마 — 의 ERDCloud 임포트용 DDL(MySQL 문법으로 옮긴 사본) 이다. 이 안내 블록 아래의 본문은 한 글자도 고치지 않았다.
--    새 스키마로 다시 쓰지도 않았다 — app:platform 이 소유할 테이블의 컬럼이 아직 정해지지
--    않았기 때문이다 (../platform/CLAUDE.md §7). 이 파일로 DB 를 만들지 마라.
--    아래 원래 머리말의 "실제 구축에는 03-schema.postgres.sql 을 사용하세요"도 더는 유효하지 않다.
--    출처 표기(docs/…, CLAUDE.md)는 저장소 루트 matching/ 기준이고 ../platform 은 옆 폴더다.
--    전체 대조표와 파일별 처분은 이 폴더의 README.md 에 있다.
--
--  (b) 어떤 결정으로 무엇이 폐기·변경됐나
--
--    [폐기 — Redis 로 이동]  docs/11 #27·#28·#32·#33, CLAUDE.md §3
--      match_request, request_sub_position
--      match_offer, offer_seat, offer_participant
--      그에 딸린 enum: request_status, offer_status, seat_status, seat_response, match_kind
--      그에 딸린 인덱스: uq_active_request_per_user, uq_offer_combo_active,
--                        uq_seat_filled_request, uq_seat_active_candidate
--      - 진행 중인 실시간 매칭 상태(요청 / 아직 안 찬 파티 / 진행 중 제안 / 수락 집계)는 Redis 에만
--        둔다. match_requests 테이블을 만들지 않는다. "사용자당 활성 요청 1건"은 부분 유니크
--        인덱스가 아니라 Redis Lua(claim-request.lua)가 지킨다 (프로젝트 INV-1).
--      - 좌석·충원 회차(round)·REFILLING 이라는 개념 자체가 없어졌다. 대기 상태는 "아직 안 찬
--        파티"이고 파티를 "아직 필요한 핵심 조건 값"으로 역색인한다.
--      - DB 는 확정된 것만 안다: matching.match_proposals + matching.proposal_members +
--        matching.outbox 를 단일 트랜잭션으로 쓴다 (docs/11 #27. 아직 구현되지 않았다).
--
--    [폐기 — Discord 연동]  docs/11 #16(자체 계정 + JWT), #6·#25(자체 WebRTC 음성/텍스트)
--      app_user.discord_id, app_user.discord_username, app_user.discord_dm_enabled
--      party_discord_channel, party_discord_member
--      enum discord_channel_status, discord_member_status, outbox_topic 의 'DISCORD'
--
--    [폐기 — Web Push]  contracts/events.md, docs/11 D-9·D-10, ../notification/CLAUDE.md §2
--      push_subscription, push_delivery, enum delivery_status, outbox_topic 의 'NOTIFICATION'
--      - 알림은 SSE 하나다. Redis Pub/Sub 으로 흘려보내고 이력을 저장하지 않는다.
--        (Web Push 는 "나중에 보완으로" 미뤄져 있을 뿐이다 — docs/WHY_SPRING_BOOT.md §5-3)
--
--    [변경 — 사용자 PK]  ../platform/CLAUDE.md §3.5 (2026-09-19 확정), docs/11 D-4
--      app_user.id 의 일련번호 PK 와 그것을 가리키는 모든 user_id 정수 FK
--      - 사용자 id 는 가입할 때 정한 로그인 아이디(문자열)다. uuid 나 일련번호를 사용자
--        식별자로 두지 않는다. 자리는 account.users 이고, 자체 로그인에 필요한
--        account.credentials/refresh_tokens 가 이 파일에는 없다.
--
--    [변경 — 단일 게임 전제]  docs/11 #8, CLAUDE.md §1·§2
--      game 테이블의 LOL 1행, enum lol_position / queue_type, riot_account,
--      app_user.primary_position, user_sub_position, party_member.position
--      - 게임은 LoL / VALORANT / PUBG 셋이다. 핵심 조건이 게임마다 다르고(LoL 포지션 /
--        VALORANT 역할군 / PUBG 플랫폼) 요청에는 값 하나만 싣는다 — 주·부 포지션이 없다.
--      - 큐(queue_type)와 목표 인원(target_size)은 사용자 입력이 아니다. 게임 모드(modeKey)가
--        인원까지 정하고(docs/11 #31), 모드 설정은 앱이 Redis qm:gameconfig:* 에서 읽는다.
--      - riot_account 의 자리는 게임 셋을 담는 account.game_accounts 다. 외부 API 연동 범위는
--        미정이고 매칭에 쓰는 티어는 지금 자기신고다.
--
--    [변경 — 조건 값]  CLAUDE.md §2 (코드가 원본)
--      enum voice_mode   ('REQUIRED','AVAILABLE','NONE')      -> REQUIRED / NO_VOICE  ("가능"은 제거됐다)
--      enum play_purpose ('CASUAL','WIN','LEARN','SKILLED')   -> RANK_UP / NORMAL / FUN
--      enum lol_position (... 'BOT' ...)                      -> TOP/JUNGLE/MID/ADC/SUPPORT/NONE
--      *_tier_order, allowed_tier_min_order / allowed_tier_max_order
--        -> 티어 순번은 DB 컬럼이 아니라 Redis ZSET 의 score 다. 사용자가 고르는 허용 범위는 없다.
--      - 네이티브 ENUM 대신 varchar + CHECK 를 쓴다 (docs/WHY_POSTGRESQL.md §4-3).
--      - play_mood(플레이 분위기)는 현재 결정에 대응하는 답이 없다.
--
--    [변경 — 예약]  docs/04, docs/11 #23·#24
--      match_request 의 kind='SCHEDULED' · play_date · window_start/end · play_minutes,
--      reservation_batch_run
--      - 예약은 match_request 에 합쳐 두지 않는다. 자리는 reservation.reservations 이고 필드는
--        "기존 매칭 조건 + availableFrom/availableTo(30분 단위, UTC) + playAmount" 다.
--      - 하루 1회 00시 배치가 아니라 1분 주기 배치다. play_date PK 로 "하루 1회"를 강제하던
--        reservation_batch_run 은 전제가 사라졌다.
--      - user_busy_interval: 실시간 요청은 시간 점유 대상이 아니다. 겹침 금지는 활성 예약끼리의
--        규칙이다 (프로젝트 INV-9).
--
--    [변경 — 파티와 outbox]  docs/11 #21, docs/00 §5, ../platform/CLAUDE.md §3.4·§3.5
--      party, party_member, outbox_event
--      - 자리는 party.parties / party.party_members 다. party.offer_id FK 와
--        party_member.request_id PK/FK 는 성립하지 않는다 — 둘 다 Redis 에만 있는 id 다.
--        파티룸에 나가기가 생겼다(옛 설계는 나가기가 없어 left_at 을 걷어냈다).
--      - outbox 는 스키마마다 따로 둔다(matching.outbox / party.outbox / social.outbox). 나르는
--        것은 알림·Discord 명령이 아니라 SQS FIFO 의 ProposalConfirmed / PartyClosed 다.
--        (BlockChanged.fifo 는 2026-09-19 에 폐기됐다.)
--
--    [변경 — 스키마 구조]  docs/11 #17·D-1
--      이 파일 전체가 단일 스키마 + 테이블 간 FK 전제다. 지금은 PostgreSQL 인스턴스 1개에
--      schema-per-service(account / gameconfig / matching / reservation / party / social)이고
--      크로스 스키마 FK·JOIN 을 금지하며 스키마별 DB 롤로 접속한다. 유일한 예외는 matching 롤에
--      준 social.blocks 의 SELECT 다.
--
--    [이 파일에 아예 없는 것]  docs/11 #13
--      친구 · 차단 · 신고 · 최근 함께한 사람. 옛 설계는 이것들을 제외 기능으로 두었으나 지금은
--      필수다 — social.friend_requests / friendships / blocks / reports / recent_players.
--      이 중 social.blocks 만 모양이 정해져 있다(matching 이 이미 읽는다):
--        id 일련번호 PK · blocker_id 문자열 · blocked_id 문자열 · (blocker_id, blocked_id) UNIQUE
--
--  (c) 지금도 참고할 만한 것 — 테이블이 아니라 기법이다
--    - btree_gist + EXCLUDE USING gist 로 "시간 구간이 겹치면 거절"을 DB 제약으로 강제하는 법
--      (user_busy_interval). 프로젝트 INV-9 에 그대로 쓰는 기법이다 (docs/WHY_POSTGRESQL.md §1-1).
--    - 부분 유니크 인덱스(WHERE 절)로 "활성인 것만 하나"를 강제하는 법.
--    - outbox 테이블의 모양(유일한 이벤트 id, 발행 시각, 시도 횟수)과 확정 트랜잭션 안에서
--      이벤트를 같이 적재한다는 생각.
--    - 티어를 문자열이 아니라 순번으로 비교해야 한다는 주의.
--
--  현재 기준
--    스키마 배치와 테이블 이름 : docs/WHY_POSTGRESQL.md §3, ../platform/CLAUDE.md §3.5
--    결정                     : docs/11_DECISION_LOG.md #17 · #21 · #27 · D-1 · D-4
--    matching 이 읽는 테이블   : backend/src/main/java/com/queuemate/matching/block/Block.java,
--                               backend/src/test/resources/schema.sql
--    테이블 컬럼              : 아직 정해지지 않았다 (../platform/CLAUDE.md §7)
-- =====================================================================

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
