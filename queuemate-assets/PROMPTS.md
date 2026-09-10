# Codex 이미지 생성 프롬프트 — QueueMate 12장 + 예비 7장

프롬프트 본문은 **영어**로 썼다. 이미지 모델은 영어 프롬프트에서 색 지정과 구도 지시를
훨씬 정확히 따른다. 한국어 설명은 의도를 이해하기 위한 것이고, Codex에는 코드블록 안만 붙여넣는다.

각 항목은 이 순서로 되어 있다:
**의도** (왜 이렇게 그리는가) → **생성 크기** → **프롬프트** → **검수 기준** → **최종 규격**

---

## 공통 — 매 프롬프트 끝에 이미 포함돼 있음

모든 프롬프트 끝에 아래 문단이 들어가 있다. 지우지 말 것.

```
Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

---

# GEN-1 · 후드 마스코트 배너 (P1, 1장)

**의도.** QueueMate는 낯선 사람을 자동으로 붙여주는 시스템이라 본질적으로 차갑다. 마스코트는
그 차가움을 상쇄하는 유일한 요소다. 그래서 **광고용 캐릭터가 아니라 "같이 기다려주는 동료"** 로
보여야 한다. 얼굴을 그리지 않고 발광하는 눈 두 개만 두는 이유가 두 가지다 — 작은 크기에서도
읽히고, 어설픈 얼굴이 주는 불쾌함을 피한다.

배너 좌측 45%는 헤드라인이 얹히는 자리라 **거의 빈 배경으로 비워야 한다.** 이걸 후처리로
지우는 건 불가능하니 프롬프트 단계에서 확보한다.

**생성 크기**: 1536×1024 (landscape)

```
A friendly rounded robot mascot in a dark cinematic banner, positioned in the right
third of the frame, facing slightly toward the viewer's left, upper body only, one
hand raised in a small calm greeting.

The mascot wears a deep purple hood with two soft cat-ear shapes rising from it.
Its face is in shadow; the only visible features are two large round glowing eyes in
warm off-white, softly blooming. The body is smooth, matte, minimal, with no seams,
buttons, badges, or decoration of any kind.

Behind the mascot, a faint constellation of small glowing particles connected by thin
lines spreads outward, like a quiet network diagram, at very low opacity.

The entire left 45 percent of the image is nearly empty deep navy background with only
the faintest ambient glow, reserved as negative space.

Color palette, strictly: background deep navy #020715; hood gradient from #7E2DF2 down
into #5A2FD8; eyes glowing #E8E9F2; particle lines purple at low opacity. Purple must
occupy less than 15 percent of the total image area; the rest is dark navy.

Lighting is soft and moody, single rim light from the upper right. Flat illustration
with subtle gradients, clean vector-adjacent shapes, no heavy texture, no grain.

Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

**검수 기준**
- 좌측 45%가 실제로 비어 있는가 (여기에 무늬가 들어오면 헤드라인이 안 읽힌다)
- 눈 두 개가 **좌우 대칭**인가 — 확산 모델이 자주 틀리는 지점이다. 8~12장 뽑아서 고를 것
- 손에 아무것도 들고 있지 않은가 (목업에는 `Q` 글자를 들고 있는데 글자 금지라 제외)
- 보라가 너무 넓게 깔리지 않았는가

**저장**: `raw/gen1-mascot-banner.png` → **최종 1600×420 WebP**

---

# GEN-2 · 랜딩 배경 아트 (P2, 1장)

**의도.** 이건 **`opacity: 0.28` + `mix-blend-mode: screen`으로 깔릴 배경**이다. 즉 완성본은
지금 보이는 것의 4분의 1 밝기로 흐려진다. 그래서 **잔디테일이 아무 의미가 없고, 큰 덩어리
구조만 살아남는다.** 세밀하게 그릴수록 손해다. 크고 단순한 흐름 하나면 충분하다.

**생성 크기**: 1536×1024 (landscape)

