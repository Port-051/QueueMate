import type { QmUser } from './api';
import { show } from './api';

const GAMES = ['LOL', 'VALORANT', 'PUBG'] as const;

export interface ResetOptions {
  /**
   * 매칭 요청이 확정(`MATCHED` — 활성 요청 `status=PARTY`)이면 풀릴 때까지 기다린다. 확정된 요청은 **지울 길이 없고 60초 뒤 저절로 사라진다**
   * (matching D-42 — 그 사이 글 쓰기 · 입장 · 자동 합류 · 새 매칭이 409 `ALREADY_QUEUED` 다). 대기열 · 방을 쓰는 시나리오만 켠다.
   */
  waitIdle?: boolean;
  /** 로그를 남길 곳(없으면 조용히). */
  log?: (line: string) => void;
}

/**
 * 한 사람을 "아무것도 안 하는" 상태로 돌린다 — 시나리오 앞(과 뒤)에 부른다.
 * 방에서 나오고 · 대기 중인 매칭을 취소하고 · 모집 중인 내 글을 만료시키고 · 차단 · 친구 · 친구 요청을 걷고 · LoL 게임 계정을 끊는다.
 *
 * - **제안(`PROPOSED`)은 거절하지 않고 취소한다** — 거절은 그 상대를 한동안 다시 만나지 않게 적어 둔다(matching D-45 `qm:user:declined:*`).
 *   그러면 뒤 시나리오의 같은 두 사람이 매칭되지 않는다. 취소(`DELETE /match-requests/{requestId}`)는 제안을 깨고 나올 뿐이다.
 * - 강퇴 · 나가기의 10분 금지 목록(P-32)과 최근 함께한 사람은 걷는 API 가 없다 — 시나리오가 새 방을 만들고, 시각으로 가른다.
 */
export async function reset(user: QmUser, options: ResetOptions = {}): Promise<void> {
  const log = options.log ?? (() => undefined);

  // 1. 방 — 방장이면 확정 전 글도 같이 만료된다(방과 글은 같이 산다 — P-22)
  const room = await user.get<{ roomId: string | null }>('/rooms/me');
  if (room.status === 200 && room.body?.roomId) {
    const left = await user.del(`/rooms/${encodeURIComponent(room.body.roomId)}/members/me`);
    log(`${user.nickname}: 방 ${room.body.roomId} 에서 나감 ${left.status}`);
  }

  // 2. 매칭
  await clearMatching(user, options);

  // 3. 모집 중인 내 글(방이 먼저 사라진 글이 남았을 때 — 목록 조회가 만료로 옮기기도 한다)
  for (const game of GAMES) {
    const list = await user.get<{ posts: { postId: number; hostId: number; status: string }[] }>(`/posts?game=${game}&limit=100`);
    for (const post of list.body?.posts ?? []) {
      if (post.hostId === user.id && post.status === 'RECRUITING') {
        const r = await user.del(`/posts/${post.postId}`);
        log(`${user.nickname}: 모집 중인 글 ${post.postId} 만료 ${r.status}`);
      }
    }
  }

  // 4. 차단
  const blocks = await user.get<{ blocks: { userId: number }[] }>('/blocks');
  for (const block of blocks.body?.blocks ?? []) await user.del(`/blocks/${block.userId}`);

  // 5. 친구 · 친구 요청(받은 것은 거절 · 보낸 것은 거두기)
  const friends = await user.get<{ friends: { userId: number }[] }>('/friends');
  for (const friend of friends.body?.friends ?? []) await user.del(`/friends/${friend.userId}`);
  const received = await user.get<{ requests: { requestId: number }[] }>('/friend-requests?direction=RECEIVED');
  for (const request of received.body?.requests ?? []) await user.post(`/friend-requests/${request.requestId}/decline`);
  const sent = await user.get<{ requests: { requestId: number }[] }>('/friend-requests?direction=SENT');
  for (const request of sent.body?.requests ?? []) await user.del(`/friend-requests/${request.requestId}`);

  // 6. LoL 게임 계정(시나리오 9 가 잇는다 — e2e 사용자 말고는 건드리지 않는다)
  const me = await user.get<{ gameAccounts: { game: string }[] }>('/users/me');
  if ((me.body?.gameAccounts ?? []).some((account) => account.game === 'LOL')) {
    const r = await user.del('/users/me/game-accounts/LOL');
    log(`${user.nickname}: LoL 계정 연결 해제 ${r.status}`);
  }
}

export interface MatchView {
  status: 'IDLE' | 'QUEUED' | 'PROPOSED' | 'MATCHED';
  requestId?: string;
  partyId?: string;
  target?: number;
  memberCount?: number;
  expiresAt?: number;
  isAccepted?: boolean;
}

/** 대기 · 제안은 취소하고, 확정이면 `waitIdle` 일 때 풀릴 때까지(최대 80초) 기다린다. */
export async function clearMatching(user: QmUser, options: ResetOptions = {}): Promise<void> {
  const log = options.log ?? (() => undefined);
  const deadline = Date.now() + 80_000;
  for (;;) {
    const view = await user.get<MatchView>('/match-requests');
    if (view.status !== 200) throw new Error(`${user.nickname}: 매칭 상태 조회 실패 ${show(view)}`);
    const { status, requestId } = view.body;
    if (status === 'IDLE') return;
    if ((status === 'QUEUED' || status === 'PROPOSED') && requestId) {
      const r = await user.del(`/match-requests/${requestId}`);
      log(`${user.nickname}: 매칭 요청 ${requestId}(${status}) 취소 ${r.status}`);
      continue;
    }
    if (status === 'MATCHED') {
      if (!options.waitIdle) return;
      if (Date.now() > deadline) throw new Error(`${user.nickname}: 확정된 매칭 요청이 80초 안에 풀리지 않았다`);
      await new Promise((resolve) => setTimeout(resolve, 2_000));
      continue;
    }
    throw new Error(`${user.nickname}: 알 수 없는 매칭 상태 ${show(view)}`);
  }
}

/** 확정된 매칭 요청이 풀릴 때까지 기다린다(대기 · 제안은 취소). 걸린 시간(ms)을 돌려준다. */
export async function waitMatchIdle(user: QmUser): Promise<number> {
  const started = Date.now();
  await clearMatching(user, { waitIdle: true });
  return Date.now() - started;
}
