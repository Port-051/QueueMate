/**
 * 이 브라우저에 **사용자별로** 남기는 값 — 회원 탈퇴 때 지운다(2026-10-02 소유자 결정 — platform P-48 · `AuthContext#deleteAccount`).
 * 로그아웃은 지우지 않는다(다시 로그인하면 이어 쓴다). **사용자 번호가 든 키를 새로 만들면 여기에도 더한다** — 키를 쓰는 곳(오른쪽)과 글자가 같아야 한다.
 *
 * 사용자 번호가 없는 키(`qm.lastProvider` — 마지막으로 누른 소셜 버튼 · `qm.recentConditions` — 최근 매칭 조건)는 이 브라우저의 것이라 남긴다.
 */
const exactLocalKeys = (userId: string) => [
  `qm.activeParty.${userId}`, // state/MatchContext — 들어간 빠른매치 파티
  `qm.activePartyInfo.${userId}`, // state/MatchContext — 그 파티의 조건
  `qm.activeMatch.${userId}`, // state/MatchContext — 대기 중인 요청의 조건
  `qm.leftParties.${userId}`, // state/MatchContext — 나간 · 강퇴당한 빠른매치 파티
  `qm.onboarding.done:${userId}`, // state/onboarding — 온보딩을 지나갔는가
  `qm:direct-messages:${userId}`, // 2026-10-02 에 걷은 DM(읽는 곳은 없다 — 남은 대화를 지운다)
  `qm:notifications:${userId}`, // 2026-10-02 에 걷은 알림함(읽는 곳은 없다)
];

const localKeyPrefixes = (userId: string) => [
  `queuemate:introduction:v1:${encodeURIComponent(userId)}:`, // domain/introduction — 게임마다 기억한 빠른매치 폼 값
  `queuemate:reservation-draft:v1:${encodeURIComponent(userId)}:`, // state/reservationDraft — 대응물 없는 예약 화면의 임시 저장
];

const exactSessionKeys = (userId: string) => [
  `qm.proposalShown.${userId}`, // state/MatchContext — 제안 화면으로 이미 옮긴 제안
];

/** 그 사람의 키를 지운다. 저장소가 막혀 있으면(사생활 보호 모드 등) 조용히 넘어간다 — 지울 것도 없다. */
export function forgetUserStorage(userId: string) {
  try {
    const prefixes = localKeyPrefixes(userId);
    const matched = Array.from({ length: localStorage.length }, (_, index) => localStorage.key(index))
      .filter((key): key is string => key !== null && prefixes.some(prefix => key.startsWith(prefix)));
    for (const key of [...exactLocalKeys(userId), ...matched]) localStorage.removeItem(key);
  } catch { /* 저장소를 못 쓰면 남은 것도 없다 */ }
  try {
    for (const key of exactSessionKeys(userId)) sessionStorage.removeItem(key);
  } catch { /* 위와 같다 */ }
}
