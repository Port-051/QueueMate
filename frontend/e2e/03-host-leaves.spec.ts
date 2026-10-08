import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { aramPost, createPost, enter, lolPost, myRoom, POSITION_NOT_AVAILABLE, roomPositions, uniqueTitle, type Post } from './support/domain';
import { expectNoEvent, waitForEvent } from './support/sse';

/**
 * 시나리오 3 — 확정 전에 방장이 나간다(방과 글은 같이 산다 — P-22 · 2026-09-25 소유자 결정).
 * 방이 닫히고(남은 사람에게 `ROOM_CLOSED` · 입장 표시 키가 지워진다) 글은 그 자리에서 `EXPIRED` 가 된다. 둘 다 곧바로 새 글을 쓸 수 있다.
 * 방은 **포지션 없는 방**(칼바람 — 찾는 포지션이 빈 글)이다(2026-10-01 — P-44): 입장에 포지션을 주면 400 이고 안 주면 들어간다 · 방 안 목록 · 카드의 포지션은 `null`.
 * 만료된 글의 카드는 `members` 가 비고 `host` 카드의 포지션도 `null` 이다(P-44 ⑨).
 */
test('시나리오 3 — 확정 전 방장 나가기 → 방 닫힘 · 글 만료(포지션 없는 방)', async ({ crew }) => {
  const a = await crew.user('a', { sse: true });
  const b = await crew.user('b', { sse: true });
  const [sseA, sseB] = [await crew.sse('a'), await crew.sse('b')];
  const post = await createPost(a, aramPost(uniqueTitle('s3')));
  const roomId = String(post.postId);
  expect(post.wantedPositions).toEqual([]);
  expect(post.hostPosition).toBeNull();

  await test.step('포지션 없는 방 — 포지션을 주면 400 · 안 주면 201 · 방 안 목록에 포지션이 없다', async () => {
    // enter-room.lua — 포지션 방이 아닌데(ARGV[7] = "0") 포지션이 오면 -6 → 골랐으니 "이 파티방에서 고를 수 없는 포지션입니다"
    const withPosition = await enter(b, roomId, 'TOP');
    expect(withPosition.status, show(withPosition)).toBe(400);
    expect(withPosition.body.details).toContain(POSITION_NOT_AVAILABLE);
    expect(await myRoom(b)).toBeNull();
    const entered = await enter(b, roomId);
    expect(entered.status, show(entered)).toBe(201);
    expect(await roomPositions(a, roomId)).toEqual({});
  });

  await test.step('A 나가기 → 204 · B 에게 ROOM_CLOSED · 방장 본인은 받지 않는다', async () => {
    const sinceA = await sseA.mark();
    const sinceB = await sseB.mark();
    const r = await a.del(`/rooms/${roomId}/members/me`);
    expect(r.status, show(r)).toBe(204);
    await waitForEvent(sseB, 'ROOM_CLOSED', (e) => e.payload.roomId === roomId, { since: sinceB });
    await expectNoEvent(sseA, 'ROOM_CLOSED', (e) => e.payload.roomId === roomId, { since: sinceA, within: 500 });
  });

  await test.step('글은 EXPIRED · 멤버 카드가 비고 방장 카드의 포지션은 null · 두 사람 다 방 밖', async () => {
    const r = await a.get<Post>(`/posts/${post.postId}`);
    expect(r.status, show(r)).toBe(200);
    expect(r.body.status).toBe('EXPIRED');
    expect(r.body.members).toEqual([]);
    expect(r.body.host?.position ?? null).toBeNull();
    expect(await myRoom(a)).toBeNull();
    expect(await myRoom(b)).toBeNull();
  });

  await test.step('만료된 글의 방에는 들어갈 수 없다', async () => {
    // 글 검사(PostEntryGate)가 방의 Lua 보다 먼저라 포지션과 상관없이 409 다
    const r = await enter(b, roomId);
    expect(r.status, show(r)).toBe(409);
    expect(r.body.code).toBe('POST_NOT_RECRUITING');
  });

  await test.step('A · B 둘 다 곧바로 새 글을 쓸 수 있다(409 가 남지 않는다)', async () => {
    const ra = await a.post<Post>('/posts', lolPost(uniqueTitle('s3-a')));
    expect(ra.status, show(ra)).toBe(201);
    // 방장 탑 · 찾는 포지션은 나머지 넷(정원 − 1 — P-44)
    const rb = await b.post<Post>('/posts', lolPost(uniqueTitle('s3-b'), { hostPosition: 'TOP', wantedPositions: ['JUNGLE', 'MID', 'ADC', 'SUPPORT'] }));
    expect(rb.status, show(rb)).toBe(201);
    expect(await myRoom(a)).toBe(String(ra.body.postId));
    expect(await myRoom(b)).toBe(String(rb.body.postId));
  });
});
