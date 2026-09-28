# 리그 오브 레전드 랭크 엠블럼

Riot Games 공식 Developer Portal이 최신 배포본으로 안내하는
`ranked-emblems-latest.zip`의 `Ranked Emblems Latest/Rank={Tier}.png` 원본이다.
파일 이름의 `Rank=` 접두어만 제거했으며, 이미지의 색상·모양·해상도·투명 여백은 수정하지 않았다.

- [공식 문서: Icons and Emblems](https://developer.riotgames.com/docs/lol#ranked-info_icons-and-emblems)
- [공식 ZIP 다운로드](https://static.developer.riotgames.com/docs/lol/ranked-emblems-latest.zip)
- [공식 엠블럼 미리보기](https://static.developer.riotgames.com/img/docs/lol/emblems-and-positions-latest.jpg)
- 확인 및 다운로드: 2026-09-14
- 서버 `Last-Modified`: 2023-10-24 04:55:04 GMT
- ZIP SHA-256: `055a79d26590f2016bc97bd09eea0459c7d29f3a68d850e9c33da006fdb8d72f`

이 ZIP에는 별도의 패치 번호가 없다. 날짜가 과거라는 이유로 오래된 디자인이라고
추정하지 않고, 확인 시점에 Riot 공식 문서가 최신으로 연결하는 배포본을 사용한다.
아이언, 브론즈, 실버, 골드, 플래티넘, 에메랄드, 다이아몬드,
마스터, 그랜드마스터, 챌린저 10개 티어를 포함한다.
언랭크 엠블럼은 이 배포본에 없으므로 임의의 티어 엠블럼으로 대체하지 않는다.

모든 원본은 투명 배경 PNG다. 티어별 투명 여백이 다르므로 UI에서는
원본을 잘라 재생성하지 않고 필요한 표시 영역과 크기를 조정할 수 있다.
`rankAssets.ts`의 `rankEmblemBounds`는 원본 PNG에서 알파 값이 0보다 큰
영역의 경계이며, CSS에서 투명 여백을 제외하고 중앙 정렬할 때 사용한다.
이미지와 리그 오브 레전드 관련 자산의 권리는 Riot Games에 있다.
