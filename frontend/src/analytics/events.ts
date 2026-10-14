import { track as trackFunnel, analyticsEnabled } from './ga';

/**
 * 사용자가 하는 모든 행동을 GA4 이벤트로 — 2026-10-13 소유자 지시("빠른 매치가 아니라 방 들어가기 방 만들기 등등 사용자가 할 수 있는 모든 거에 대해서 다 이벤트 넣어").
 *
 * 두 갈래다.
 * 1. **서버를 바꾸는 요청(POST · PUT · PATCH · DELETE)이 성공하면 `api/http.ts` 가 여기 {@link trackRequest} 를 부른다** — 화면마다 손으로 넣지 않아 빠지는 행동이 없고
 *    새 API 가 생겨도 저절로 잡힌다. 이벤트 이름은 아래 {@link KNOWN} 표의 친숙한 이름이 먼저, 없으면 경로에서 만든 이름(`api_post_rooms_members` 꼴).
 * 2. **서버를 안 거치는 행동**(마이크 켬 · 끔 · 채팅 전송 · 온보딩 건너뛰기 · 게시판 필터 · 게임 레일)은 그 화면이 {@link trackAction} 을 직접 부른다.
 *
 * **개인정보를 싣지 않는다** — 경로의 번호(사용자 · 글 · 방 · 요청 번호 · UUID)는 `{id}` 로 지운다. 값에는 게임 · 모드 · 결과 코드 같은 구분값만 둔다.
 * 반복 신호(접속 확인 · 시그널 · 재발급 · 개발용 로그인)는 세지 않는다({@link SKIP}).
 */

/** 경로 템플릿(번호 · UUID 를 `{id}` 로 바꾼 것) + 메서드 → 이벤트 이름. GA4 이름 규칙: 영문 · 숫자 · 밑줄 · 40자 이내. */
const KNOWN: Record<string, string> = {
  'POST /auth/logout': 'logout',
  'POST /auth/social/signup': 'sign_up_submit',
  'DELETE /auth/account': 'account_delete',
  'PATCH /users/me': 'profile_update',
  'PUT /users/me': 'profile_update',
  'PUT /users/me/game-accounts/{id}': 'game_account_link',
  'DELETE /users/me/game-accounts/{id}': 'game_account_unlink',
  'DELETE /users/me/social/{id}': 'social_unlink',
  'POST /posts': 'room_create',
  'DELETE /posts/{id}': 'post_delete',
  'PATCH /posts/{id}': 'room_settings_save',
  'POST /posts/auto-join': 'match_auto_join',
  'POST /match-requests': 'match_request_queue',
  'DELETE /match-requests/{id}': 'match_cancel',
  'POST /proposals/{id}/accept': 'proposal_accept',
  'POST /proposals/{id}/decline': 'proposal_decline',
  'POST /match-parties/{id}/room': 'match_room_enter',
  'POST /rooms/{id}/members': 'room_enter',
  'DELETE /rooms/{id}/members/me': 'room_leave',
  'DELETE /rooms/{id}/members/{id}': 'room_kick',
  'POST /rooms/{id}/confirm': 'room_confirm',
  'POST /rooms/{id}/recruitment/extend': 'recruitment_extend',
  'POST /friend-requests': 'friend_request',
  'POST /friend-requests/{id}/accept': 'friend_accept',
  'POST /friend-requests/{id}/decline': 'friend_decline',
  'DELETE /friend-requests/{id}': 'friend_cancel',
  'DELETE /friends/{id}': 'friend_remove',
  'POST /blocks': 'block',
  'DELETE /blocks/{id}': 'unblock',
  'POST /reports': 'report',
  'POST /messages/{id}': 'dm_send',
  'PUT /reservations/{id}': 'reservation_save',
  'DELETE /reservations/{id}': 'reservation_cancel',
};

/** 세지 않는 요청 — 반복 신호 · 개발 전용. */
const SKIP = [/^\/rooms\/[^/]+\/heartbeat$/, /^\/rooms\/[^/]+\/signals$/, /^\/match-requests\/[^/]+\/heartbeat$/, /^\/auth\/refresh$/, /^\/auth\/dev-login$/];

/** `/rooms/123/members?position=TOP` → `/rooms/{id}/members`. 숫자 · UUID · 대문자 enum(`LOL` · `KAKAO`)을 `{id}` 로. */
export function templatePath(fullPath: string): string {
  const path = fullPath.split('?')[0];
  return path.split('/').map((seg, i) => (i === 0 ? seg : /^(\d+|[0-9a-f-]{36}|[A-Z][A-Z0-9_]*)$/.test(seg) ? '{id}' : seg)).join('/');
}

/** 경로에서 만든 이름 — `api_post_rooms_members`(40자 안). */
function autoName(method: string, template: string): string {
  const slug = template.replace(/\{id\}/g, '').replace(/[^a-z0-9]+/gi, '_').replace(/^_+|_+$/g, '').toLowerCase();
  return `api_${method.toLowerCase()}_${slug}`.slice(0, 40);
}

export type ActionParams = Record<string, string | number | boolean>;

/** 성공한 변경 요청 하나 — `api/http.ts` 가 부른다. */
export function trackRequest(method: string, fullPath: string, status: number, params: ActionParams = {}): void {
  if (!analyticsEnabled()) return;
  const upper = method.toUpperCase();
  if (upper === 'GET' || upper === 'HEAD' || upper === 'OPTIONS') return;
  const path = fullPath.split('?')[0];
  if (SKIP.some((re) => re.test(path))) return;
  const template = templatePath(fullPath);
  const name = KNOWN[`${upper} ${template}`] ?? autoName(upper, template);
  send(name, { status, ...params });
}

/** 서버를 안 거치는 행동 — 화면이 직접 부른다. 이름은 영문 소문자 · 밑줄. */
export function trackAction(name: string, params: ActionParams = {}): void {
  if (!analyticsEnabled()) return;
  send(name, params);
}

function send(name: string, params: ActionParams): void {
  // 값에서 개인정보가 될 수 있는 긴 문자열은 자른다(이름은 구분값만 받는 것이 규칙이지만 안전망).
  const safe: ActionParams = {};
  for (const [key, value] of Object.entries(params)) safe[key] = typeof value === 'string' ? value.slice(0, 40) : value;
  // ga.ts 의 track 은 퍼널 이름만 받게 타입을 좁혀 두었다 — 여기서는 모든 행동 이름을 보낸다.
  (trackFunnel as unknown as (event: string, params: ActionParams) => void)(name, safe);
}
