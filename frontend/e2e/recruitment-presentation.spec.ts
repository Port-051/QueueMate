import { expect, test, type Locator, type Page } from '@playwright/test';
import { login } from './helpers';

async function expectLoadedPortrait(portrait: Locator) {
  await expect(portrait).toBeVisible();
  await expect.poll(() => portrait.evaluate(image => image instanceof HTMLImageElement && image.complete && image.naturalWidth > 0)).toBe(true);
}

async function expectMetaDividers(row: Locator, selectors: string[], compact = false) {
  for (const selector of selectors) {
    const divider = await row.locator(selector).evaluate(cell => {
      const style = getComputedStyle(cell, '::before');
      const cellStyle = getComputedStyle(cell);
      return { display: style.display, content: style.content, width: style.width, height: style.height, background: style.backgroundColor, opacity: style.opacity, left: style.left, paddingLeft: cellStyle.paddingLeft, paddingRight: cellStyle.paddingRight };
    });
    expect(divider.display).not.toBe('none');
    expect(divider.content).not.toBe('none');
    expect(divider.background).not.toBe('rgba(0, 0, 0, 0)');
    expect(parseFloat(divider.opacity)).toBeGreaterThan(0);
    expect(divider.width).toBe('1px');
    expect(divider.height).toBe('16px');
    expect(divider.left).toBe(compact ? '-14px' : '0px');
    expect(divider.paddingLeft).toBe(compact ? '0px' : '14px');
    if (!compact) expect(divider.paddingRight).toBe(selector === '.row-fresh' ? '0px' : '14px');
  }
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

  await page.getByRole('button', { name: '찾는 상대 티어', exact: true }).click();
  const tiers = page.getByRole('listbox', { name: '찾는 상대 티어', exact: true });
  await expect(tiers.locator('.rank-emblem img')).toHaveCount(10);
  for (const name of ['아이언', '브론즈', '실버', '골드', '플래티넘', '에메랄드', '다이아몬드', '마스터', '그랜드마스터', '챌린저']) {
    const option = tiers.getByRole('option', { name, exact: true });
    await option.scrollIntoViewIfNeeded();
    await expectLoadedPortrait(option.locator('.rank-emblem img'));
  }
  await page.keyboard.press('Escape');
  await expect(tiers).toHaveCount(0);

  for (const mode of ['랭크', '일반', '신속', '칼바람']) {
    const filter = modes.getByRole('button', { name: mode, exact: true });
    await filter.click();
    await expect(page.locator('.board-results-head')).toContainText('10개 모집');
    await expect(rows.locator(':scope > .row-mode .recruitment-mode')).toHaveText(Array(10).fill(mode));
    await expect(page.locator('.recruitment-list-head')).toHaveCount(0);
    await expect(rows.locator(':scope > .row-rank > .row-tier')).toHaveCount(10);
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
    await expect(champion.locator('.preferred-champion-portrait')).toHaveCSS('border-radius', '50%');
    await champion.hover();
    await expect(page.getByRole('tooltip')).toBeVisible({ timeout: 500 });
    await expect(page.getByRole('tooltip')).toHaveText(name);
    if (name === '리 신') await page.screenshot({ path: testInfo.outputPath('recruitment-champion-tooltip.png') });
    await page.mouse.move(0, 0);
    await expect(page.getByRole('tooltip')).toHaveCount(0);
  }

  for (const width of [1600, 1100, 900, 360]) {
    await page.setViewportSize({ width, height: 900 });
    await rows.first().scrollIntoViewIfNeeded();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `모집 목록 ${width}px 가로 넘침`).toBe(true);
    await expect(rows.first().locator('.row-rank')).toBeVisible();
    await expect(rows.first().locator('.row-mode')).toBeVisible();
    await expect(rows.first().locator('.row-roles')).toBeVisible();
    await expect(rows.first().locator('.row-voice')).toBeVisible();
    await expect(rows.first().locator('.row-fresh')).toBeVisible();
    const player = (await rows.first().locator('.row-player').boundingBox())!;
    const rank = (await rows.first().locator('.row-rank').boundingBox())!;
    const mode = (await rows.first().locator('.row-mode').boundingBox())!;
    const role = (await rows.first().locator('.row-roles').boundingBox())!;
    const voice = (await rows.first().locator('.row-voice').boundingBox())!;
    const posted = (await rows.first().locator('.row-fresh').boundingBox())!;
    expect(rank.x + rank.width, `랭크와 모드는 ${width}px에서 별도 열이다`).toBeLessThanOrEqual(mode.x);
    const compact = await page.locator('.recruitment-list').evaluate(element => element.clientWidth <= 680);
    if (compact) {
      await expectMetaDividers(rows.first(), ['.row-mode', '.row-voice'], true);
      expect(rank.y, '모바일 랭크·모드는 플레이어 아래에 표시한다').toBeGreaterThanOrEqual(player.y + player.height);
      expect(Math.abs(rank.y + rank.height / 2 - mode.y - mode.height / 2), '모바일 랭크와 모드는 같은 줄이다').toBeLessThanOrEqual(1);
      expect(role.y, '모바일 포지션·음성은 랭크·모드 아래에 표시한다').toBeGreaterThanOrEqual(Math.max(rank.y + rank.height, mode.y + mode.height));
      expect(role.x + role.width, '모바일 포지션과 음성은 별도 열이다').toBeLessThanOrEqual(voice.x);
      expect(Math.abs(role.y + role.height / 2 - voice.y - voice.height / 2), '모바일 포지션과 음성은 같은 줄이다').toBeLessThanOrEqual(1);
      expect(posted.y, '모바일 게시 시간은 마지막 줄이다').toBeGreaterThanOrEqual(voice.y + voice.height);
      expect(player.x + player.width - Math.min(rank.x, mode.x, role.x, voice.x, posted.x), '모바일 부가 정보는 오른쪽의 좁은 영역에 모은다').toBeLessThanOrEqual(208);
    } else {
      await expectMetaDividers(rows.first(), ['.row-mode', '.row-roles', '.row-voice', '.row-fresh']);
      expect(player.x + player.width, '랭크는 플레이어 오른쪽에 별도 열로 표시한다').toBeLessThanOrEqual(rank.x);
      expect(mode.x + mode.width, '모드와 포지션은 별도 열이다').toBeLessThanOrEqual(role.x);
      expect(voice.x + voice.width, '음성과 게시 시간은 별도 열이다').toBeLessThanOrEqual(posted.x);
    }
    await expectLoadedPortrait(rows.first().getByRole('img', { name: '리 신 초상화', exact: true }));
    await page.screenshot({ path: testInfo.outputPath(`recruitment-presentation-${width}.png`) });
    await rows.first().click();
    expect(await dialog.evaluate(element => element.scrollWidth <= element.clientWidth), `모집 상세 ${width}px 가로 넘침`).toBe(true);
    await expectLoadedPortrait(dialog.getByRole('img', { name: '리 신 초상화', exact: true }));
    await page.keyboard.press('Escape');
  }

  await modes.getByRole('button', { name: '칼바람', exact: true }).click();
  await expect(rows.locator(':scope > .row-roles')).toHaveCount(0);
  await rows.first().scrollIntoViewIfNeeded();
  const aramMode = (await rows.first().locator('.row-mode').boundingBox())!;
  const aramVoice = (await rows.first().locator('.row-voice').boundingBox())!;
  const aramPosted = (await rows.first().locator('.row-fresh').boundingBox())!;
  expect(aramVoice.y, '모바일 칼바람 음성·게시 시간은 랭크·모드 아래에 표시한다').toBeGreaterThanOrEqual(aramMode.y + aramMode.height);
  expect(Math.abs(aramVoice.y + aramVoice.height / 2 - aramPosted.y - aramPosted.height / 2), '모바일 칼바람 음성과 게시 시간은 같은 줄이다').toBeLessThanOrEqual(1);
  expect(aramVoice.x + aramVoice.width, '모바일 칼바람 음성과 게시 시간은 별도 열이다').toBeLessThanOrEqual(aramPosted.x);
  await expectMetaDividers(rows.first(), ['.row-fresh'], true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '모바일 칼바람 가로 넘침').toBe(true);
  await page.screenshot({ path: testInfo.outputPath('recruitment-presentation-aram-360.png') });
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

