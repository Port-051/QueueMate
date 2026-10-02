# QueueMate SEO 랜딩

기존 React 앱과 독립적으로 빌드하는 정적 소개 페이지입니다. `frontend/`, `backend/`, 계약 파일은 변경하지 않습니다.
UI 참고: `codex/room-card-board`의 `7ee7177294b064d01ecda6934a7ba6d5d65e8172`.
원본 워드마크를 재사용하며 모집방과 대화는 테스트용 HTML 예시입니다. 실제 매칭 화면 캡처나 연결된 서비스가 아닙니다.

## 구매한 도메인

사용자가 구매 완료를 알린 정식 도메인은 **`queue-mate.com`**입니다. 이전 계획인 `q-mate.com`과 다릅니다.
대표 주소는 `https://queue-mate.com/`, 별도 앱을 유지할 경우의 예정 주소는 `https://app.queue-mate.com/`입니다.
2026-09-30에 코드의 대표 주소·공유 URL·푸터·검색 파일 생성 기준·호스트 규칙을 변경했습니다.
이 작업은 DNS 연결, 재배포, 검색엔진 소유 확인 또는 서비스 출시 완료를 뜻하지 않습니다.

기존 공유 이미지에는 옛 도메인이 적혀 있어 `media.ogImage`를 비워 공유 카드에서 임시 제외했습니다. 새 도메인의 이미지 검토 후 다시 설정합니다. 원본 파일과 이미지 무결성 테스트는 보존했습니다.
과거 날짜의 감사·검색 계획 문서는 당시 기록이며, 실제 연결에는 아래 최신 공개 체크리스트와 `site.config.json`을 사용합니다.

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

문구 승인·검색 공개·실제 앱 연결은 꺼져 있습니다. 디자인은 사용자 피드백에 따라 재검토 대상입니다.
현재 빌드는 noindex이며 `robots.txt`, `llms.txt`만 생성합니다. 승인된 production 빌드에서만 새 도메인의 `sitemap.xml`을 생성합니다.
검색 공개와 앱 연결은 독립 설정이므로 앱을 출시하기 전에도 승인된 소개 페이지를 먼저 공개할 수 있습니다.

[Vercel 배포·도메인·검색 등록 체크리스트](docs/LAUNCH.md)를 따르세요. 기존 서비스 프로젝트를 덮어쓰거나 main에 자동 병합하지 않습니다.
