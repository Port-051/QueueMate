import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { lolMatch, myRoom, roomMembers } from './support/domain';
import type { MatchView } from './support/reset';
import { expectNoEvent, waitForEvent } from './support/sse';

/**
 * 시나리오 5 — 퀵 매칭(두 사람 — 화면 이름은 2026-10-01 에 "자동 매칭" 에서 바뀌었다). 대기열 → 제안(`MATCH_PROPOSAL_CREATED`) → 둘 다 수락 → 확정(`MATCH_CONFIRMED {partyId}`) →
 * `POST /match-parties/{partyId}/room`(처음 201 · 다음 200 — P-30) → 같은 방(`roomId = partyId`) → 시그널이 상대에게 `WEBRTC_SIGNAL` 로 → 나가기.
 * 플레이 목적의 옛 이름 `NORMAL` 은 400 이다(matching D-49 — `TRYHARD` 로 바뀌었다).
 *
 * 확정된 요청(`status=PARTY`)은 60초 동안 풀리지 않는다(D-42) — 뒤 시나리오의 준비가 그만큼 기다린다.
 */
test('시나리오 5 — 퀵 매칭 두 사람 · 제안 · 확정 · 파티 방 · 시그널', async ({ crew }) => {
  const a = await crew.user('a', { sse: true });
  const b = await crew.user('b', { sse: true });
  const [sseA, sseB] = [await crew.sse('a'), await crew.sse('b')];

  await test.step('playPurpose NORMAL → 400', async () => {
    const r = await a.post('/match-requests', lolMatch('TOP', { playPurpose: 'NORMAL' }));
    expect(r.status, show(r)).toBe(400);
    expect((await a.get<MatchView>('/match-requests')).body.status).toBe('IDLE');
  });

  const sinceA = await sseA.mark();
  const sinceB = await sseB.mark();
  await test.step('A(탑) · B(미드) 매칭 요청 → 둘 다 201 QUEUED', async () => {
    const ra = await a.post<MatchView>('/match-requests', lolMatch('TOP'));
    expect(ra.status, show(ra)).toBe(201);
    expect(ra.body.status).toBe('QUEUED');
    expect(ra.body.requestId).toBeTruthy();
    const rb = await b.post<MatchView>('/match-requests', lolMatch('MID'));
    expect(rb.status, show(rb)).toBe(201);
    expect(rb.body.status).toBe('QUEUED');
    // 이미 대기 중이면 409
    const again = await a.post('/match-requests', lolMatch('TOP'));
    expect(again.status, show(again)).toBe(409);
    expect(again.body.code).toBe('ALREADY_QUEUED');
  });

  let partyId = '';
  await test.step('둘 다 MATCH_PROPOSAL_CREATED(같은 partyId · 정원 2) · 상태 PROPOSED', async () => {
    const eventA = await waitForEvent(sseA, 'MATCH_PROPOSAL_CREATED', () => true, { since: sinceA, timeout: 15_000 });
    const eventB = await waitForEvent(sseB, 'MATCH_PROPOSAL_CREATED', () => true, { since: sinceB, timeout: 15_000 });
    partyId = eventA.payload.partyId;
    expect(partyId).toBeTruthy();
    expect(eventB.payload.partyId).toBe(partyId);
    expect(eventA.payload.target).toBe(2);
    const viewA = (await a.get<MatchView>('/match-requests')).body;
    expect(viewA).toMatchObject({ status: 'PROPOSED', partyId, isAccepted: false });
    expect((await b.get<MatchView>('/match-requests')).body).toMatchObject({ status: 'PROPOSED', partyId });
  });

  await test.step('둘 다 수락 → 204 · 둘 다 MATCH_CONFIRMED · 상태 MATCHED', async () => {
    const ra = await a.post(`/proposals/${partyId}/accept`);
    expect(ra.status, show(ra)).toBe(204);
    expect((await a.get<MatchView>('/match-requests')).body).toMatchObject({ status: 'PROPOSED', isAccepted: true });
    // 한 사람만 수락한 동안에는 확정이 나가지 않는다
    await expectNoEvent(sseA, 'MATCH_CONFIRMED', () => true, { since: sinceA, within: 500 });
    const rb = await b.post(`/proposals/${partyId}/accept`);
    expect(rb.status, show(rb)).toBe(204);
    await waitForEvent(sseA, 'MATCH_CONFIRMED', (e) => e.payload.partyId === partyId, { since: sinceA });
    await waitForEvent(sseB, 'MATCH_CONFIRMED', (e) => e.payload.partyId === partyId, { since: sinceB });
    expect((await a.get<MatchView>('/match-requests')).body).toMatchObject({ status: 'MATCHED', partyId });
    expect((await b.get<MatchView>('/match-requests')).body).toMatchObject({ status: 'MATCHED', partyId });
  });

  await test.step('파티 방 — A 201(만든다) · B 200(들어간다) · 둘 다 같은 방', async () => {
    const ra = await a.post<{ roomId: string }>(`/match-parties/${partyId}/room`);
    expect(ra.status, show(ra)).toBe(201);
    expect(ra.body.roomId).toBe(partyId);
    const sinceEnter = await sseA.mark();
    const rb = await b.post<{ roomId: string }>(`/match-parties/${partyId}/room`);
    expect(rb.status, show(rb)).toBe(200);
    expect(rb.body.roomId).toBe(partyId);
    await waitForEvent(sseA, 'ROOM_MEMBER_ENTERED', (e) => e.payload.roomId === partyId && e.payload.userId === b.userId, { since: sinceEnter });
    const members = await roomMembers(a, partyId);
    expect(members.status).toBe(200);
    expect(members.hostId).toBe(a.userId);
    expect(members.members?.sort()).toEqual([a.userId, b.userId].sort());
    expect(await myRoom(a)).toBe(partyId);
    expect(await myRoom(b)).toBe(partyId);
    // 다시 불러도 200(이미 들어와 있다)
    expect((await a.post(`/match-parties/${partyId}/room`)).status).toBe(200);
  });

  await test.step('시그널 A → B → 202 · B 에게 WEBRTC_SIGNAL(보낸 그대로) · A 는 받지 않는다', async () => {
    const signal = { kind: 'candidate', candidate: { candidate: 'candidate:e2e 1 udp 1 127.0.0.1 9 typ host', sdpMid: '0', sdpMLineIndex: 0 } };
    const since = await sseB.mark();
    const sinceSelf = await sseA.mark();
    const r = await a.post(`/rooms/${partyId}/signals`, { toUserId: b.userId, signal });
    expect(r.status, show(r)).toBe(202);
    const event = await waitForEvent(sseB, 'WEBRTC_SIGNAL', (e) => e.payload.roomId === partyId, { since });
    expect(event.payload.fromUserId).toBe(a.userId);
    expect(event.payload.signal).toEqual(signal);
    await expectNoEvent(sseA, 'WEBRTC_SIGNAL', () => true, { since: sinceSelf, within: 500 });
  });

  await test.step('나가기 — B 나가면 A 에게 ROOM_MEMBER_LEFT · A 도 나가면 둘 다 방 밖', async () => {
    const since = await sseA.mark();
    const rb = await b.del(`/rooms/${partyId}/members/me`);
    expect(rb.status, show(rb)).toBe(204);
    await waitForEvent(sseA, 'ROOM_MEMBER_LEFT', (e) => e.payload.roomId === partyId && e.payload.userId === b.userId, { since });
    const ra = await a.del(`/rooms/${partyId}/members/me`);
    expect(ra.status, show(ra)).toBe(204);
    expect(await myRoom(a)).toBeNull();
    expect(await myRoom(b)).toBeNull();
  });
});
