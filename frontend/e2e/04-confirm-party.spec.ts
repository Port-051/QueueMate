import { expect, test } from './support/fixtures';
import { show, type QmUser } from './support/api';
import { createPost, enter, lolPost, myRoom, roomMembers, uniqueTitle, type Post } from './support/domain';
import { expectNoEvent, waitForEvent } from './support/sse';

interface RecentPlayer { userId: number; nickname: string; lastPartyId: number | null; lastPlayedAt: string }

async function recentOf(user: QmUser, other: QmUser): Promise<RecentPlayer | undefined> {
  const r = await user.get<{ players: RecentPlayer[] }>('/recent-players');
  expect(r.status, show(r)).toBe(200);
  return r.body.players.find((player) => player.userId === other.id);
}

/**
 * 시나리오 4 — 방장 확정 → 파티 → 닫힘 → 최근 함께한 사람(D-21 · P-25).
 * 확정은 방장만 · 2명 이상 · 되돌릴 수 없다. 확정 뒤 새 입장은 막히고 글은 `CONFIRMED` 로 고정된다.
 * 확정한 방은 방장이 나가도 이어지고(D-23 승계), 마지막 사람이 나가 방이 없어지면 파티가 닫히며 그 순간의 파티원끼리 서로를 `recent_players` 에 적는다.
 * 포지션(P-44 ⑨) — 모집 중에는 카드에 사람마다 포지션이 실리고(방장 미드 · B 가 고른 탑), **확정 뒤 카드의 포지션은 전부 `null`** 이다.
 */
