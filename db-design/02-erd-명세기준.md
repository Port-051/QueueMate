# ERD 수정본 (기능 명세서 기준)

> 기준: 기능 명세서 대주제 1~5 (**명세가 최신**)
> DB: PostgreSQL 16 / 테이블 18개

---

## 1. 이전 ERD에서 걷어낸 것

명세에 **명시적으로 "제공하지 않는다"** 고 적힌 기능들입니다.

| 삭제 대상 | 근거 |
|---|---|
| `party.status = RECRUITING` | 2.8 도입부 — 충원은 **제안 단계에서만**. 공개 빈자리 모집 없음 |
| `party.status = COMPLETED`, `party.completed_at` | 5.5 도입부 — "파티 완료 기능이 없으므로" |
| `party_member.left_at`, `leave_reason` | 1.7.14 나가기 미제공 / 1.7.16 구성원 변경 미제공 |
| `seat_recruitment` (party에 매달린 형태) | 좌석은 `match_offer` 소속으로 이동 → `offer_seat` |
| `party_rating` 테이블 | 명세에 평가 기능 없음 |
| `user_relation` 테이블 | 명세에 재회·차단 기능 없음 |
| `notification_job` 테이블 | `outbox_event` + `push_delivery`로 분리 (아래 참고) |

## 2. 새로 넣은 것

| 추가 | 근거 |
|---|---|
| `game` 테이블 + 모든 곳에 `game_id` | 멀티게임 확장 최소 탈출구. 지금 비용 0, 나중에 넣으면 전체 마이그레이션 |
| `offer_seat` / `offer_participant` | 1.6.11~1.6.19, 2.8 — 좌석 단위 + 충원 회차 |
| `*_tier_order` 정수 컬럼 | 2.2.4 양방향 티어 판정. **문자열 비교는 틀립니다** (`'SILVER_1' < 'PLATINUM_4'` → false) |
| `party.offer_id` UNIQUE | 1.6.25 — 한 제안에서 파티가 두 개 생기는 사고 차단 |
| `user_busy_interval`에 `EXCLUDE` 제약 | 1.4.17 — PK로는 "겹치는 구간"을 못 막습니다 |
| `user_busy_interval.source_type` | 1.4.17은 **진행 중 요청**과 확정 파티 둘 다 점유해야 함 |
| `party_member.ready_state` | 1.7.7 참여 가능 / 1.7.8 늦을 예정 / 1.7.9 미응답 인원 |
| `outbox_event` | 1.9 — 파티 확정 트랜잭션 안에서 이벤트 적재 (확정↔알림 원자성) |
| `push_delivery` | 4.2.4 전송 성공 기록 / 4.2.5 실패 기록 / 4.4.6 실패 지표 |
| `reservation_batch_run` | 3.1.7 중복 실행 검증 / 3.5.6 실행 결과 기록 |
| `match_request.version` | 1.5.9 수정·취소 경합 (낙관적 락) |
| `app_user.discord_dm_enabled` | 5.4.1 / 5.4.2 기본값 꺼짐 |

---

## 3. 불변식 (INV) — DB 제약으로 강제