test('모집은 닉네임 뒤에 챔피언을 한 번 표시하고 랭크는 별도 열에 표시한다', async ({ page }) => {
  const now = Date.parse('2026-09-14T12:00:00Z');
  const ago = (offset: number) => new Date(now - offset).toISOString();
  const examples = [
    { ownTier: 'GOLD', tier: '골드', voicePreference: 'REQUIRED', voice: '음성 사용', createdAt: ago(90_000), posted: '1분 전', confirmedAt: ago(30_000) },
    { ownTier: 'SILVER', tier: '실버', voicePreference: 'NO_VOICE', voice: '음성 사용 안 함', createdAt: ago(2 * 3_600_000 + 30_000), posted: '2시간 전', confirmedAt: ago(90_000) },
    { ownTier: null, tier: '티어 미입력', voicePreference: 'OPTIONAL', voice: '음성 무관', createdAt: ago(3 * 86_400_000 + 30_000), posted: '3일 전', confirmedAt: ago(2 * 3_600_000 + 30_000) },
    { ownTier: 'DIAMOND', tier: '다이아몬드', modeKey: 'ARAM', voicePreference: 'REQUIRED', voice: '음성 사용', createdAt: ago(30_000), posted: '방금', confirmedAt: ago(10_000) },
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
          condition: { ...row.condition, modeKey: examples[index].modeKey ?? row.condition.modeKey, voicePreference: examples[index].voicePreference },
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
  const referenceColumns = await rows.first().locator(':scope > .row-meta').evaluateAll(cells => cells.map(cell => {
    const rect = cell.getBoundingClientRect();
    return { x: rect.x, width: rect.width };
  }));
  const voiceDrawings: string[] = [];
  for (const [index, example] of examples.entries()) {
    const row = rows.nth(index);
    const heading = row.locator('.row-player-heading');
    const stats = row.locator('.row-introduction-stats');
    const rank = row.locator(':scope > .row-rank');
    const tier = rank.locator(':scope > .row-tier');
    await expect(tier).toContainText(example.tier);
    if (example.ownTier) await expectLoadedPortrait(tier.locator('img'));
    await expect(heading.locator(':scope > b + .preferred-champions')).toBeVisible();
    await expect(row.locator('.preferred-champions')).toHaveCount(1);
    await expect(stats.locator('.preferred-champions')).toHaveCount(0);
    await expect(row.locator('.row-player .row-tier')).toHaveCount(0);
    await expect(row.locator(':scope > .row-player + .row-rank + .row-mode')).toBeVisible();
    const playerBox = (await row.locator('.row-player').boundingBox())!;
    const rankBox = (await rank.boundingBox())!;
    const modeBox = (await row.locator('.row-mode').boundingBox())!;
    expect(playerBox.x + playerBox.width, '랭크는 플레이어 오른쪽에 별도 열로 표시한다').toBeLessThanOrEqual(rankBox.x);
    expect(rankBox.x + rankBox.width, '랭크 다음 열에 모드를 표시한다').toBeLessThanOrEqual(modeBox.x);
    const hasRoles = example.modeKey !== 'ARAM';
    await expect(row.locator('.row-roles')).toHaveCount(1);
    if (!hasRoles) {
      const placeholder = row.getByRole('img', { name: '포지션 지정 없음', exact: true });
      await expect(placeholder).toHaveText('—');
      await expect(placeholder).toBeVisible();
    }
    const columns = await row.locator(':scope > .row-meta').evaluateAll(cells => cells.map(cell => {
      const rect = cell.getBoundingClientRect();
      return { x: rect.x, width: rect.width };
    }));
    expect(columns).toHaveLength(referenceColumns.length);
    for (const [columnIndex, column] of columns.entries()) {
      expect(Math.abs(column.x - referenceColumns[columnIndex].x), `${index + 1}번 행 ${columnIndex + 1}번 열의 시작 경계`).toBeLessThanOrEqual(1);
      expect(Math.abs(column.width - referenceColumns[columnIndex].width), `${index + 1}번 행 ${columnIndex + 1}번 열의 너비`).toBeLessThanOrEqual(1);
    }
    await expectMetaDividers(row, ['.row-mode', '.row-roles', '.row-voice', '.row-fresh']);
    await expect(row).not.toContainText('직접 입력');
    const voice = row.locator(':scope > .row-voice').getByRole('img', { name: example.voice, exact: true });
    await expect(voice).toHaveClass(/recruitment-voice/);
    await expect(voice).toHaveText('');
    await expect(voice.locator('svg')).toBeVisible();
    voiceDrawings.push(await voice.locator('svg').innerHTML());
    const posted = row.locator(':scope > .row-fresh > time.row-posted');
    await expect(posted).toHaveText(example.posted);
    await expect(posted).toHaveAttribute('datetime', example.createdAt);
    await expect(row.locator('.row-active')).toHaveCount(0);
    await expect(row.locator('.row-fresh')).not.toContainText('게시');
    await expect(row.locator('.row-fresh')).not.toContainText('활동');
  }
  expect(new Set(voiceDrawings).size, '사용·미사용·무관은 서로 다른 아이콘이다').toBe(3);
  await page.setViewportSize({ width: 360, height: 900 });
  const aram = rows.nth(3);
  await aram.scrollIntoViewIfNeeded();
  await expect(aram.locator('.row-roles')).toBeHidden();
  await expect(aram.locator('.row-voice')).toBeVisible();
  await expect(aram.locator('.row-fresh')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '혼합 목록의 모바일 칼바람 가로 넘침').toBe(true);
});
