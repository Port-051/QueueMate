import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, enter, foreignCandidates, lolMatch, lolPost, myRoom, POSITION_NOT_AVAILABLE, POSITION_REQUIRED, roomMembers, roomPositions, uniqueTitle } from './support/domain';
import type { MatchView } from './support/reset';
import { waitForEvent } from './support/sse';

/**
 * 시나리오 6 — 게시판 방 먼저 합류(P-28) + 방장 포지션(P-38) + 참가할 때 고르는 포지션(P-44).
 * 글의 찾는 포지션에 내 포지션이 있어야 합류된다(방장 포지션은 찾는 포지션이 될 수 없으니 방장과 같은 포지션은 저절로 빠진다). 합류는 요청의 포지션으로 들어간다.
 * 스스로 나간 사람은 10분 동안 자동 합류에서만 그 방을 건너뛴다(P-32). 자동 합류는 매칭 요청(활성 요청 키)을 만들지 않는다.
 * 나가기는 그 사람의 포지션을 돌려놓는다(`leave-room.lua`) — 그래서 C 가 같은 탑으로 합류되고, 나간 B 의 직접 입장은 남은 정글로만 된다.
 */
test('시나리오 6 — 게시판 방 먼저 합류 · 포지션 · 나간 뒤 10분 건너뛰기', async ({ crew }) => {
  const a = await crew.user('a', { sse: true });
  const b = await crew.user('b');
  const c = await crew.user('c');
  const sseA = await crew.sse('a');
  // 일반 3인 · 방장 미드 · 찾는 포지션 탑 · 정글(정원 − 1 = 둘 — P-44)
  const post = await createPost(a, lolPost(uniqueTitle('s6'), { mode: 'NORMAL_3', hostPosition: 'MID', wantedPositions: ['TOP', 'JUNGLE'] }));
  const roomId = String(post.postId);
  const asMid = lolMatch('MID', { modeKey: 'NORMAL_3' });
  const asTop = lolMatch('TOP', { modeKey: 'NORMAL_3' });

  const others = [...await foreignCandidates(b, asMid, [a.id, b.id, c.id]), ...await foreignCandidates(b, asTop, [a.id, b.id, c.id])];
  test.skip(others.length > 0, `남의 모집 중인 방(${others.join(', ')})이 같은 조건이라 합류 판정을 가를 수 없다 — 남의 방에 들여보내지 않으려고 건너뛴다`);

  await test.step('B 가 미드로 → 404 NO_MATCHING_POST(찾는 포지션이 탑 · 정글뿐 — 미드는 방장 것)', async () => {
    const r = await b.post('/posts/auto-join', asMid);
    expect(r.status, show(r)).toBe(404);
    expect(r.body.code).toBe('NO_MATCHING_POST');
    expect(await myRoom(b)).toBeNull();
  });

  await test.step('B 가 탑으로 → 200 {postId, roomId} · A 에게 ROOM_MEMBER_ENTERED · 매칭 요청은 생기지 않는다', async () => {
    const since = await sseA.mark();
    const r = await b.post('/posts/auto-join', asTop);
    expect(r.status, show(r)).toBe(200);
    expect(r.body).toEqual({ postId: post.postId, roomId: post.postId });
    await waitForEvent(sseA, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === roomId && e.payload.userId === b.userId, { since });
    expect(await myRoom(b)).toBe(roomId);
    expect((await roomMembers(a, roomId)).members?.sort()).toEqual([a.userId, b.userId].sort());
    // 합류는 요청의 포지션(탑)으로 들어간다(P-44 — AutoJoinService 가 찾는 포지션이 있는 글에 내 포지션을 실어 enter 를 부른다)
    expect(await roomPositions(a, roomId)).toEqual({ [a.userId]: 'MID', [b.userId]: 'TOP' });
    expect((await b.get<MatchView>('/match-requests')).body.status).toBe('IDLE');
  });

  await test.step('B 나가기 → A 에게 ROOM_MEMBER_LEFT', async () => {
    const since = await sseA.mark();
    const r = await b.del(`/rooms/${roomId}/members/me`);
    expect(r.status, show(r)).toBe(204);
    await waitForEvent(sseA, 'ROOM_MEMBER_LEFT', (e) => e.payload.roomId === roomId && e.payload.userId === b.userId, { since });
    expect(await myRoom(b)).toBeNull();
  });

  await test.step('B 가 10분 안에 다시 탑으로 → 그 방을 건너뛰어 404 · C 는 같은 본문으로 들어간다(대조)', async () => {
    const r = await b.post('/posts/auto-join', asTop);
    expect(r.status, `스스로 나간 B 가 10분 안에 같은 방에 다시 합류됐다: ${show(r)}`).toBe(404);
    expect(r.body.code).toBe('NO_MATCHING_POST');
    const since = await sseA.mark();
    const rc = await c.post('/posts/auto-join', asTop);
    expect(rc.status, show(rc)).toBe(200);
    expect(rc.body.roomId).toBe(post.postId);
    await waitForEvent(sseA, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === roomId && e.payload.userId === c.userId, { since });
  });

  await test.step('스스로 나간 B 의 직접 입장은 된다(P-32 — 막는 것은 자동 합류뿐) — C 가 고른 탑은 400 · 포지션 없이도 400 · 남은 정글로 201', async () => {
    const taken = await enter(b, roomId, 'TOP');
    expect(taken.status, `C 가 고른 탑으로 들어갔다: ${show(taken)}`).toBe(400);
    expect(taken.body.details).toContain(POSITION_NOT_AVAILABLE);
    const none = await enter(b, roomId);
    expect(none.status, show(none)).toBe(400);
    expect(none.body.details).toContain(POSITION_REQUIRED);
    const r = await enter(b, roomId, 'JUNGLE');
    expect(r.status, show(r)).toBe(201);
    expect(await myRoom(b)).toBe(roomId);
    expect(await roomPositions(a, roomId)).toEqual({ [a.userId]: 'MID', [c.userId]: 'TOP', [b.userId]: 'JUNGLE' });
  });
});
