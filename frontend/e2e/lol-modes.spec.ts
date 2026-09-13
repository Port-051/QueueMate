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
    const resource = performance.getEntriesByType('resource').find(entry => new URL(entry.name).pathname === '/src/api/recruitment.ts');
    if (!resource) throw new Error('모집 API 모듈이 로드되지 않았습니다');
    const api = await import(/* @vite-ignore */ resource.name);
    return api.myRecruitments();
  });
}

test('실시간·예약 모두 네 모드를 같은 이름과 2인 정원으로 보여주고 칼바람에는 포지션 필터가 없다', async ({ page }) => {
  await login(page);
  const filters = page.locator('.board-filter-bar');
  const modeGroup = filters.getByRole('group', { name: '찾는 큐 타입', exact: true });
  const roleGroup = filters.getByRole('group', { name: '찾는 상대 포지션', exact: true });
  const rows = page.locator('.recruitment-row');
  await expect(modeGroup.getByRole('button')).toHaveText(['전체', ...modes.map(mode => mode.label)]);
  await expect(page.locator('.board-results-head')).toContainText('40개 모집');

  for (const type of ['실시간', '예약']) {
    await page.getByRole('tab', { name: `${type} 매치`, exact: true }).click();
    for (const mode of modes) {
      await modeGroup.getByRole('button', { name: mode.label, exact: true }).click();
      await expect(page.locator('.board-results-head')).toContainText('10개 모집');
      await expect(rows).toHaveCount(10);
      await expect(rows.locator('.row-roles > small')).toHaveText(Array(10).fill(`${mode.label} · 1/2명`));
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
  await expect(page.locator('.board-results-head')).toContainText('2개 모집');
  await modeGroup.getByRole('button', { name: '칼바람', exact: true }).click();
  await expect(roleGroup).toHaveCount(0);
  await expect(page.locator('.board-results-head')).toContainText('10개 모집');
  await modeGroup.getByRole('button', { name: '랭크', exact: true }).click();
  await expect(roleGroup.getByRole('button', { pressed: true })).toHaveCount(0);
  await expect(page.locator('.board-results-head')).toContainText('10개 모집');
});

test('칼바람 소개는 포지션 입력을 숨기고 전송 조건만 무관으로 정리하며 작성하던 소개를 보존한다', async ({ page }) => {
  await login(page);
  await page.locator('.intro-launch > button').click();
  const dialog = page.getByRole('dialog');
  const queue = dialog.getByLabel('원하는 큐 타입', { exact: true });
  const primary = dialog.getByLabel('주 포지션', { exact: true });
  const desired = dialog.getByRole('group', { name: '찾는 상대 포지션', exact: true });
  await expect(queue.locator('option')).toHaveText(['무관', ...modes.map(mode => mode.label)]);
  await queue.selectOption('SOLO_DUO_RANKED');
  await primary.selectOption('ADC');
  await desired.getByRole('button', { name: '서포터', exact: true }).click();
  await queue.selectOption('ARAM');
  await expect(primary).toHaveCount(0);
  await expect(desired).toHaveCount(0);
  await queue.selectOption('NORMAL_DRAFT');
  await expect(primary).toHaveValue('ADC');
  await expect(desired.getByRole('button', { name: '서포터', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await queue.selectOption('ARAM');
  await dialog.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const [created] = await myRecruitments(page);
  expect(created.condition).toMatchObject({ modeKey: 'ARAM', keyCondition: { value: 'ANY' } });
  expect(created.preferences.desiredKeys).toEqual([]);
  expect(created.targetSize).toBe(2);
  await manageRecruitment(page, '조건 수정');
  await expect(queue).toHaveValue('ARAM');
  await expect(primary).toHaveCount(0);
  await expect(desired).toHaveCount(0);
  await page.keyboard.press('Escape');
  await manageRecruitment(page, '모집 종료');
  await page.locator('.intro-launch > button').click();
  await queue.selectOption('SOLO_DUO_RANKED');
  await expect(primary).toHaveValue('ADC');
  await expect(desired.getByRole('button', { name: '서포터', exact: true })).toHaveAttribute('aria-pressed', 'true');
});

for (const mode of modes) {
  test(`${mode.label} ${mode.type} 모집은 같은 모드에 참여해 두 명 수락 후 파티로 연결된다`, async ({ page }) => {
    await login(page);
    await page.getByRole('tab', { name: `${mode.type} 매치`, exact: true }).click();
    const filters = page.locator('.board-filter-bar');
    await filters.getByRole('group', { name: '찾는 큐 타입', exact: true }).getByRole('button', { name: mode.label, exact: true }).click();
    await page.locator('.intro-launch > button').click();
    const dialog = page.getByRole('dialog');
    await dialog.getByLabel('원하는 큐 타입', { exact: true }).selectOption(mode.key);
    await dialog.getByRole('radio', { name: '수동 매칭', exact: true }).check();
    await dialog.getByRole('button', { name: '모집 시작', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    const [created] = await myRecruitments(page);
    expect(created.condition.modeKey).toBe(mode.key);
    expect(created.type).toBe(mode.type === '예약' ? 'RESERVATION' : 'REALTIME');
    expect(created.targetSize).toBe(2);
    await page.getByRole('button', { name: `${mode.host} 모집 상세`, exact: true }).click();
    await page.getByRole('button', { name: '이 모집에 참여 신청', exact: true }).click();
    await expect(page.locator('.board-proposal')).toBeVisible();
    await expect(page.locator('.compact-party')).toHaveCount(0);
    await page.getByRole('button', { name: '함께할게요', exact: true }).click();
    await expect(page.locator('.compact-party')).toBeVisible();
    await expect(page.locator('.compact-party h2').first()).toHaveText(`리그 오브 레전드 · ${mode.label}`);
    await expect(page.locator('.compact-party-members > div')).toHaveCount(2);
    await expect(page.locator('.compact-party-members')).toContainText(mode.host);
    await expect(page.getByRole('button', { name: '게임 준비 완료', exact: true })).toBeEnabled();
  });
}

test('저장된 랭크 소개가 있어도 칼바람 글에서 바로 참여하면 해당 모드로 소개를 연다', async ({ page }) => {
  await login(page);
  await page.locator('.intro-launch > button').click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('원하는 큐 타입', { exact: true }).selectOption('SOLO_DUO_RANKED');
  await dialog.getByLabel('주 포지션', { exact: true }).selectOption('MID');
  await dialog.getByLabel('모집 한마디', { exact: true }).fill('서로 존중하면서 즐겨요');
  await dialog.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await manageRecruitment(page, '모집 종료');
  await page.locator('.board-filter-bar').getByRole('button', { name: '칼바람', exact: true }).click();
  await page.getByRole('button', { name: '포로간식 모집 상세', exact: true }).click();
  await page.getByRole('button', { name: '자기소개 입력하고 참여 신청', exact: true }).click();
  await expect(dialog.getByLabel('원하는 큐 타입', { exact: true })).toHaveValue('ARAM');
  await expect(dialog.getByLabel('주 포지션', { exact: true })).toHaveCount(0);
  await expect(dialog.getByLabel('모집 한마디', { exact: true })).toHaveValue('서로 존중하면서 즐겨요');
  await dialog.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator('.board-proposal')).toBeVisible();
  await page.getByRole('button', { name: '함께할게요', exact: true }).click();
  await expect(page.locator('.compact-party h2').first()).toHaveText('리그 오브 레전드 · 칼바람');
  await expect(page.locator('.compact-party-members > div')).toHaveCount(2);
});

test('첫 페이지 밖 신속 모집과 자동 매칭돼도 상대 소개를 확인하고 수락할 수 있다', async ({ page }) => {
  await login(page);
  await expect(page.locator('.recruitment-row .row-roles > small')).toHaveText(Array(10).fill('랭크 · 1/2명'));
  await expect(page.getByRole('button', { name: '퇴근후십분 모집 상세', exact: true })).toHaveCount(0);
  await page.locator('.intro-launch > button').click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('원하는 큐 타입', { exact: true }).selectOption('SWIFTPLAY');
  await dialog.getByRole('radio', { name: '자동 매칭', exact: true }).check();
  await dialog.getByRole('button', { name: '모집 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const proposal = page.locator('.board-proposal');
  await expect(proposal).toBeVisible();
  const peer = proposal.locator('.participant-introduction');
  await expect(peer.locator('.participant-facts')).toBeVisible();
  await peer.getByText('소개 보기', { exact: true }).click();
  await expect(peer.locator('.recruitment-introduction dl')).toContainText('신속');
  await expect(peer.locator('.recruitment-introduction > p')).toHaveText('함께 한 판 하실 분 구해요');
  await expect(peer).not.toContainText('불러오지 못했어요');
  await page.getByRole('button', { name: '함께할게요', exact: true }).click();
  await expect(page.locator('.compact-party h2').first()).toHaveText('리그 오브 레전드 · 신속');
  await expect(page.locator('.compact-party-members > div')).toHaveCount(2);
});
