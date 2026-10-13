import { expect, test } from './support/fixtures';
import { show } from './support/api';

interface GameProfile { game: string; gameNickname: string; tiers: Record<string, string | null>; verified: boolean }

/**
 * 시나리오 16 — 온보딩(게임 계정 연결 권유 — 한 번만 · 건너뛸 수 있다)과 VALORANT 게임 계정(자기신고 티어 — "티어" · "단계" 두 칸).
 * 근거 — `frontend/CLAUDE.md` §3-16(게임 계정은 선택 · 로그인 직후 게임 계정이 0개이고 지나간 적이 없을 때 한 번 `/onboarding` · "나중에 할게요" · `localStorage['qm.onboarding.done:{userId}']`) ·
 * §3-13(VALORANT 경쟁전 티어는 티어 + 단계 두 칸 · 합친 값 `BRONZE_1` · "없음" 이면 `tier` 를 싣지 않는다) · platform-api.md "계정"(`PUT /users/me/game-accounts/VALORANT {gameNickname, tier?}` → 200 게임 프로필 ·
 * `tiers.COMPETITIVE` · `DELETE` 는 없어도 204).
 *
 * **LoL · PUBG 는 건드리지 않는다** — 둘은 저장 전에 Riot · PUBG API 를 동기로 긁는다(외부 키 · 호출 예산). VALORANT 만 자기신고라 외부 호출이 없다.
 * `e2e-a` 의 게임 계정을 전부 끊고 시작한다(`reset` 은 LoL 만 끊는다 — VALORANT · PUBG 는 여기서 `DELETE` · 없어도 204).
 */
