import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, enter, findOnBoard, foreignCandidates, lolMatch, lolPost, myRoom, POSITION_NOT_AVAILABLE, POSITION_REQUIRED, roomMembers, roomPositions, uniqueTitle } from './support/domain';
import { expectNoEvent, waitForEvent } from './support/sse';

/**
 * 시나리오 2 — 입장(포지션 고르기) → 강퇴 → 10분 금지(P-32 · 2026-09-29 소유자 결정 · 포지션 P-44).
 * 포지션 방(찾는 포지션이 있는 글)은 남은 포지션 하나를 `?position=` 으로 골라 들어온다 — 안 고르면 400 `"position: 필요합니다"`, 남이 고른 것이면 400
 * `"position: 이 파티방에서 고를 수 없는 포지션입니다"`(스크립트 -6 — 고른 포지션은 남은 SET 에서 빠진다). 고른 포지션은 방 안 사람 목록(P-44 ⑩)과 게시판 카드(⑨)에 실린다.
 * 강퇴당한 사람은 그 방에 10분 동안 직접 입장(403 `KICKED_RECENTLY`)도 게시판 방 먼저 합류도 안 된다. 강퇴는 그 사람의 포지션을 돌려놓아
 * 다른 사람(C)은 같은 포지션 · 같은 조건으로 그 방에 합류된다(대조).
 */
test('시나리오 2 — 입장 · 포지션 고르기 · 강퇴 · 10분 재입장 금지(P-32)', async ({ crew }) => {
  const a = await crew.user('a', { sse: true });
  const b = await crew.user('b', { sse: true });
  const c = await crew.user('c');
  const [sseA, sseB] = [await crew.sse('a'), await crew.sse('b')];
  // 일반 5인 · 방장 미드 · 찾는 포지션 넷(탑 · 정글 · 원딜 · 서포터 — 정원 − 1)
  const post = await createPost(a, lolPost(uniqueTitle('s2')));
  const roomId = String(post.postId);

  await test.step('B 가 포지션 없이 입장 → 400 position: 필요합니다 · 아무것도 쓰이지 않는다', async () => {
    const r = await enter(b, roomId);
    expect(r.status, show(r)).toBe(400);
    expect(r.body.code).toBe('VALIDATION_FAILED');
    expect(r.body.details).toContain(POSITION_REQUIRED);
    expect(await myRoom(b)).toBeNull();
  });

  await test.step('B 가 탑으로 입장 → 201 · A 에게 ROOM_MEMBER_ENTERED · 방 안에 둘 · 고른 포지션이 방 안 목록 · 카드에 실린다', async () => {
    const sinceA = await sseA.mark();
    const sinceB = await sseB.mark();
    const r = await enter(b, roomId, 'TOP');
    expect(r.status, show(r)).toBe(201);
    await waitForEvent(sseA, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === roomId && e.payload.userId === b.userId, { since: sinceA });
    expect(await myRoom(b)).toBe(roomId);
    const members = await roomMembers(a, roomId);
    expect(members.members?.sort()).toEqual([a.userId, b.userId].sort());
    // 방 안 사람 목록(P-44 ⑩) — 방장은 글의 방장 포지션 · B 는 고른 것
    expect(await roomPositions(a, roomId)).toEqual({ [a.userId]: 'MID', [b.userId]: 'TOP' });
    // 게시판 카드(P-44 ⑨) — C(방 밖)의 목록에서도 같은 값
    const seen = await findOnBoard(c, 'LOL', post.postId);
    expect(seen?.members.find((m) => m.userId === b.id)?.position).toBe('TOP');
    expect(seen?.members.find((m) => m.userId === a.id)?.position).toBe('MID');
    // 들어온 본인은 받지 않는다
    await expectNoEvent(sseB, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === roomId, { since: sinceB, within: 500 });
  });

  await test.step('C 가 B 가 고른 탑으로 → 400(남은 포지션이 아니다) · 찾는 포지션이 아닌 미드(방장 것)도 400 · C 는 방 밖', async () => {
    const taken = await enter(c, roomId, 'TOP');
    expect(taken.status, show(taken)).toBe(400);
    expect(taken.body.details).toContain(POSITION_NOT_AVAILABLE);
    const hosts = await enter(c, roomId, 'MID');
    expect(hosts.status, show(hosts)).toBe(400);
    expect(hosts.body.details).toContain(POSITION_NOT_AVAILABLE);
    expect(await myRoom(c)).toBeNull();
    expect((await roomMembers(a, roomId)).members?.sort()).toEqual([a.userId, b.userId].sort());
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

  await test.step('B 가 다시 입장(돌아온 탑으로) → 403 KICKED_RECENTLY', async () => {
    // enter-room.lua 는 입장 금지 목록을 맨 먼저 본다 — 포지션이 맞아도 403 이다(강퇴가 탑을 돌려놓았으니 포지션 때문에 막힌 것이 아니다)
    const r = await enter(b, roomId, 'TOP');
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

    // 대조 — 강퇴 기록이 없는 C 는 같은 본문으로 그 방에 들어간다(방이 조건에 맞는다는 증거 · 강퇴가 B 의 탑을 돌려놓았다는 증거 — P-44)
    const sinceA = await sseA.mark();
    const rc = await c.post('/posts/auto-join', body);
    expect(rc.status, show(rc)).toBe(200);
    expect(rc.body).toEqual({ postId: post.postId, roomId: post.postId });
    await waitForEvent(sseA, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === roomId && e.payload.userId === c.userId, { since: sinceA });
    // 게시판 방 먼저 합류는 요청의 포지션으로 들어간다
    expect(await roomPositions(a, roomId)).toEqual({ [a.userId]: 'MID', [c.userId]: 'TOP' });
  });
});
