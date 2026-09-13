import { expect, test, type Locator, type Page } from '@playwright/test';
import { login } from './helpers';

async function expectLoadedPortrait(portrait: Locator) {
  await expect(portrait).toBeVisible();
  await expect.poll(() => portrait.evaluate(image => image instanceof HTMLImageElement && image.complete && image.naturalWidth > 0)).toBe(true);
}

async function saveExamples(page: Page, examples: { userId: string; champions: string[]; winRate: number | null; kda: number | null }[]) {
  await page.addInitScript(examples => {
    for (const { userId, ...introduction } of examples) {
      localStorage.setItem(`queuemate:introduction:v1:${encodeURIComponent(userId)}:LOL`, JSON.stringify(introduction));
    }
  }, examples);
}

test('네 모드의 모집은 필터와 같은 아이콘을 쓰며 2인 정원 표시 없이 챔피언 사진을 보여준다', async ({ page }, testInfo) => {
  await login(page);
  const modes = page.getByRole('group', { name: '찾는 큐 타입', exact: true });
  const rows = page.locator('.recruitment-row');
  const dialog = page.getByRole('dialog', { name: '모집 상세', exact: true });

  for (const mode of ['랭크', '일반', '신속', '칼바람']) {
    const filter = modes.getByRole('button', { name: mode, exact: true });
    await filter.click();
    await expect(page.locator('.board-results-head')).toContainText('10개 모집');
    await expect(rows.locator(':scope > .row-mode .recruitment-mode')).toHaveText(Array(10).fill(mode));
    await expect(page.locator('.recruitment-list-head > span')).toHaveText(mode === '칼바람'
      ? ['플레이어 · 자기소개', '모드', '음성 · 게시/활동']
      : ['플레이어 · 자기소개', '모드', '주 포지션 → 찾는 상대', '음성 · 게시/활동']);
    await expect(rows.locator(':scope > .row-roles')).toHaveCount(mode === '칼바람' ? 0 : 10);
    if (mode !== '칼바람') {
      const modeColumn = await rows.first().locator('.row-mode').boundingBox();
      const roleColumn = await rows.first().locator('.row-roles').boundingBox();
      expect(modeColumn).not.toBeNull();
      expect(roleColumn).not.toBeNull();
      expect(modeColumn!.x + modeColumn!.width, '모드와 포지션은 나란히 분리된 열이다').toBeLessThanOrEqual(roleColumn!.x);
    }
    const filterDrawing = await filter.locator('svg').innerHTML();
    expect(await rows.first().locator('.recruitment-mode svg').innerHTML()).toBe(filterDrawing);
    await expect(rows.filter({ hasText: '1/2명' })).toHaveCount(0);
    await rows.first().click();
    await expect(dialog).not.toContainText('1/2명');
    await expect(dialog.locator('.recruitment-mode')).toHaveText(mode);
    expect(await dialog.locator('.recruitment-mode svg').innerHTML()).toBe(filterDrawing);
    await expectLoadedPortrait(dialog.getByRole('img', { name: '리 신 초상화', exact: true }));
    await page.keyboard.press('Escape');
  }

  await modes.getByRole('button', { name: '랭크', exact: true }).click();
  for (const name of ['리 신', '비에고', '아리', '오리아나', '쓰레쉬', '룰루', '징크스', '카이사', '신드라', '아지르']) {
    const champion = rows.locator('.preferred-champion').filter({ has: page.getByRole('img', { name: `${name} 초상화`, exact: true }) }).first();
    await expect(champion.locator('.preferred-champion-name')).toBeHidden();
    await expect(champion).not.toHaveAttribute('title');
    await expectLoadedPortrait(champion.getByRole('img', { name: `${name} 초상화`, exact: true }));
    await champion.hover();
    await expect(page.getByRole('tooltip')).toBeVisible({ timeout: 500 });
    await expect(page.getByRole('tooltip')).toHaveText(name);
    if (name === '리 신') await page.screenshot({ path: testInfo.outputPath('recruitment-champion-tooltip.png') });
    await page.mouse.move(0, 0);
    await expect(page.getByRole('tooltip')).toHaveCount(0);
  }

  for (const width of [1600, 360]) {
    await page.setViewportSize({ width, height: 900 });
    await rows.first().scrollIntoViewIfNeeded();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `모집 목록 ${width}px 가로 넘침`).toBe(true);
    await expect(rows.first().locator('.row-mode')).toBeVisible();
    await expect(rows.first().locator('.row-roles')).toBeVisible();
    await expectLoadedPortrait(rows.first().getByRole('img', { name: '리 신 초상화', exact: true }));
    await page.screenshot({ path: testInfo.outputPath(`recruitment-presentation-${width}.png`) });
    await rows.first().click();
    expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth), `모집 상세 ${width}px 가로 넘침`).toBe(true);
    await expectLoadedPortrait(dialog.getByRole('img', { name: '리 신 초상화', exact: true }));
    await page.keyboard.press('Escape');
  }
});

