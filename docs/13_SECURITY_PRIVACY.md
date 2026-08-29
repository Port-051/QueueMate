# 13. Security & Privacy

## Authentication
- password는 BCrypt/Argon 계열 안전한 hash 사용
- access/refresh token 또는 server session 중 한 방식을 선택해 일관되게 사용
- refresh/token rotation 정책 문서화

## Authorization
모든 party/friend/block/report API는 requester가 해당 resource에 접근 권한이 있는지 검증.
클라이언트 userId를 신뢰하지 않는다.

## WebRTC
- signaling은 authenticated WebSocket만
- party membership 확인 후 SDP/ICE relay
- audio/chat content는 서버 저장하지 않음
- TURN credential은 단기 credential 사용 권장

## Blocking
block은 privacy/safety rule이며 matcher 최종 claim 직전에 재검증.

## Reporting
서버가 음성/채팅을 녹음하지 않으므로 report는:
- category
- optional description
- party/session identifiers
- server-side event metadata
만 저장한다.

## PII
외부 게임 ID는 필요한 범위만 저장.
로그에 이메일/게임 ID 전체를 무분별하게 남기지 않는다.
