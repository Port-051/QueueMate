# 게임 로고 출처

`frontend/src/assets/game-logo-*.webp` 세 개는 **우리가 만든 것이 아니라 각 배포사의 공식 자산**이다.
다른 에셋(`queuemate-assets/final/`)과 달리 재생성할 수 없고, 마음대로 고칠 수도 없다.

## 받은 곳

| 파일 | 원본 | 받은 곳 |
|---|---|---|
| `game-logo-lol.webp` | `lol-logo-rendered-hi-res.png` (3331×2160) | Riot Games 프레스 페이지 <https://www.riotgames.com/en/press/riot-games-assets> |
| `game-logo-valorant.webp` | `valorant-logos.zip` → `Lockup Horizontal/V_Lockup_Horizontal_Neg_Off-White.png` (4000×2251) | 같은 곳 |
| `game-logo-pubg.webp` | 공식 사이트 헤더 로고 (524×178) | PUBG: BATTLEGROUNDS 공식 사이트 <https://www.pubg.com/> |

VALORANT은 밝은 배경용(`Pos`)과 어두운 배경용(`Neg`)이 따로 있다. 우리 화면은 어두우므로 `Neg`다.

## 가공

투명 여백을 잘라내고 **높이 96px로 줄인 것이 전부다.** 색·비율·구성은 원본 그대로다.
배포사 브랜드 규정은 공통적으로 로고 변형(색 바꾸기, 늘리기, 요소 재배치, 효과 추가)을 금지한다.
화면에서 크기를 바꿀 때도 CSS `height`만 주고 비율은 건드리지 않는다 (`GameWordmark.tsx`).

96px인 이유는 배너에서 21~30px로 쓰기 때문이다. 3배 화면까지 감당한다.

## 조건

Riot Games 자산은 커뮤니티 프로젝트에 쓸 수 있지만 **보증을 시사해서는 안 된다.**
그래서 화면에 고지를 단다 (`AppShell`의 좌측 하단). 이 문구는 지우지 않는다.

PUBG 자산은 KRAFTON 프레스룸의 별도 이용 약정을 따른다
(<https://press.krafton.com/PUBG-Asset-Usage-and-Video-Agreement>). 프레스룸 본문은 등록해야 볼 수 있어
이 저장소에는 조건 전문을 옮겨 두지 않았다. **서비스를 공개하기 전에 확인이 필요하다.**

상표는 각 소유자의 것이다. 이 저장소는 로고를 인용해 쓸 뿐 권리를 주장하지 않는다.
