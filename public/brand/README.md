# QueueMate 브랜드 파일

서비스 브랜드 파일은 이 폴더에서 관리한다. 게임 로고·일반 UI 아이콘은 별도다.

## 심볼

- **`queuemate-mark.svg`가 화면·favicon·아바타 심볼의 단일 원본이다.** 말풍선 둘이 겹쳐 Q가 된 기존 윤곽과 보라색(`#9560ff`)을 유지한다.
- `src/components/Logo.tsx`의 `LogoMark`는 이 파일을 이미지로 읽고, `LogoGlyph`는 `<use>`로 같은 파일의 `#glyph` path를 참조한다. `index.html`의 SVG favicon도 같은 파일을 가리킨다. 윤곽은 이 SVG의 path 한 곳에서만 수정한다.
- 아바타는 기존 SVG 표시 방식을 유지하고 `currentColor`로 흰색을 적용한다. `src/styles/theme.css`의 `.avatar-glyph`가 높이를 얼굴 지름의 60%로 정한다. 흰색 SVG 사본이나 TSX의 별도 path를 만들지 않는다.
- 헤더의 기존 `mix-blend-mode: screen`은 유지한다.

### 애플 홈 화면 아이콘

`queuemate-symbol.png`는 사용자가 선택한 1254×1254 원본(검정 배경)이며, `index.html`의 `apple-touch-icon`에서 사용한다. 현재 홈 화면 아이콘 모양을 유지하기 위해 그대로 보존한다. 벡터 심볼과 표현이 다르므로 벡터 수정만으로 PNG가 바뀌지는 않는다.

## 워드마크

`queuemate-wordmark.svg`는 외부 폰트 로딩이 필요 없는 벡터 윤곽 파일이다.

- [Discord 워드마크 제작사 Dinamo의 설명](https://abcdinamo.com/custom/discord)을 참고했다. 넓고 굵은 획, 원형과 사각형의 균형, 조정된 글자 비례를 벤치마크했다.
- 기본 글자 윤곽은 [Gabarito](https://github.com/google/fonts/tree/main/ofl/gabarito)의 900 굵기다. 사용 허가와 저작권 고지는 `Gabarito-OFL.txt`에 보존한다. Discord 전용 폰트 파일이나 글자 윤곽은 사용하지 않았다.
- Q는 O의 열린 내부 공간을 유지하면서 짧고 둥근 꼬리를 새로 그렸다. 소문자 사이 간격과 M 앞의 단어 경계를 조정했다.
- 색상은 `#F3F1FF`. 심볼이 색을 담당하고 워드마크는 단일 색으로 읽히도록 구성한다.

공통 `Logo` 컴포넌트에서 홈의 사이드바, 랜딩 페이지, 로그인·회원가입·약관 화면에 동일하게 적용한다.

## 정리 이력

2026-10-12: 아바타의 중복 SVG 윤곽을 제거하고 공통 심볼 파일로 통합했다. 코드에서 사용하지 않는 PNG 내장 `queuemate-symbol.svg`, `src/assets/brand-mark.svg`, `public/favicon.svg`, `public/favicon-32.png`, `public/apple-touch-icon-180.png`는 제거했다. 공통 SVG에는 참조용 `id="glyph"`만 추가했고, 윤곽·색·viewBox·PNG·워드마크는 변경하지 않았다.

2026-10-12 후속 수정: 사용자 요청으로 중앙 빈 공간의 오른쪽 위 사선을 왼쪽 아래와 같은 반지름 70의 둥근 모서리로 바꿨다. 바깥 윤곽·색·크기와 나머지 모서리는 유지한다.
