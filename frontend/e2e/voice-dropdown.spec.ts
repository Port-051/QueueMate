import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('마이크 무관은 모두 표시하고 사용과 미사용을 개별 검색한다', async ({ page }) => {
  await login(page);
  const filter = page.locator('.board-filter-bar').getByRole('button', { name: '마이크', exact: true });
  const choose = async (label: string) => {
    await filter.click();
    await page.getByRole('listbox', { name: '마이크', exact: true }).getByRole('option', { name: label, exact: true }).click();
  };
  const rows = page.locator('.recruitment-row');
  await choose('사용');
  await expect(rows).toHaveCount(3);
  await expect(rows.locator('.voice-required')).toHaveCount(3);
  await choose('미사용');
  await expect(rows).toHaveCount(3);
  await expect(rows.locator('.voice-no_voice')).toHaveCount(3);
  await choose('무관');
  await expect(rows).toHaveCount(10);
});

test('다섯 포지션은 ALL 아이콘으로 축약되며 본인과 상대가 별도 칸에 정렬된다', async ({ page }) => {
  await login(page);
  const form = page.locator('.recruitment-composer-shell');
  for (const label of ['포지션', '찾는 포지션']) {
    const group = form.getByRole('group', { name: label, exact: true });
    for (const button of await group.locator('[aria-pressed=false]').all()) await button.click();
  }
  await startRealtimeMatch(page);
  const row = page.getByRole('button', { name: 'QueueMaster 매칭 글 상세', exact: true });
  await expect(row.locator('.recruitment-role-own').getByRole('img', { name: 'ALL' })).toHaveCount(1);
  await expect(row.locator('.recruitment-role-targets').getByRole('img', { name: 'ALL' })).toHaveCount(1);
  await expect(row.locator('.recruitment-role-pair')).toHaveCSS('display', 'grid');
});
