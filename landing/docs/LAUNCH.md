# 공개 순서와 완료 기준

갱신: 2026-09-30. 사용자가 구매 완료를 알린 주소는 **queue-mate.com**입니다. 이전 q-mate.com 계획과 과거 날짜 문서의 주소를 그대로 사용하지 않습니다.

## 1. 현재 상태와 코드 검토

`feat/seo-landing`의 PR #6를 검토합니다. `main`, 기존 앱과 UI 브랜치는 이 작업에서 변경하지 않았습니다.
코드 변경 `7a7926f9ed601fcdb865669ef2e403b62576c708`은 대표 주소·앱 예정 주소·푸터·공유 URL·검색 파일 생성 기준·Vercel 호스트 규칙을 새 도메인으로 변경합니다.
로컬 Node 테스트 96개, 빌드, 단일 검토 HTML 생성, 현재 설정의 HTTP 검사 16개가 통과했습니다. 공유 이미지를 사용하는 별도 테스트에서는 기존 이미지 검증도 유지합니다.
이 수치는 새 도메인이나 Vercel 실서버 검사 결과가 아닙니다. 이번 도메인 수정 작업에서는 재배포·DNS 변경·공개 권한 변경·검색 등록을 실행하지 않았습니다.

기존 공유 이미지에는 q-mate.com이 적혀 있어 `media.ogImage`를 비워 공유 메타정보에서 임시 제외했습니다. 새 도메인에 맞는 이미지를 검토한 후 복원합니다. 디자인은 여전히 재검토 대상이며 도메인 구매가 디자인 승인을 의미하지 않습니다.

## 2. 기존 랜딩 프로젝트의 공개·갱신

기존 프로젝트는 `port-051 / q-mate-landing-36418820590`입니다. 도메인이 바뀌어도 이 내부 프로젝트 이름을 바꾸거나 새 프로젝트를 반복 생성할 필요는 없습니다.
최초 배포 주소는 `https://q-mate-landing-36418820590-cerit5nsr-port-051.vercel.app/`, 당시 배포 소스는 `f63b93e96d7e8982157268ad674a2ae4fec21a91`입니다. 과거 실제 점검에서는 로그인 보호로 익명 접속이 막혔습니다. 최신 상태는 다시 확인해야 합니다.

| 설정 | 값 |
|---|---|
| 기존 프로젝트 | q-mate-landing-36418820590 |
| 전체 저장소를 Git으로 연결할 때 Root Directory | landing |
| landing 내용만 CLI로 올릴 때 코드 위치 | ./ |
| Framework Preset | Other |
| Node.js | 22.x |
| Install Command | npm ci --ignore-scripts --no-audit --no-fund |
| Build Command | npm run build |
| Output Directory | dist |

기존 매칭 앱 프로젝트를 선택하지 않습니다. PR 병합 전에는 main에 최신 랜딩이 없을 수 있으므로 검증된 작업 브랜치를 사용합니다. Git 통합이나 운영 병합은 별도 확인 후 진행합니다.
Vercel Authentication을 해제하면 해당 프로젝트의 배포들이 영향을 받습니다. 공개 대상과 소유 범위를 확인한 뒤 이 랜딩 프로젝트만 변경합니다.
`publish-existing-preview.mjs`는 최초 배포의 소스 해시·주소·단일 배포·도메인을 고정 검증하는 일회성 스크립트입니다. 새 배포나 도메인을 추가한 뒤 그대로 실행하지 말고 실제 대상과 검증 조건을 다시 검토합니다.

재배포로 받은 실제 주소에서 다음 검사를 수행합니다.

```sh
npm run check:url -- https://실제-배포-호스트 preview live-check.json
```

GET 전용 검사이며 종료 코드 0은 통과, 2는 로그인 보호, 1은 그 외 실패입니다. 로그인 화면의 최종 200을 랜딩 성공으로 세지 않습니다. 기존 공개 기록이나 로컬 검사 결과를 최신 배포 증거로 사용하지 않습니다.

## 3. queue-mate.com 연결

