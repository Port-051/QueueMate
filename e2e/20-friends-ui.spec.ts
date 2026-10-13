import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, enter, lolPost, uniqueTitle } from './support/domain';

/**
 * 시나리오 20 — 친구 화면(`/app/friends?tab=`)의 다섯 탭과 왼쪽 레일 "친구" 배지(= 받은 친구 요청 수).
 * 근거 — `frontend/CLAUDE.md` §3-39(탭은 쿼리 `?tab=received|sent|blocks|recent` · 사람 검색 없음 — 사용자 번호를 직접 넣는다 · 배지는 받은 요청 수 · `FRIEND_*` 로 다시 센다) ·
 * platform-api.md "친구 · 신고 · 최근 함께한 사람" · "차단". 시나리오 8 이 API 로 본 것을 화면으로 본다.
 * 최근 함께한 사람은 지우는 API 가 없다 — 이번 파티로 새로 적혔는지는 시나리오 4 의 몫이고, 여기서는 **확정된 파티가 닫힌 뒤 그 탭에 상대가 보이는 것**만 본다.
 */
test('시나리오 20 — 친구 화면 · 번호로 요청 · 레일 배지 · 수락 · 보낸 요청 취소 · 차단 탭 · 친구 삭제 · 최근 함께한 사람 탭', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  const c = await crew.user('c', { waitIdle: false });
  const toast = (page: Page, text: string) => page.getByRole('status').filter({ hasText: text });
  const row = (page: Page, nickname: string) => page.locator('.friends-row', { hasText: nickname });

  const pageA = await crew.appPage('a', '/app/friends');
  const pageB = await crew.appPage('b', '/app/home');

  await test.step('친구 탭 — 비어 있다 · 번호가 아닌 값 · 내 번호는 보내기 전 · 서버에서 막힌다', async () => {
    await expect(pageA.getByRole('heading', { level: 1, name: '친구' })).toBeVisible();
    await expect(pageA.getByRole('tab', { name: /^친구/ })).toHaveAttribute('aria-selected', 'true');
    const form = pageA.getByRole('form', { name: '사용자 번호로 친구 요청' });
    await expect(form.getByRole('button', { name: '요청 보내기' })).toBeDisabled();
    await form.getByRole('textbox', { name: '사용자 번호' }).fill('abc');
    await form.getByRole('button', { name: '요청 보내기' }).click();
    await expect(toast(pageA, '사용자 번호(숫자)를 넣어 주세요')).toBeVisible();
    await form.getByRole('textbox', { name: '사용자 번호' }).fill(a.userId);
    await form.getByRole('button', { name: '요청 보내기' }).click();
    await expect(toast(pageA, '자기 자신에게는 친구 요청을 보낼 수 없어요')).toBeVisible();
  });

  await test.step('A → B 요청(번호 입력 · `#` 을 붙여도 된다) → 토스트 · 보낸 요청 탭에 B · B 의 레일 "친구" 에 배지 "받은 친구 요청 1개"', async () => {
    const form = pageA.getByRole('form', { name: '사용자 번호로 친구 요청' });
    await form.getByRole('textbox', { name: '사용자 번호' }).fill(`#${b.userId}`);
    const response = pageA.waitForResponse((r) => r.url().endsWith('/api/v1/friend-requests') && r.request().method() === 'POST');
    await form.getByRole('button', { name: '요청 보내기' }).click();
    expect((await response).status()).toBe(201);
    await expect(toast(pageA, `#${b.userId} 님에게 친구 요청을 보냈습니다`)).toBeVisible();
    await expect(form.getByRole('textbox', { name: '사용자 번호' })).toHaveValue('');
    await pageA.getByRole('tab', { name: /^보낸 요청/ }).click();
    await expect(pageA).toHaveURL(/\/app\/friends\?tab=sent$/);
    await expect(row(pageA, b.nickname)).toBeVisible();
    await expect(row(pageA, b.nickname).getByRole('button', { name: '요청 취소' })).toBeVisible();
    // 받은 쪽 — 알림(FRIEND_REQUEST_RECEIVED)으로 다시 세어 배지가 뜬다 · 타이머가 아니다.
    await expect(pageB.getByLabel('받은 친구 요청 1개')).toBeVisible();
    await expect(toast(pageB, '새 친구 요청이 왔어요')).toBeVisible();
  });

  await test.step('B — 받은 요청 탭(?tab=received)에 A · "수락" → 토스트 · 배지 사라짐 · 친구 탭에 A · A 의 화면도 친구 탭에 B(FRIEND_REQUEST_ACCEPTED)', async () => {
    await pageB.goto('/app/friends?tab=received');
    await expect(pageB.getByRole('tab', { name: /^받은 요청/ })).toHaveAttribute('aria-selected', 'true');
    const received = row(pageB, a.nickname);
    await expect(received).toContainText(`#${a.userId}`);
    await expect(received.getByRole('button', { name: '거절' })).toBeVisible();
    await received.getByRole('button', { name: '수락' }).click();
    await expect(toast(pageB, '친구 요청을 수락했습니다')).toBeVisible();
    await expect(received).toHaveCount(0);
    await expect(pageB.getByLabel(/받은 친구 요청 \d+개/)).toHaveCount(0);
    await pageB.getByRole('tab', { name: /^친구/ }).click();
    await expect(pageB).toHaveURL(/\/app\/friends$/);
    await expect(row(pageB, a.nickname)).toBeVisible();
    await expect(toast(pageA, '친구 요청이 수락됐어요')).toBeVisible();
    await pageA.getByRole('tab', { name: /^친구/ }).click();
    const friend = row(pageA, b.nickname);
    await expect(friend).toBeVisible();
    await expect(friend.getByRole('link', { name: '메시지' })).toHaveAttribute('href', `/app/messages/${b.id}`);
    await expect(friend.getByRole('button', { name: `${b.nickname} 신고` })).toBeVisible();
    await expect(pageA.getByRole('tab', { name: /^보낸 요청/ })).not.toContainText('1');
  });

  await test.step('이름으로 거르기 — 없는 이름이면 "검색 결과가 없습니다"', async () => {
    await pageA.getByRole('searchbox', { name: '이름으로 거르기' }).fill('없는-사람-zzz');
    await expect(pageA.getByText('검색 결과가 없습니다')).toBeVisible();
    await pageA.getByRole('searchbox', { name: '이름으로 거르기' }).fill('');
    await expect(row(pageA, b.nickname)).toBeVisible();
  });

  await test.step('A → C 요청 뒤 보낸 요청 탭에서 "요청 취소" → 토스트 · C 의 받은 요청이 빈다', async () => {
    const r = await a.post('/friend-requests', { userId: c.userId });
    expect(r.status, show(r)).toBe(201);
    await pageA.goto('/app/friends?tab=sent');
    await row(pageA, c.nickname).getByRole('button', { name: '요청 취소' }).click();
    await expect(toast(pageA, '요청을 취소했습니다')).toBeVisible();
    await expect(row(pageA, c.nickname)).toHaveCount(0);
    await expect.poll(async () => (await c.get<{ requests: unknown[] }>('/friend-requests?direction=RECEIVED')).body.requests).toEqual([]);
  });

  await test.step('차단 탭(?tab=blocks) — 번호로 차단 → 토스트 · 줄에 "차단 해제" · GET /blocks · 해제 → 빈 안내', async () => {
    await pageA.goto('/app/friends?tab=blocks');
    const form = pageA.getByRole('form', { name: '사용자 번호로 차단' });
    await form.getByRole('textbox', { name: '사용자 번호' }).fill(c.userId);
    await form.getByRole('button', { name: '차단' }).click();
    await expect(toast(pageA, `#${c.userId} 님을 차단했습니다`)).toBeVisible();
    await expect(row(pageA, c.nickname)).toBeVisible();
    expect((await a.get<{ blocks: { userId: number }[] }>('/blocks')).body.blocks.map((x) => x.userId)).toContain(c.id);
    // 내 정보의 "차단 목록" 수도 같은 목록이다.
    await row(pageA, c.nickname).getByRole('button', { name: '차단 해제' }).click();
    await expect(toast(pageA, '차단을 해제했습니다')).toBeVisible();
    await expect(pageA.getByText('차단한 사용자가 없습니다')).toBeVisible();
    expect((await a.get<{ blocks: unknown[] }>('/blocks')).body.blocks).toEqual([]);
  });

  await test.step('친구 삭제 — 확인 창 → 토스트 · 두 사람의 목록에서 빠진다 · 상대에게 알리지 않는다', async () => {
    await pageA.goto('/app/friends');
    await row(pageA, b.nickname).getByRole('button', { name: `${b.nickname} 친구 삭제` }).click();
    const confirm = pageA.getByRole('dialog', { name: `${b.nickname}님을 친구에서 삭제할까요?` });
    await expect(confirm).toContainText('상대에게 알리지 않습니다');
    await confirm.getByRole('button', { name: '친구 삭제' }).click();
    await expect(toast(pageA, '친구를 삭제했습니다')).toBeVisible();
    await expect(row(pageA, b.nickname)).toHaveCount(0);
    await expect(pageA.getByText('아직 친구가 없습니다')).toBeVisible();
    expect((await b.get<{ friends: { userId: number }[] }>('/friends')).body.friends.map((x) => x.userId)).not.toContain(a.id);
  });

  await test.step('최근 함께한 사람 탭(?tab=recent) — 확정된 파티가 닫히면(API) 상대가 "함께 플레이" 로 보이고 "친구 추가" 버튼', async () => {
    const post = await createPost(a, lolPost(uniqueTitle('s20-recent')));
    expect((await enter(b, String(post.postId), 'TOP')).status).toBe(201);
    expect((await a.post(`/rooms/${post.postId}/confirm`)).status).toBe(204);
    expect((await b.del(`/rooms/${post.postId}/members/me`)).status).toBe(204);
    expect((await a.del(`/rooms/${post.postId}/members/me`)).status).toBe(204);
    await pageA.goto('/app/friends?tab=recent');
    await expect(pageA.getByRole('tab', { name: /^최근 함께한 사람/ })).toHaveAttribute('aria-selected', 'true');
    const recent = row(pageA, b.nickname);
    await expect(recent).toContainText('함께 플레이');
    await expect(recent.getByRole('button', { name: '친구 추가' })).toBeVisible();
    await expect(recent.getByRole('button', { name: '차단' })).toBeVisible();
    // 여기서 친구 추가를 누르면 같은 줄이 "요청 취소" 로 바뀐다.
    await recent.getByRole('button', { name: '친구 추가' }).click();
    await expect(toast(pageA, '친구 요청을 보냈습니다')).toBeVisible();
    await expect(recent.getByRole('button', { name: '요청 취소' })).toBeVisible();
  });
});
