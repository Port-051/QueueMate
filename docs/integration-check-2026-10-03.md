# 실제 백엔드 통합 점검 — 2026-10-03

`feature/mentor-room-ux`, 기준 HEAD `c90bf44`와 이번 연결 설정·E2E 수정으로 검증했다.
Vite 5173 → platform 8082 · matching 18080 · notification 8081,
PostgreSQL 15433 · Redis 16380에 연결했다. 개발용 로그인으로 받은 실제 쿠키를 사용했다.
테스트 성공 경로는 실제 API·DB·Redis·SSE를 사용하고, 실패 복구 확인에만 네트워크 오류를 주입했다.

## 발견하고 수정한 문제

- Vite는 matching 요청을 기본 8080으로 전달했지만 실제 matching은 18080에서 실행 중이었다. 8080은 다른 프로젝트의 앱이었다. `QUEUEMATE_MATCHING_URL`로 로컬 프록시 주소를 지정할 수 있게 했고, 이 환경의 무시된 `.env.local`에 18080을 설정했다. 기본 포트는 유지했다.
- 최근 UI 변경에 맞춰 음성·참가·방 이동 테스트의 버튼명과 경고 문구를 수정했다. 마감 버튼은 접근성 이름에 사유가 붙으므로 `^마감`으로 찾는다. 실패한 두 문구 검사를 수정한 뒤 해당 시나리오를 모두 재실행해 통과했다.
- `14-room-settings-close.spec.ts`를 추가해 실제 방 설정 저장, 상대 화면 동기화, 비방장 권한 거부, 수동 마감 취소·확정, 마감 후 채팅을 검증한다.

## 결과

| 검증 | 결과 |
|---|---|
| E2E 01–08 | 8개 통과: 방 생성 규칙, 참가·강퇴·재입장 제한, 방장 퇴장, 확정·파티, 빠른매치 제안·수락·방 입장, 게시판 자동 합류, 차단, 친구 요청·수락·실시간 알림 |
| E2E 10–14 | 9개 통과: UI 방 생성·참가, 3인 양방향 WebRTC 음성 및 음소거 시 실제 수신 에너지 감소, 빠른매치 취소 후 참가·오류 복구, 정원 충원 자동 마감, 개인 메시지 송수신·재시도·새로고침 후 보존, 방 이동 취소·실패·경합, 설정·수동 마감·방 채팅 |
| PostSettingsTest | 7개 통과: 저장·Redis 동기화, 권한, 정원·포지션 충돌, 마감 후 편집 제한, 방장 승계, 오래된 자동 합류 후보, 참가와 정원 수정 동시성 |
| RecruitmentDisabledTest | 2개 통과: 지난 자동 마감 시각에도 모집 유지·기한 응답 제거·연장 거부, 수동 및 정원 충원 마감 유지 |
| MentorRoomFlowTest | 3개 통과: 선택적으로 시간 자동 마감을 켠 경우의 흐름도 유지 |
| 별도 수동 마감 브라우저 점검 | 방장만 표시·혼자일 때 비활성·취소·503 후 재시도·실제 확정·채팅 중복 없음·390px 화면 넘침 없음 통과 |
| 프런트 빌드·타입 검사 | 통과 |

브라우저/API 시나리오 총 17개, 백엔드 총 12개이며 백엔드 XML 기준 실패·건너뜀 모두 0이다.
백엔드 테스트는 별도 PostgreSQL 17433·Redis 17380에서 실행했고 완료 후 해당 테스트 컨테이너만 삭제했다.
E2E는 `e2e-a/b/c` 전용 계정만 사용했고 끝난 테스트 모집 글만 정리했다. 사용자의 `dev-tester` 계정과 예시 방은 유지했다.

## 재현

백엔드 세 서비스와 Vite, gameconfig가 있는 DB·Redis가 실행되어 있어야 한다.
아래 명령은 `frontend/`에서 실행한다. 테스트 계정이 공유되므로 두 실행을 동시에 돌리지 않는다.

```sh
E2E_DOCKER=docker E2E_PG_CONTAINER=qm-mentor-preview-pg npx playwright test e2e/01-post-rules.spec.ts e2e/02-kick-ban.spec.ts e2e/03-host-leaves.spec.ts e2e/04-confirm-party.spec.ts e2e/05-auto-match.spec.ts e2e/06-board-auto-join.spec.ts e2e/07-block.spec.ts e2e/08-friends.spec.ts
E2E_DOCKER=docker E2E_PG_CONTAINER=qm-mentor-preview-pg npx playwright test e2e/10-voice.spec.ts e2e/11-quick-match-room-entry.spec.ts e2e/12-mentor-room.spec.ts e2e/13-room-switch.spec.ts e2e/14-room-settings-close.spec.ts
npm run build
npm run typecheck
```

백엔드는 별도 테스트 DB·Redis를 띄운 뒤 `platform/backend/`에서 다음을 실행한다. 기존 JWT 키를 읽으며 미리보기 키를 교체하지 않는다.

```sh
DB_PORT=17433 REDIS_PORT=17380 JWT_GENERATE_DEV_KEYS=false ./gradlew test --tests '*RecruitmentDisabledTest' --tests '*PostSettingsTest' --tests '*MentorRoomFlowTest'
```

## 검증 범위의 한계

실제 카카오·디스코드·Google OAuth, Riot/PUBG 외부 계정 연결·전적 수집, 다른 네트워크 간 TURN 연결과 운영 배포 환경은 이번 점검에 포함하지 않았다.
LoL 외부 API를 호출하는 E2E 09도 실행하지 않았다. 음성은 같은 로컬 환경의 브라우저 3개와 가짜 마이크를 사용했다.
따라서 로컬 핵심 통합 기능의 통과를 외부 로그인·전적 및 운영 네트워크까지 검증한 것으로 해석하지 않는다.