도메인 구매 업체와 현재 DNS 관리 업체를 확인합니다. 이 문서 작성 시 업체명·기존 레코드·DNS 변경 권한은 미확인입니다.
위의 기존 랜딩 프로젝트에 `queue-mate.com`을 추가하고, Vercel이 해당 프로젝트에 안내한 정확한 DNS 값을 적용합니다. 일반 예시 IP/CNAME을 확정값처럼 넣지 않습니다.[1]
`www.queue-mate.com`을 추가한다면 대표 주소 `https://queue-mate.com/`으로 리다이렉트합니다. 기존 MX/TXT 등 다른 레코드는 삭제하지 않습니다. 네임서버를 무작정 교체하지 않습니다.
`app.queue-mate.com`은 별도 앱 구조를 유지할 경우의 예정 주소이며, 실제 서비스의 배포 위치와 동작을 확인하기 전에는 연결하지 않습니다.
HTTPS·대표 주소·www 이동·404 응답을 실제 URL에서 검사합니다. 구매 완료와 DNS 연결 완료는 구분합니다.

## 4. 문구와 검색 공개

`allowIndexing=false`, `appReady=false`를 유지합니다. 도메인 변경만으로 검색 공개를 켜지 않습니다.
디자인과 문구·출시 범위를 확정하고 별도의 공개 변경에서 `contentApproved`, `uiApproved`, `allowIndexing`을 true로 설정합니다. 기본값을 확인하는 테스트도 의도한 공개 정책에 맞추고, 미리보기 안전장치·검증 테스트를 유지합니다.
Production으로 새로 빌드합니다. Preview 산출물을 단순 승격하지 않습니다. 소개 페이지 색인을 위해 실제 앱 출시를 기다릴 필요는 없으며 `appReady`는 앱 검증 전까지 false입니다.

```sh
npm run check:url -- https://queue-mate.com production launch-check.json
```

홈은 로그인 없이 200 HTML, 올바른 대표 주소, 공개 가능한 robots 지시문이어야 합니다. 사이트맵에는 대표 홈페이지 한 개만 넣고 없는 앱 경로나 임의 lastmod를 만들지 않습니다.
`robots.txt`의 Disallow로 noindex까지 읽지 못하게 막지 않습니다. `llms.txt`도 텍스트로 열리는지 확인합니다. 임시 호스트의 noindex 헤더를 실제 배포에서 확인합니다.

## 5. 검색엔진 등록

정식 등록 대상은 `queue-mate.com`입니다. Google Search Console, 네이버 서치어드바이저, Bing의 실제 소유확인 값을 발급받아 DNS 또는 `verification.google` / `verification.naver` / `verification.bing`에 적용합니다. 빈 값을 임의 인증 문자열로 채우지 않습니다.
공개가 확인된 뒤 `https://queue-mate.com/sitemap.xml`을 제출하고 수집·색인·검색 유입을 각각 확인합니다.
Daum은 공개 주소·신청 정보·담당 연락처·동의를 확인한 뒤 신청합니다. ZUM 신규 신청 경로는 최신 공식 접수 여부부터 확인합니다. 과거 [검색 계획](SEARCH_PLAN_2026-09-28.md)의 절차를 참고하되 옛 도메인으로 제출하지 않습니다.
이 문서 작성 시 실제 사이트 추가·소유확인·사이트맵 제출은 수행하지 않았습니다.

## 6. 품질 측정과 앱 연결

실제 공개 URL에서 PageSpeed Insights를 PC·모바일로 측정합니다. [과거 감사 결과](AUDIT_2026-09-28.md)는 실행 서버의 로컬 빌드 측정이며 실서비스 성능이나 검색 성과가 아닙니다.
검색 노출수·클릭수·클릭률·유입 검색어를 기준으로 본문을 개선합니다. 현재 검색어 후보는 검색량·광고 단가 실측 순위가 아닙니다. 발로란트·배그 페이지는 실제 기능과 고유 콘텐츠가 있을 때 검토합니다.
GA4·광고 픽셀·사전 신청 수집은 추가하지 않았습니다. 필요 시 수집 목적과 범위를 먼저 정합니다.
앱 담당자가 최종 주소와 동작을 확인한 뒤 `appReady=true`로 변경하고 준비 중 설명과 FAQ를 함께 수정합니다. 오류 시 이전 검증 배포 또는 되돌린 커밋을 사용하며 기존 앱과 DNS까지 한꺼번에 변경하지 않습니다.

## 공식 참고

[1] https://vercel.com/docs/domains/set-up-custom-domain

- https://developers.google.com/search/docs/crawling-indexing/block-indexing
- https://developers.google.com/search/docs/appearance/page-experience
- https://developers.google.com/search/docs/appearance/ai-features

좋은 성능 점수나 llms.txt가 검색·AI 노출을 보장하지 않습니다. llms.txt는 보조 설명이며 필수 등록 파일로 취급하지 않습니다.