| ID | 내용 | 구현 | 명세 |
|---|---|---|---|
| INV-1 | 사용자당 진행 중 요청 1건 | `match_request(user_id)` 부분 유니크 | 1.5.1 |
| INV-2 | 사용자 시간 점유 비중복 | `EXCLUDE USING gist` | 1.4.17, 2.2.10, 3.2.9 |
| INV-3 | 한 요청은 한 파티에만 | `party_member.request_id` PK | 3.5.4 |
| INV-4 | 파티 내 포지션 중복 금지 | `UNIQUE(party_id, position)` | 2.5.2 |
| INV-5 | 이벤트 중복 발행 금지 | `outbox_event.event_id` UK | 1.9.9, 4.4.1 |
| INV-6 | 한 제안에서 파티 1개 | `party.offer_id` UK | 1.6.25 |
| INV-7 | 좌석당 활성 후보 1명 | `offer_participant(seat_id)` 부분 유니크 `WHERE response='PENDING'` | 2.8.14 |
| INV-8 | 같은 제안에 같은 요청 재등장 금지 | `UNIQUE(offer_id, request_id)` | 2.8.4, 2.8.5 |
| INV-9 | 자유 랭크 4인 차단 | `CHECK (queue<>'FLEX' OR target_size<>4)` | 1.4.8 |
| INV-10 | 예약 배치 하루 1회 | `reservation_batch_run.play_date` PK | 3.1.7, 3.4.3 |
| INV-11 | 파티당 Discord 채널 1개 | `party_discord_channel.party_id` PK | 5.6.7 |
| INV-12 | 같은 이벤트를 같은 구독에 1회만 | `UNIQUE(event_id, subscription_id)` | 4.4.1 |

---

## 4. ERD

