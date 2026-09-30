import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, foreignCandidates, lolMatch, lolPost, myRoom, roomMembers, uniqueTitle } from './support/domain';
import { expectNoEvent, waitForEvent } from './support/sse';

/**
 * 시나리오 2 — 입장 → 강퇴 → 10분 금지(P-32 · 2026-09-29 소유자 결정).
 * 강퇴당한 사람은 그 방에 10분 동안 직접 입장(403 `KICKED_RECENTLY`)도 게시판 방 먼저 합류도 안 된다. 다른 사람(C)은 같은 조건으로 그 방에 합류된다(대조).
 */
test('시나리오 2 — 입장 · 강퇴 · 10분 재입장 금지(P-32)', async ({ crew }) => {
  const a = await crew.user('a', { sse: true });
  const b = await crew.user('b', { sse: true });
  const c = await crew.user('c');
  const [sseA, sseB] = [await crew.sse('a'), await crew.sse('b')];
  const post = await createPost(a, lolPost(uniqueTitle('s2'), { mode: 'NORMAL_5', hostPosition: 'MID', wantedPositions: ['TOP', 'JUNGLE'] }));
  const roomId = String(post.postId);

  await test.step('B 입장 → 201 · A 에게 ROOM_MEMBER_ENTERED · 방 안에 둘', async () => {
    const sinceA = await sseA.mark();
    const sinceB = await sseB.mark();
    const r = await b.post(`/rooms/${roomId}/members`);
    expect(r.status, show(r)).toBe(201);
    await waitForEvent(sseA, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === roomId && e.payload.userId === b.userId, { since: sinceA });
    expect(await myRoom(b)).toBe(roomId);
    const members = await roomMembers(a, roomId);
    expect(members.members?.sort()).toEqual([a.userId, b.userId].sort());
    // 들어온 본인은 받지 않는다
    await expectNoEvent(sseB, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === roomId, { since: sinceB, within: 500 });
  });

  await test.step('A 가 B 를 강퇴 → 204 · B 와 A 에게 ROOM_MEMBER_KICKED · B 는 방 밖', async () => {
    const sinceA = await sseA.mark();
    const sinceB = await sseB.mark();
    const r = await a.del(`/rooms/${roomId}/members/${b.userId}`);
    expect(r.status, show(r)).toBe(204);
    await waitForEvent(sseB, 'ROOM_MEMBER_KICKED', (e) => e.payload.roomId === roomId && e.payload.userId === b.userId, { since: sinceB });
    await waitForEvent(sseA, 'ROOM_MEMBER_KICKED', (e) => e.payload.roomId === roomId && e.payload.userId === b.userId, { since: sinceA });
    expect((await roomMembers(a, roomId)).members).toEqual([a.userId]);
    expect(await myRoom(b)).toBeNull();
    // 방 밖의 사람은 방 안 사람 목록을 못 본다
    expect(await roomMembers(b, roomId)).toMatchObject({ status: 403, code: 'NOT_IN_ROOM' });
  });

  await test.step('B 가 다시 입장 → 403 KICKED_RECENTLY', async () => {
    const r = await b.post(`/rooms/${roomId}/members`);
    expect(r.status, show(r)).toBe(403);
    expect(r.body.code).toBe('KICKED_RECENTLY');
    expect((await roomMembers(a, roomId)).members).toEqual([a.userId]);
  });

  await test.step('B 의 게시판 방 먼저 합류(같은 조건)는 그 방을 건너뛴다 · C 는 같은 조건으로 그 방에 들어간다', async () => {
    const body = lolMatch('TOP', { modeKey: 'NORMAL_5' });
    const others = await foreignCandidates(b, body, [a.id, b.id, c.id]);
    test.skip(others.length > 0, `남의 모집 중인 방(${others.join(', ')})이 같은 조건이라 합류 판정을 가를 수 없다 — 남의 방에 들여보내지 않으려고 건너뛴다`);

    const rb = await b.post('/posts/auto-join', body);
    expect(rb.status, `강퇴당한 B 가 합류됐다: ${show(rb)}`).toBe(404);
    expect(rb.body.code).toBe('NO_MATCHING_POST');
    expect(await myRoom(b)).toBeNull();

    // 대조 — 강퇴 기록이 없는 C 는 같은 본문으로 그 방에 들어간다(방이 조건에 맞는다는 증거)
    const sinceA = await sseA.mark();
    const rc = await c.post('/posts/auto-join', body);
    expect(rc.status, show(rc)).toBe(200);
    expect(rc.body).toEqual({ postId: post.postId, roomId: post.postId });
    await waitForEvent(sseA, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === roomId && e.payload.userId === c.userId, { since: sinceA });
  });
});
