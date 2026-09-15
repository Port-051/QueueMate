# 팀 개발과 공유 데모

## 브랜치

공통 기준은 `main` 하나다. `develop`이나 개인별 장기 브랜치는 두지 않는다.

1. `git switch main` → `git pull --ff-only`.
2. 작업마다 `git switch -c codex/짧은-작업명`으로 분리한다.
3. 작은 단위로 커밋하고 바로 푸시한다. 하루 안에 끝낼 정도로 PR을 나눈다.
4. `main` 대상 PR을 만들고 계약 검증·백엔드 테스트·프론트 빌드 및 브라우저 테스트를 확인한다.
5. 다른 팀원 1명이 확인하면 병합한다. 통합 이력을 보존하려면 merge commit을 쓴다.
6. 병합된 작업 브랜치는 삭제하고 다음 작업은 최신 `main`에서 시작한다.

동일 파일을 동시에 수정할 때는 PR을 작게 나누고 먼저 통합한 변경을 반영한다. API 변경은 계약을 먼저 합친다. 작업 분담은 `docs/10_TEAM_PARALLEL_PLAN.md`를 참고한다.

## 공유 화면

https://port-051.github.io/QueueMate/#/app/home

`main`의 프론트 변경이 병합되면 GitHub Actions의 **UI demo**가 자동 배포한다. 데모 계정은 `demo@queuemate.gg` / `queuemate1`이다.

이 배포는 브라우저 Mock 모드다. 팀원들은 같은 코드와 초기 예시 데이터를 보지만, 직접 쓴 글·메시지·매칭 상태는 서로 공유하지 않는다. 실제 사용자 간 동시 매칭과 음성 연결 검증에는 별도 API 서버가 필요하다. 소셜 로그인도 데모에서는 시뮬레이션이다.

로컬 화면: `cd frontend && npm ci && npm run dev:mock`.
검증: `npm run build`와 `npx playwright test`. 백엔드는 Docker를 켜고 `cd backend && ./gradlew test`.

## 기존 DB를 사용하는 팀원

통합 전 두 브랜치가 `V5`를 서로 다른 SQL에 사용했다. 통합본은 기존 UI 브랜치의 `V5__recruitment_board.sql`을 유지하고, develop의 랭크 동기화 SQL은 `V7__game_account_rank_sync.sql`로 옮겼다. `V6` 자유 랭크 컬럼은 그대로다.

새 DB와 UI 브랜치의 DB는 통합본의 마이그레이션을 순서대로 적용할 수 있다. 기존 develop DB는 `V5` 기록이 다르므로 그대로 실행하면 Flyway 검증이 실패한다. 데이터를 보존해야 한다면 먼저 백업하고 `flyway_schema_history`와 실제 컬럼을 확인한 뒤 별도 이관해야 한다. `repair`나 볼륨 삭제를 일괄 실행하지 않는다. 버려도 되는 개인 개발 DB만 새로 생성한다.