test('승률과 KDA는 수치 구간에 따라 다섯 색상으로 구분하며 미입력을 낮은 기록으로 표시하지 않는다', async ({ page }) => {
  const examples = [
    { userId: 'u-gankflow', nickname: 'GankFlow', winRate: 44, kda: 0.99, tone: 'red' },
    { userId: 'u-playmaker', nickname: 'PlayMaker', winRate: 45, kda: 1, tone: 'orange' },
    { userId: 'u-supportlife', nickname: 'SupportLife', winRate: 48, kda: 1.99, tone: 'orange' },
    { userId: 'u-lategame', nickname: 'LateGame', winRate: 49, kda: 2, tone: 'yellow' },
    { userId: 'u-midtheory', nickname: 'MidTheory', winRate: 52, kda: 2.99, tone: 'yellow' },
    { userId: 'u-blueocean', nickname: 'BlueOcean', winRate: 53, kda: 3, tone: 'green' },
    { userId: 'u-chickendinner', nickname: 'ChickenDinner', winRate: 59, kda: 3.99, tone: 'green' },
    { userId: 'u-silentjungle', nickname: 'SilentJungle', winRate: 60, kda: 4, tone: 'blue' },
  ];
  await saveExamples(page, [...examples.map(example => ({ ...example, champions: ['아리'] })), { userId: 'u-aimking', champions: [], winRate: null, kda: null }]);
  await login(page);
  await page.getByRole('group', { name: '찾는 큐 타입', exact: true }).getByRole('button', { name: '랭크', exact: true }).click();
  const colors: string[] = [];
  const dialog = page.getByRole('dialog', { name: '모집 상세', exact: true });
  for (const example of examples) {
    const row = page.getByRole('button', { name: `${example.nickname} 모집 상세`, exact: true });
    const values = row.locator('.performance-value');
    await expect(values).toHaveText([`${example.winRate}%`, example.kda.toFixed(2)]);
    for (const value of await values.all()) await expect(value).toHaveAttribute('data-tone', example.tone);
    colors.push(await values.first().evaluate(element => getComputedStyle(element).color));
    await row.click();
    const details = dialog.locator('.performance-value');
    await expect(details).toHaveText([`${example.winRate}%`, example.kda.toFixed(2)]);
    for (const value of await details.all()) await expect(value).toHaveAttribute('data-tone', example.tone);
    expect(await details.first().evaluate(element => getComputedStyle(element).color)).toBe(colors.at(-1));
    await page.keyboard.press('Escape');
  }
  expect(new Set(colors).size, '다섯 구간은 서로 다른 실제 글자 색상으로 표시한다').toBe(5);
  const noRecord = page.getByRole('button', { name: 'AimKing 모집 상세', exact: true });
  await expect(noRecord.locator('.performance-value')).toHaveCount(0);
  await noRecord.click();
  await expect(dialog.locator('.performance-value')).toHaveText(['미입력', '미입력']);
  await expect(dialog.locator('.performance-value[data-tone]')).toHaveCount(0);
});

test('알 수 없는 챔피언과 불러오지 못한 초상화는 대체 아이콘과 즉시 보이는 이름 툴팁을 제공한다', async ({ page }) => {
  await saveExamples(page, [{ userId: 'u-gankflow', champions: ['처음 보는 챔피언', '리 신'], winRate: null, kda: null }]);
  await page.route(/\/[^/]*LeeSin[^/]*\.png(?:\?.*)?$/i, route => route.request().resourceType() === 'image' ? route.abort() : route.continue());
  await login(page);
  const row = page.getByRole('button', { name: 'GankFlow 모집 상세', exact: true });
  const dialog = page.getByRole('dialog', { name: '모집 상세', exact: true });
  for (const surface of [row, dialog]) {
    if (surface === dialog) await row.click();
    for (const name of ['처음 보는 챔피언', '리 신']) {
      const champion = surface.locator('.preferred-champion').filter({ has: page.getByRole('img', { name: `${name} 초상화`, exact: true }) });
      await expect(champion.getByRole('img', { name: `${name} 초상화`, exact: true })).toBeVisible();
      await expect(champion.locator('.preferred-champion-name')).toBeHidden();
      await expect(champion.locator('img')).toHaveCount(0);
      await champion.hover();
      await expect(page.getByRole('tooltip')).toBeVisible({ timeout: 500 });
      await expect(page.getByRole('tooltip')).toHaveText(name);
      await page.mouse.move(0, 0);
      await expect(page.getByRole('tooltip')).toHaveCount(0);
    }
  }
});

