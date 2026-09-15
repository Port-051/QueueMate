import { expect, test } from '@playwright/test';
import { login, manageRecruitment, selectBoardFilter } from './helpers';

test('기본 랭크 모드로 선택 단계 없이 매칭을 시작한다', async ({ page }) => {
  await login(page);
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  const dialog = page.locator('.recruitment-composer-shell');
  await expect(dialog.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '무관', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(dialog.getByRole('group', { name: '원하는 큐 타입', exact: true }).locator('[aria-pressed="true"]')).toHaveCount(1);
  await expect(dialog.locator('.matching-rail-heading')).toHaveCount(0);
  await expect(dialog.getByLabel('내 티어', { exact: true })).toHaveCount(0);
  await expect(dialog.getByRole('region', { name: '롤 전적 정보' })).toHaveCount(0);
  await expect(dialog.getByRole('group', { name: '음성', exact: true }).getByRole('button', { name: '무관', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(dialog.getByRole('radiogroup', { name: '매칭 방식' })).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: '매칭 시작', exact: true })).toBeEnabled();
  await dialog.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  const row = await page.evaluate(async () => {
    const apiPath = '/src/api/recruitment.ts';
    const api = await import(/* @vite-ignore */ apiPath);
    return (await api.myRecruitments())[0];
  });
  expect(row.condition).toMatchObject({ modeKey: 'SOLO_DUO_RANKED', keyCondition: { value: 'ANY' }, voicePreference: 'OPTIONAL' });
  expect(row.preferences).toMatchObject({ ownTier: null, desiredKeys: [], minTier: null, maxTier: null, purposeRequired: false });
  expect(row.autoMatch).toBe(true);
});

test('검색과 별개로 아이콘 버튼을 선택하고 수정·다시 시작할 때 유지한다', async ({ page }) => {
  await login(page);
  const filters = page.locator('.board-filter-bar');
  await selectBoardFilter(page, '찾는 상대 티어', '다이아몬드');
  await filters.getByRole('button', { name: '칼바람', exact: true }).click();
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  const form = page.locator('.recruitment-composer-shell');
  await expect(form.getByRole('group', { name: '원하는 큐 타입' }).locator('[aria-pressed="true"]')).toHaveCount(1);
  await form.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '미드', exact: true }).click();
  await form.getByRole('group', { name: '원하는 큐 타입' }).getByRole('button', { name: '랭크', exact: true }).click();
  const desired = form.getByRole('group', { name: '찾는 포지션', exact: true });
  await desired.getByRole('button', { name: '정글', exact: true }).click();
  await desired.getByRole('button', { name: '서포터', exact: true }).click();
  await form.getByRole('group', { name: '음성', exact: true }).getByRole('button', { name: '사용', exact: true }).click();
  await form.getByLabel('한마디').fill('미드에서 편하게 함께해요');
  await form.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(form).toHaveCount(0);
  await expect(filters.getByRole('button', { name: '칼바람', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await manageRecruitment(page, '조건 수정');
  await expect(form.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '미드', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(desired.locator('[aria-pressed="true"]')).toHaveCount(2);
  await expect(form.locator('input[type="number"], select')).toHaveCount(0);
  await page.keyboard.press('Escape'); await manageRecruitment(page, '매칭 종료');
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  await expect(form.getByLabel('한마디')).toHaveValue('미드에서 편하게 함께해요');
  await expect(form.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '미드', exact: true })).toHaveAttribute('aria-pressed', 'true');
});

test('매칭 글 목록과 상세에서 자기소개 전적을 보여주고 최근 경기 20칸을 구분한다', async ({ page }) => {
  await login(page);
  const row = page.locator('.recruitment-row').first();
  await expect(row.locator('.row-introduction-stats')).toContainText('승률');
  await expect(row.locator('.row-introduction-stats')).toContainText('KDA');
  await expect(row.locator('.recruitment-role-pair')).toHaveText('');
  await expect(row.getByRole('img', { name: '탑', exact: true })).toHaveAttribute('title', '탑');
  await expect(row.getByRole('img', { name: '무관', exact: true })).toBeVisible();
  await expect(page.locator('.board-filter-bar').getByRole('button', { name: '바텀', exact: true })).toHaveAttribute('title', '바텀');
  await expect(page.getByRole('button', { name: 'LateGame 매칭 글 상세', exact: true }).getByRole('img', { name: '바텀', exact: true })).toBeVisible();
  await row.click();
  const dialog = page.getByRole('region', { name: '매칭 글 상세', exact: true });
  await expect(dialog).toContainText('예시 전적');
  await expect(dialog).toContainText('선호 챔피언');
  await expect(dialog.locator('.recruitment-role-pair')).toHaveText('');
  await expect(dialog.getByRole('img', { name: '탑', exact: true })).toBeVisible();
  await expect(dialog.locator('.recent-results > span')).toHaveCount(20);
  await expect(dialog.locator('.recent-results .win')).toHaveCount(12);
  await expect(dialog.locator('.recent-results .loss')).toHaveCount(8);
  await expect(dialog.getByRole('button', { name: '자기소개 작성하고 오케이 보내기' })).toBeEnabled();
  await page.keyboard.press('Escape');
  await expect(row).toBeFocused();
});

test('내 전적은 매칭 조건 대신 개인 프로필에서 확인한다', async ({ page }) => {
  await login(page);
  await page.locator('.home-profile-link').click();
  await expect(page.getByRole('region', { name: '롤 전적 정보', exact: true })).toContainText('연동 대기');
});

test('저장한 소개가 있어도 홈 재진입 시 바로 조건 선택으로 시작한다', async ({ page }) => {
  await login(page);
  await page.evaluate(() => localStorage.setItem('queuemate:introduction:v1:u-me:LOL', JSON.stringify({ primaryRole: 'MID', queueType: 'ANY', bio: '저장된 소개' })));
  await page.locator('.home-profile-link').click();
  await page.locator('.side-nav a[href="/app/home"]').click();
  const form = page.locator('.recruitment-composer-shell');
  await expect(form).toBeVisible();
  await expect(form.getByLabel('한마디')).toHaveValue('저장된 소개');
  await expect(form.getByRole('group', { name: '포지션', exact: true }).getByRole('button', { name: '미드', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.home-profile-introduction, .intro-launch')).toHaveCount(0);
});
