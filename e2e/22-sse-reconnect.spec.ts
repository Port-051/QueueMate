import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, enter, lolPost, uniqueTitle } from './support/domain';

/**
 * 시나리오 22 — 네트워크가 잠깐 끊긴 동안 온 알림과 그 뒤의 화면.
 * 근거 — `frontend/CLAUDE.md` §2 "실시간"(놓친 알림은 다시 오지 않는다 — 재연결 직후 상태를 REST 로 맞춘다) · §3-36 · §3-39 ·
 * `src/state/RoomSessionContext.tsx`(`ROOM_*` → `GET …/members` · SSE 가 다시 붙은 뒤 한 번 더) · `src/state/SocialContext.tsx` · `src/api/sse.ts`.
 *
 * **2026-10-13 에 실제로 잰 것** — Chromium 의 오프라인 에뮬레이션(`context.setOffline(true)`)은 **이미 열린 SSE 스트림을 끊지 않는다**. 45초 오프라인 동안 하트비트가 3번 그대로
 * 들어왔고(`scratchpad/sse-offline.mjs`) 오류 · 재연결은 없었다. 그래서 여기서는 "SSE 가 끊겼다 다시 붙는" 길(오류 백오프 · 60초 침묵 감시)을 만들 수 없다 — 그 둘은 `[미커버]` 다(CHECKLIST 10.2).
 * 대신 이 에뮬레이션이 정확히 만드는 상황을 본다 — **SSE 는 살아 있어 알림은 오는데, 그 알림이 일으킨 REST 재조회(`GET …/members`)만 실패한다**(순단 · 프록시 502 와 같은 모양).
 * 1) 그동안 화면은 옛 상태로 남고 로그아웃으로 떨어지지 않는다 2) 다음 알림이 오면 그때 메워진다(지금 제품의 동작) 3) 제품이 "다시 온라인이 되면 스스로 메우기" 는 아직 없다 — 아래 둘째 테스트가 `test.fail` 로 적는다.
 */
test('시나리오 22 — SSE 는 살아 있고 REST 만 실패하는 순단 — 그동안 화면은 옛 상태 · 로그아웃 아님 · 다음 알림에 메워진다', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  const c = await crew.user('c');
  const post = await createPost(a, lolPost(uniqueTitle('s22')));
  const roomId = String(post.postId);
  expect((await enter(b, roomId, 'TOP')).status).toBe(201);

  const page = await crew.appPage('b', `/app/party/${roomId}`);
  const seats = page.getByRole('region', { name: '방', exact: true }).getByRole('list', { name: /^파티원 / });
  await expect(seats).toHaveAttribute('aria-label', '파티원 2 / 5');
  await expect(page.getByLabel(/받은 친구 요청 \d+개/)).toHaveCount(0);

  let membersCalls = 0;
  let membersFailed = 0;
  page.on('request', (r) => { if (r.url().endsWith(`/api/v1/rooms/${roomId}/members`) && r.method() === 'GET') membersCalls += 1; });
  page.on('requestfailed', (r) => { if (r.url().endsWith(`/api/v1/rooms/${roomId}/members`)) membersFailed += 1; });

  await test.step('오프라인 — C 입장 · A 친구 요청 → 알림은 오고 재조회는 실패 → 좌석 2 / 5 · 배지 없음 · 여전히 방 화면(로그아웃 아님)', async () => {
    const before = membersCalls;
    await b.context.setOffline(true);
    const rc = await enter(c, roomId, 'JUNGLE');
    expect(rc.status, show(rc)).toBe(201);
    const rf = await a.post('/friend-requests', { userId: b.userId });
    expect(rf.status, show(rf)).toBe(201);
    // ROOM_MEMBER_ENTERED 가 SSE 로 와 재조회를 시도하지만(요청 1번 이상) 오프라인이라 실패한다.
    await expect.poll(() => membersCalls, { timeout: 10_000 }).toBeGreaterThan(before);
    await expect.poll(() => membersFailed, { timeout: 10_000 }).toBeGreaterThan(0);
    await page.waitForTimeout(1_500);
    await expect(seats).toHaveAttribute('aria-label', '파티원 2 / 5');
    await expect(page.getByLabel(/받은 친구 요청 \d+개/)).toHaveCount(0);
    await expect(page).toHaveURL(new RegExp(`/app/party/${roomId}$`));
  });

  await test.step('온라인 — 다음 알림(C 나가기)이 오면 재조회가 성공해 목록이 맞는다(2 / 5 · C 없음)', async () => {
    await b.context.setOffline(false);
    expect((await c.del(`/rooms/${roomId}/members/me`)).status).toBe(204);
    await expect(seats).toHaveAttribute('aria-label', '파티원 2 / 5', { timeout: 15_000 });
    await expect(seats.getByRole('button', { name: new RegExp(`^${c.nickname} · `) })).toHaveCount(0);
    await expect(seats.getByRole('button', { name: new RegExp(`^${a.nickname} · `) })).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/app/party/${roomId}$`));
  });
});

/**
 * 제품 개선 후보(2026-10-13 관찰 · 소유자 결정 전) — 알림이 일으킨 재조회가 네트워크 오류로 실패하면 `RoomSessionContext.refresh` 는 삼키고 다시 하지 않아, SSE 가 끊기지 않은 채
 * 순단이 끝나도 다음 알림까지 옛 상태로 남는다(받은 친구 요청 배지도 같다 — `SocialContext`). 의도한 동작은 "다시 온라인이 되면 스스로 메운다" 이고 지금은 아니라 `test.fail` 로 적는다.
 * 제품이 고쳐지면 이 테스트가 "뜻밖에 통과" 로 알려 준다 — 그때 `test.fail()` 을 지운다.
 */
test('시나리오 22-b — [제품 개선 후보] REST 재조회만 실패한 순단이 끝나면 스스로 메워야 한다(3 / 5 · 배지 1)', async ({ crew }) => {
  test.fail(true, '재조회 실패를 다시 시도하는 길이 없다 — RoomSessionContext.refresh · SocialContext (2026-10-13 관찰)');
  const a = await crew.user('a');
  const b = await crew.user('b');
  const c = await crew.user('c');
  const post = await createPost(a, lolPost(uniqueTitle('s22b')));
  const roomId = String(post.postId);
  expect((await enter(b, roomId, 'TOP')).status).toBe(201);
  const page = await crew.appPage('b', `/app/party/${roomId}`);
  const seats = page.getByRole('region', { name: '방', exact: true }).getByRole('list', { name: /^파티원 / });
  await expect(seats).toHaveAttribute('aria-label', '파티원 2 / 5');
  await b.context.setOffline(true);
  expect((await enter(c, roomId, 'JUNGLE')).status).toBe(201);
  expect((await a.post('/friend-requests', { userId: b.userId })).status).toBe(201);
  await page.waitForTimeout(3_000);
  await b.context.setOffline(false);
  // 사용자 조작 · 다음 알림 없이 20초 안에 메워져야 한다.
  await expect(seats).toHaveAttribute('aria-label', '파티원 3 / 5', { timeout: 20_000 });
  await expect(page.getByLabel('받은 친구 요청 1개')).toBeVisible({ timeout: 5_000 });
});
