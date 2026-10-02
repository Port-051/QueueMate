import { expect, test } from './support/fixtures';
import { createPost, enter, lolPost, uniqueTitle, type Post } from './support/domain';

test('방 이동 — 현재 방 표시, 컬러 유지, 취소 후 확인하면 새 방으로 이동', async ({ crew }) => {
  const a = await crew.user('a'); const b = await crew.user('b');
  const original = await createPost(a, lolPost(uniqueTitle('switch-from')));
  const target = await createPost(b, lolPost(uniqueTitle('switch-to')));
  const page = await crew.appPage('a', '/app/home');
  const current = page.getByRole('article', { name: `${original.title} 방 정보`, exact: true });
  const destination = page.getByRole('article', { name: `${target.title} 방 정보`, exact: true });
  await expect(current.getByRole('button', { name: '참가 중', exact: true })).toBeDisabled();
  expect(await current.evaluate(e => getComputedStyle(e).filter)).toBe('none');
  await expect(destination.getByRole('button', { name: '참가', exact: true })).toBeEnabled();
  expect(await destination.evaluate(e => getComputedStyle(e).filter)).toBe('none');
  await expect(destination.locator('.room-seat.is-empty')).toHaveCount(1);
  await expect(destination.locator('.room-seat-vacancy')).toHaveText('4명 더 모집 중!');
  await destination.getByRole('button', { name: '참가', exact: true }).click();
  let dialog = page.getByRole('dialog', { name: '다른 방에 참가할까요?', exact: true });
  await expect(dialog.getByRole('alert')).toContainText('그래도 참가하시겠습니까?');
  await expect(dialog.getByRole('alert')).toContainText('방장으로 모집 중인 방은 닫힙니다.');
  await expect(dialog.getByRole('button', { name: '나가고 참가하기', exact: true })).toBeDisabled();
  await dialog.getByRole('button', { name: '취소', exact: true }).click();
  expect((await a.get<{ roomId: string }>('/rooms/me')).body.roomId).toBe(String(original.postId));
  await destination.getByRole('button', { name: '참가', exact: true }).click();
  dialog = page.getByRole('dialog', { name: '다른 방에 참가할까요?', exact: true });
  await dialog.getByRole('radio', { name: '탑', exact: true }).check();
  await dialog.getByRole('button', { name: '나가고 참가하기', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/app/party/${target.postId}$`));
  await expect(page.getByRole('region', { name: '방', exact: true }).getByRole('heading', { name: target.title })).toBeVisible();
  await expect(destination.getByRole('button', { name: '참가 중', exact: true })).toBeDisabled();
  expect((await a.get<{ roomId: string }>('/rooms/me')).body.roomId).toBe(String(target.postId));
  expect((await a.get<Post>(`/posts/${original.postId}`)).body.status).toBe('EXPIRED');
  expect((await a.get<Post>(`/posts/${target.postId}`)).body.members.find(member => member.userId === a.id)?.position).toBe('TOP');
});

test('방 이동 실패 — 퇴장 실패는 기존 방 유지, 입장 실패는 상태 안내 후 재시도', async ({ crew }) => {
  const a = await crew.user('a'); const b = await crew.user('b');
  const original = await createPost(a, lolPost(uniqueTitle('switch-failure-from')));
  const target = await createPost(b, lolPost(uniqueTitle('switch-failure-to')));
  const page = await crew.appPage('a', '/app/home');
  await expect(page.getByRole('button', { name: '참가 중', exact: true })).toBeVisible();
  await page.getByRole('article', { name: `${target.title} 방 정보`, exact: true }).getByRole('button', { name: '참가', exact: true }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByRole('radio', { name: '탑', exact: true }).check();
  await page.route(`**/api/v1/rooms/${original.postId}/members/me`, route => route.abort('failed'), { times: 1 });
  await dialog.getByRole('button', { name: '나가고 참가하기', exact: true }).click();
  await expect(dialog.getByText(/현재 방에서 나가지 못해 이동하지 않았어요/)).toBeVisible();
  expect((await a.get<{ roomId: string }>('/rooms/me')).body.roomId).toBe(String(original.postId));
  await page.route(`**/api/v1/rooms/${target.postId}/members?position=TOP`, route => route.abort('failed'), { times: 1 });
  await dialog.getByRole('button', { name: '나가고 참가하기', exact: true }).click();
  await expect(dialog.getByText(/이전 방에서는 나왔지만 새 방에 참가하지 못했어요/)).toBeVisible();
  expect((await a.get<{ roomId: string | null }>('/rooms/me')).body.roomId).toBeNull();
  await dialog.getByRole('button', { name: '참여하기', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/app/party/${target.postId}$`));
  expect((await a.get<{ roomId: string }>('/rooms/me')).body.roomId).toBe(String(target.postId));
});

test('방 이동 재확인 — 대상 방이 먼저 차면 기존 방을 나가지 않는다', async ({ crew }) => {
  const a = await crew.user('a'); const b = await crew.user('b'); const c = await crew.user('c');
  const original = await createPost(a, lolPost(uniqueTitle('switch-race-from')));
  const target = await createPost(b, lolPost(uniqueTitle('switch-race-to'), { mode: 'NORMAL_2', wantedPositions: ['TOP'] }));
  const page = await crew.appPage('a', '/app/home');
  await expect(page.getByRole('button', { name: '참가 중', exact: true })).toBeVisible();
  await page.getByRole('article', { name: `${target.title} 방 정보`, exact: true }).getByRole('button', { name: '참가', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '다른 방에 참가할까요?', exact: true });
  await dialog.getByRole('radio', { name: '탑', exact: true }).check();
  await page.route(`**/api/v1/posts/${target.postId}`, async route => {
    expect((await enter(c, String(target.postId), 'TOP')).status).toBe(201);
    await route.continue();
  }, { times: 1 });
  await dialog.getByRole('button', { name: '나가고 참가하기', exact: true }).click();
  await expect(dialog.locator('.room-preview-error')).toContainText('확정된 방이에요');
  expect((await a.get<{ roomId: string }>('/rooms/me')).body.roomId).toBe(String(original.postId));
  expect((await a.get<Post>(`/posts/${original.postId}`)).body.status).toBe('RECRUITING');
});
