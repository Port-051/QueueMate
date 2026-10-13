import { expect, test } from './support/fixtures';

/**
 * 시나리오 21 — 회원 탈퇴 창(내 정보)과 거절 갈래(409 `ALREADY_QUEUED` · 503 `ROOM_STATE_UNAVAILABLE`).
 * 근거 — `frontend/CLAUDE.md` §3-41 · platform-api.md "회원 탈퇴"(P-48 — `DELETE /api/v1/auth/account` · 409 면 "빠른매치를 먼저 취소" · 503 이면 `Retry-After` 초 뒤 · 둘 다 아무것도 지워지지 않는다).
 *
 * **실제로 탈퇴하지 않는다** — `e2e-a` · `e2e-b` · `e2e-c` 는 다른 시나리오가 함께 쓰는 고정 사용자다(사용자를 다시 만드는 길은 개발용 로그인뿐이고 번호가 바뀐다).
 * 그래서 탈퇴 요청은 `page.route` 로 받아 **서버에 닿기 전에** 409 · 503 을 돌려준다 — 화면이 그 갈래를 맞게 다루는지만 본다. 204 의 행복 경로는 손으로 본다(체크리스트 `[수동]`).
 * 409 를 실제로 만들려면 빠른매치 대기 중이어야 하는데 그 상태에서 실수로 204 가 나면 사용자가 지워진다 — 흉내로 대신한다.
 */
test('시나리오 21 — 회원 탈퇴 창 열기 · 돌아가기 · 409 ALREADY_QUEUED · 503 Retry-After(실제 탈퇴 없음)', async ({ crew }) => {
  const a = await crew.user('a', { waitIdle: false });
  const page = await crew.appPage('a', '/app/me');
  const ACCOUNT = '**/api/v1/auth/account';
  const open = async () => {
    await page.getByRole('button', { name: '회원 탈퇴' }).click();
    const dialog = page.getByRole('dialog', { name: '회원 탈퇴할까요?' });
    await expect(dialog).toBeVisible();
    return dialog;
  };

  await test.step('내 정보 맨 아래 "회원 탈퇴" → 확인 창 — 지워지는 것 · 되돌릴 수 없음 · 확정한 글 예외 · 빠른매치 중 불가 문구 · "돌아가기" 는 아무 요청도 보내지 않는다', async () => {
    let sent = 0;
    page.on('request', (r) => { if (r.url().endsWith('/api/v1/auth/account')) sent += 1; });
    const dialog = await open();
    await expect(dialog).toContainText('되돌릴 수 없습니다');
    await expect(dialog).toContainText('소셜 로그인 연결 · 닉네임');
    await expect(dialog).toContainText('작성자 표시만 지운 채');
    await expect(dialog).toContainText('빠른매치 중에는 탈퇴할 수 없으니');
    await expect(dialog.getByRole('button', { name: '탈퇴하기' })).toBeEnabled();
    await dialog.getByRole('button', { name: '돌아가기' }).click();
    await expect(dialog).toBeHidden();
    await page.waitForTimeout(300);
    expect(sent).toBe(0);
    expect((await a.get('/users/me')).status).toBe(200);
  });

  await test.step('409 ALREADY_QUEUED(흉내) — "빠른매치 중에는 탈퇴할 수 없어요 …" 토스트 · 창이 닫히고 그대로 로그인 상태 · 요청은 DELETE 하나', async () => {
    const seen: string[] = [];
    await page.route(ACCOUNT, (route) => {
      seen.push(route.request().method());
      return route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 'ALREADY_QUEUED', message: '매칭 중에는 탈퇴할 수 없습니다. 매칭을 먼저 취소해 주세요', details: [] }) });
    });
    const dialog = await open();
    await dialog.getByRole('button', { name: '탈퇴하기' }).click();
    await expect(page.getByRole('status').filter({ hasText: '빠른매치 중에는 탈퇴할 수 없어요. 빠른매치를 먼저 취소해 주세요' })).toBeVisible();
    await expect(dialog).toBeHidden();
    expect(seen).toEqual(['DELETE']);
    await expect(page).toHaveURL(/\/app\/me$/);
    await expect(page.getByRole('heading', { level: 1 })).toContainText(a.nickname);
    expect((await a.get('/users/me')).status).toBe(200);
    await page.unroute(ACCOUNT);
  });

  await test.step('503 ROOM_STATE_UNAVAILABLE + Retry-After: 7(흉내) — "7초쯤 뒤에 다시" 토스트 · 아무것도 지워지지 않았다', async () => {
    await page.route(ACCOUNT, (route) => route.fulfill({ status: 503, headers: { 'Retry-After': '7' }, contentType: 'application/json', body: JSON.stringify({ code: 'ROOM_STATE_UNAVAILABLE', message: 'unavailable', details: [] }) }));
    const dialog = await open();
    await dialog.getByRole('button', { name: '탈퇴하기' }).click();
    await expect(page.getByRole('status').filter({ hasText: '7초쯤 뒤에 다시 시도해 주세요' })).toBeVisible();
    await expect(dialog).toBeHidden();
    await page.unroute(ACCOUNT);
    expect((await a.get('/users/me')).status).toBe(200);
    // 로그아웃 줄은 그대로 있다(익명이 되지 않았다).
    await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible();
  });
});