```
An abstract atmospheric background. A single broad current of purple energy flows
diagonally from the upper left toward the lower right through dark empty space, like a
slow nebula drift. Large simple forms only, soft and out of focus, with wide areas of
undisturbed darkness between them.

Scattered fine star-like particles, sparse and small.

No objects, no characters, no architecture, no planets, no recognizable shapes.

Color palette, strictly: dominant deep navy #020715 across most of the frame; the
energy flow in #7E2DF2 with highlights of #9A56FF. Purple must occupy less than 15
percent of the image area.

Very low contrast overall, soft gradients, cinematic and restrained. This image will
be displayed at 28 percent opacity, so it must read through large-scale structure
rather than fine detail.

Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

**검수 기준**
- 눈을 가늘게 뜨고 봤을 때 큰 흐름 하나가 읽히는가
- 어떤 물체로도 안 보이는가 (행성, 얼굴, 손 같은 형상이 생기면 재생성)

**저장**: `raw/gen2-landing-backdrop.png` → **최종 1920×1080 WebP**

---

# GEN-3 · 게임 카드 배경 3종 (P1, 3장)

**의도.** 게임 카드에 배경이 없어서 지금 카드가 비어 보인다. 그런데 **실제 게임의 캐릭터나
로고는 상표·저작권 문제로 절대 못 쓴다.** 그래서 "그 게임이 주는 공간의 분위기"만 그린다.
캐릭터가 없어야 오히려 세 장이 나란히 놓였을 때 톤이 통일된다.

**중요 — 원래 스펙을 내가 뒤집었다.** 문서에는 "우측부터 페이드아웃"이라 적혀 있었는데
카드 텍스트가 좌측에 얹히므로 **어두워져야 하는 쪽은 좌측**이다. 그래서 아래 프롬프트는
**시각적 중심을 우측에 두고 좌측으로 갈수록 평평한 어둠**으로 지시한다.

세 장의 명도·채도가 서로 튀면 카드가 나란히 놓였을 때 조악해진다. **한 번에 세 장을 같은
세션에서 뽑고, 나란히 놓고 비교해서 고를 것.**

**생성 크기**: 1536×1024 (landscape) × 3

### GEN-3a — LoL

```
A dark cinematic environment illustration with no characters and no creatures.
An ancient stone canyon passage, weathered carved blocks, faint runic patterns glowing
softly in the crevices. Mist settles low between the walls.

Composition: the visual interest sits in the right half of the frame; the left third
flattens into near-empty darkness with almost no detail, reserved as negative space.

Color palette, strictly: dominant deep navy #020715 to #141A2C; the rune glow in warm
antique gold #C8AA6E, occupying less than 8 percent of the image area. No other hues.

Wide establishing shot, low ambient light, soft haze, painterly but clean.

Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

### GEN-3b — VALORANT

```
A dark cinematic environment illustration with no characters and no creatures.
A near-future urban tactical zone at night: clean concrete structures, a narrow
corridor between buildings, thin red lighting strips tracing the architecture.

Composition: the visual interest sits in the right half of the frame; the left third
flattens into near-empty darkness with almost no detail, reserved as negative space.

Color palette, strictly: dominant deep navy #020715 to #141A2C; the light strips in
#EF4347, occupying less than 8 percent of the image area. No other hues.

Wide establishing shot, crisp geometry, low ambient light, faint atmospheric haze.

Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

### GEN-3c — PUBG

```
A dark cinematic environment illustration with no characters and no creatures.
A desolate open plain under heavy fog at dusk, abandoned concrete ruins scattered
across a very low horizon, dry grass, overcast sky.

Composition: the visual interest sits in the right half of the frame; the left third
flattens into near-empty darkness with almost no detail, reserved as negative space.

Color palette, strictly: dominant deep navy #020715 to #141A2C; a muted amber light
#F6660E breaking through the fog, occupying less than 8 percent of the image area.
No other hues.

Wide establishing shot, heavy atmospheric depth, muted and bleak.

Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

**검수 기준**
- 세 장을 **나란히 놓고** 봤을 때 밝기가 비슷한가 (한 장만 밝으면 카드가 튄다)
- 좌측 3분의 1이 실제로 비어 있는가
- 사람·실루엣·차량·간판이 하나도 없는가
- 각 게임의 실제 맵이나 상징물을 닮지 않았는가

**저장**: `raw/gen3-lol.png`, `raw/gen3-valorant.png`, `raw/gen3-pubg.png`
→ **최종 각 800×320 WebP** (좌측 페이드 그라디언트는 postprocess.py가 적용)

---

# GEN-4 · 아바타 플레이스홀더 8종 (P1, 8장)

**의도.** 이건 **실제 사람을 대신하는 자리**다. 그래서 두 가지가 중요하다.
첫째, 어떤 특정 인물이나 특정 게임 캐릭터로도 읽히면 안 된다. 둘째, **28~64px로 표시되므로
얼굴 생김새는 어차피 안 보이고 실루엣만 보인다.** 그래서 8종을 얼굴이 아니라 **머리 부분의
실루엣으로 구분**한다. 파티 최대 5명 + 친구 목록 4명이 한 화면에 뜨므로 8종이 필요하다.

