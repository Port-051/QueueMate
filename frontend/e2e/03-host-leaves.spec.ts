import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, lolPost, myRoom, uniqueTitle, type Post } from './support/domain';
import { expectNoEvent, waitForEvent } from './support/sse';

/**
 * 시나리오 3 — 확정 전에 방장이 나간다(방과 글은 같이 산다 — P-22 · 2026-09-25 소유자 결정).
 * 방이 닫히고(남은 사람에게 `ROOM_CLOSED` · 입장 표시 키가 지워진다) 글은 그 자리에서 `EXPIRED` 가 된다. 둘 다 곧바로 새 글을 쓸 수 있다.
 */
test('시나리오 3 — 확정 전 방장 나가기 → 방 닫힘 · 글 만료', async ({ crew }) => {
  const a = await crew.user('a', { sse: true });
  const b = await crew.user('b', { sse: true });
  const [sseA, sseB] = [await crew.sse('a'), await crew.sse('b')];
  const post = await createPost(a, lolPost(uniqueTitle('s3')));
  const roomId = String(post.postId);
  const entered = await b.post(`/rooms/${roomId}/members`);
  expect(entered.status, show(entered)).toBe(201);

  await test.step('A 나가기 → 204 · B 에게 ROOM_CLOSED · 방장 본인은 받지 않는다', async () => {
    const sinceA = await sseA.mark();
    const sinceB = await sseB.mark();
    const r = await a.del(`/rooms/${roomId}/members/me`);
    expect(r.status, show(r)).toBe(204);
    await waitForEvent(sseB, 'ROOM_CLOSED', (e) => e.payload.roomId === roomId, { since: sinceB });
    await expectNoEvent(sseA, 'ROOM_CLOSED', (e) => e.payload.roomId === roomId, { since: sinceA, within: 500 });
  });

  await test.step('글은 EXPIRED · 두 사람 다 방 밖', async () => {
    const r = await a.get<Post>(`/posts/${post.postId}`);
    expect(r.status, show(r)).toBe(200);
    expect(r.body.status).toBe('EXPIRED');
    expect(await myRoom(a)).toBeNull();
    expect(await myRoom(b)).toBeNull();
  });

  await test.step('만료된 글의 방에는 들어갈 수 없다', async () => {
    const r = await b.post(`/rooms/${roomId}/members`);
    expect(r.status, show(r)).toBe(409);
    expect(r.body.code).toBe('POST_NOT_RECRUITING');
  });

  await test.step('A · B 둘 다 곧바로 새 글을 쓸 수 있다(409 가 남지 않는다)', async () => {
    const ra = await a.post<Post>('/posts', lolPost(uniqueTitle('s3-a')));
    expect(ra.status, show(ra)).toBe(201);
    const rb = await b.post<Post>('/posts', lolPost(uniqueTitle('s3-b'), { hostPosition: 'TOP', wantedPositions: ['MID'] }));
    expect(rb.status, show(rb)).toBe(201);
    expect(await myRoom(a)).toBe(String(ra.body.postId));
    expect(await myRoom(b)).toBe(String(rb.body.postId));
  });
});