test('시나리오 16 — 온보딩 한 번 · 나중에 할게요 · VALORANT 계정 연결(티어 두 칸) · 수정 · 연결 해제', async ({ crew }) => {
  const a = await crew.user('a', { waitIdle: false });
  const key = `qm.onboarding.done:${a.userId}`;

  await test.step('준비 — e2e-a 의 게임 계정 0개(VALORANT · PUBG 를 끊는다 · LoL 은 reset 이 끊었다)', async () => {
    for (const game of ['VALORANT', 'PUBG']) {
      const r = await a.del(`/users/me/game-accounts/${game}`);
      expect(r.status, show(r)).toBe(204);
    }
    const me = await a.get<{ gameAccounts: GameProfile[] }>('/users/me');
    expect(me.body.gameAccounts).toEqual([]);
  });

  const page = await a.context.newPage();
  await test.step('로그인 직후(/) — 게임 계정이 없고 지나간 적이 없으면 /onboarding 으로 한 번', async () => {
    // localStorage 는 앱 출처의 화면에서만 읽을 수 있다(about:blank 는 SecurityError) — 리디렉트가 없는 /privacy 를 먼저 연다(2026-10-13 첫 실행에서 겪었다).
    await page.goto('/privacy');
    await expect(page.getByRole('heading', { level: 1, name: '개인정보 처리방침' })).toBeVisible();
    expect(await page.evaluate((k) => localStorage.getItem(k), key)).toBeNull();
    await page.goto('/');
    await page.waitForURL(/\/onboarding$/);
    await expect(page.getByRole('heading', { level: 1, name: '플레이할 게임 계정을 연결해 보세요' })).toBeVisible();
    // 계정이 없으면 "시작하기" 는 눌리지 않고 "나중에 할게요" 가 있다.
    await expect(page.getByRole('button', { name: '시작하기' })).toBeDisabled();
    await expect(page.getByRole('button', { name: '나중에 할게요' })).toBeEnabled();
    // 온보딩 화면에도 작은 푸터(처리방침 · 약관 · Riot 고지문).
    await expect(page.locator('footer.site-footer .site-footer-riot[lang="en"]')).toBeVisible();
  });

  await test.step('"나중에 할게요" → 홈 · 이 브라우저에 사용자별로 적는다(skipped) · 다시 / · /login 으로 와도 온보딩으로 보내지 않는다', async () => {
    await page.getByRole('button', { name: '나중에 할게요' }).click();
    await page.waitForURL(/\/app\/home$/);
    expect(await page.evaluate((k) => localStorage.getItem(k), key)).toBe('skipped');
    await page.goto('/');
    await page.waitForURL(/\/app\/home$/);
    await page.goto('/login');
    await page.waitForURL(/\/app\/home$/);
    // 앱은 게임 계정 없이도 전부 열린다(문지기 없음 — §3-16).
    await expect(page.getByRole('region', { name: '방 목록' })).toBeVisible();
  });

  await test.step('/onboarding 직접 — VALORANT 고르기 · 티어 두 칸(브론즈 + 단계 1·2·3 · 레디언트는 단계 없음) · "계정 연결" → PUT {gameNickname, tier: BRONZE_1} · 토스트 · "시작하기"(linked)', async () => {
    await page.evaluate((k) => localStorage.removeItem(k), key);
    await page.goto('/onboarding');
    await page.locator('button.game-card', { hasText: '발로란트' }).click();
    await expect(page.locator('button.game-card.on')).toContainText('발로란트');
    const form = page.locator('form.game-account-form');
    const nickname = form.getByLabel('게임 닉네임');
    await expect(form.getByRole('button', { name: '계정 연결' })).toBeDisabled();
    await nickname.fill('e2e-valo');
    const tier = form.getByRole('combobox', { name: '경쟁전 티어' });
    const division = form.getByRole('combobox', { name: '단계' });
    // 처음은 "없음(배치 전)" 이고 단계 칸은 비활성이다.
    await expect(tier).toHaveValue('');
    await expect(division).toBeDisabled();
    // 레디언트 — 단계가 없는 티어라 단계 칸이 비활성 · "—".
    await tier.selectOption({ label: '레디언트' });
    await expect(division).toBeDisabled();
    await expect(division.locator('option')).toHaveText(['—']);
    // 브론즈 — VALORANT 의 단계는 1 → 3 이고 첫째(가장 낮은 1)가 기본이다(`domain/tierParts.ts`).
    await tier.selectOption({ label: '브론즈' });
    await expect(division).toBeEnabled();
    await expect(division.locator('option')).toHaveText(['1', '2', '3']);
    await expect(division).toHaveValue('1');

    const request = page.waitForRequest((r) => r.url().endsWith('/api/v1/users/me/game-accounts/VALORANT') && r.method() === 'PUT');
    const response = page.waitForResponse((r) => r.url().endsWith('/api/v1/users/me/game-accounts/VALORANT') && r.request().method() === 'PUT');
    await form.getByRole('button', { name: '계정 연결' }).click();
    expect((await request).postDataJSON()).toEqual({ gameNickname: 'e2e-valo', tier: 'BRONZE_1' });
    const res = await response;
    expect(res.status(), await res.text()).toBe(200);
    await expect(page.getByRole('status').filter({ hasText: 'e2e-valo 계정을 연결했습니다' })).toBeVisible();
    // 연결 확인 태그 — 게임 · 닉네임 · 가장 높은 사다리 티어.
    await expect(page.locator('.tag', { hasText: 'e2e-valo' })).toContainText('브론즈 1');
    await expect(page.getByRole('button', { name: '나중에 할게요' })).toHaveCount(0);
    await page.getByRole('button', { name: '시작하기' }).click();
    await page.waitForURL(/\/app\/home$/);
    expect(await page.evaluate((k) => localStorage.getItem(k), key)).toBe('linked');
    // 서버에 적혔다 — 사다리 `COMPETITIVE`.
    const me = await a.get<{ gameAccounts: GameProfile[] }>('/users/me');
    const valo = me.body.gameAccounts.find((account) => account.game === 'VALORANT');
    expect(valo?.gameNickname).toBe('e2e-valo');
    expect(valo?.tiers).toMatchObject({ COMPETITIVE: 'BRONZE_1' });
    expect(valo?.verified).toBe(false);
  });

  await test.step('/app/me#games — 발로란트 카드(닉네임 · 경쟁전 브론즈 1 · 자기신고) · "계정 수정" 창은 두 칸이 미리 채워진다 · "없음" 으로 바꾸면 tier 없이 PUT → 언랭', async () => {
    await page.goto('/app/me#games');
    const card = page.locator('.profile-game-card', { hasText: '발로란트' });
    await expect(card).toContainText('e2e-valo');
    await expect(card).toContainText('브론즈 1');
    await expect(card).toContainText('자기신고');
    await card.getByRole('button', { name: '발로란트 계정 수정' }).click();
    const dialog = page.getByRole('dialog', { name: '발로란트 계정 수정' });
    await expect(dialog.getByLabel('게임 닉네임')).toHaveValue('e2e-valo');
    const tier = dialog.getByRole('combobox', { name: '경쟁전 티어' });
    const division = dialog.getByRole('combobox', { name: '단계' });
    await expect(tier).toHaveValue('BRONZE');
    await expect(division).toHaveValue('1');
    // "없음(배치 전)" — `tier` 칸을 싣지 않는다(`UNRANKED` 를 보내지 않는다 — §3-13).
    await tier.selectOption({ value: '' });
    await expect(division).toBeDisabled();
    const request = page.waitForRequest((r) => r.url().endsWith('/api/v1/users/me/game-accounts/VALORANT') && r.method() === 'PUT');
    await dialog.getByRole('button', { name: '변경 사항 저장' }).click();
    expect((await request).postDataJSON()).toEqual({ gameNickname: 'e2e-valo' });
    await expect(dialog).toBeHidden();
    await expect(page.getByRole('status').filter({ hasText: 'e2e-valo 계정을 수정했습니다' })).toBeVisible();
    await expect(card).toContainText('언랭');
    const me = await a.get<{ gameAccounts: GameProfile[] }>('/users/me');
    expect(me.body.gameAccounts.find((account) => account.game === 'VALORANT')?.tiers.COMPETITIVE ?? null).toBeNull();
  });

  await test.step('"연결 해제" → 확인 창 → DELETE 204 → 카드가 "연결된 계정이 없습니다" · GET /users/me 에 VALORANT 가 없다', async () => {
    const card = page.locator('.profile-game-card', { hasText: '발로란트' });
    await card.getByRole('button', { name: '발로란트 연결 해제' }).click();
    const dialog = page.getByRole('dialog', { name: '발로란트 연결을 해제할까요?' });
    // 돌아가기 — 아무것도 바뀌지 않는다.
    await dialog.getByRole('button', { name: '돌아가기' }).click();
    await expect(dialog).toBeHidden();
    await expect(card).toContainText('e2e-valo');
    await card.getByRole('button', { name: '발로란트 연결 해제' }).click();
    const response = page.waitForResponse((r) => r.url().endsWith('/api/v1/users/me/game-accounts/VALORANT') && r.request().method() === 'DELETE');
    await page.getByRole('dialog', { name: '발로란트 연결을 해제할까요?' }).getByRole('button', { name: '연결 해제' }).click();
    expect((await response).status()).toBe(204);
    await expect(card).toContainText('연결된 계정이 없습니다');
    await expect(card.getByRole('button', { name: '발로란트 계정 연결' })).toBeVisible();
    const me = await a.get<{ gameAccounts: GameProfile[] }>('/users/me');
    expect(me.body.gameAccounts.some((account) => account.game === 'VALORANT')).toBe(false);
  });

  await test.step('정리 — 온보딩 표시를 지운다(다음 실행이 같은 상태에서 시작하게)', async () => {
    await page.evaluate((k) => localStorage.removeItem(k), key);
    await page.close();
  });
});
