# QueueMate 브랜드 파일

2026-09-12 적용.

## 심볼

사용자가 선택한 두 번째 이미지(`exec-f58482b4-c2f1-4818-9d62-5b3d7abc493f.png`)를 사용한다.

- `queuemate-symbol.png`: 선택한 1254×1254 원본 그대로. 검정 배경.
- `queuemate-symbol.svg`: 같은 PNG를 내장한 표시용 SVG. 원본의 여백만 viewBox로 조정한다. 심볼의 윤곽·색·픽셀은 수정하지 않았다. 순수 벡터 심볼은 아니다.
- 어두운 앱 화면에서는 `mix-blend-mode: screen`으로 배경을 합성한다. SVG와 PNG를 브라우저·홈 화면 아이콘으로도 사용한다.

## 워드마크

`queuemate-wordmark.svg`는 외부 폰트 로딩이 필요 없는 벡터 윤곽 파일이다.

- [Discord 워드마크 제작사 Dinamo의 설명](https://abcdinamo.com/custom/discord)을 참고했다. 넓고 굵은 획, 원형과 사각형의 균형, 조정된 글자 비례를 벤치마크했다.
- 기본 글자 윤곽은 [Gabarito](https://github.com/google/fonts/tree/main/ofl/gabarito)의 900 굵기다. 사용 허가와 저작권 고지는 `Gabarito-OFL.txt`에 보존한다. Discord 전용 폰트 파일이나 글자 윤곽은 사용하지 않았다.
- Q는 O의 열린 내부 공간을 유지하면서 짧고 둥근 꼬리를 새로 그렸다. 소문자 사이 간격과 M 앞의 단어 경계를 조정했다.
- 색상은 `#F3F1FF`. 심볼이 색을 담당하고 워드마크는 단일 색으로 읽히도록 구성한다.

공통 `Logo` 컴포넌트에서 홈의 사이드바, 랜딩 페이지, 로그인·회원가입 화면에 동일하게 적용한다.
