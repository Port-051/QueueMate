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
      ? ['플레이어 · 자기소개', '모드', '음성 · 활동 확인']
      : ['플레이어 · 자기소개', '모드', '주 포지션 → 찾는 상대', '음성 · 활동 확인']);
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
    const champion = rows.locator('.preferred-champion').filter({ has: page.getByText(name, { exact: true }) }).first();
    await expect(champion.getByText(name, { exact: true })).toBeVisible();
    await expectLoadedPortrait(champion.getByRole('img', { name: `${name} 초상화`, exact: true }));
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

test('알 수 없는 챔피언과 불러오지 못한 초상화도 이름을 유지하고 깨진 이미지를 남기지 않는다', async ({ page }) => {
  await saveExamples(page, [{ userId: 'u-gankflow', champions: ['처음 보는 챔피언', '리 신'], winRate: null, kda: null }]);
  await page.route(/\/[^/]*LeeSin[^/]*\.png(?:\?.*)?$/i, route => route.request().resourceType() === 'image' ? route.abort() : route.continue());
  await login(page);
  const row = page.getByRole('button', { name: 'GankFlow 모집 상세', exact: true });
  const dialog = page.getByRole('dialog', { name: '모집 상세', exact: true });
  for (const surface of [row, dialog]) {
    if (surface === dialog) await row.click();
    for (const name of ['처음 보는 챔피언', '리 신']) {
      const champion = surface.locator('.preferred-champion').filter({ has: page.getByText(name, { exact: true }) });
      await expect(champion.getByText(name, { exact: true })).toBeVisible();
      await expect(champion.locator('img')).toHaveCount(0);
    }
  }
});