test('시나리오 4 — 확정 · 파티 · 닫힘 · 최근 함께한 사람', async ({ crew }) => {
  const a = await crew.user('a', { sse: true });
  const b = await crew.user('b', { sse: true });
  const c = await crew.user('c', { sse: true });
  const [sseA, sseB, sseC] = [await crew.sse('a'), await crew.sse('b'), await crew.sse('c')];
  // 최근 함께한 사람은 지우는 API 가 없다 — 이번 파티가 새로 적었는지는 이전 값과 시각으로 가른다
  const before = { a: await recentOf(a, b), b: await recentOf(b, a) };
  const post = await createPost(a, lolPost(uniqueTitle('s4')));
  const roomId = String(post.postId);

  await test.step('혼자서는 확정 불가 · B 가 탑으로 입장 · 카드에 포지션 · 방장이 아니면 확정 불가', async () => {
    // 혼자서는 확정할 수 없다(409 NOT_ENOUGH_MEMBERS)
    const alone = await a.post(`/rooms/${roomId}/confirm`);
    expect(alone.status, show(alone)).toBe(409);
    expect(alone.body.code).toBe('NOT_ENOUGH_MEMBERS');
    const r = await enter(b, roomId, 'TOP');
    expect(r.status, show(r)).toBe(201);
    // 모집 중인 글의 카드 — 방장은 글의 방장 포지션 · B 는 고른 것(P-44 ⑨)
    const recruiting = (await a.get<Post>(`/posts/${post.postId}`)).body;
    expect(Object.fromEntries(recruiting.members.map((m) => [m.userId, m.position]))).toEqual({ [a.id]: 'MID', [b.id]: 'TOP' });
    // 방장이 아니면 확정할 수 없다
    const notHost = await b.post(`/rooms/${roomId}/confirm`);
    expect(notHost.status, show(notHost)).toBe(403);
  });

  const confirmedAt = Date.now();
  await test.step('A 확정 → 204 · A · B 에게 ROOM_CONFIRMED(파티원 둘) · C 는 받지 않는다', async () => {
    const [sinceA, sinceB, sinceC] = [await sseA.mark(), await sseB.mark(), await sseC.mark()];
    const r = await a.post(`/rooms/${roomId}/confirm`);
    expect(r.status, show(r)).toBe(204);
    const members = [a.userId, b.userId].sort();
    const eventA = await waitForEvent(sseA, 'ROOM_CONFIRMED', (e) => e.payload.roomId === roomId, { since: sinceA });
    const eventB = await waitForEvent(sseB, 'ROOM_CONFIRMED', (e) => e.payload.roomId === roomId, { since: sinceB });
    expect([...eventA.payload.members].sort()).toEqual(members);
    expect([...eventB.payload.members].sort()).toEqual(members);
    await expectNoEvent(sseC, 'ROOM_CONFIRMED', (e) => e.payload.roomId === roomId, { since: sinceC, within: 500 });
    // 다시 눌러도 성공(200)이고 바뀌는 것은 없다
    const again = await a.post(`/rooms/${roomId}/confirm`);
    expect(again.status, show(again)).toBe(200);
  });

  await test.step('글은 CONFIRMED · 카드의 포지션은 전부 null · C 의 입장은 409', async () => {
    const r = await a.get<Post>(`/posts/${post.postId}`);
    expect(r.body.status).toBe('CONFIRMED');
    // 확정됐지만 방에 아직 사람이 있다 — 파티는 ACTIVE 라 끝나지 않았다(P-46)
    expect(r.body.closed).toBe(false);
    // 확정된 글의 카드(파티원 전원 — P-40)는 포지션을 싣지 않는다(P-44 ⑨ — 확정 · 만료 = null). 방장 카드도 같다
    expect(r.body.members.map((m) => m.userId).sort()).toEqual([a.id, b.id].sort());
    expect(r.body.members.map((m) => m.position)).toEqual([null, null]);
    expect(r.body.host?.position ?? null).toBeNull();
    // 남은 포지션(정글)을 줘도 글 검사(PostEntryGate)가 먼저라 포지션 때문에 막힌 것이 아니다
    const entered = await enter(c, roomId, 'JUNGLE');
    expect(entered.status, show(entered)).toBe(409);
    // 계약(platform-api.md "입장" 의 에러 표 · §3.3 검사 순서)은 글 검사가 먼저라 POST_NOT_RECRUITING 이다. 방의 Lua 까지 가면 ROOM_CONFIRMED.
    expect(['POST_NOT_RECRUITING', 'ROOM_CONFIRMED']).toContain(entered.body.code);
    test.info().annotations.push({ type: '확정된 방에 C 입장', description: `${entered.status} ${entered.body.code}` });
    console.log(`  [시나리오 4] 확정된 방에 C 입장 → ${entered.status} ${entered.body.code}`);
    expect(await myRoom(c)).toBeNull();
  });

  await test.step('A(방장) 나가기 → 방은 이어지고 B 가 방장을 넘겨받는다(D-23)', async () => {
    const sinceB = await sseB.mark();
    const r = await a.del(`/rooms/${roomId}/members/me`);
    expect(r.status, show(r)).toBe(204);
    await waitForEvent(sseB, 'ROOM_MEMBER_LEFT', (e) => e.payload.roomId === roomId && e.payload.userId === a.userId, { since: sinceB });
    await expectNoEvent(sseB, 'ROOM_CLOSED', (e) => e.payload.roomId === roomId, { since: sinceB, within: 300 });
    expect(await roomMembers(b, roomId)).toEqual({ status: 200, hostId: b.userId, members: [b.userId] });
    expect((await a.get<Post>(`/posts/${post.postId}`)).body.status).toBe('CONFIRMED');
  });

  await test.step('B(마지막) 나가기 → 방이 없어지고 파티가 닫힌다 → 서로 최근 함께한 사람', async () => {
    const r = await b.del(`/rooms/${roomId}/members/me`);
    expect(r.status, show(r)).toBe(204);
    expect(await myRoom(b)).toBeNull();
    const afterA = await recentOf(a, b);
    const afterB = await recentOf(b, a);
    expect(afterA, 'A 의 최근 함께한 사람에 B 가 없다').toBeTruthy();
    expect(afterB, 'B 의 최근 함께한 사람에 A 가 없다').toBeTruthy();
    // 이번 파티가 새로 적었다 — 시각이 확정 뒤이고(시계 오차 5초) 파티 번호가 바뀌었다
    for (const [now, old] of [[afterA!, before.a], [afterB!, before.b]] as const) {
      expect(Date.parse(now.lastPlayedAt)).toBeGreaterThan(confirmedAt - 5_000);
      if (old) expect(now.lastPartyId).not.toBe(old.lastPartyId);
    }
    expect(afterA!.lastPartyId).toBe(afterB!.lastPartyId);
    // 마지막 사람이 나가 파티가 닫혔다 — 글은 CONFIRMED 그대로이고 closed 가 true 다(P-46 — 게시판 카드의 "끝남")
    const closedPost = (await a.get<Post>(`/posts/${post.postId}`)).body;
    expect(closedPost.status).toBe('CONFIRMED');
    expect(closedPost.closed).toBe(true);
  });
});