**8장의 조명·화각·선 굵기가 반드시 통일돼야 한다.** 한 장만 화풍이 다르면 목록에서 즉시
눈에 띈다. 아래 공통 블록을 8장 모두에 그대로 쓰고, `[VARIANT]` 줄만 교체한다.

**생성 크기**: 1024×1024, **배경 투명(transparent)** 으로 요청할 것

```
A chibi-proportioned character bust, front-facing, centered, shoulders and head only,
on a fully transparent background.

[VARIANT]

Style: flat vector illustration, clean even line weight, simple rounded shapes, minimal
shading with one soft light source from the upper left. No outline glow, no background
elements, no shadow on the ground. The face is simplified with small calm eyes and no
mouth detail.

Color: the body and clothing base are dark navy #141A2C and #070E1D. Exactly one accent
color is used for the distinguishing headwear, specified in the variant line above.

Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
No badges, patches, insignia, or markings on the clothing.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

`[VARIANT]` 자리에 넣을 8줄:

| # | 파일명 | `[VARIANT]` 문장 | 액센트 |
|---|---|---|---|
| 1 | `raw/gen4-avatar-01.png` | `The character wears a soft hood pulled up over the head, accent color #7E2DF2.` | 보라 |
| 2 | `raw/gen4-avatar-02.png` | `The character wears a smooth rounded helmet with a visor across the eyes, accent color #9A56FF.` | 밝은 보라 |
| 3 | `raw/gen4-avatar-03.png` | `The character wears a headset with two cat-ear shapes on top, accent color #24C254.` | 초록 |
| 4 | `raw/gen4-avatar-04.png` | `The character wears a bandana tied around the head, accent color #EF4347.` | 빨강 |
| 5 | `raw/gen4-avatar-05.png` | `The character wears a wide-brimmed hat, accent color #F6660E.` | 주황 |
| 6 | `raw/gen4-avatar-06.png` | `The character wears a simple breathing mask covering the lower face, accent color #5A2FD8.` | 진보라 |
| 7 | `raw/gen4-avatar-07.png` | `The character has a bare head with round goggles pushed up on the forehead, accent color #C8AA6E.` | 금색 |
| 8 | `raw/gen4-avatar-08.png` | `The character wears a knit beanie and a scarf around the neck, accent color #E8E9F2.` | 밝은 회백 |

**검수 기준**
- 8장을 **한 줄로 늘어놓고** 봤을 때 화풍이 같은가 — 이게 제일 중요하다
- **64px로 축소해서** 봤을 때 8종이 서로 구분되는가 (여기서 구분 안 되면 실패다)
- 배경이 진짜 투명인가 (흰색/체크무늬가 구워져 있으면 재생성)
- 옷에 글자·배지·문양이 없는가

**저장**: 위 표의 파일명 → **최종 각 256×256 알파 WebP**

---

# GEN-5 · 빈 상태 일러스트 3종 (P2, 3장)

**의도.** 빈 상태는 **시선을 끌면 안 된다.** 사용자가 보고 싶은 건 목록이지 그림이 아니다.
그래서 GEN-1 마스코트를 쓰되 **채도를 확 낮추고 단색에 가깝게** 간다. 마스코트 조형은
GEN-1과 같아야 하므로, **GEN-1 결과를 참조 이미지로 함께 넣어 생성하는 것을 권장한다.**

**생성 크기**: 1024×1024, **배경 투명(transparent)**

공통 블록 (3장 모두 동일, `[SCENE]`만 교체):

