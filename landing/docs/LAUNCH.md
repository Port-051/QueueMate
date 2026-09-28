# 공개 순서와 완료 기준

## 1. 코드 검토

`feat/seo-landing` PR을 팀원이 검토합니다. 기존 앱은 변경하지 않고 `landing/`만 별도 프로젝트로 배포합니다.
Vercel에는 최초 배포가 존재하지만 로그인 보호 때문에 익명 접속 검증은 미완료입니다. 도메인 구매·DNS 연결·검색엔진 소유 확인·검색 유입 성과도 완료한 상태가 아닙니다. 최신 검사 기록은 [AUDIT_2026-09-28.md](AUDIT_2026-09-28.md)를 참고합니다.

## 2. 이미 만든 랜딩 프로젝트의 공개·갱신

기존 프로젝트는 `port-051 / q-mate-landing-36418820590`입니다. 새 프로젝트를 반복해서 만들지 않습니다.
배포 주소는 `https://q-mate-landing-36418820590-cerit5nsr-port-051.vercel.app/`이며, 공개 전환은 이 랜딩 프로젝트에만 적용합니다. 기존 매칭 앱을 선택하지 않습니다.

Vercel Authentication을 해제할 때에는 해당 프로젝트의 모든 배포가 영향을 받는다는 점을 확인합니다. 준비된 공개 스크립트는 알려진 팀·프로젝트·소스·단일 배포·도메인 범위를 검증하고, 실제 HTTP 검사에 실패하면 기존 보호 설정을 복구하도록 구성되어 있습니다. 실행 전에 현재 상태를 다시 읽어야 합니다.

**CLI 업로드 위치와 Git 저장소의 Root Directory를 혼동하지 않습니다.** 최초 배포는 `landing/` 내용이 프로젝트 루트에 오도록 별도 디렉터리에서 CLI로 업로드했습니다. 이 방식에서 코드 위치는 `./`입니다. 향후 전체 저장소를 Git 통합으로 연결하면 그때 Root Directory를 `landing`으로 설정합니다. 브랜치 선택과 배포 설정을 확인하기 전에는 통합을 변경하지 않습니다.

| 설정 | 값 |
|---|---|
| 기존 프로젝트 | q-mate-landing-36418820590 |
| 저장소 전체를 Git으로 연결할 때 Root Directory | landing |
| landing 내용만 CLI로 업로드할 때 코드 위치 | ./ |
| Framework Preset | Other |
| Node.js | 22.x |
| Install Command | npm ci --ignore-scripts --no-audit --no-fund |
| Build Command | npm run build |
| Output Directory | dist |

`main`에는 아직 랜딩 PR을 병합하지 않았으므로 Git 통합에서 main을 선택하면 현재 작업본이 없을 수 있습니다. 팀 검토 후 병합하거나, 검증된 작업 브랜치의 코드를 사용합니다. 기존 앱 설정을 덮어쓰거나 이를 이유로 강제 병합하지 않습니다.

현재 설정은 Production 빌드라도 noindex입니다. 새 소스의 파비콘 및 검증 보완은 기존 배포에 자동 반영된 것이 아닙니다. 재배포 후 실제 주소에서 확인합니다.

```sh
npm run check:url -- https://q-mate-landing-36418820590-cerit5nsr-port-051.vercel.app preview live-check.json
```

검사는 GET 조회만 수행합니다. 종료 코드 0은 통과, 2는 로그인 보호, 1은 그 외 실패입니다. 검색 등록·도메인 변경·앱 연결·보호 우회는 하지 않습니다. URL이 재배포로 바뀌면 실제 반환된 최신 주소를 사용합니다.

## 3. q-mate.com 구매·연결

구매 완료 후 위 랜딩 프로젝트에만 도메인을 추가합니다. DNS 값은 그 프로젝트의 안내 값을 그대로 사용합니다.
다른 서비스의 MX/TXT 등 기존 레코드는 지우지 않습니다. www는 대표 주소로 리다이렉트합니다.
app.q-mate.com은 앱 담당자가 준비한 실제 배포 대상으로 별도 연결하며 도메인을 다시 구매하는 작업은 아닙니다.
HTTPS, 대표 주소, www 이동, 404 응답을 실제 주소에서 확인합니다. Vercel 배포 주소는 위에 기록되어 있지만 q-mate.com용 DNS 값과 연결 완료는 아직 확인하지 않았습니다.

