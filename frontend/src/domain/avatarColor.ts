/**
 * 얼굴(아바타)의 색 — Discord 의 기본 아바타처럼 **모두 같은 아이콘(QueueMate 로고 실루엣)에 배경색만 사람마다 다르다**(2026-09-30 소유자 결정 — 안 A).
 * 소유자의 말 — "중요한 점은 같은 방 내에 있는 사람들은 색깔이 다 달라야 한다는 거야. 색깔이 다 달라야지 구별이 가능하니까".
 *
 * - **집 색**(`homeColor`) — `팔레트[사용자 번호 mod 10]`(= 번호의 끝자리). 방 밖(내 정보 · 왼쪽 레일 · 친구 · 최근 함께한 사람 · 차단 · DM · 방 채팅 밖)은 늘 이 색이다.
 * - **방 색**(`roomColors`) — 한 방 · 한 파티의 사람을 한꺼번에 그릴 때는 **모두 다른 색**이다. 방은 많아야 5명이고 색은 10개라 늘 다 다르게 나눌 수 있다.
 *   순서 = **방장 먼저 → 나머지는 사용자 번호 오름차순**, 저마다 집 색이 비어 있으면 그 색 · 이미 누가 가졌으면 **팔레트 번호가 위로 다음인 빈 색**(10 다음은 0).
 *   같은 방이면 어느 브라우저에서 봐도 같은 색이다(입력이 사람 번호와 방장뿐이다).
 *
 * 팔레트 · 순서 규칙 · 로고 실루엣은 **Claude 가 정한 세부**다(소유자 검토 항목 — `CLAUDE.md` §3-30).
 * 사람의 번호는 십진 문자열(`"42"`)이다 — 방 응답 · 알림 `payload` · `AuthContext.userId` 와 같은 글자. 숫자로 받아도 된다.
 */

/**
 * 팔레트 10색 — 흰 아이콘과의 명암비(WCAG)가 전부 3 : 1 이상이고(가장 낮은 것 3.46 — 주황 · 청록), 어두운 화면(`--app-background` #0b0d18 · `--panel-3` #171a33)과도 2.4 : 1 이상 떨어진다.
 * **번호 순서는 색상환 순서가 아니다** — 이웃한 번호끼리 가장 다르게 늘어놓았다. 사용자 번호가 이어진 사람(끝자리가 이웃)과, 색이 겹쳐 "다음 빈 색" 으로 밀린 사람이
 * 이웃 번호를 받기 때문이다(OKLab 거리로 이웃의 최소 22 · 전체 쌍의 최소 12.7 — 적록 색약 흉내(Machado 2009)로도 이웃은 14 이상).
 * 흰 아이콘 명암비 — 0 빨강 5.68 · 1 회색 6.77 · 2 주황 3.46 · 3 남색 7.04 · 4 겨자 3.52 · 5 자홍 5.27 · 6 파랑 3.64 · 7 초록 6.14 · 8 보라 4.96 · 9 청록 3.46.
 */
export const AVATAR_PALETTE = [
  '#C8202C', // 0 빨강
  '#535C69', // 1 회색
  '#E26410', // 2 주황
  '#4249C4', // 3 남색
  '#A38600', // 4 겨자
  '#C81E86', // 5 자홍
  '#2586F2', // 6 파랑
  '#23702A', // 7 초록
  '#9C45D6', // 8 보라
  '#0B9A96', // 9 청록
] as const;

const COUNT = AVATAR_PALETTE.length;

type UserKey = string | number;

/** 닉네임 같은 글자의 해시 → 팔레트 번호. **사용자 번호를 모를 때만** 쓰는 마지막 수단이다(지금은 로그인 전 · 이름도 번호도 없는 자리뿐). */
export function nameColor(name: string | null | undefined): number {
  let hash = 0;
  for (const char of name ?? '') hash = (hash * 31 + (char.codePointAt(0) ?? 0)) >>> 0;
  return hash % COUNT;
}

/** 집 색 — `사용자 번호 mod 10`. 십진 문자열이면 끝자리다(아주 큰 번호도 그대로). 숫자가 아닌 글자는 해시로(`nameColor`). */
export function homeColor(userId: UserKey | null | undefined): number {
  if (typeof userId === 'number') return Number.isInteger(userId) ? ((userId % COUNT) + COUNT) % COUNT : nameColor(String(userId));
  const id = (userId ?? '').trim();
  return /^\d+$/.test(id) ? Number(id.slice(-1)) % COUNT : nameColor(id);
}

/** 사용자 번호의 오름차순 — 십진 문자열을 자릿수 → 글자 순으로(숫자로 바꾸지 않아 큰 번호도 정확하다). 숫자가 아닌 것은 뒤로. */
function byUserId(a: string, b: string): number {
  const da = /^\d+$/.test(a), db = /^\d+$/.test(b);
  if (da !== db) return da ? -1 : 1;
  if (da && a.length !== b.length) return a.length - b.length;
  return a < b ? -1 : a > b ? 1 : 0;
}

/**
 * 한 방 · 한 파티 사람들의 색 — 사람 번호 → 팔레트 번호. **많아야 10명까지 모두 다르다**(그보다 많으면 11번째부터 집 색 — 방은 5명을 넘지 않는다).
 *
 * 순서 — ① 방장(`hostId` — `memberIds` 에 없어도 먼저 자리를 잡는다: 게시판 카드와 방 화면이 같은 색을 내게) ② `memberIds` 의 나머지를 사용자 번호 오름차순
 * ③ `lateIds`(카드에 아직 없는 늦게 온 사람 등)의 나머지를 사용자 번호 오름차순. 저마다 집 색이 비어 있으면 그 색, 아니면 번호가 위로 다음인 빈 색이다.
 * ③ 을 따로 두는 까닭 — ①②만으로 정한 색(게시판 카드의 색)을 늦게 온 사람이 바꾸지 않게.
 */
export function roomColors(memberIds: readonly UserKey[], hostId?: UserKey | null, lateIds: readonly UserKey[] = []): Map<string, number> {
  const colors = new Map<string, number>();
  const taken = new Set<number>();
  const place = (id: string) => {
    if (!id || colors.has(id)) return;
    let color = homeColor(id);
    if (taken.size < COUNT) while (taken.has(color)) color = (color + 1) % COUNT;
    colors.set(id, color);
    taken.add(color);
  };
  const host = hostId === null || hostId === undefined ? '' : String(hostId);
  place(host);
  [...new Set(memberIds.map(String))].sort(byUserId).forEach(place);
  [...new Set(lateIds.map(String))].sort(byUserId).forEach(place);
  return colors;
}
