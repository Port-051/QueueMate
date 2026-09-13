# QueueMate Development Pack

이 저장소는 QueueMate를 **3명이 Claude Code로 병렬 개발**하기 위한 기준 저장소다.

## 제품 한 줄
게임 자체 매칭이 다루지 못하는 **게임별 핵심 조건**을 반영해, 호환되는 팀원의 실시간·예약 모집을 보여주고 직접 참여 또는 자동매칭으로 연결하는 웹 서비스.

## 고정 기술 스택
- Frontend: React + TypeScript + Vite
- Backend: Java 21 + Spring Boot
- Persistent DB: PostgreSQL
- Realtime state / queue / lock: Redis
- Party signaling: Spring WebSocket
- Party voice + text chat: WebRTC (audio + DataChannel)
- Local infra: Docker Compose

## 지원 게임
**League of Legends / VALORANT / PUBG: BATTLEGROUNDS만 지원한다.**
다른 게임은 UI/코드/문서에 추가하지 않는다.

## 시작 순서
1. `CLAUDE.md`를 읽는다.
2. `docs/00_PRODUCT_SPEC.md` ~ `docs/12_ELBOW_CONDITION_SELECTION.md`를 읽는다.
3. `contracts/openapi.yaml`과 `contracts/events.md`를 API 기준으로 사용한다.
4. 각 팀원은 `team/START_HERE.md`와 자신의 Claude 프롬프트를 사용한다.
5. 로컬 인프라: `docker compose up -d postgres redis`

## 핵심 원칙
- 홈은 공개에 동의한 실시간·예약 모집을 보여주는 게시판이다. `docs/16_RECRUITMENT_BOARD.md`를 따른다.
- 상대팀은 다루지 않는다. **같은 파티에 들어갈 팀원만 구성한다.**
- 매칭 조건은 게임별로 다르며 현재 조건 수는 의도적으로 축약되어 있다.
- 예약 매칭 = 기존 매칭 조건 + `플레이 가능한 시간` + `플레이할 양` 두 항목만 추가한다.
- 이미지 레퍼런스보다 문서 스펙이 우선한다.

## Repository Map
- `docs/`: 스펙 / 하네스 / 운영 기준
- `contracts/`: REST + WebSocket 계약
- `frontend/`: React 작업 영역
- `backend/`: Spring Boot 작업 영역
- `harness/`: 부하/정합성/fixture
- `infra/`: Redis/Postgres/Coturn 로컬 설정
- `design/`: 지금까지 생성한 웹 UI 레퍼런스
- `team/`: 3인 병렬 개발 지침 + Claude Code 프롬프트

모집 게시판의 실제 서버/데모 실행, 초기 설정, 검증 방법: [구현과 검증](docs/17_RECRUITMENT_IMPLEMENTATION.md).
