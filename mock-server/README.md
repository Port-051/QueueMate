# QueueMate 더미 서버

백엔드가 없어도 프론트가 화면을 끝까지 붙일 수 있게 하는 가짜 서버다.
`docs/14_API_REQUEST_RESPONSE_EXAMPLES.md`의 계약을 그대로 흉내 낸다.

## 띄우기

```bash
node mock-server/server.js
# → http://localhost:8080/api/v1
```

**설치할 것이 없다.** 의존성이 0이고 `package.json`도 없다. Node 18 이상이면 바로 뜬다.
`npm install`을 돌릴 필요가 없으니 새로 받은 사람이 30초 안에 화면을 볼 수 있다.

## 환경 변수

| 변수 | 기본값 | 무엇 |
|---|---|---|
| `PORT` | `8080` | 포트 |
| `MOCK_AUTO_MATCH_MS` | `4000` | 매칭 요청 후 이 시간이 지나면 호환되는 대기자와 제안을 만든다. `0`이면 자동 매칭을 끈다 |
| `MOCK_PROPOSAL_TTL_MS` | `30000` | 제안 만료까지 |
| `MOCK_LATENCY_MS` | `0` | 모든 응답에 지연을 준다. 로딩 상태 UI를 확인할 때 쓴다 |
| `MOCK_DEBUG` | (없음) | JSON 파싱 실패 시 받은 원문을 콘솔에 찍는다 |

```bash
# 느린 네트워크에서 로딩 UI를 보고 싶을 때
MOCK_LATENCY_MS=800 node mock-server/server.js

# 매칭을 수동으로만 붙이고 싶을 때
MOCK_AUTO_MATCH_MS=0 node mock-server/server.js
```

## 상태

전부 메모리에 있다. 재시작하면 사라진다. DB도 Redis도 필요 없다.

## 계약 확인

```bash
node mock-server/server.js &
bash mock-server/contract-smoke.sh
```

78개 검사를 돌려 계약을 지키는지 본다. 서버를 고쳤으면 이걸 먼저 돌린다.
다른 포트로 띄웠으면 `MOCK_PORT=8091 bash mock-server/contract-smoke.sh`.

## 구현한 엔드포인트

실물 백엔드가 가진 38개를 전부 구현했다. 인증·유저·게임설정·매칭·제안·예약·파티·
친구·차단·최근플레이어·신고.

**구현하지 않은 것**
- 파티 초대 — v2 계약에서 제거됐다 (docs/14 §11)
- WebSocket `/ws` — 상태 변화는 폴링으로 확인한다
- rate limit (429) — 화면 개발을 방해하기만 한다

## 더미 서버에만 있는 것

화면을 보려고 상태를 억지로 만들 때 쓴다. 실물에는 없다.

| 엔드포인트 | 하는 일 |
|---|---|
| `POST /__mock/reset` | 모든 상태를 지운다. 테스트 사이에 부른다 |
| `POST /__mock/proposals` | 제안을 즉시 만든다. `{"userIds":["...","..."]}` — 매칭이 붙기를 기다리지 않아도 된다 |

## 실물과 다른 점

**없다.** 2026-09-05에 계약을 v2로 정리하면서 실물 백엔드도 같이 고쳤다.
전에는 세 군데를 일부러 다르게 뒀지만(버그를 흉내 내지 않으려고), 지금은 양쪽이 같다.
자세한 내역은 `docs/14` §11.

## 실물과 **똑같이** 맞춘 것

헷갈리기 쉬운 지점이라 일부러 재현했다.

- **모든 4xx가 `{code, message}`다.** 인증 필터가 막은 401도 같다
- `POST /auth/logout`은 **공개**다. refresh token이 JWT로 파싱되지 않으면 401이다
- `PATCH /users/me`에 `avatarUrl: null`을 보내면 **지워진다.** 키를 빼면 유지된다
- 예약 수정은 **`PUT`**이 정본이고 전체 교체다. `PATCH`도 같은 동작으로 받는다
- enum(`game`, `voicePreference`, `playPurpose`)은 대소문자를 안 봐주고,
  `keyCondition`의 두 필드만 `trim` + 대문자 정규화를 한다
- enum 밖의 값과 uuid가 아닌 경로 변수도 **`VALIDATION_FAILED`**로 답한다
- 201에 **`Location` 헤더를 붙이지 않는다**
- 예약 겹침은 `[from, to)` 반열림이고 `ACTIVE`·`PROPOSED`·`MATCHED`만 시간을 점유한다
- 남의 리소스는 403이 아니라 **404**다

## 전형적인 흐름

```bash
B=http://localhost:8080/api/v1

# 1. 두 명 가입 + 로그인
curl -s -X POST $B/auth/signup -H 'Content-Type: application/json' \
  -d '{"email":"a@queuemate.dev","password":"Qm!passw0rd","nickname":"알파"}'
A=$(curl -s -X POST $B/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"a@queuemate.dev","password":"Qm!passw0rd"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

# 2. 매칭 시작 (조건이 호환되는 두 사람이 대기하면 4초 뒤 제안이 붙는다)
LOL='{"game":"LOL","modeKey":"SOLO_DUO_RANKED","keyCondition":{"type":"POSITION","value":"MID"},"voicePreference":"OPTIONAL","playPurpose":"RANK_UP"}'
curl -s -X POST $B/match-requests -H "Authorization: Bearer $A" \
  -H 'Content-Type: application/json' -d "$LOL"

# 3. 상태를 폴링해서 proposalId를 얻는다
curl -s $B/match-requests/<id> -H "Authorization: Bearer $A"
```

**주의** — 셸에서 JSON을 인라인으로 쓸 때 `$( )` 안에 중괄호를 넣으면 셸 중괄호 확장에
걸려 콤마 단위로 쪼개진다. 키가 둘 이상인 payload는 위처럼 **변수에 먼저 담는다.**
