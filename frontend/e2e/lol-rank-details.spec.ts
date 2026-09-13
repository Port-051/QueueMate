import { expect, test } from '@playwright/test';
import { login, manageRecruitment } from './helpers';

test('롤 자기소개는 LP 없이 IV~I 단계를 보관하고 티어 변경 시 지난 세부 정보를 지운다', async ({ page }) => {
  await login(page);
  await page.locator('.intro-launch').getByRole('button').click();
  const dialog = page.getByRole('dialog');
  const tier = dialog.getByLabel('내 티어', { exact: true });
  const division = dialog.getByLabel('세부 단계', { exact: true });
  const lp = dialog.getByLabel('LP', { exact: true });
  const submit = dialog.getByRole('button', { name: '모집 시작', exact: true });
  await expect(tier.locator('option')).toHaveText(['미입력', '아이언', '브론즈', '실버', '골드', '플래티넘', '에메랄드', '다이아몬드', '마스터', '그랜드마스터', '챌린저']);
  await expect(division).toHaveCount(0);
  await expect(lp).toHaveCount(0);
  await tier.selectOption('GOLD');
  await expect(division.locator('option')).toHaveText(['미입력', 'IV', 'III', 'II', 'I']);
  await expect(division).toHaveValue('');
  await expect(lp).toHaveCount(0);
  await division.selectOption('II');
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect(dialog).toHaveCount(0);
  await expect(page.locator('.home-profile-facts .rank-badge-label')).toHaveText('골드 II');
  const saved = await page.evaluate(async () => {
    const introductionPath = '/src/domain/introduction.ts';
    const apiPath = '/src/api/client.ts';
    const recruitmentPath = '/src/api/recruitment.ts';
    const { readIntroduction } = await import(/* @vite-ignore */ introductionPath);
    const api = await import(/* @vite-ignore */ apiPath);
    const recruitment = await import(/* @vite-ignore */ recruitmentPath);
    const me = await api.getMe();
    return { introduction: readIntroduction(me.id, 'LOL'), preferences: (await recruitment.myRecruitments())[0].preferences };
  });
  expect(saved.introduction).toMatchObject({ ownTier: 'GOLD', rankDivision: 'II' });
  expect(saved.introduction).not.toHaveProperty('rankLp');
  expect(saved.preferences.ownTier).toBe('GOLD');
  expect(saved.preferences).not.toHaveProperty('rankDivision');
  expect(saved.preferences).not.toHaveProperty('rankLp');
  await manageRecruitment(page, '조건 수정');
  await expect(division).toHaveValue('II');
  await expect(lp).toHaveCount(0);
  await tier.selectOption('MASTER');
  await expect(division).toHaveCount(0);
  await expect(lp).toHaveCount(0);
  await tier.selectOption('IRON');
  await expect(division).toHaveValue('');
  await tier.selectOption('');
  await expect(division).toHaveCount(0);
  await expect(lp).toHaveCount(0);
});

test('이전 저장 데이터의 LP를 무시하고 누락되거나 잘못된 랭크 단계는 표시하지 않는다', async ({ page }) => {
  await page.goto('/login');
  const results = await page.evaluate(async () => {
    const introductionPath = '/src/domain/introduction.ts';
    const rankPath = '/src/domain/lolRank.ts';
    const gameConfigPath = '/src/domain/gameConfig.ts';
    const { readIntroduction, saveIntroduction, emptyIntroduction, introductionFromBoard } = await import(/* @vite-ignore */ introductionPath);
    const { formatLolRank } = await import(/* @vite-ignore */ rankPath);
    const { defaultCondition } = await import(/* @vite-ignore */ gameConfigPath);
    localStorage.setItem('queuemate:introduction:v1:legacy:LOL', JSON.stringify({ ownTier: 'GOLD' }));
    const legacy = readIntroduction('legacy', 'LOL');
    localStorage.setItem('queuemate:introduction:v1:legacy-lp:LOL', JSON.stringify({ ownTier: 'GOLD', rankDivision: 'II', rankLp: 50 }));
    const legacyLp = readIntroduction('legacy-lp', 'LOL');
    saveIntroduction('apex', 'LOL', { ...emptyIntroduction(), ownTier: 'MASTER', rankDivision: 'I' });
    saveIntroduction('invalid', 'LOL', { ...emptyIntroduction(), ownTier: 'SILVER', rankDivision: 'V' });
    saveIntroduction('other-game', 'VALORANT', { ...emptyIntroduction(), ownTier: 'GOLD', rankDivision: 'II' });
    const changedTier = introductionFromBoard({ condition: defaultCondition('LOL'), preferences: { ownTier: 'DIAMOND', desiredKeys: [] } }, { ...emptyIntroduction(), ownTier: 'GOLD', rankDivision: 'II' });
    return {
      legacy, legacyLp, apex: readIntroduction('apex', 'LOL'), invalid: readIntroduction('invalid', 'LOL'),
      otherGame: readIntroduction('other-game', 'VALORANT'), changedTier,
      labels: [formatLolRank(null), formatLolRank(legacy.ownTier, legacy.rankDivision), formatLolRank(legacyLp.ownTier, legacyLp.rankDivision), formatLolRank('DIAMOND', 'IV'), formatLolRank('MASTER', 'I'), formatLolRank('CHALLENGER', null)],
    };
  });
  expect(results.legacy).toMatchObject({ ownTier: 'GOLD', rankDivision: null });
  expect(results.legacyLp).toMatchObject({ ownTier: 'GOLD', rankDivision: 'II' });
  for (const introduction of [results.legacy, results.legacyLp, results.apex, results.invalid, results.otherGame, results.changedTier]) {
    expect(introduction).not.toHaveProperty('rankLp');
  }
  expect(results.apex).toMatchObject({ ownTier: 'MASTER', rankDivision: null });
  expect(results.invalid).toMatchObject({ ownTier: 'SILVER', rankDivision: null });
  expect(results.otherGame).toMatchObject({ ownTier: 'GOLD', rankDivision: null });
  expect(results.changedTier).toMatchObject({ ownTier: 'DIAMOND', rankDivision: null });
  expect(results.labels).toEqual(['티어 미입력', '골드', '골드 II', '다이아몬드 IV', '마스터', '챌린저']);
});