test('모집은 닉네임 앞에 티어 아이콘을 두고 음성 상태와 게시·활동 시간을 각각 표시한다', async ({ page }) => {
  const now = Date.parse('2026-09-14T12:00:00Z');
  const ago = (offset: number) => new Date(now - offset).toISOString();
  const examples = [
    { ownTier: 'GOLD', tier: '골드', voicePreference: 'REQUIRED', voice: '음성 사용', createdAt: ago(90_000), posted: '1분 전 게시', confirmedAt: ago(30_000), active: '방금 활동' },
    { ownTier: 'SILVER', tier: '실버', voicePreference: 'NO_VOICE', voice: '음성 사용 안 함', createdAt: ago(2 * 3_600_000 + 30_000), posted: '2시간 전 게시', confirmedAt: ago(90_000), active: '1분 전 활동' },
    { ownTier: null, tier: '티어 미입력', voicePreference: 'OPTIONAL', voice: '음성 무관', createdAt: ago(3 * 86_400_000 + 30_000), posted: '3일 전 게시', confirmedAt: ago(2 * 3_600_000 + 30_000), active: '2시간 전 활동' },
    { ownTier: 'DIAMOND', tier: '다이아몬드', voicePreference: 'REQUIRED', voice: '음성 사용', createdAt: ago(4 * 86_400_000 + 30_000), posted: '4일 전 게시', confirmedAt: ago(2 * 86_400_000 + 30_000), active: '2일 전 활동' },
  ];
  await page.clock.install({ time: new Date(now) });
  await page.route(/\/src\/api\/recruitment\.ts(?:\?.*)?$/, async route => {
    const response = await route.fetch();
    const source = await response.text();
    expect(source).toContain('export const searchBoard =');
    // 조회 응답의 시각을 분리해 끌어올린 시각이나 활동 시각이 게시 시각을 덮지 않는지 확인한다.
    const body = source.replace('export const searchBoard =', 'const originalSearchBoard =') + `
      export const searchBoard = async query => {
        const result = await originalSearchBoard(query);
        const examples = ${JSON.stringify(examples)};
        result.items = result.items.map((row, index) => index >= examples.length ? row : {
          ...row, createdAt: examples[index].createdAt, confirmedAt: examples[index].confirmedAt,
          bumpedAt: ${JSON.stringify(new Date(now).toISOString())},
          condition: { ...row.condition, voicePreference: examples[index].voicePreference },
          preferences: { ...row.preferences, ownTier: examples[index].ownTier },
        });
        return result;
      };
    `;
    await route.fulfill({ response, body });
  });
  await login(page);
  const rows = page.locator('.recruitment-row');
  await expect(rows).toHaveCount(10);
  const voiceDrawings: string[] = [];
  for (const [index, example] of examples.entries()) {
    const row = rows.nth(index);
    const heading = row.locator('.row-player-heading');
    const tier = heading.locator(':scope > .row-tier');
    await expect(tier).toHaveText(example.tier);
    await expect(tier.locator('svg.filter-tier-symbol')).toBeVisible();
    await expect(heading.locator(':scope > .row-tier + b')).toBeVisible();
    const tierBox = (await tier.boundingBox())!;
    const nicknameBox = (await heading.locator('b').boundingBox())!;
    expect(tierBox.x + tierBox.width, '티어는 닉네임 왼쪽에 배치한다').toBeLessThanOrEqual(nicknameBox.x);
    await expect(row).not.toContainText('직접 입력');
    const voice = row.getByRole('img', { name: example.voice, exact: true });
    await expect(voice).toHaveClass(/recruitment-voice/);
    await expect(voice).toHaveText('');
    await expect(voice.locator('svg')).toBeVisible();
    voiceDrawings.push(await voice.locator('svg').innerHTML());
    const posted = row.locator('time.row-posted');
    const active = row.locator('time.row-active');
    await expect(posted).toHaveText(example.posted);
    await expect(posted).toHaveAttribute('datetime', example.createdAt);
    await expect(active).toHaveText(example.active);
    await expect(active).toHaveAttribute('datetime', example.confirmedAt);
    await expect(row.locator('.row-fresh')).not.toContainText('활동 확인');
  }
  expect(new Set(voiceDrawings).size, '사용·미사용·무관은 서로 다른 아이콘이다').toBe(3);
});
