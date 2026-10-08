import { expect, test } from './support/fixtures';
import { show, type QmUser } from './support/api';
import { expectNoEvent, waitForEvent } from './support/sse';

async function friendIds(user: QmUser): Promise<number[]> {
  const r = await user.get<{ friends: { userId: number }[] }>('/friends');
  expect(r.status, show(r)).toBe(200);
  return r.body.friends.map((friend) => friend.userId);
}

/**
 * 시나리오 8 — 친구(`FRIEND_*` 2종 · platform-api.md "친구 · 신고 · 최근 함께한 사람" · "이 앱이 내는 알림").
 * 요청 → 받은 사람에게 `FRIEND_REQUEST_RECEIVED {requestId, fromUserId}` → 수락 → 보낸 사람에게 `FRIEND_REQUEST_ACCEPTED {requestId, userId}` →
 * 서로의 친구 목록 → 끊기(알리지 않는다). 알림 `payload` 의 번호는 JSON 숫자다(방 알림의 문자열과 다르다).
 */
test('시나리오 8 — 친구 요청 · 알림 · 수락 · 끊기', async ({ crew }) => {
  const a = await crew.user('a', { sse: true, waitIdle: false });
  const b = await crew.user('b', { sse: true, waitIdle: false });
  const [sseA, sseB] = [await crew.sse('a'), await crew.sse('b')];

  let requestId = 0;
  await test.step('A → B 요청 201 · B 에게 FRIEND_REQUEST_RECEIVED · B 의 받은 요청 · A 의 보낸 요청', async () => {
    const sinceA = await sseA.mark();
    const sinceB = await sseB.mark();
    const r = await a.post<{ requestId: number }>('/friend-requests', { userId: b.userId });
    expect(r.status, show(r)).toBe(201);
    requestId = r.body.requestId;
    const event = await waitForEvent(sseB, 'FRIEND_REQUEST_RECEIVED', (e) => e.payload.requestId === requestId, { since: sinceB });
    expect(event.payload.fromUserId).toBe(a.id);
    await expectNoEvent(sseA, 'FRIEND_REQUEST_RECEIVED', () => true, { since: sinceA, within: 300 });
    const received = await b.get<{ requests: { requestId: number }[] }>('/friend-requests?direction=RECEIVED');
    expect(received.body.requests.map((request) => request.requestId)).toContain(requestId);
    const sent = await a.get<{ requests: { requestId: number }[] }>('/friend-requests?direction=SENT');
    expect(sent.body.requests.map((request) => request.requestId)).toContain(requestId);
    // 같은 방향의 대기 중 요청은 하나 — 다시 보내면 409
    expect((await a.post('/friend-requests', { userId: b.userId })).status).toBe(409);
  });

  await test.step('B 수락 → 200 · A 에게 FRIEND_REQUEST_ACCEPTED · 서로의 친구 목록', async () => {
    const sinceA = await sseA.mark();
    const r = await b.post(`/friend-requests/${requestId}/accept`);
    expect(r.status, show(r)).toBe(200);
    expect(r.body.userId).toBe(a.id);
    const event = await waitForEvent(sseA, 'FRIEND_REQUEST_ACCEPTED', (e) => e.payload.requestId === requestId, { since: sinceA });
    expect(event.payload.userId).toBe(b.id);
    expect(await friendIds(a)).toContain(b.id);
    expect(await friendIds(b)).toContain(a.id);
    const received = await b.get<{ requests: { requestId: number }[] }>('/friend-requests?direction=RECEIVED');
    expect(received.body.requests.map((request) => request.requestId)).not.toContain(requestId);
  });

  await test.step('A 가 친구 끊기 → 204 · 두 목록에서 빠진다 · 알림은 없다', async () => {
    const sinceB = await sseB.mark();
    const r = await a.del(`/friends/${b.userId}`);
    expect(r.status, show(r)).toBe(204);
    expect(await friendIds(a)).not.toContain(b.id);
    expect(await friendIds(b)).not.toContain(a.id);
    await sseB.page.waitForTimeout(500);
    expect((await sseB.events()).slice(sinceB).filter((e) => e.type.startsWith('FRIEND_'))).toEqual([]);
  });
});
