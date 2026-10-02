/**
 * 개인정보 처리방침 · 이용약관 · 푸터가 같이 쓰는 값(2026-10-02 소유자 결정 — 운영자 표기는 팀 이름 + 담당자 실명).
 *
 * 값은 전부 2026-10-02 에 소유자가 준(확정한) 것이다 — 바꿀 때는 이 파일 한 곳만 고친다(두 문서 · 홈 · 푸터가 여기서 읽는다 · 이메일의 `mailto:` 도).
 * **소유자가 주지 않은 값을 지어 넣거나 다른 곳(git 설정 · 계정 메일)에서 가져와 채우지 않는다** — 새로 정할 값이 생기면 묻는다.
 * (값이 비어 있던 동안 쓰던 대괄호 자리표시와 그것을 노란 글자로 그리던 `isPlaceholder` · `Ph` · `.legal-ph` 는 다 채운 날 걷었다.)
 */
export const LEGAL = {
  /** 운영 주체 — 팀 이름 */
  teamName: 'Port-051',
  /** 개인정보 보호책임자 = 담당자 실명 */
  officerName: '김동연',
  /** 문의 · 권리 행사 · 삭제 요청을 받는 이메일 */
  contactEmail: 'findkim22@gmail.com',
  /** 두 문서의 시행일(같은 날 — 2026-10-02) */
  effectiveDate: '2026년 10월 2일',
} as const;

/**
 * Riot Games 가 요구하는 고지문(General Policies — "You must post the following legal boilerplate to your product in a location that is readily visible to players").
 * 원문(https://developer.riotgames.com/policies/general · LAST UPDATED May 29, 2025 · 2026-10-02 확인)의 `[Your product]` 자리에 서비스 이름만 넣었다 — **글자를 바꾸지 않는다.**
 */
export const RIOT_NOTICE_EN =
  "QueueMate isn't endorsed by Riot Games and doesn't reflect the views or opinions of Riot Games or anyone officially involved in producing or managing Riot Games properties. Riot Games, and all associated properties are trademarks or registered trademarks of Riot Games, Inc.";

/** 위 고지문의 한국어 번역(참고용 — 효력이 있는 것은 영어 원문이다). */
export const RIOT_NOTICE_KO =
  'QueueMate는 Riot Games의 보증을 받지 않았으며, Riot Games 또는 Riot Games 자산의 제작·관리에 공식적으로 관여하는 누구의 견해나 의견도 반영하지 않습니다. Riot Games 및 관련된 모든 자산은 Riot Games, Inc.의 상표 또는 등록 상표입니다.';
