import { selectButton } from './helpers';
import { expect, test, type Page } from '@playwright/test';
import { login, manageRecruitment } from './helpers';

const modes = [
  { key: 'SOLO_DUO_RANKED', label: '랭크', host: 'PlayMaker', type: '실시간' },
  { key: 'NORMAL_DRAFT', label: '일반', host: '한판더할래', type: '실시간' },
  { key: 'SWIFTPLAY', label: '신속', host: '퇴근후십분', type: '예약' },
  { key: 'ARAM', label: '칼바람', host: '포로간식', type: '실시간' },
] as const;

async function myRecruitments(page: Page) {
  return page.evaluate(async () => {
    const path = '/src/api/recruitment.ts';
    const api = await import(/* @vite-ignore */ path);
    return api.myRecruitments();
  });
}

test('실시간·예약 모두 네 모드를 같은 이름과 2인 정원으로 보여주고 칼바람에는 포지션 필터가 없다', async ({ page }) => {
  await page.clock.install(); await login(page);
  const filters = page.locator('.board-filter-bar');
  const modeGroup = filters.getByRole('group', { name: '찾는 큐 타입', exact: true });
  const roleGroup = filters.getByRole('group', { name: '찾는 포지션', exact: true });
  const rows = page.locator('.recruitment-row');
  await expect(modeGroup.getByRole('button')).toHaveText(modes.map(mode => mode.label));
  await expect(page.locator('.board-results-head')).toContainText('40개 매칭 글');

  for (const type of ['실시간', '예약']) {
    await page.getByRole('tab', { name: `${type} 매칭`, exact: true }).click();
    for (const mode of modes) {
      await modeGroup.getByRole('button', { name: mode.label, exact: true }).click();
      await expect(page.locator('.board-results-head')).toContainText('10개 매칭 글');
      await expect(rows).toHaveCount(10);
      await expect(rows.locator('.row-mode .recruitment-mode')).toHaveText(Array(10).fill(mode.label));
      if (mode.key === 'ARAM') {
        await expect(roleGroup).toHaveCount(0);
        await expect(rows.locator('.recruitment-role-pair')).toHaveCount(0);
      } else {
        await expect(roleGroup).toBeVisible();
        await expect(rows.locator('.recruitment-role-pair')).toHaveCount(10);
      }
    }
  }
  await modeGroup.getByRole('button', { name: '랭크', exact: true }).click();
  await roleGroup.getByRole('button', { name: '탑', exact: true }).click();
  await expect(page.locator('.board-results-head')).toContainText('2개 매칭 글');
  await modeGroup.getByRole('button', { name: '칼바람', exact: true }).click();
  await expect(roleGroup).toHaveCount(0);
  await expect(page.locator('.board-results-head')).toContainText('10개 매칭 글');
  await modeGroup.getByRole('button', { name: '랭크', exact: true }).click();
  await expect(roleGroup.getByRole('button', { pressed: true })).toHaveCount(0);
  await expect(page.locator('.board-results-head')).toContainText('10개 매칭 글');
});

