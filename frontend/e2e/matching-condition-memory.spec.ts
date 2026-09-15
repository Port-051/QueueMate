import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('조건은 메시지와 프로필을 다녀와도 유지되고 모드는 재클릭해도 해제되지 않는다', async ({ page }) => {
  await login(page);
  const form = page.locator('.recruitment-composer-shell');
  const modes = form.getByRole('group', { name: '원하는 큐 타입' });
  const rank = modes.getByRole('button', { name: '랭크', exact: true });
  await expect(rank).toHaveAttribute('aria-pressed', 'true');
  await rank.click();
  await expect(rank).toHaveAttribute('aria-pressed', 'true');
  await modes.getByRole('button', { name: '일반', exact: true }).click();
  await modes.getByRole('button', { name: '일반', exact: true }).click();
  await form.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '정글', exact: true }).click();
  await form.getByRole('group', { name: '찾는 포지션', exact: true }).getByRole('button', { name: '미드', exact: true }).click();
  await form.getByRole('group', { name: '음성', exact: true }).getByRole('button', { name: '사용', exact: true }).click();
  await form.getByLabel('한마디').fill('조건을 유지해 주세요');
  for (const destination of ['messages', 'me']) {
    await page.locator(`.side-nav a[href="/app/${destination}"]`).click();
    await page.locator('.side-nav a[href="/app/home"]').click();
    await expect(modes.locator('[aria-pressed="true"]')).toHaveCount(1);
    await expect(modes.getByRole('button', { name: '일반', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(form.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '정글', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(form.getByRole('group', { name: '찾는 포지션', exact: true }).getByRole('button', { name: '미드', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(form.getByRole('group', { name: '음성', exact: true }).getByRole('button', { name: '사용', exact: true })).toHaveAttribute('aria-pressed', 'true');
    await expect(form.getByLabel('한마디')).toHaveValue('조건을 유지해 주세요');
  }
  await expect(form.getByRole('radiogroup', { name: '매칭 방식' })).toHaveCount(0);
  await expect(form.getByRole('button', { name: '매칭 시작', exact: true }).locator('svg')).toHaveCount(1);
});

test('예약 시간과 플레이 양도 페이지 이동 뒤 유지된다', async ({ page }) => {
  await page.clock.setFixedTime(new Date('2026-09-14T00:00:00Z'));
  await login(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  const form = page.locator('.recruitment-composer-shell');
  await form.getByLabel('시작 가능 시각').fill('2026-09-16T20:00');
  await form.getByLabel('마지막 종료 시각').fill('2026-09-16T22:00');
  await form.getByRole('button', { name: '두 게임 이상' }).click();
  await page.locator('.side-nav a[href="/app/messages"]').click();
  await page.locator('.side-nav a[href="/app/home"]').click();
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await expect(form.getByLabel('시작 가능 시각')).toHaveValue('2026-09-16T20:00');
  await expect(form.getByLabel('마지막 종료 시각')).toHaveValue('2026-09-16T22:00');
  await expect(form.getByRole('button', { name: '두 게임 이상' })).toHaveAttribute('aria-pressed', 'true');
});
