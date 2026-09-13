import { expect, test } from '@playwright/test';
import { login, manageRecruitment } from './helpers';

test('롤 전적은 직접 입력할 수 없으며 이전 수동 전적을 API 정보로 사용하지 않는다', async ({ page }) => {
  await login(page);
  await page.evaluate(() => localStorage.setItem('queuemate:introduction:v1:u-me:LOL', JSON.stringify({ primaryRole: 'MID', ownTier: 'GOLD', rankDivision: 'II', champions: ['아리'], winRate: 99, kda: 9, queueType: 'ANY' })));
  await page.locator('.intro-launch > button').click();
  const form = page.locator('.recruitment-composer-shell');
  await expect(form.getByRole('region', { name: '롤 전적 정보' })).toHaveCount(0);
  await expect(form.locator('input[type="number"], select, .introduction-records')).toHaveCount(0);
  await expect(form.getByLabel('선호 챔피언')).toHaveCount(0);
  await expect(form).not.toContainText('골드');
  await form.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(form).toHaveCount(0);
  const record = await page.evaluate(() => JSON.parse(localStorage.getItem('queuemate:introduction:v1:u-me:LOL')!));
  expect(record).toMatchObject({ primaryRole: 'MID', ownTier: null, rankDivision: null, champions: [], winRate: null, kda: null });
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