test('칼바람 소개는 포지션 입력을 숨기고 전송 조건만 무관으로 정리하며 작성하던 소개를 보존한다', async ({ page }) => {
  await page.clock.install(); await login(page);
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  const dialog = page.locator('.recruitment-composer-shell');
  const queue = dialog.getByRole('group', { name: '원하는 큐 타입', exact: true });
  const primary = dialog.getByRole('group', { name: '내 포지션', exact: true });
  const desired = dialog.getByRole('group', { name: '찾는 포지션', exact: true });
  await expect(queue.getByRole('button')).toHaveText(modes.map(mode => mode.label));
  await selectButton(queue, '랭크');
  await selectButton(primary, '바텀');
  await desired.getByRole('button', { name: '서포터', exact: true }).click();
  await selectButton(queue, '칼바람');
  await expect(primary).toHaveCount(0);
  await expect(desired).toHaveCount(0);
  await selectButton(queue, '일반');
  await expect(primary.getByRole('button', { name: '바텀', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(desired.getByRole('button', { name: '서포터', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await selectButton(queue, '칼바람');
  await dialog.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const [created] = await myRecruitments(page);
  expect(created.condition).toMatchObject({ modeKey: 'ARAM', keyCondition: { value: 'ANY' } });
  expect(created.preferences.desiredKeys).toEqual([]);
  expect(created.targetSize).toBe(2);
  await manageRecruitment(page, '조건 수정');
  await expect(queue.getByRole('button', { name: '칼바람', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(primary).toHaveCount(0);
  await expect(desired).toHaveCount(0);
  await page.keyboard.press('Escape');
  await manageRecruitment(page, '매칭 종료');
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  await selectButton(queue, '랭크');
  await expect(primary.getByRole('button', { name: '바텀', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(desired.getByRole('button', { name: '서포터', exact: true })).toHaveAttribute('aria-pressed', 'true');
});

for (const mode of modes) {
  test(`${mode.label} ${mode.type} 매칭은 같은 모드에 참여해 서로 수락 후 메시지로 연결된다`, async ({ page }) => {
    await page.clock.install(); await login(page);
    await page.getByRole('tab', { name: `${mode.type} 매칭`, exact: true }).click();
    const filters = page.locator('.board-filter-bar');
    await filters.getByRole('group', { name: '찾는 큐 타입', exact: true }).getByRole('button', { name: mode.label, exact: true }).click();
    await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
    const dialog = page.locator('.recruitment-composer-shell');
    await dialog.getByRole('group', { name: '원하는 큐 타입', exact: true }).getByRole('button', { name: mode.label, exact: true }).click();
    await dialog.getByRole('button', { name: '매칭 시작', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    const [created] = await myRecruitments(page);
    expect(created.condition.modeKey).toBe(mode.key);
    expect(created.type).toBe(mode.type === '예약' ? 'RESERVATION' : 'REALTIME');
    expect(created.targetSize).toBe(2);
    await page.getByRole('button', { name: `${mode.host} 매칭 글 상세`, exact: true }).click();
    await page.getByRole('button', { name: '같이 할래요', exact: true }).click();
    await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
    expect((await myRecruitments(page))[0].status).toBe('OPEN');
    await page.clock.fastForward(22000);
    await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toContainText(mode.host);
    const [matched] = await myRecruitments(page);
    expect(matched.condition.modeKey).toBe(mode.key); expect(matched.status).toBe('MATCHED');
    await page.getByRole('button', { name: '메시지로 이동' }).click();
    await expect(page.getByRole('note')).toContainText('매칭 성사');
  });
}

test('저장된 랭크 소개가 있어도 칼바람 글에서 바로 참여하면 해당 모드로 소개를 연다', async ({ page }) => {
  await page.clock.install(); await login(page);
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  const dialog = page.locator('.recruitment-composer-shell');
  await selectButton(dialog.getByRole('group', { name: '원하는 큐 타입', exact: true }), '랭크');
  await selectButton(dialog.getByRole('group', { name: '내 포지션', exact: true }), '미드');
  await dialog.getByLabel('한마디', { exact: true }).fill('서로 존중하면서 즐겨요');
  await dialog.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await manageRecruitment(page, '매칭 종료');
  await page.locator('.board-filter-bar').getByRole('button', { name: '칼바람', exact: true }).click();
  await page.getByRole('button', { name: '포로간식 매칭 글 상세', exact: true }).click();
  await page.getByRole('button', { name: '자기소개 작성하고 오케이 보내기', exact: true }).click();
  await expect(dialog.getByRole('group', { name: '원하는 큐 타입', exact: true }).getByRole('button', { name: '칼바람', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(dialog.getByRole('group', { name: '내 포지션', exact: true })).toHaveCount(0);
  await expect(dialog.getByLabel('한마디', { exact: true })).toHaveValue('서로 존중하면서 즐겨요');
  await dialog.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await page.clock.fastForward(22000);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toContainText('포로간식');
  expect((await myRecruitments(page))[0].condition.modeKey).toBe('ARAM');
});

test('첫 페이지 밖 신속 매칭과 자동 매칭돼도 상대 소개를 확인하고 수락할 수 있다', async ({ page }) => {
  await page.clock.install(); await login(page);
  await expect(page.locator('.recruitment-row .row-mode .recruitment-mode')).toHaveText(Array(10).fill('랭크'));
  await expect(page.getByRole('button', { name: '퇴근후십분 매칭 글 상세', exact: true })).toHaveCount(0);
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  const dialog = page.locator('.recruitment-composer-shell');
  await selectButton(dialog.getByRole('group', { name: '원하는 큐 타입', exact: true }), '신속');
  await dialog.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const proposal = page.locator('.duo-offer');
  await page.clock.fastForward(7000);
  await expect(proposal).toBeVisible();
  const peer = proposal.locator('.participant-introduction');
  await expect(peer.locator('.participant-facts')).toBeVisible();
  await peer.getByText('소개 보기', { exact: true }).click();
  await expect(peer.locator('.recruitment-introduction dl')).toContainText('신속');
  await expect(peer.locator('.recruitment-introduction > p')).not.toBeEmpty();
  await expect(peer).not.toContainText('불러오지 못했어요');
  await proposal.getByRole('button', { name: '같이 할래요', exact: true }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await page.clock.fastForward(22000);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toBeVisible();
  expect((await myRecruitments(page))[0].condition.modeKey).toBe('SWIFTPLAY');
});
