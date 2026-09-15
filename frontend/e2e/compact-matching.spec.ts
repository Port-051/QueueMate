import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('상대 포지션 전체 선택은 다섯 개 그대로 유지된다', async ({ page }) => {
  await login(page);
  const roles = page.locator('.recruitment-composer-shell').getByRole('group', { name: '찾는 포지션', exact: true });
  for (const role of ['탑', '정글', '미드', '바텀', '서포터']) await roles.getByRole('button', { name: role, exact: true }).click();
  await expect(roles.locator('[aria-pressed=true]')).toHaveCount(5);
  await expect(roles.getByRole('button', { name: '무관', exact: true })).toHaveCount(0);
  await expect(roles.locator('[aria-pressed=true]')).toHaveCount(5);
  await page.locator('.home-profile-link').click();
  await page.locator('.side-nav a[href="/app/home"]').click();
  await expect(roles.getByRole('button', { name: '무관', exact: true })).toHaveCount(0);
  await expect(roles.locator('[aria-pressed=true]')).toHaveCount(5);
});

test('매칭 관리 세 버튼과 별도 발견 카드, 원형 수락 버튼이 작동한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  const own = page.locator('.my-recruitment');
  await expect(own).not.toContainText('내 실시간 매칭');
  await expect(own).not.toContainText('자동 매칭');
  await expect(own.locator('.my-recruitment-actions button')).toHaveCount(3);
  await expect(own.locator('.match-condition-summary svg')).not.toHaveCount(0);
  await page.clock.fastForward(7000);
  const offer = page.locator('.duo-offer').first();
  await expect(offer).toBeVisible();
  await expect(page.locator('.home-profile-content .duo-offer')).toHaveCount(0);
  const panel = await page.locator('.home-profile-content').boundingBox();
  const card = await offer.boundingBox();
  expect(card!.y).toBeGreaterThan(panel!.y + panel!.height);
  expect(await offer.evaluate(el => getComputedStyle(el).animationName)).toContain('duo-discovery-glow');
  for (const label of ['다음에', '같이 할래요']) {
    const button = offer.getByRole('button', { name: label, exact: true });
    await expect(button).toHaveText('');
    expect(await button.evaluate(el => getComputedStyle(el).borderRadius)).toBe('50%');
  }
  await offer.getByRole('button', { name: '같이 할래요' }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await expect(own).toContainText('매칭 중');
  await page.clock.fastForward(22000);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toBeVisible();
});

test('좁은 화면에서도 네 포지션이 한 줄이며 X로 제안을 넘긴다', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 800 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.clock.install(); await login(page);
  const roles = page.locator('.recruitment-composer-shell').getByRole('group', { name: '찾는 포지션', exact: true });
  for (const role of ['탑', '정글', '미드', '바텀']) await roles.getByRole('button', { name: role, exact: true }).click();
  await startRealtimeMatch(page);
  await page.clock.fastForward(7000);
  const offer = page.locator('.duo-offer').first();
  await expect(offer).toBeVisible();
  expect(await offer.evaluate(el => getComputedStyle(el).animationName)).toBe('none');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  const summary = page.locator('.my-recruitment .match-condition-summary');
  expect(await summary.evaluate(el => el.scrollWidth <= el.clientWidth)).toBe(true);
  await offer.getByRole('button', { name: '다음에' }).click();
  await expect(page.locator('.duo-offer')).toHaveCount(0);
  await expect(page.locator('.my-recruitment')).toContainText('매칭 중');
});

test('조건 수정은 저장 전까지 상대와 원래 조건을 유지하고 취소할 수 있다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.clock.fastForward(7000);
  const offer = page.locator('.duo-offer').first();
  await expect(offer).toBeVisible();
  const person = await offer.getAttribute('aria-label');
  expect(await offer.evaluate(el => getComputedStyle(el).animationIterationCount)).toContain('infinite');
  await page.getByRole('button', { name: '조건 수정', exact: true }).click();
  await expect(offer).toHaveAttribute('aria-label', person!);
  await expect(offer).toBeVisible();
  const save = page.getByRole('button', { name: '매칭 조건 저장', exact: true });
  const bio = page.getByLabel('한마디', { exact: true });
  const original = await bio.inputValue();
  await expect(save).toBeDisabled();
  await bio.fill('취소할 변경');
  await expect(save).toBeEnabled();
  await bio.fill(original);
  await expect(save).toBeDisabled();
  await bio.fill('저장하지 않은 변경');
  await page.getByRole('button', { name: '취소', exact: true }).click();
  await expect(offer).toHaveAttribute('aria-label', person!);
  await expect(page.locator('.my-recruitment')).not.toContainText('저장하지 않은 변경');
  await page.getByRole('button', { name: '조건 수정', exact: true }).click();
  await expect(bio).toHaveValue(original);
  await expect(save).toBeDisabled();
});
