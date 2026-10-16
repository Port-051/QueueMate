# 검색 파일 생성과 검색엔진 등록 상태

확인일: 2026-09-29 (한국시간)
검증한 소스: e144376b5bb355d6603c3c5fcd9fe299f05260f8

## 이번 작업 결과

- 원격 landing 디렉터리와 로컬 소스의 Git tree가 da1a2d80fb51447be3db4f61aafd84fcb6ab3688로 일치함을 확인했습니다.
- 기존 Node 테스트 88개와 npm run build가 통과했습니다.
- 현재 설정의 robots.txt와 llms.txt를 실제 생성했습니다. 홈페이지 한 개만 포함한 sitemap.xml과 Sitemap 지시문이 있는 robots.txt는 공개 후 사용할 검토용 준비본으로 별도 생성했습니다.
- 준비본을 만들 때만 승인된 설정을 메모리에서 렌더링했습니다. site.config.json, Vercel, DNS, 실제 공개 정책을 바꾸지 않았습니다.
- 생성된 XML의 문법, 대표 URL 한 개, 임의 lastmod와 앱 URL 제외, 미리보기/공개 준비본 분리, 파일 SHA-256을 확인했습니다.
- 기존 Vercel 주소의 익명 접속 검사를 다시 실행한 결과는 DEPLOYED_LOGIN_REQUIRED, HTTP 302에서 로그인 화면의 200으로 이동입니다. 랜딩 공개 성공이 아닙니다.
- Vercel 관리 커넥터는 port-051 조회에서 403을 반환했습니다. 새로운 인증 요청이나 권한 변경은 하지 않았습니다.
- 검색엔진 계정의 사이트 추가, 소유확인, 사이트맵 제출은 이번 작업에서 수행하지 않았습니다. 구매 전이라고 안내받은 q-mate.com의 소유권을 가정하지 않습니다.

## 필요한 파일

| 파일 | 역할 | 현재 처리 |
|---|---|---|
| robots.txt | 자동 수집 허용·제한 규칙 | 기존 생성기를 실행해 생성·확인. Allow: / 유지 |
| sitemap.xml | 검색에 노출할 대표 URL 목록 | 정식 홈페이지 https://q-mate.com/ 한 개의 공개 후 준비본 생성 |
| llms.txt | LLM이 읽기 쉬운 서비스 안내 | 기존 생성기를 실행해 생성·확인. 준비 중·정적 예시 표시 유지 |

실제 배포 파일은 landing에서 npm run build를 실행하여 생성합니다. 현재 dist에는 robots.txt와 llms.txt가 있고, noindex 상태라 sitemap.xml은 생성하지 않습니다. 문구·UI·검색 공개 승인 및 production 빌드 조건이 충족되어야 sitemap.xml도 생성됩니다. 검토용 준비본을 현재 공개 상태의 증거로 사용하거나, robots.txt만 바꾸어 색인 가능 상태라고 판단하지 않습니다.

robots.txt는 접근 인증이나 검색 등록 신청서가 아니며 호스트별로 적용됩니다. q-mate.com의 규칙이 app.q-mate.com에 자동 적용되지 않습니다.[1] sitemap.xml에는 검색에 노출할 대표 URL을 넣습니다.[2]

llms.txt는 선택 보조 파일입니다. Google은 AI 검색에 별도 AI 텍스트 파일이 필요하지 않다고 명시합니다.[3] 제안 형식과 검색엔진의 실제 요구조건을 구분합니다.[4]

현재 User-agent: * / Allow: / 정책은 유지했습니다. OAI-SearchBot은 검색용, GPTBot은 학습용으로 목적이 다릅니다. 학습 허용·차단 정책은 이번에 임의 변경하지 않았습니다.[5] 개인 정보·대화 기록이나 존재하지 않는 성과 수치를 안내 파일에 넣지 않습니다.

## 등록 진행 순서

1. q-mate.com 구매 및 DNS 관리 권한을 확보합니다. Google의 DNS 소유확인은 도메인을 확보하면 웹페이지 공개 전에도 진행할 수 있습니다.[6]
2. 서비스 소유자 Google 계정으로 Search Console에 도메인 속성 q-mate.com을 추가하고, 실제 발급된 DNS TXT 값을 적용·확인합니다. URL 접두어 속성과 HTML 태그 방식을 선택하는 경우에는 실제 content 값을 site.config.json의 verification.google에 넣어 재배포합니다. 발급값을 임의로 생성하지 않습니다.[6]
3. 네이버 서치어드바이저에 https://q-mate.com/을 추가합니다. HTML 태그 방식이면 실제 content 값을 verification.naver에 넣어 재배포 후 소유확인합니다. 검증 태그는 최초 HTML head에 있어야 합니다.[7]
4. Bing Webmaster Tools의 소유확인 방법을 따릅니다. 메타태그 방식이면 실제 msvalidate.01의 content 값을 verification.bing에 넣습니다. 소유확인 후 사이트맵을 제출합니다.[8]
5. 본문이 로그인 없이 직접 200으로 열리고, noindex가 해제된 정식 주소에서 검사한 뒤 https://q-mate.com/sitemap.xml을 제출합니다. 소유확인·사이트맵 제출·실제 색인은 다른 상태로 기록합니다.[2][9]
6. Daum은 공개 주소, 등록정보, 운영 연락처 및 동의를 확인한 뒤 신청합니다. ZUM은 공식 고객센터의 검색 서비스 메뉴까지만 확인했으며 신규 사이트 접수 완료나 접수 가능 여부는 미확인입니다.[10][11]

```sh
cd landing
npm run check:url -- https://q-mate.com production search-registration-check.json
```

별도로 /llms.txt가 텍스트로 읽히는지도 확인합니다. 앱이 미출시라면 appReady=false를 유지합니다. 임시 vercel.app 주소는 최종 도메인의 대체 등록 대상으로 제출하지 않았습니다.

## 공식 참고

[1] https://developers.google.com/crawling/docs/robots-txt/create-robots-txt
[2] https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap
[3] https://developers.google.com/search/docs/appearance/ai-features
[4] https://llmstxt.org/
[5] https://developers.openai.com/api/docs/bots
[6] https://developers.google.com/site-verification/v1/getting_started
[7] https://searchadvisor.naver.com/guide/faq-start-register
[8] https://www.bing.com/webmasters/help/webmaster-support-24ab5ebf
[9] https://searchadvisor.naver.com/guide/request-feed
[10] https://register.search.daum.net/index.daum
[11] https://help.zum.com/
