import { expect, test } from './support/fixtures';
import { aramPost, createPost, lolMatch, myRoom, uniqueTitle } from './support/domain';

/*
 * 방은 포지션 없는 방(칼바람 — `aramPost`)이다(2026-10-02 가져오며 바꿨다). 포지션 방이면 참여 창에서 남은 포지션을 먼저 골라야 "참여하기" 가 눌리고
 * 입장 요청에 `?position=` 이 붙어(platform P-44) 아래의 요청 맞추기(`…/members` 로 끝나는가)가 어긋난다. 취소-후-입장의 순서는 방의 종류와 상관이 없다.
 */

/** 빠른매치 중 수동 입장은 확인 → 취소 성공 → 입장 순서다. 확인을 닫거나 취소가 실패하면 큐를 유지한다. */
test('빠른매치 중 방 참여 — 확인 취소 · 서버 오류 · 취소 완료 후 입장', async ({ crew }) => {
  const host = await crew.user('a');
  const guest = await crew.user('b');
  const post = await createPost(host, aramPost(uniqueTitle('quick-entry')));
  const queued = await guest.post('/match-requests', lolMatch('MID'));
  expect(queued.status).toBe(201);
  const page = await crew.appPage('b', '/app/home');
  const card = page.locator('.room-deck').filter({ hasText: post.title });
  const dialog = page.getByRole('dialog', { name: '이 방에 참여할까요?' });
  const confirm = dialog.getByRole('button', { name: '빠른매치 취소 후 참여하기' });
  const cancelPath = `**/api/v1/match-requests/${queued.body.requestId}`;
  let joins = 0;
  const order: string[] = [];
  page.on('request', r => {
    if (r.method() === 'DELETE' && r.url().endsWith(`/match-requests/${queued.body.requestId}`)) order.push('cancel');
    if (r.method() === 'POST' && r.url().endsWith(`/rooms/${post.postId}/members`)) { joins++; order.push('join'); }
  });

  await expect(page.getByRole('button', { name: '빠른매치 현황 열기' })).toBeVisible();
  await card.getByRole('button', { name: '참가', exact: true }).click();
  await expect(dialog.getByText('이 방에 입장하면 현재 진행 중인 빠른매치가 취소됩니다.')).toBeVisible();
  await dialog.getByRole('button', { name: '취소', exact: true }).click();
  expect(order).toEqual([]);
  expect((await guest.get('/match-requests')).body.status).toBe('QUEUED');

  // 취소 요청이 실패하면 입장 요청은 보내지 않는다.
  await page.route(cancelPath, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'MATCHING_UNAVAILABLE', message: 'unavailable', details: [] }) }));
  await card.getByRole('button', { name: '참가', exact: true }).click();
  await confirm.click();
  await expect(dialog.getByRole('alert')).toContainText('빠른매치를 취소하지 못해 방에 입장하지 않았어요.');
  expect(joins).toBe(0);
  expect((await guest.get('/match-requests')).body.status).toBe('QUEUED');
  await page.unroute(cancelPath);

  // 응답이 돌아오기 전에는 닫기 · Escape · 중복 클릭으로 작업이 분리되지 않는다.
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route(cancelPath, async route => { await gate; await route.continue(); });
  order.length = 0;
  const cancelling = page.waitForRequest(r => r.method() === 'DELETE' && r.url().endsWith(`/match-requests/${queued.body.requestId}`));
  await confirm.click();
  await cancelling;
  await page.keyboard.press('Escape');
  await expect(dialog).toBeVisible();
  await expect(dialog.getByRole('button', { name: '참여 중…' })).toBeDisabled();
  expect(joins).toBe(0);
  release();
  await page.waitForURL(`**/app/party/${post.postId}`);
  expect(order).toEqual(['cancel', 'join']);
  expect(await myRoom(guest)).toBe(String(post.postId));
  expect((await guest.get('/match-requests')).body.status).toBe('IDLE');
});

test('빠른매치 취소 뒤 입장 실패 — 취소 상태 안내와 입장 재시도', async ({ crew }) => {
  const host = await crew.user('a');
  const guest = await crew.user('b');
  const post = await createPost(host, aramPost(uniqueTitle('entry-retry')));
  expect((await guest.post('/match-requests', lolMatch('MID'))).status).toBe(201);
  const page = await crew.appPage('b', '/app/home');
  await expect(page.getByRole('button', { name: '빠른매치 현황 열기' })).toBeVisible();
  const entryPath = `**/api/v1/rooms/${post.postId}/members`;
  await page.route(entryPath, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'ROOM_STATE_UNAVAILABLE', message: 'unavailable', details: [] }) }));
  await page.locator('.room-deck').filter({ hasText: post.title }).getByRole('button', { name: '참가', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '이 방에 참여할까요?' });
  await dialog.getByRole('button', { name: '빠른매치 취소 후 참여하기' }).click();
  await expect(dialog.getByRole('alert')).toContainText('빠른매치는 취소됐지만 방에 입장하지 못했어요.');
  expect((await guest.get('/match-requests')).body.status).toBe('IDLE');
  expect(await myRoom(guest)).toBeNull();
  await page.unroute(entryPath);
  await dialog.getByRole('button', { name: '참여하기', exact: true }).click();
  await page.waitForURL(`**/app/party/${post.postId}`);
  expect(await myRoom(guest)).toBe(String(post.postId));
});
