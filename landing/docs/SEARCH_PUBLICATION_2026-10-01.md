# 소개 랜딩 검색 공개 준비

작성일: 2026-10-01 (KST)
코드 커밋: 86000b47d6d76ae3aebdb16cc94bfa636b6b1b54
브랜치: feat/seo-landing
대상 도메인: https://queue-mate.com/

## 완료한 변경

사용자는 검색 공개 진행을 승인했고, 실제 매칭 서비스는 아직 배포하지 않았다고 설명했습니다. 소개 페이지 공개와 실제 앱 출시를 구분합니다.

- site.config.json의 allowIndexing을 true로 변경했습니다.
- appReady=false는 유지했습니다. 앱 주소를 실제 시작 버튼에 연결하지 않습니다.
- production 빌드는 index, follow 메타정보와 홈페이지 한 개만 포함한 sitemap.xml을 생성합니다.
- preview/development/일반 로컬 빌드는 noindex이며 사이트맵을 생성하지 않습니다.
- 기존 Vercel 호스트 조건을 유지했습니다. 정식 호스트 queue-mate.com 외의 배포 주소에는 X-Robots-Tag: noindex, nofollow가 적용됩니다.
- 본문 생성기·CSS·이미지·레이아웃·버튼·FAQ는 변경하지 않았습니다. 서비스 준비 중 안내도 유지했습니다.

## 검증

작업 환경의 Node.js 22.16.0에서 전체 테스트 125개가 통과했습니다. production 및 preview 빌드를 각각 실행했고, 다음 내용을 검증했습니다.

- production: 검색 허용 메타정보, 정식 홈페이지 한 개의 사이트맵, robots.txt의 사이트맵 주소
- preview: noindex, 사이트맵 미생성
- 검색 허용 전후 body HTML 바이트 동일
- 기존 src와 public 파일, vercel.json 바이트 동일
- 미출시 앱으로 향하는 링크 없음
- 테스트한 수정 파일과 GitHub에 업로드한 파일의 blob SHA 일치

위 결과는 코드·빌드 검증입니다. 실제 도메인 접속 성공이나 검색 등록 완료를 의미하지 않습니다.

## 아직 하지 못한 작업

- 가비아 DNS 수정
- 이 검색 공개 커밋의 Vercel 재배포
- 정식 도메인의 HTTPS 및 검색 허용 상태 검증
- Google Search Console / 네이버 서치어드바이저 소유확인 및 사이트맵 제출

현재 Vercel 관리 커넥터는 port-52 접근에서 403을 반환했습니다. 이전 일회성 CLI 배포 승인은 해당 작업에서 종료·정리되었으므로 지속적인 관리 권한으로 간주하지 않습니다. 사용 가능한 플러그인 검색에서도 가비아 DNS를 수정하는 연동은 찾지 못했습니다. 인증정보·검증 토큰을 임의로 생성하거나 다른 계정·프로젝트로 대체하지 않았습니다.

## 배포 시 주의

기존 .github/workflows/seo-benefits-release.yml은 이전의 검색 차단 소스 9ed059501d2689e0eddc2856f07fde956918afb4에 고정되어 있습니다. 그 작업을 그대로 재실행하면 이 검색 공개 커밋은 반영되지 않습니다.

후속 배포는 기존 port-52 / queue-mate-landing 프로젝트만 대상으로 해야 합니다. 현재 배포 ID를 다시 읽고, 새 소스·검색 허용 플래그·검증 방식에 맞게 배포 작업을 갱신해야 합니다. 예전 스크립트의 allowIndexing=false 단언과 임시 주소 HTML noindex 단언을 그대로 재사용하지 않습니다. 임시 Vercel 주소는 HTTP X-Robots-Tag로 계속 검색 차단해야 합니다.

구매 도메인의 DNS는 기존 메일·인증 레코드를 보존하면서 Vercel이 안내한 값과 대조합니다. DNS 연결 후 정식 도메인이 로그인 없이 200으로 응답하고, 올바른 canonical과 사이트맵을 반환하며, HTML과 응답 헤더에 noindex가 없는지 검사합니다. 실제 매칭 서비스 출시는 별도이며 appReady=false를 유지합니다.

## 공식 참고

- Google 검색의 기술 요구사항: https://developers.google.com/search/docs/essentials/technical
- Vercel 외부 DNS 도메인 연결: https://vercel.com/docs/domains/set-up-custom-domain

공개 소개 페이지 자체가 Google의 기술 요구사항을 충족할 수 있으며, 실제 앱 출시는 해당 기술 요구사항이 아닙니다. 기술 요구사항 충족이나 사이트맵 제출만으로 색인 또는 순위는 보장되지 않습니다.
