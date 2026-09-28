# QueueMate SEO 랜딩

기존 React 앱과 독립적으로 빌드하는 정적 소개 페이지입니다. `frontend/`, `backend/`, 계약 파일은 변경하지 않습니다.
UI 참고: `codex/room-card-board`의 `7ee7177294b064d01ecda6934a7ba6d5d65e8172`.
원본 워드마크를 재사용하며 모집방과 대화는 테스트용 HTML 예시입니다. 실제 매칭 화면 캡처나 연결된 서비스가 아닙니다.

## 실행

Node.js 22 이상에서 저장소 루트 기준:

```sh
cd landing
npm ci --ignore-scripts --no-audit --no-fund
npm test
npm run build
npm run preview
```

로컬 주소: `http://127.0.0.1:4173`. 별도 터미널에서 `npm run check:url -- http://127.0.0.1:4173 preview`로 응답을 검사합니다.
`npm run export:preview`는 검토용 단일 HTML을 만듭니다. 이 파일 대신 `dist/`를 배포합니다.
프로덕션 런타임 의존성, 외부 추적기, 다운로드하는 웹폰트는 없습니다.

## 기본 상태

도메인 계획은 `https://q-mate.com`, 앱 주소 계획은 `https://app.q-mate.com/`입니다. 구매·DNS·앱 가동을 확인했다는 의미가 아닙니다.
UI 검토만 승인 상태입니다. 문구 검토, 검색 공개, 실제 앱 연결은 꺼져 있습니다. 임시 페이지는 noindex이며 사이트맵은 생성하지 않습니다.
검색 공개와 앱 연결은 독립 설정이므로 앱을 출시하기 전에도 승인된 소개 페이지를 먼저 공개할 수 있습니다.

[Vercel 배포·도메인·검색 등록 체크리스트](docs/LAUNCH.md)를 따르세요. 기존 서비스 프로젝트를 덮어쓰거나 main에 자동 병합하지 않습니다.