```mermaid
erDiagram
    game ||--o{ match_request : ""
    game ||--o{ match_offer : ""
    game ||--o{ party : ""

    app_user ||--o| riot_account : "1:1"
    app_user ||--o{ user_sub_position : "부 포지션"
    app_user ||--o{ push_subscription : "브라우저별"
    app_user ||--o{ match_request : "신청"
    app_user ||--o{ user_busy_interval : "시간 점유"
    app_user ||--o{ offer_participant : "응답"
    app_user ||--o| party_member : "참가"
    app_user ||--o{ party_discord_member : ""

    match_request ||--o{ request_sub_position : ""
    match_request ||--o{ offer_participant : "회차마다"
    match_request ||--o| party_member : "확정 시 1건 INV-3"
    match_request ||--o| user_busy_interval : "진행 중 점유"

    match_offer ||--|{ offer_seat : "정원만큼"
    match_offer ||--o| party : "전원 수락 시 1건 INV-6"
    offer_seat  ||--|{ offer_participant : "충원 회차"

    party ||--|{ party_member : "정원만큼"
    party ||--o{ user_busy_interval : "확정 점유"
    party ||--o| party_discord_channel : "확정 후 1건 INV-11"
    party ||--o{ party_discord_member : ""
    party ||--o{ outbox_event : ""

    outbox_event      ||--o{ push_delivery : ""
    push_subscription ||--o{ push_delivery : ""

    game {
        smallint id PK
        text code UK "LOL"
        text name
        boolean is_active
    }

    app_user {
        bigint id PK
        text discord_id UK "OAuth2 신원 - 1.1.1"
        text discord_username "확정 후 공개 - 1.7.4"
        text nickname "초기값 Discord 표시명 - 1.2.1"
        voice_mode voice_mode "필수 가능 사용안함 - 1.2.2"
        play_purpose purpose "1.2.3"
        play_mood mood "표시 전용 - 1.2.5"
        lol_position primary_position "1.2.6"
        boolean discord_dm_enabled "기본 false - 5.4.2"
        timestamptz created_at
        timestamptz updated_at
    }

    user_sub_position {
        bigint user_id PK "FK app_user"
        lol_position position PK "1.2.7"
    }

    riot_account {
        bigint user_id PK "FK app_user"
        text game_name "Riot ID 앞부분 - 1.3.1"
        text tag_line "Riot ID 태그"
        text puuid UK "account-v1 결과 - 1.3.3"
        text solo_tier "1.3.5"
        smallint solo_tier_order "비교용 정수"
        text solo_division
        int solo_lp
        int solo_wins
        int solo_losses
        boolean solo_in_placement "배치 진행 중 - 1.3.7"
        text flex_tier "1.3.6"
        smallint flex_tier_order
        text flex_division
        int flex_lp
        int flex_wins
        int flex_losses
        boolean flex_in_placement
        riot_link_status link_status "정상 확인필요 조회실패 - 1.3.8"
        timestamptz refreshed_at "마지막 갱신 - 1.3.9"
    }

    push_subscription {
        bigint id PK
        bigint user_id FK
        text endpoint UK "Web Push - 1.9.1"
        text p256dh
        text auth
        timestamptz created_at
        timestamptz revoked_at "410 Gone 감지 - 4.2.3"
    }

    match_request {
        bigint id PK
        bigint user_id FK
        smallint game_id FK
        match_kind kind "IMMEDIATE SCHEDULED - 1.4.1"
        queue_type queue "1.4.6"
        smallint target_size "1.4.7 INV-9"
        request_status status "1.5.2 부터 1.5.8"
        timestamptz requested_at "FCFS 1차 정렬키 - 2.4.1"
        int play_minutes "1.4.16"
        int max_wait_minutes "즉시 전용 - 1.4.2"
        date play_date "예약 전용 - 1.4.13"
        timestamptz window_start "시작 가능 범위 - 1.4.15"
        timestamptz window_end
        text tier_snapshot "1.4.19"
        smallint tier_snapshot_order "양방향 판정용 - 2.2.4"
        timestamptz tier_snapshot_at "신선도 - 1.3.11"
        smallint allowed_tier_min_order "1.4.10"
        smallint allowed_tier_max_order
        lol_position primary_position "2.5.1"
        play_purpose purpose "2.2.8"
        voice_mode voice_mode "2.3"
        jsonb conditions "게임 확장 여지"
        int version "낙관적 락 - 1.5.9"
        timestamptz created_at
        timestamptz updated_at
    }

    request_sub_position {
        bigint request_id PK "FK match_request"
        lol_position position PK "1.4.18 상관없음 전개 후"
    }

    user_busy_interval {
        bigint id PK
        bigint user_id FK "INV-2 EXCLUDE 대상"
        tstzrange during "겹침 금지 - 1.4.17"
        busy_source source_type "REQUEST 또는 PARTY"
        bigint request_id FK "둘 중 하나만"
        bigint party_id FK
    }

    match_offer {
        bigint id PK
        smallint game_id FK
        queue_type queue "2.7.5"
        smallint target_size
        play_purpose purpose "합의 조건 - 2.7.5"
        boolean voice_party "2.3.5 2.3.6"
        text combo_hash "중복 제안 방지"
        offer_status status "PENDING REFILLING CONFIRMED FAILED"
        timestamptz created_at
        timestamptz respond_by "최초 응답 만료 - 1.6.3 2.7.6"
        timestamptz refill_by "전체 충원 제한 - 1.6.21 2.7.7"
        timestamptz closed_at
    }

    offer_seat {
        bigint id PK
        bigint offer_id FK
        lol_position position "좌석은 포지션 단위 - 1.6.14"
        seat_status status "OPEN PENDING FILLED"
        bigint filled_by_request_id FK "수락 확정자 - 2.8.17"
        timestamptz opened_at
        timestamptz closed_at
    }

    offer_participant {
        bigint id PK
        bigint offer_id FK "INV-8"
        bigint seat_id FK "INV-7"
        bigint request_id FK
        bigint user_id FK
        smallint round "충원 회차 - 2.8.19"
        seat_response response "PENDING ACCEPTED_LOCKED DECLINED TIMED_OUT - 1.6.11"
        timestamptz offered_at
        timestamptz expires_at "1.6.8"
        timestamptz responded_at "조건부 전이로 멱등 - 1.6.9"
    }

    party {
        bigint id PK
        smallint game_id FK
        bigint offer_id UK "INV-6 예약은 NULL"
        queue_type queue "합의 스냅샷 - 1.7.6"
        smallint target_size
        play_purpose purpose
        boolean voice_party "1.7.5"
        timestamptz start_at "1.7.1 3.3.4"
        timestamptz end_at "start_at + play_minutes - 3.3.5"
        timestamptz confirmed_at "1.6.25"
    }

    party_member {
        bigint request_id PK "INV-3"
        bigint party_id FK "INV-4 UNIQUE party_id position"
        bigint user_id FK
        lol_position position "2.5.8"
        ready_state ready_state "NONE READY LATE - 1.7.7 1.7.8"
        timestamptz ready_at
        timestamptz joined_at
    }

    party_discord_channel {
        bigint party_id PK "INV-11 명령 멱등키"
        text guild_id "5.1.5"
        text category_id
        text channel_id UK "5.2.5"
        discord_channel_status status "REQUESTED CREATED SKIPPED FAILED DELETED"
        text fail_reason "권한부족 서버미가입 API장애 - 5.6.1"
        timestamptz created_at
        timestamptz delete_after "end_at + 24h - 5.5.1"
        timestamptz deleted_at "5.5.5"
    }

    party_discord_member {
        bigint party_id PK "FK party 채널 실패와 무관하게 기록"
        bigint user_id PK "FK app_user"
        discord_member_status status "PENDING GRANTED NOT_JOINED FAILED - 5.3.4"
        timestamptz granted_at
        text fail_reason "5.6.5"
        timestamptz updated_at
    }

    outbox_event {
        bigint id PK
        uuid event_id UK "INV-5 - 1.9.9"
        outbox_topic topic "NOTIFICATION 또는 DISCORD"
        text event_type "4.1.2"
        bigint party_id FK "nullable 실패 알림"
        bigint user_id FK "nullable"
        jsonb payload
        timestamptz available_at "리마인더 지연 - 4.3.11 부터 13"
        timestamptz published_at
        smallint attempts "4.4.2"
        text last_error
        timestamptz created_at
    }

    push_delivery {
        bigint id PK
        uuid event_id FK "INV-12"
        bigint subscription_id FK
        delivery_status status "SENT FAILED EXPIRED"
        smallint http_status "4.2.5"
        text error
        timestamptz sent_at "4.2.4"
    }

    reservation_batch_run {
        date play_date PK "INV-10 - 3.1.7"
        timestamptz started_at
        timestamptz cutoff_at "처리 경계 - 3.1.3"
        timestamptz finished_at
        int processed_count "3.5.6"
        int party_count
        int failed_count
        text error
    }
```

