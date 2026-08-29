# 01. IA and User Flows

## 1. Routes
```text
/
/login
/signup
/onboarding

/app/home
/app/match
/app/match/waiting/:requestId
/app/reservations
/app/reservations/new
/app/proposals/:proposalId
/app/party/:partyId
/app/friends
/app/recent
/app/me
/app/settings
```

`proposal`은 화면/route로 구현하되 상황에 따라 modal overlay로 전환 가능하다.

## 2. Left navigation
로그인 후 desktop UI 좌측 탭은 고정한다.

```text
홈
매칭
예약 매칭
파티룸
친구
최근 함께한 사람
내 정보
설정
```

파티룸이 없으면 `파티룸`은 disabled 또는 최근 활성 파티로 이동한다.

## 3. Home
- CTA: `지금 매칭`, `예약 매칭`
- 최근 사용 조건 빠른 재사용
- 진행 중 실시간 매칭
- 예정 예약
- 최근 함께한 사람 일부
- 온라인 친구 일부

## 4. Match condition page
상단에서 `실시간 매칭 / 예약 매칭` 방식을 전환할 수 있다.

실시간:
1. 게임
2. 게임 모드
3. 게임별 핵심 조건 1개
4. 음성 사용
5. 플레이 목적
6. 매칭 시작

예약:
위 1~5 +
6. 플레이 가능한 시간
7. 플레이할 양
8. 예약 등록

## 5. Realtime flow
```text
Home
→ Match Condition
→ QUEUED
→ Waiting
→ Proposal
  ├ decline/timeout → Waiting
  └ all accept → Party Room
→ Ready / Voice / Chat
→ Playing
→ Complete
→ Recent Players
```

## 6. Reservation flow
```text
Home
→ Reservation Form
→ Reservation ACTIVE
→ Reservation Management
→ compatible reservations 발견
→ Proposal
  ├ decline/timeout → ACTIVE 복귀
  └ all accept → Reservation MATCHED
→ Party Room
→ session
→ Complete
```

## 7. Friend flow
```text
Party Room / Recent Player
→ Friend Request
→ Accept
→ Friend List
→ Party Invite
```

## 8. Block flow
```text
Party Room / Recent / Friend
→ Block
→ friendship 제거
→ future candidate set에서 즉시 제외
```

## 9. Screen source of truth
`design/` 이미지는 레이아웃/톤 참고만 한다. 다음은 이미지에 보여도 구현 금지:
- LoL Solo/Duo에서 5인 팀
- 상대팀/VS
- 지원하지 않는 게임
- premium
- age 조건
- 과도한 플레이 스타일/챔피언 조건
