# 큐메이트 SEO 공개 준비 점검

검사 시각: 2026-10-01 04:03:30 KST (2026-09-30T19:03:29.999Z)
검사 환경: GitHub Actions / 익명 HTTP·DNS 조회
구매 도메인 및 현재 소스 기준: queue-mate.com

## 실제 확인 결과

| 항목 | 결과 |
|---|---|
| 기존 랜딩 주소 | https://queue-mate-landing.vercel.app/ 에서 HTTP 200, 기존 승인 제품 설명과 참여 예시 확인 |
| 현재 배포 제목 | 롤 듀오 구하기·자동 매칭·파티 음성 채팅 \| 큐메이트 |
| 수정 예정 제목 | 롤 듀오 찾기·파티 구하기 \| 큐메이트 |
| 최신 SEO 문구 | GitHub 수정본과 실제 배포 제목이 달라 미반영으로 확인 |
| 임시 주소 검색 정책 | HTML과 X-Robots-Tag 모두 noindex, nofollow |
| 도메인 네임서버 | ns.gabia.co.kr, ns1.gabia.co.kr, ns.gabia.net |
| 도메인 A·AAAA 및 www CNAME | ETIMEOUT. 레코드가 없다는 판정이 아니라 조회 시간 초과 |
| 정식 도메인과 www HTTPS | EAI_AGAIN. 이번 검사에서 정상 접속을 확인하지 못함 |
| Vercel 관리 연결 | port-52 팀 조회에서 403 Forbidden. 팀 접근 재승인 필요 |
| 배포 자동화 | 기존 seo-benefits-release.yml이 af1201484256ac2feb5c6339cf29447c7ff4b5bc에 고정돼 있음 |

## 이번 작업

오래된 임시 배포 주소를 검사하던 워크플로를 현재 주소와 정식 도메인을 확인하도록 수정했습니다. 로컬에서 JavaScript 구문, 메타정보 파싱·URL 비식별화, YAML과 읽기 전용 권한을 검증했고, GitHub Actions에서 실제 HTTP·DNS 검사를 실행해 위 결과를 읽었습니다.

검사 코드 커밋: 5e3b5d3feb2ca767cd1533602e61ed1a219ba81a
기존 SEO 문구 커밋: 9ed059501d2689e0eddc2856f07fde956918afb4
기존 SEO 수정본은 별도 검증 실행에서 npm test·npm run build·미리보기 HTTP 검사 성공을 확인했습니다.

랜딩 본문·스타일·이미지·배포·DNS·색인 설정·계정 권한은 이번 작업에서 변경하지 않았습니다. 검색엔진 소유확인과 사이트맵 제출도 수행하지 않았습니다. 검사 작업의 성공은 배포 성공이나 검색 등록 완료가 아닙니다.

## 이어서 필요한 작업

1. Vercel 연결을 port-52 팀과 queue-mate-landing 프로젝트에 접근 가능한 계정으로 재승인합니다.
2. 해당 프로젝트의 현재 DNS 안내값과 가비아 DNS 설정을 대조합니다. 시간 초과만으로 기존 레코드를 삭제하거나 교체하지 않습니다.
3. 배포 대상 소스를 최신 SEO 수정본으로 맞추고 기존 프로젝트에 재배포합니다. 디자인은 유지합니다.
4. 정식 도메인의 익명 HTTPS 접속을 확인한 뒤 검색 공개를 활성화하고 사이트맵을 검사합니다. 임시 vercel.app 주소의 noindex는 유지합니다.
5. 소유자의 Google·네이버 계정에서 소유확인과 사이트맵 제출을 진행합니다. 실제 발급된 검증값만 적용합니다.

## 실행 근거

- 현재 HTTP·DNS 검사: https://github.com/Port-051/QueueMate/actions/runs/36762937914
- 상세 검사 작업: https://github.com/Port-051/QueueMate/actions/runs/36762937914/job/110049829792
- 검사 결과 아티팩트: https://github.com/Port-051/QueueMate/actions/runs/36762937914/artifacts/11119602363
- 기존 SEO 수정본 검증: https://github.com/Port-051/QueueMate/actions/runs/36762227568
- 배포 소스 고정 설정: https://github.com/Port-051/QueueMate/blob/9ed059501d2689e0eddc2856f07fde956918afb4/.github/workflows/seo-benefits-release.yml

## 계정·DNS 공식 안내

- ChatGPT 연결 관리: https://help.openai.com/en/articles/11487775-connected-apps-in-chatgpt
- Vercel MCP 인증 안내: https://vercel.com/docs/agent-resources/vercel-mcp
- 가비아 DNS 설정 위치: https://customer.gabia.com/faq/detail/2929/2932