---

## 5. 상태 흐름

```
match_request.status
  RESERVED ──(00시 배치)──> CONFIRMED  (3.3.3 자동 확정)
       └──────────────────> FAILED     (3.4.2 미성립)

  QUEUED ──(후보 구성)──> PROPOSED ──(거절/무응답)──> REFILLING
     │                       │                          │
     │                       └──(전원 수락)──> CONFIRMED <┘
     │                       │
     │                       └──(refill_by 만료)──> FAILED (1.6.22)
     │                                                │
     └<──────── 정상 수락자 재대기 (1.6.23) ──────────┘
     │
     └──(max_wait 만료)──> FAILED (2.1.7)
     └──(사용자 취소)────> CANCELED (1.4.23)
```

```
offer_seat.status
  PENDING ──(수락)──> FILLED
     └──(거절/시간초과)──> OPEN ──(대체 후보 선점)──> PENDING   ← 2.8.19 반복
```

---

## 6. Redis 담당 (Postgres에 없는 것)

Redis가 통째로 날아가도 위 테이블로 복구 가능해야 합니다 (1.8.14).

| 용도 | 자료구조 | 명세 |
|---|---|---|
| 즉시 매칭 대기열 | Sorted Set, score = `requested_at` | 2.1.2, 2.4.1 |
| 요청/사용자 선점 | Lua + SET NX | 2.6.1~2.6.7, 2.8.14 |
| SSE 인스턴스 간 전달 | Pub/Sub | 1.8.15 |
| 현재 대기 인원 | Counter | 1.4.21, 2.1.9 |
| API 멱등 키 | String + TTL | 1.5.11, 1.6.9 |
| Riot 조회 single-flight | SET NX + TTL | 1.3.13 |