```
The same rounded robot mascot as before: deep purple hood with two soft cat-ear shapes,
face in shadow, two large round glowing off-white eyes, smooth minimal body.
Full body, small in frame, on a fully transparent background.

[SCENE]

Style: flat vector illustration, heavily desaturated and quiet, muted purple as the only
hue, low contrast. This is a placeholder for an empty screen state, so it must feel calm
and recede visually rather than draw attention.

Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
No calendar numbers, no clock faces, no speech bubbles, no icons.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

`[SCENE]` 3종:

| 파일명 | `[SCENE]` 문장 | 쓰이는 곳 |
|---|---|---|
| `raw/gen5-no-match.png` | `The mascot leans forward and peers down into a flat circular device showing empty concentric rings, finding nothing there.` | 매칭·제안·파티 빈 상태 |
| `raw/gen5-no-social.png` | `The mascot sits alone on one end of a simple bench, with the other end conspicuously empty.` | 친구·최근 함께한 사람 빈 상태 |
| `raw/gen5-no-reservation.png` | `The mascot stands in front of a large upright board divided into an empty grid of blank rectangles, none of them filled.` | 예약 빈 상태 |

**검수 기준**
- **눈에 안 띄는가.** 화려하면 실패다
- 격자판에 숫자나 요일이 안 들어갔는가 (달력처럼 보이면 재생성)
- 마스코트 조형이 GEN-1과 같은가
- 배경이 투명인가

**저장**: 위 파일명 → **최종 각 480×360 알파 WebP**

---

# GEN-6 · OG 배경 레이어 (P2, 1장)

**의도.** 소셜 공유 미리보기다. **정적 파일이라 브라우저가 글자를 얹어줄 수 없으므로**,
그래픽만 만들고 카피는 별도 SVG로 빌드 타임에 합성한다. 그래야 문구가 바뀌어도 이미지를
다시 뽑지 않고 SVG 한 줄만 고치면 된다.

따라서 **좌측 55%는 카피가 들어갈 자리라 반드시 비워야 한다.**

**생성 크기**: 1536×1024 (landscape)

```
A wide dark promotional key visual, empty on the left.

The left 55 percent of the frame is almost entirely empty deep navy with only a very
faint ambient gradient, deliberately reserved as negative space for typography that
will be added later.

In the right 45 percent, a friendly rounded robot mascot in a deep purple hood with two
soft cat-ear shapes, face in shadow with two large round glowing off-white eyes, upper
body only, turned slightly toward the left. Behind it, a soft purple radial glow and a
sparse constellation of small glowing particles connected by thin lines.

Color palette, strictly: background #020715; hood #7E2DF2 into #5A2FD8; eyes and
particles #E8E9F2; glow #9A56FF. Purple must occupy less than 15 percent of the image.

Cinematic, restrained, soft rim lighting, flat illustration with subtle gradients.

Absolutely no text, letters, numbers, glyphs, symbols, signage, HUD elements,
watermarks, signatures, or user-interface chrome anywhere in the image.
Do not reference or resemble any real video game, its logos, characters, or assets.
No photorealistic human faces.
```

**검수 기준**
- 좌측 55%가 진짜로 비어 있는가 (여기에 뭐가 있으면 카피가 안 읽힌다)
- 마스코트가 GEN-1과 같은 조형인가

**저장**: `raw/gen6-og-backdrop.png` → **최종 1200×630 PNG** (합성 소스라 무손실 유지)

---

# 생성하지 말 것

아래는 이미지 모델이 못 하거나, 파일 자체가 필요 없는 것들이다. Codex에 시키지 마라.

| 항목 | 이유 |
|---|---|
| 파비콘, 브랜드 마크 | 16px에서 형태가 살아야 하고 리사이즈마다 픽셀 정합이 같아야 한다. 확산 모델은 매 생성마다 형태가 달라져 파일 간 일관성이 깨진다 → SVG로 |
| 게임 심볼 3종 | 30~40px 타일에서 형태와 브랜드 컬러를 정확히 물려야 한다 → SVG로 |
| UI 아이콘 | 기존 21종이 `currentColor`로 색을 상속받는다. 래스터를 섞으면 hover 색 변화가 깨진다 → 인라인 SVG로 |
| 노이즈 타일 | 심리스 타일링이 필요한데 확산 모델은 이음매를 못 맞춘다 → SVG `feTurbulence`가 1KB로 완벽하다 |
| 대기화면 레이더 | **이미 CSS로 완성돼 있다.** 3개 링이 시차 펄스하고 `prefers-reduced-motion`에서 멈춘다. 이미지로 바꾸면 이 동작을 잃는다 |
| 페이지 배경·패널 글로우 | CSS `radial-gradient`가 더 가볍고 반응형에 강하다 |
| 로딩 스피너 | `.skeleton` CSS 애니메이션으로 이미 처리 중 |
| 404 일러스트 | `App.tsx`가 모든 미지 경로를 `/`로 리다이렉트해서 404 화면이 존재하지 않는다 |
| PWA 아이콘 | 데스크톱 전용(`min-width:1180px`)이고 설치형 앱 계획이 없다 |
