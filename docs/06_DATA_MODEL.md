# 06. Data Model

PostgreSQL이 영속 데이터 source of truth다.

## Core tables

### users
```text
id UUID PK
email UNIQUE
password_hash
nickname
avatar_url
status
created_at
updated_at
```

### game_accounts
```text
id UUID PK
user_id FK
provider_game
external_game_id
region
rank_code nullable
verified_at nullable
UNIQUE(user_id, provider_game, external_game_id)
```

### game_mode_configs
```text
id
 game_key
 mode_key
 target_party_size
 role_uniqueness
 active
 policy_json
 UNIQUE(game_key, mode_key)
```

### match_requests
```text
id
user_id
match_type REALTIME
status
condition_json
queued_at
proposal_id nullable
created_at
updated_at
```

### reservations
```text
id
user_id
status
condition_json
available_from
available_to
play_amount
scheduled_start nullable
proposal_id nullable
created_at
updated_at
```

### match_proposals
```text
id
source_type REALTIME|RESERVATION
status
expires_at
created_at
confirmed_at nullable
```

### proposal_members
```text
proposal_id
user_id
source_request_id
acceptance PENDING|ACCEPTED|DECLINED
PRIMARY KEY(proposal_id, user_id)
```

### parties
```text
id
proposal_id UNIQUE
 game_key
 mode_key
 target_size
status
scheduled_start nullable
created_at
closed_at nullable
```

### party_members
```text
party_id
user_id
ready
joined_at
left_at nullable
PRIMARY KEY(party_id, user_id)
```

### friend_requests
```text
id
sender_id
receiver_id
status PENDING|ACCEPTED|DECLINED|CANCELLED
created_at
responded_at nullable
```

### friendships
정규화된 `(user_low_id, user_high_id)` unique pair.

### blocks
```text
blocker_id
blocked_id
created_at
PRIMARY KEY(blocker_id, blocked_id)
```

### reports
```text
id
reporter_id
target_user_id
party_id nullable
reason
 description nullable
created_at
```

### recent_players
별도 테이블 대신 완료된 `party_members` + party history에서 조회하거나 materialized summary를 만든다.
초기에는 중복 source를 만들지 않는다.

## Constraints
- friendship self relation 금지
- block self relation 금지
- party target_size > 1
- reservation available_from < available_to
- reservation timestamps는 service layer에서 30분 alignment 검증
- proposal member unique

## Redis vs DB
DB에 저장해야 하는 것:
- 회원/친구/차단/신고
- 예약 원본
- proposal/party 결과 history
- 게임 설정

Redis에만 있어도 되는 것:
- active queue ordering
- active claim lock
- presence
- ephemeral signaling routing
