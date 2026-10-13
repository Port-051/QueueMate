import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, enter, lolPost, myRoom, uniqueTitle, type Post } from './support/domain';
import { waitForEvent } from './support/sse';

const esc = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/**
 * 시나리오 18 — 방 화면의 좌석 상세(메뉴) · 신고 · 친구 추가 · 차단 · 내보내기(UI) · 글 지우기의 효과.
 * 근거 — `frontend/CLAUDE.md` §3-29 · §3-37(좌석을 누르면 뜨는 상세 — 친구 추가 · 방장의 내보내기 · 차단 · 신고 · 내보내기는 방장에게만 · 내 좌석은 대인 작업 없음) ·
 * `platform/contracts/mentor-room-ux.md` "멤버 상세창"(본인 상세에는 대인 작업 버튼이 없다 · 친구면 비활성 `친구` · 요청을 보냈으면 `친구 요청 보냄` · 내보내기는 맨 아래 별도 행) ·
 * platform-api.md "친구 · 신고"(`POST /reports {targetUserId, reason, detail, contextId}` → 201 · `OTHER` 는 `detail` 필수) · "차단" · "강퇴"(P-32 10분) · "모집 글"(`DELETE /posts/{id}` — 만료 + `ROOM_CLOSED`).
 *
 * 글 지우기 버튼은 2026-10-02 팀 작업("시각 정리 및 메뉴 제거")으로 방 메뉴째 화면에서 없어졌다 — API 로 지우고 **방 안 사람의 화면이 받는 효과**(토스트 "방이 닫혔어요" · 홈으로)만 본다.
 */
