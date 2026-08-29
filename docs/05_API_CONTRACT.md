# 05. API Contract Guide

기계 판독 기준은 `contracts/openapi.yaml`이다. 이 문서는 팀 간 의미를 설명한다.

## Auth/User
- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/logout`
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me`
- `GET/POST/DELETE /api/v1/users/me/game-accounts`

## Game config
- `GET /api/v1/games`
- `GET /api/v1/games/{gameKey}/modes`
- `GET /api/v1/games/{gameKey}/match-schema`

Frontend는 게임별 폼을 backend schema에 맞춰 그릴 수 있어야 하지만, 현재 3개 게임 UI는 명시적인 typed component로 구현해도 된다.

## Realtime matching
- `POST /api/v1/match-requests`
- `GET /api/v1/match-requests/{id}`
- `DELETE /api/v1/match-requests/{id}`
- `POST /api/v1/proposals/{id}/accept`
- `POST /api/v1/proposals/{id}/decline`

## Reservation
- `POST /api/v1/reservations`
- `GET /api/v1/reservations`
- `GET /api/v1/reservations/{id}`
- `PATCH /api/v1/reservations/{id}`
- `DELETE /api/v1/reservations/{id}`

## Party
- `GET /api/v1/parties/{id}`
- `POST /api/v1/parties/{id}/ready`
- `POST /api/v1/parties/{id}/leave`
- `POST /api/v1/parties/{id}/invite/{friendUserId}`

## Social
- friends requests/list/delete
- blocks create/delete/list
- recent players
- reports

## Realtime transport
`/ws` WebSocket은 다음 용도만:
- queue/proposal/party 상태 이벤트
- friend/notification 이벤트
- WebRTC signaling

텍스트 파티 채팅 payload는 서버 WebSocket이 아니라 WebRTC DataChannel을 기본으로 한다.
