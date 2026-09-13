import { expect, test } from '@playwright/test';
import { login, manageRecruitment } from './helpers';

test('롤 자기소개는 IV~I 단계와 티어별 LP를 보관하고 티어 변경 시 지난 세부 정보를 지운다', async ({ page }) => {
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
  await expect(lp).toHaveValue('');
  await division.selectOption('II');
  await lp.fill('100');
  await expect(submit).toBeDisabled();
  await expect(dialog).toContainText('LP는 0~99 사이의 정수로 입력해 주세요.');
  await lp.fill('99');
  await expect(submit).toBeEnabled();
  await tier.selectOption('MASTER');
  await expect(division).toHaveCount(0);
  await expect(lp).toHaveValue('');
  await expect(lp).not.toHaveAttribute('max');
  await lp.fill('1250');
  await submit.click();
  await expect(dialog).toHaveCount(0);
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
  expect(saved.introduction).toMatchObject({ ownTier: 'MASTER', rankDivision: null, rankLp: 1250 });
  expect(saved.preferences.ownTier).toBe('MASTER');
  expect(saved.preferences).not.toHaveProperty('rankDivision');
  expect(saved.preferences).not.toHaveProperty('rankLp');
  await manageRecruitment(page, '조건 수정');
  await expect(lp).toHaveValue('1250');
  await tier.selectOption('IRON');
  await expect(division).toHaveValue('');
  await expect(lp).toHaveValue('');
  await expect(lp).toHaveAttribute('max', '99');
  await tier.selectOption('');
  await expect(division).toHaveCount(0);
  await expect(lp).toHaveCount(0);
});

test('이전 저장 데이터의 누락된 랭크를 추정하지 않고 잘못된 세부 정보는 표시하지 않는다', async ({ page }) => {
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
    saveIntroduction('apex', 'LOL', { ...emptyIntroduction(), ownTier: 'MASTER', rankDivision: 'I', rankLp: 1500 });
    saveIntroduction('invalid', 'LOL', { ...emptyIntroduction(), ownTier: 'SILVER', rankDivision: 'V', rankLp: 100 });
    saveIntroduction('other-game', 'VALORANT', { ...emptyIntroduction(), ownTier: 'GOLD', rankDivision: 'II', rankLp: 50 });
    const changedTier = introductionFromBoard({ condition: defaultCondition('LOL'), preferences: { ownTier: 'DIAMOND', desiredKeys: [] } }, { ...emptyIntroduction(), ownTier: 'GOLD', rankDivision: 'II', rankLp: 50 });
    return {
      legacy, apex: readIntroduction('apex', 'LOL'), invalid: readIntroduction('invalid', 'LOL'),
      otherGame: readIntroduction('other-game', 'VALORANT'), changedTier,
      labels: [formatLolRank(null), formatLolRank(legacy.ownTier, legacy.rankDivision, legacy.rankLp), formatLolRank('DIAMOND', 'IV', 0), formatLolRank('MASTER', 'I', 1500), formatLolRank('CHALLENGER', null, 2500)],
    };
  });
  expect(results.legacy).toMatchObject({ ownTier: 'GOLD', rankDivision: null, rankLp: null });
  expect(results.apex).toMatchObject({ ownTier: 'MASTER', rankDivision: null, rankLp: 1500 });
  expect(results.invalid).toMatchObject({ ownTier: 'SILVER', rankDivision: null, rankLp: null });
  expect(results.otherGame).toMatchObject({ ownTier: 'GOLD', rankDivision: null, rankLp: null });
  expect(results.changedTier).toMatchObject({ ownTier: 'DIAMOND', rankDivision: null, rankLp: null });
  expect(results.labels).toEqual(['티어 미입력', '골드', '다이아몬드 IV · 0 LP', '마스터 · 1500 LP', '챌린저 · 2500 LP']);
});
