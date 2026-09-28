# 공개 순서와 완료 기준

## 1. 코드 검토

`feat/seo-landing` PR을 팀원이 검토합니다. 기존 앱은 변경하지 않고 `landing/`만 별도 프로젝트로 배포합니다.
아직 도메인 구매, DNS 연결, 실제 배포, 검색엔진 소유 확인, 검색 노출 성과를 완료한 상태는 아닙니다.

## 2. Vercel에 별도 프로젝트 만들기

PR 병합 후 기존 `Port-051/QueueMate` 저장소를 새 Vercel 프로젝트로 가져오면 main에 `landing/`이 있습니다.
병합 전에는 이 브랜치를 로컬로 받은 뒤 `landing/`에서 Vercel CLI의 미리보기 배포를 사용합니다. CLI 로그인은 커넥터 연결과 별개입니다.

| 설정 | 값 |
|---|---|
| 프로젝트 이름 제안 | q-mate-landing |
| Root Directory | landing |
| Framework Preset | Other |
| Node.js | 22.x |
| Install Command | npm ci --ignore-scripts --no-audit --no-fund |
| Build Command | npm run build |
| Output Directory | dist |

기존 앱의 프로젝트 설정이나 도메인을 덮어쓰지 않습니다. 현재 설정은 Production 빌드라도 noindex입니다.
배포 URL을 받은 후 `npm run check:url -- https://실제-배포-호스트 preview`를 실행합니다.
이 명령은 GET 조회만 수행하며, 검색 등록·도메인 변경·앱 연결을 하지 않습니다.

## 3. q-mate.com 구매·연결

구매 완료 후 새 랜딩 프로젝트에만 도메인을 추가합니다. DNS 값은 그 프로젝트의 안내 값을 그대로 사용합니다.
다른 서비스의 MX/TXT 등 기존 레코드는 지우지 않습니다. www는 대표 주소로 리다이렉트합니다.
app.q-mate.com은 앱 담당자가 준비한 실제 배포 대상으로 별도 연결하며 도메인을 다시 구매하는 작업은 아닙니다.
HTTPS, 대표 주소, www 이동, 404 응답을 실제 주소에서 확인합니다. 현재는 DNS 값이나 배포 URL을 확정하지 않았습니다.

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
회의에서 언급한 Bing·다음·줌은 후속 등록 검토 목록입니다. 이 작업에서는 각 서비스의 최신 접수 여부나 등록 완료를 확인하지 않았습니다.

## 6. 공개 이후 측정

PageSpeed Insights를 실제 공개 주소에서 PC·모바일로 측정하고 보고서를 남깁니다. 아직 점수를 측정하지 않았습니다.
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
