import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('기본 랭크는 해제되지 않고 목록에는 모드 없이 챔피언 옆 음성이 보인다', async ({ page }) => {
  await login(page);
  const modes = page.locator('.board-filter-bar').getByRole('group', { name: '찾는 큐 타입' });
  const ranked = modes.getByRole('button', { name: '랭크', exact: true });
  await expect(ranked).toHaveAttribute('aria-pressed', 'true');
  await ranked.click();
  await expect(modes.locator('[aria-pressed=true]')).toHaveCount(1);
  await expect(ranked).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.recruitment-row .row-mode')).toHaveCount(0);
  await expect(page.locator('.recruitment-row .voice-optional')).toHaveCount(0);
  const enabled = page.locator('.row-player-heading .voice-required').first();
  const disabled = page.locator('.row-player-heading .voice-no_voice').first();
  await expect(enabled).toHaveCSS('color', 'rgb(74, 222, 128)');
  await expect(disabled).toHaveCSS('color', 'rgb(248, 113, 113)');
  await modes.getByRole('button', { name: '칼바람', exact: true }).click();
  await expect(modes.locator('[aria-pressed=true]')).toHaveCount(1);
  await expect(page.locator('.recruitment-row .row-roles')).toHaveCount(0);
  await page.getByRole('button', { name: '초기화', exact: true }).click();
  await expect(ranked).toHaveAttribute('aria-pressed', 'true');
});
