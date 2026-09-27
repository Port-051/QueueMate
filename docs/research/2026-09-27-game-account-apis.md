# 게임 계정·전적 공식 API 조사

확인일: 2026-09-27. 문서와 공식 응답 스키마를 조사했으며, 실제 사용자 키로 API를 호출한 결과는 아니다.

## 무엇을 가져올 수 있나

| 게임 | 공식 API로 가능한 데이터 | 조건·한계 |
| --- | --- | --- |
| 리그 오브 레전드 | 솔로/듀오·자유 랭크 티어와 단계, 승/패, 최근 경기, 사용 챔피언, 킬·데스·어시스트, 챔피언 숙련도 | Riot ID → PUUID로 조회. 최근 경기 표본으로 승률·KDA·많이 쓴 챔피언을 계산. 계정 주인 인증은 조회와 별개 |
| 발로란트 | 경기 목록, 경기별 요원, 킬·데스·어시스트, 경기 결과, 경기 응답의 competitiveTier | Production 승인 및 RSO 로그인·데이터 공개 동의 필요. 일반 유저 모두의 현재 RR를 얻는 API는 공식 목록에서 확인되지 않음. 경기 당시 티어를 현재 티어로 단정하지 않음 |
| 배틀그라운드 | 시즌별 현재 티어·단계·랭크 점수, 경기 수·승수·KDA, 경기 기록·텔레메트리, 무기 숙련도 | steam/kakao 등 플랫폼 구분 필수. 경기 상세와 목록은 최근 14일 범위이므로 항상 20경기를 확보할 수 있지는 않음 |

롤은 [LEAGUE-V4](https://developer.riotgames.com/apis#league-v4), [MATCH-V5](https://developer.riotgames.com/apis#match-v5), [CHAMPION-MASTERY-V4](https://developer.riotgames.com/apis#champion-mastery-v4)를 조합한다. MATCH-V5의 경기 ID 요청은 기본 20개이며, 경기 상세를 추가 조회한다. 챔피언 이미지·이름은 [Data Dragon](https://developer.riotgames.com/docs/lol#data-dragon)을 사용할 수 있다.

발로란트는 [VAL-MATCH-V1](https://developer.riotgames.com/apis#val-match-v1)의 `characterId`, `competitiveTier`, `stats.kills/deaths/assists`를 확인했다. 최근 경기 집계와 공식 콘텐츠 카탈로그를 결합하면 요원 이미지와 기록을 구성할 수 있다. API가 반환하는 플레이 이력은 사용자의 **선호**를 직접 증명하지 않으므로 `최근 많이 플레이한 요원`이라고 표현하는 편이 정확하다.

배그는 [공식 시작 문서](https://documentation.pubg.com/en/getting-started.html)의 ranked/season/lifetime 조회와 [랭크 응답 스키마](https://documentation.pubg.com/en/_static/swagger/en/schemas/rankedGameModeStats.yml)를 확인했다. `currentTier`, `currentRankPoint`, `roundsPlayed`, `wins`, `kda`가 제공된다. 승률은 `wins / roundsPlayed`로 계산하고, 최근 20경기와 시즌 통계를 혼용하지 않는다. [보관 기간](https://documentation.pubg.com/en/making-requests.html#data-retention-period)은 14일이며 시즌 통계의 경기 목록은 그 안에서 최대 32개다.

## 조회와 본인 인증은 다르다

Riot ID/배그 닉네임을 적어 공개 전적을 조회했다고 해서 그 사용자가 계정 소유자라는 뜻은 아니다. Riot의 본인 동의 기반 연결은 [RSO(OAuth)](https://developer.riotgames.com/docs/faqs#rso-riot-sign-on)로 분리한다. RSO는 승인된 Production 애플리케이션이 있어야 신청할 수 있다.

[발로란트 정책](https://developer.riotgames.com/docs/valorant#rso-integration)은 통계 표시와 LFG 서비스에 RSO 동의를 요구하며, Personal Key 신청을 지원하지 않는다. 가입·연결한 사용자만 전적을 공개해야 한다.

PUBG 공식 공개 API는 개발자 API 키와 player ID를 통한 조회 방식이다. 이번 공식 문서 조사에서는 PUBG 계정 소유자를 증명하는 일반 OAuth 연결 절차를 확인하지 못했다. 따라서 닉네임 등록을 `인증 완료`로 표시하지 않는다. Steam 로그인만 붙여도 PUBG player ID와의 소유권 연결이 저절로 해결되는 것은 아니다.

## QueueMate 적용 순서

1. 현재 있는 LoL Riot ID → PUUID → 솔로/자유 랭크 조회 코드에 최근 경기 집계를 붙인다. 아직 최근 경기·챔피언·KDA 서버 연동은 없다.
2. 계정 연결과 전적은 프로필에서 관리한다. 방 조건에는 역할·원하는 상대·모드·음성 등 사용자의 의사만 받는다. 수동 전적 입력은 제거한다.
3. `조회 중 / 연동 안 됨 / 기록 부족 / 갱신 실패`를 구별하고, 표본 경기 수·모드·마지막 갱신 시각을 저장한다. 12경기뿐이면 12경기 기준으로 표시한다. 배그에는 요원·챔피언 필드를 억지로 붙이지 않는다.
4. API 키는 백엔드에만 둔다. 캐시, 동시 조회 합치기, 429 Retry-After 처리로 방 목록을 열 때마다 경기 20개를 다시 요청하지 않는다.
5. 발로란트는 동의 화면·서비스 흐름을 먼저 준비해 Production/RSO 승인을 진행하고, 배그는 플랫폼별 전적 조회를 별도로 붙인다.

[키 종류 안내](https://developer.riotgames.com/docs/portal#web-apis)에 따르면 개발 키는 24시간마다 만료되고, 공개 서비스에는 Production 키가 필요하다. 현재 데모에서 실제 게임사 전적이 연동되었다고 표시하지 않는다.
