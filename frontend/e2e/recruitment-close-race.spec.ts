import { selectButton } from './helpers';
import { expect, test } from '@playwright/test';
import type { MatchRequestView } from '../src/api/types';
import { login, manageRecruitment } from './helpers';

declare global {
  interface Window {
    closedRequestSnapshots: { held: (() => void)[]; completed: number; released: boolean };
    delayClosedRequestSnapshot: (read: () => Promise<MatchRequestView>) => Promise<MatchRequestView>;
  }
}

test('종료된 매칭의 상태 응답이 늦어도 다른 매칭에 바로 참여하고 새 매칭을 유지한다', async ({ page }) => {
  // 종료 요청 자체는 완료하고, MatchContext가 읽는 이전 요청의 종료 응답만 늦춘다.
  await page.addInitScript(() => {
    const state = { held: [] as (() => void)[], completed: 0, released: false };
    window.closedRequestSnapshots = state;
    window.delayClosedRequestSnapshot = async read => {
      const request = await read();
      if (request.status === 'CANCELLED' && !state.released) {
        await new Promise<void>(resolve => state.held.push(resolve));
        state.completed++;
      }
      return request;
    };
  });
  await page.route(/\/src\/api\/client\.ts(?:\?.*)?$/, async route => {
    const response = await route.fetch();
    const source = await response.text();
    expect(source).toContain('export const getMatchRequest =');
    const body = source.replace('export const getMatchRequest =', 'const originalGetMatchRequest =')
      + '\nexport const getMatchRequest = id => window.delayClosedRequestSnapshot(() => originalGetMatchRequest(id));\n';
    await route.fulfill({ response, body });
  });

  await page.clock.install(); await login(page);
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  const dialog = page.locator('.recruitment-composer-shell');
  await selectButton(dialog.getByRole('group', { name: '원하는 큐 타입', exact: true }), '랭크');
  await selectButton(dialog.getByRole('group', { name: '내 포지션', exact: true }), '미드');
  await dialog.getByLabel('한마디', { exact: true }).fill('서로 존중하면서 즐겨요');
  await dialog.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await manageRecruitment(page, '매칭 종료');
  await expect.poll(() => page.evaluate(() => window.closedRequestSnapshots.held.length)).toBeGreaterThan(0);
  await expect(page.locator('.my-recruitment')).toHaveCount(0);
  await expect(page.locator('.match-stage')).toHaveCount(0);
  await expect(page.locator('.recruitment-composer-shell button[type=submit]')).toBeEnabled();

  await page.locator('.board-filter-bar').getByRole('button', { name: '칼바람', exact: true }).click();
  await page.getByRole('button', { name: '포로간식 매칭 글 상세', exact: true }).click();
  await page.getByRole('button', { name: '자기소개 작성하고 오케이 보내기', exact: true }).click();
  await expect(dialog.getByRole('group', { name: '원하는 큐 타입', exact: true }).getByRole('button', { name: '칼바람', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(dialog.getByLabel('한마디', { exact: true })).toHaveValue('서로 존중하면서 즐겨요');
  await dialog.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  const held = await page.evaluate(() => {
    const state = window.closedRequestSnapshots;
    const count = state.held.length;
    state.released = true;
    state.held.splice(0).forEach(resolve => resolve());
    return count;
  });
  await expect.poll(() => page.evaluate(() => window.closedRequestSnapshots.completed)).toBe(held);
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await expect(page.getByText('진행 중인 실시간 매칭을 확인해 주세요. 동시에 두 개를 등록할 수 없습니다.', { exact: true })).toHaveCount(0);
  await page.clock.fastForward(22000);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toContainText('포로간식');
  await page.getByRole('button', { name: '메시지로 이동' }).click();
  await expect(page.getByRole('note')).toContainText('매칭 성사');
});