## 4. 승인된 소개 페이지의 검색 공개

문구가 출시 준비 상태를 정확히 설명하는지 팀원이 확인한 후 공개 전용 PR을 만듭니다.
`contentApproved`, `uiApproved`, `allowIndexing`을 true로 설정하고 Production으로 새로 빌드합니다.
현재 테스트에는 출시 전 설정을 고정하는 검사가 있으므로, 공개 PR에서 그 검사도 의도한 공개 상태에 맞게 갱신하고 나머지 안전장치 테스트는 유지합니다.
`appReady`는 앱이 준비될 때까지 false로 둡니다. 소개 페이지 색인을 위해 앱 출시를 기다릴 필요는 없습니다.
미리보기 빌드를 단순 승격하지 않고 재빌드합니다. `VERCEL_ENV=preview`는 로컬의 production 덮어쓰기를 무시합니다.

```sh
npm run check:url -- https://q-mate.com production
```

홈은 200, robots는 Allow, 사이트맵은 대표 주소 1개, 알 수 없는 경로는 404여야 합니다.
robots.txt의 Disallow로 noindex 페이지 수집까지 막지 않습니다. 검색로봇이 noindex를 읽을 수 있어야 합니다.
임시 호스트의 HTTP noindex 규칙은 코드 수준에서 검사했으며, Vercel의 실제 응답 헤더는 배포 후 추가 확인해야 합니다.

## 5. 검색엔진 등록과 확인

구글 Search Console과 네이버 서치어드바이저에서 소유권 확인을 진행합니다.
실제 발급받은 값을 `verification.google` / `verification.naver`에 넣거나 해당 서비스가 안내한 DNS 검증을 사용합니다. 빈 값은 출력하지 않습니다.
검색 공개가 확인된 뒤 `https://q-mate.com/sitemap.xml`을 제출하고 수집·색인 상태를 확인합니다.
Bing·Daum의 공식 절차와 ZUM의 신청 경로 확인 결과, 입력 정보 및 문구 초안은 [SEARCH_PLAN_2026-09-28.md](SEARCH_PLAN_2026-09-28.md)에 정리했습니다. 등록은 아직 수행하지 않았습니다.

## 6. 공개 이후 측정

실제 공개 주소의 PageSpeed Insights 측정은 남아 있습니다. GitHub 실행 서버에서 측정한 모바일·PC Lighthouse 실험실 결과는 [AUDIT_2026-09-28.md](AUDIT_2026-09-28.md)에 별도로 기록했습니다. 이를 실제 배포 성능이나 검색 성과와 혼동하지 않습니다.
검색 노출수·클릭수·클릭률·실제 유입 검색어를 기준으로 제목과 본문을 개선합니다. 지금 키워드는 후보이지 검색량 검증 결과가 아닙니다.
우선 후보: 롤 듀오 구하기, 롤 듀오 찾기, 롤 파티 찾기, 큐메이트, QueueMate.
발로란트·배그 전용 페이지는 실제 공개 범위와 고유 콘텐츠가 준비된 뒤 검토합니다. 검색어만 바꾼 중복 페이지를 늘리지 않습니다.
GA4·광고 픽셀·사전 신청 수집 폼은 구현하지 않았습니다. 필요하다면 측정 목적과 수집 범위를 먼저 정합니다.

## 7. 앱 연결과 복구

앱 담당자가 최종 주소와 실제 동작을 확인한 뒤 `appReady=true`로 변경하고 준비 중 설명과 FAQ를 함께 검토합니다.
오류 시 이전 검증된 배포로 되돌리거나 문제 변경을 되돌린 커밋을 재배포합니다. 기존 앱·DNS를 함께 수정하지 않습니다.

## 공식 참고

- Google noindex: https://developers.google.com/search/docs/crawling-indexing/block-indexing
- Google 페이지 경험: https://developers.google.com/search/docs/appearance/page-experience
- Google AI 검색 안내: https://developers.google.com/search/docs/appearance/ai-features

좋은 성능 점수는 검색 상위 노출을 보장하지 않습니다. llms.txt는 보조 설명으로 유지하며 Google AI 노출의 필수 파일로 취급하지 않습니다.
