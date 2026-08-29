# 00. Product Spec

## 1. Opportunity
게임 자체 매칭은 MMR/랭크/모드/일부 역할 같은 기본 조건을 처리하지만, 파티 구성 전에 사용자가 중요하게 여기는 조건을 충분히 합의시키지 못한다. 반대로 커뮤니티/LFG는 세부 조건을 표현할 수 있으나 사용자가 글을 쓰고 사람을 찾고 연락해야 한다.

QueueMate의 기회는 두 방식 사이에 있다.

> **조건은 사용자가 정하고, 사람 선택은 시스템이 한다.**

## 2. Product definition
사용자가 게임별 핵심 조건을 선택하면, QueueMate가 호환 가능한 사용자만 남긴 뒤 그 후보군 안에서 자동 랜덤 배정한다.

두 가지 방식:
- `REALTIME`: 지금 바로 팀원을 찾는다.
- `RESERVATION`: 미래의 플레이 가능 시간에 맞는 팀원을 미리 찾는다.

## 3. Supported games
- League of Legends
- VALORANT
- PUBG: BATTLEGROUNDS

지원 게임 확장은 제품 범위 밖이다.

## 4. Core loop
`조건 설정 → 매칭 → 제안 수락 → 파티룸 → 게임 준비 → 최근 함께한 사람 → 친구/차단 → 다음 매칭`

## 5. Required capabilities
### Account
- 회원가입/로그인/로그아웃
- 기본 프로필
- 게임 계정 연결/해제

### Matching
- 실시간 조건 설정
- 실시간 매칭 시작/취소
- 매칭 대기 상태
- 매칭 제안 accept/decline/timeout
- 조건의 자동/수동 완화는 soft condition 범위에서만 수행

### Reservation
- 기존 게임 조건 그대로 사용
- 30분 단위 플레이 가능 시간
- `1판`, `2판 이상`
- 예약 등록/수정/취소/목록
- 예약 조건이 맞으면 proposal 생성

### Party room
- 현재 파티원
- 게임 ID
- Ready
- 음성 WebRTC
- 텍스트 WebRTC DataChannel
- mute
- 나가기
- 친구 추가
- 차단
- 신고

### Social safety
- 친구 요청/수락/거절/삭제
- 최근 함께한 사람
- 차단/차단 해제
- 신고

## 6. Explicit non-goals
- 게시판/LFG 글 작성
- 사람/프로필 공개 검색
- 커뮤니티 서버
- 길드/클랜
- 공개 피드
- 공개 채팅방
- 상대팀 자동 구성
- 승패 예측/AI 코칭
- 유료 프리미엄

## 7. Product metrics
제품 판단에 우선해서 보는 지표:
- active queue size by game/mode
- compatible candidate count per request
- time to proposal
- time to confirmed party
- proposal accept / decline / expire rate
- reservation confirmation rate
- party room reach rate
- friend-after-match rate
- block/report rate
- invariant violation count (항상 0이어야 함)