test('시나리오 18 — 좌석 상세 메뉴 · 신고 · 친구 추가 · 차단 · 내보내기 · 글 지우기 효과', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b', { sse: true });
  const c = await crew.user('c');
  const sseB = await crew.sse('b');
  const post = await createPost(a, lolPost(uniqueTitle('s18')));
  const roomId = String(post.postId);
  expect((await enter(b, roomId, 'TOP')).status).toBe(201);

  const pageA = await crew.appPage('a', `/app/party/${roomId}`);
  const pageB = await crew.appPage('b', `/app/party/${roomId}`);
  const seats = (page: Page) => page.getByRole('region', { name: '방', exact: true }).getByRole('list', { name: /^파티원 / });
  const seat = (page: Page, nickname: string) => seats(page).getByRole('button', { name: new RegExp(`^${esc(nickname)} · `) });
  const detail = (page: Page, nickname: string) => page.getByRole('dialog', { name: `${nickname} 상세 정보` });
  const action = (page: Page, nickname: string, key: string) => detail(page, nickname).locator(`.room-detail-action[data-action="${key}"]`);

  await test.step('내 좌석 — 상세는 열리지만 대인 작업 버튼이 없다 · Escape 로 닫힌다', async () => {
    await expect(seats(pageA)).toHaveAttribute('aria-label', '파티원 2 / 5');
    await seat(pageA, a.nickname).click();
    const mine = detail(pageA, a.nickname);
    await expect(mine).toBeVisible();
    await expect(mine.locator('.room-detail-action')).toHaveCount(0);
    await expect(mine).toContainText(a.nickname);
    await pageA.keyboard.press('Escape');
    await expect(mine).toBeHidden();
  });

  await test.step('방장이 상대 좌석을 누르면 — 메시지 보내기 · 친구 추가 · 차단 · 신고 + 맨 아래 별도 행의 내보내기', async () => {
    await seat(pageA, b.nickname).click();
    const dialog = detail(pageA, b.nickname);
    await expect(dialog).toBeVisible();
    for (const key of ['message', 'friend', 'block', 'report']) await expect(action(pageA, b.nickname, key)).toBeVisible();
    await expect(action(pageA, b.nickname, 'friend')).toHaveText('친구 추가');
    await expect(action(pageA, b.nickname, 'block')).toHaveText('차단');
    const kick = dialog.locator('.room-detail-kick-row .room-detail-action[data-action="kick"]');
    await expect(kick).toHaveText('내보내기');
    await expect(kick).toHaveClass(/is-danger/);
    // 같은 좌석을 다시 누르면 닫힌다(토글).
    await seat(pageA, b.nickname).click();
    await expect(dialog).toBeHidden();
  });

  await test.step('일반 멤버가 방장 좌석을 누르면 — 내보내기가 없다', async () => {
    await seat(pageB, a.nickname).click();
    const dialog = detail(pageB, a.nickname);
    await expect(dialog).toBeVisible();
    await expect(action(pageB, a.nickname, 'kick')).toHaveCount(0);
    await expect(action(pageB, a.nickname, 'report')).toBeVisible();
    await pageB.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
  });

  await test.step('신고 — "기타" 는 설명이 없으면 접수 버튼이 눌리지 않는다 · POST /reports {targetUserId, reason, detail, contextId=글 번호} 201 · 토스트', async () => {
    await seat(pageA, b.nickname).click();
    await action(pageA, b.nickname, 'report').click();
    const report = pageA.getByRole('dialog', { name: `${b.nickname}님 신고하기` });
    await expect(report).toBeVisible();
    await report.getByRole('combobox', { name: '신고 사유' }).selectOption('OTHER');
    await expect(report.getByRole('button', { name: '신고 접수' })).toBeDisabled();
    const detailText = `e2e 신고 ${Date.now().toString(36)}`;
    await report.getByRole('textbox', { name: /^설명/ }).fill(detailText);
    await expect(report.getByRole('button', { name: '신고 접수' })).toBeEnabled();
    const request = pageA.waitForRequest((r) => r.url().endsWith('/api/v1/reports') && r.method() === 'POST');
    const response = pageA.waitForResponse((r) => r.url().endsWith('/api/v1/reports') && r.request().method() === 'POST');
    await report.getByRole('button', { name: '신고 접수' }).click();
    expect((await request).postDataJSON()).toEqual({ targetUserId: b.userId, reason: 'OTHER', detail: detailText, contextId: roomId });
    const res = await response;
    expect(res.status(), await res.text()).toBe(201);
    expect(typeof (await res.json()).reportId).toBe('number');
    await expect(report).toBeHidden();
    await expect(pageA.getByRole('status').filter({ hasText: '신고가 접수되었습니다' })).toBeVisible();
  });

  await test.step('친구 추가 — POST /friend-requests → 상대에게 FRIEND_REQUEST_RECEIVED · 상대 화면의 레일에 배지 · 메뉴가 "친구 요청 보냄"(비활성)으로', async () => {
    const since = await sseB.mark();
    await seat(pageA, b.nickname).click();
    await action(pageA, b.nickname, 'friend').click();
    await expect(pageA.getByRole('status').filter({ hasText: `${b.nickname}님에게 친구 요청을 보냈습니다` })).toBeVisible();
    const event = await waitForEvent(sseB, 'FRIEND_REQUEST_RECEIVED', (e) => e.payload.fromUserId === a.id, { since });
    expect(typeof event.payload.requestId).toBe('number');
    await expect(pageB.getByLabel('받은 친구 요청 1개')).toBeVisible();
    await seat(pageA, b.nickname).click();
    await expect(action(pageA, b.nickname, 'friend')).toHaveText('친구 요청 보냄');
    await expect(action(pageA, b.nickname, 'friend')).toBeDisabled();
    await pageA.keyboard.press('Escape');
  });

  await test.step('차단 — 메뉴의 "차단" → 토스트 · GET /blocks 에 상대 · 메뉴가 "차단 해제" · 해제하면 목록이 빈다', async () => {
    await seat(pageA, b.nickname).click();
    await action(pageA, b.nickname, 'block').click();
    await expect(pageA.getByRole('status').filter({ hasText: `${b.nickname}님을 차단했습니다` })).toBeVisible();
    await expect.poll(async () => (await a.get<{ blocks: { userId: number }[] }>('/blocks')).body.blocks.map((x) => x.userId)).toContain(b.id);
    await seat(pageA, b.nickname).click();
    await expect(action(pageA, b.nickname, 'unblock')).toHaveText('차단 해제');
    // 차단 중에는 친구 추가가 비활성이다(이미 "친구 요청 보냄" 이라 비활성 — 둘 다 비활성이 맞다).
    await expect(action(pageA, b.nickname, 'friend')).toBeDisabled();
    await action(pageA, b.nickname, 'unblock').click();
    await expect(pageA.getByRole('status').filter({ hasText: `${b.nickname}님 차단을 해제했습니다` })).toBeVisible();
    await expect.poll(async () => (await a.get<{ blocks: unknown[] }>('/blocks')).body.blocks).toEqual([]);
  });

  await test.step('내보내기 — 확인 창(10분 문구) · 돌아가기는 아무것도 안 한다 · 내보내면 상대 화면은 토스트 "방에서 내보내졌어요" 와 홈 · 방 밖 · 내 좌석 줄 1 / 5', async () => {
    await seat(pageA, b.nickname).click();
    await detail(pageA, b.nickname).locator('.room-detail-action[data-action="kick"]').click();
    const confirm = pageA.getByRole('dialog', { name: `${b.nickname}님을 내보낼까요?` });
    await expect(confirm).toContainText('10분 동안 이 방에 다시 들어올 수 없어요');
    await confirm.getByRole('button', { name: '돌아가기' }).click();
    await expect(confirm).toBeHidden();
    await expect(seats(pageA)).toHaveAttribute('aria-label', '파티원 2 / 5');

    await seat(pageA, b.nickname).click();
    await detail(pageA, b.nickname).locator('.room-detail-action[data-action="kick"]').click();
    const response = pageA.waitForResponse((r) => r.url().endsWith(`/api/v1/rooms/${roomId}/members/${b.userId}`) && r.request().method() === 'DELETE');
    await pageA.getByRole('dialog', { name: `${b.nickname}님을 내보낼까요?` }).getByRole('button', { name: '내보내기' }).click();
    expect((await response).status()).toBe(204);
    await expect(pageB.getByRole('status').filter({ hasText: '방에서 내보내졌어요' })).toBeVisible();
    await pageB.waitForURL(/\/app\/home$/);
    expect(await myRoom(b)).toBeNull();
    await expect(seats(pageA)).toHaveAttribute('aria-label', '파티원 1 / 5');
    // 강퇴당한 사람은 10분 동안 다시 못 들어온다(P-32 — 시나리오 2 가 API 로 본 것과 같다).
    const again = await enter(b, roomId, 'TOP');
    expect(again.status, show(again)).toBe(403);
    expect(again.body.code).toBe('KICKED_RECENTLY');
  });

  await test.step('글 지우기(DELETE /posts/{id} — 방장 · 모집 중) → 방 안 사람(c)의 화면에 "방이 닫혔어요" 토스트 · 홈으로 · 글은 EXPIRED · 모두 방 밖', async () => {
    expect((await enter(c, roomId, 'JUNGLE')).status).toBe(201);
    const pageC = await crew.appPage('c', `/app/party/${roomId}`);
    await expect(seats(pageC)).toHaveAttribute('aria-label', '파티원 2 / 5');
    const r = await a.del(`/posts/${post.postId}`);
    expect(r.status, show(r)).toBe(204);
    await expect(pageC.getByRole('status').filter({ hasText: '방이 닫혔어요' })).toBeVisible({ timeout: 20_000 });
    await pageC.waitForURL(/\/app\/home$/);
    expect(await myRoom(c)).toBeNull();
    expect(await myRoom(a)).toBeNull();
    const after = await a.get<Post>(`/posts/${post.postId}`);
    expect(after.body.status).toBe('EXPIRED');
    expect(after.body.members).toEqual([]);
    // 방장의 화면도 방을 잃고 홈으로(방 세션이 "이 방에 없음" 을 알아챈다).
    await pageA.waitForURL(/\/app\/home$/, { timeout: 20_000 });
    // 홈의 카드는 흑백 · "마감 · 모집이 끝났어요".
    const card = pageC.getByRole('article', { name: `${post.title} 방 정보`, exact: true });
    await expect(card.locator('.room-join-button')).toHaveAttribute('aria-label', '마감 · 모집이 끝났어요', { timeout: 20_000 });
  });
});
