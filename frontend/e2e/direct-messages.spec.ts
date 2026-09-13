import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { login } from './helpers';

async function openMessages(page: Page) {
  await login(page);
  if (await page.getByRole('button', { name: '메뉴 열기', exact: true }).isVisible()) {
    await page.getByRole('button', { name: '메뉴 열기', exact: true }).click();
    await page.getByRole('dialog').getByRole('link', { name: '메시지', exact: true }).click();
  } else await page.locator('.side-nav').getByRole('link', { name: '메시지', exact: true }).click();
  await expect(page.locator('.dm-contact').first()).toBeVisible();
}

function contactRow(page: Page, nickname: string) {
  return page.locator('.dm-contact').filter({ has: page.locator('.dm-contact-top b').filter({ hasText: new RegExp(`^${nickname}$`) }) });
}

async function openConversationMenu(page: Page, nickname: string) {
  const row = contactRow(page, nickname);
  await row.hover();
  await row.getByRole('button', { name: `${nickname} 대화 메뉴`, exact: true }).click();
}

test('메시지를 나눈 상대만 대화 목록에 표시하고 검색과 읽음 상태를 반영한다', async ({ page }) => {
  await openMessages(page);
  await expect(page.locator('.dm-contact')).toHaveCount(3);
  await expect(page.locator('.dm-contact-top b')).toHaveText(['HealingYou', 'SilentJungle', 'BlueOcean']);
  await expect(page.locator('.dm-contact-top b').filter({ hasText: /^(GankFlow|LateGame)$/ })).toHaveCount(0);
  await page.getByRole('searchbox', { name: '대화 검색' }).fill('Blue');
  await expect(page.locator('.dm-contact')).toHaveCount(1);
  await expect(page.locator('.dm-contact-top b')).toHaveText('BlueOcean');
  await page.getByRole('searchbox', { name: '대화 검색' }).fill('없는 대화 이름');
  await expect(page.getByText('검색 결과가 없습니다', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: '검색 지우기', exact: true }).click();
  const unreadRows = page.locator('.dm-contact.is-unread');
  const before = await unreadRows.count();
  expect(before).toBeGreaterThan(0);
  await unreadRows.first().locator('.dm-contact-select').click();
  await expect(page.getByRole('log')).toBeVisible();
  await expect(unreadRows).toHaveCount(before - 1);
  await page.getByRole('button', { name: /읽지 않음/ }).click();
  await expect(page.locator('.dm-contact')).toHaveCount(before - 1);
});

test('추천 상대는 초안만으로 목록에 들어가지 않고 첫 메시지를 보내면 대화로 이동한다', async ({ page }) => {
  await openMessages(page);
  await page.getByRole('button', { name: '추천', exact: true }).click();
  await expect(contactRow(page, 'GankFlow')).toBeVisible();
  await expect(contactRow(page, 'LateGame')).toBeVisible();
  await expect(contactRow(page, 'BlueOcean')).toHaveCount(0);
  await contactRow(page, 'GankFlow').locator('.dm-contact-select').click();
  await expect(page.getByRole('button', { name: '추천', exact: true })).toHaveAttribute('aria-pressed', 'true');
  const field = page.getByRole('textbox', { name: 'GankFlow에게 메시지', exact: true });
  await field.fill('처음으로 이야기해요');
  await page.getByRole('button', { name: '대화', exact: true }).click();
  await expect(contactRow(page, 'GankFlow')).toHaveCount(0);
  await page.getByRole('button', { name: '추천', exact: true }).click();
  await field.press('Enter');
  await expect(page.getByRole('button', { name: '대화', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(contactRow(page, 'GankFlow')).toBeVisible();
  await expect(page.getByRole('log')).toContainText('처음으로 이야기해요');
  await page.getByRole('button', { name: '추천', exact: true }).click();
  await expect(contactRow(page, 'GankFlow')).toHaveCount(0);
});

test('행 메뉴에서 친구가 아닌 대화도 고정하고 고정 표시와 순서를 유지한다', async ({ page }) => {
  await openMessages(page);
  const row = contactRow(page, 'BlueOcean');
  const trigger = row.getByRole('button', { name: 'BlueOcean 대화 메뉴', exact: true });
  await expect(trigger).toHaveCSS('opacity', '0');
  await row.locator('.dm-contact-select').focus();
  await expect(trigger).toHaveCSS('opacity', '1');
  await openConversationMenu(page, 'BlueOcean');
  await expect(page.getByRole('menuitem')).toHaveCount(2);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('menu')).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await openConversationMenu(page, 'BlueOcean');
  await page.getByRole('menuitem', { name: 'BlueOcean 상단 고정', exact: true }).click();
  await expect(page.getByRole('menu')).toHaveCount(0);
  await expect(trigger).toBeFocused();
  await expect(page.locator('.dm-contact-top b').first()).toHaveText('BlueOcean');
  await expect(row.getByLabel('상단 고정', { exact: true })).toBeVisible();
  await openMessages(page);
  await expect(page.locator('.dm-contact-top b').first()).toHaveText('BlueOcean');
  await openConversationMenu(page, 'BlueOcean');
  await page.getByRole('menuitem', { name: 'BlueOcean 고정 해제', exact: true }).click();
  await expect(row.getByLabel('상단 고정', { exact: true })).toHaveCount(0);
  await expect(page.locator('.dm-contact-top b').first()).toHaveText('HealingYou');
});

test('대화 삭제는 확인 후 내용·초안·고정을 지우고 친구를 유지하며 다시 로그인해도 복원되지 않는다', async ({ page }) => {
  await openMessages(page);
  await contactRow(page, 'HealingYou').locator('.dm-contact-select').click();
  await page.getByRole('button', { name: '요청 수락', exact: true }).click();
  await expect(page.locator('.dm-thread-person span')).toHaveText('친구');
  await openConversationMenu(page, 'HealingYou');
  await page.getByRole('menuitem', { name: 'HealingYou 상단 고정', exact: true }).click();
  await page.getByRole('textbox', { name: 'HealingYou에게 메시지', exact: true }).fill('지워질 초안');
  await openConversationMenu(page, 'HealingYou');
  await page.getByRole('menuitem', { name: 'HealingYou 대화 삭제', exact: true }).click();
  const confirmation = page.getByRole('dialog', { name: 'HealingYou님과의 대화를 삭제할까요?', exact: true });
  await expect(confirmation).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(confirmation).toHaveCount(0);
  await expect(contactRow(page, 'HealingYou')).toBeVisible();
  await openConversationMenu(page, 'HealingYou');
  await page.getByRole('menuitem', { name: 'HealingYou 대화 삭제', exact: true }).click();
  await confirmation.getByRole('button', { name: '대화 삭제', exact: true }).click();
  await expect(contactRow(page, 'HealingYou')).toHaveCount(0);
  await page.getByRole('button', { name: '친구 관리', exact: true }).click();
  const management = page.getByRole('region', { name: '친구 관리', exact: true });
  await expect(management.locator('.dm-friend-row b').filter({ hasText: /^HealingYou$/ })).toBeVisible();
  await management.getByRole('button', { name: '친구 관리 닫기', exact: true }).click();
  await openMessages(page);
  await expect(contactRow(page, 'HealingYou')).toHaveCount(0);
  await page.getByRole('button', { name: '추천', exact: true }).click();
  await contactRow(page, 'HealingYou').locator('.dm-contact-select').click();
  await expect(page.getByRole('textbox', { name: 'HealingYou에게 메시지', exact: true })).toHaveValue('');
  await expect(page.locator('.dm-message-entry')).toHaveCount(0);
  await expect(contactRow(page, 'HealingYou').getByLabel('상단 고정', { exact: true })).toHaveCount(0);
});

test('대화별 초안을 유지하고 Enter 전송과 Shift+Enter 줄바꿈을 저장한다', async ({ page }) => {
  await openMessages(page);
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  const field = page.getByRole('textbox', { name: 'SilentJungle에게 메시지', exact: true });
  await field.fill('  ');
  await expect(page.getByRole('button', { name: '메시지 보내기', exact: true })).toBeDisabled();
  await field.fill('아직 작성 중인 이야기');
  await page.locator('.dm-contact-select').filter({ has: page.locator('b', { hasText: /^BlueOcean$/ }) }).click();
  await expect(page.getByRole('textbox', { name: 'BlueOcean에게 메시지' })).toHaveValue('');
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  await expect(field).toHaveValue('아직 작성 중인 이야기');
  await field.fill('오늘도 같이 할까요?');
  await field.press('Shift+Enter');
  await field.pressSequentially('20:00');
  await expect(field).toHaveValue('오늘도 같이 할까요?\n20:00');
  await field.press('Enter');
  await expect(field).toHaveValue('');
  await expect(page.getByRole('log').locator('.dm-message.is-own p').last()).toHaveText('오늘도 같이 할까요?\n20:00');
  await openMessages(page);
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  await expect(page.getByRole('log').locator('.dm-message.is-own p').last()).toHaveText('오늘도 같이 할까요?\n20:00');
});

test('새 대화에서 상대를 검색해 열고 친구 요청·신고·차단을 관리한다', async ({ page }) => {
  await openMessages(page);
  await page.locator('.dm-list-header').getByRole('button', { name: '새 대화', exact: true }).click();
  await page.getByRole('searchbox', { name: '대화 상대 검색' }).fill('BlueOcean');
  await page.getByRole('dialog').getByRole('button', { name: /BlueOcean/ }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page).toHaveURL(/user=u-blueocean/);
  await page.locator('.dm-thread-header').getByRole('button', { name: '친구 추가', exact: true }).click();
  await expect(page.locator('.dm-thread-header').getByRole('button', { name: '요청 취소', exact: true })).toBeVisible();
  const menu = page.locator('.dm-thread-header summary');
  await menu.click();
  await page.locator('.dm-thread-header').getByRole('button', { name: '신고', exact: true }).click();
  await expect(page.getByRole('dialog', { name: 'BlueOcean님 신고하기' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(menu).toBeFocused();
  await menu.click();
  await page.locator('.dm-thread-header').getByRole('button', { name: '차단', exact: true }).click();
  await page.getByRole('button', { name: '차단하기', exact: true }).click();
  await expect(page.locator('.dm-contact-top b').filter({ hasText: /^BlueOcean$/ })).toHaveCount(0);
  await page.getByRole('button', { name: '친구 관리', exact: true }).click();
  await page.getByRole('tab', { name: /차단 목록/ }).click();
  await expect(page.getByRole('region', { name: '친구 관리', exact: true })).toContainText('BlueOcean');
});

test('360px 모바일에서 목록과 대화를 오가고 메뉴와 새 대화 버튼이 겹치지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 780 });
  await openMessages(page);
  const menu = await page.getByRole('button', { name: '메뉴 열기', exact: true }).boundingBox();
  const compose = await page.locator('.dm-list-header').getByRole('button', { name: '새 대화', exact: true }).boundingBox();
  expect(compose!.x + compose!.width).toBeLessThanOrEqual(menu!.x);
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  await expect(page.locator('.dm-sidebar')).toBeHidden();
  const field = page.getByRole('textbox', { name: 'SilentJungle에게 메시지' });
  await expect(field).toBeInViewport();
  await field.fill('모바일에서도 이어가요');
  await page.getByRole('button', { name: '메시지 보내기', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('모바일에서도 이어가요');
  await page.getByRole('button', { name: '대화 목록으로', exact: true }).click();
  await expect(page.locator('.dm-sidebar')).toBeVisible();
  await expect(page.getByRole('button', { name: 'SilentJungle 대화', exact: true })).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('친구 요청 상대에게 바로 진입하고 받은 요청을 수락한다', async ({ page }) => {
  await openMessages(page);
  await page.locator('.dm-contact-select').filter({ has: page.locator('b', { hasText: /^HealingYou$/ }) }).click();
  await expect(page).toHaveURL(/user=u-healingyou/);
  await page.getByRole('button', { name: '요청 수락', exact: true }).click();
  await expect(page.locator('.dm-thread-person span')).toHaveText('친구');
  await openConversationMenu(page, 'HealingYou');
  await expect(page.getByRole('menuitem', { name: 'HealingYou 상단 고정', exact: true })).toBeVisible();
});

test('대화 목록이 길어도 태블릿과 데스크톱의 메시지 입력란은 화면 안에 남는다', async ({ page }, testInfo) => {
  await openMessages(page);
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  for (const width of [768, 1024, 1280]) {
    await page.setViewportSize({ width, height: 844 });
    await expect(page.getByRole('textbox', { name: 'SilentJungle에게 메시지', exact: true }), `${width}px 입력란`).toBeInViewport();
    await expect(page.getByRole('button', { name: '메시지 보내기', exact: true }), `${width}px 전송 버튼`).toBeInViewport();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`messages-${width}.png`) });
  }
});

test('같은 브라우저에서 계정을 바꿔도 대화와 고정 상태가 섞이지 않는다', async ({ page }) => {
  await openMessages(page);
  await openConversationMenu(page, 'SilentJungle');
  await page.getByRole('menuitem', { name: 'SilentJungle 상단 고정', exact: true }).click();
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  await page.getByRole('textbox', { name: 'SilentJungle에게 메시지', exact: true }).fill('첫 번째 계정에만 남길 이야기');
  await page.getByRole('button', { name: '메시지 보내기', exact: true }).click();
  await page.locator('.side-nav').getByRole('link', { name: 'QueueMaster 프로필', exact: true }).click();
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await page.getByRole('link', { name: '시작하기', exact: true }).click();
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill('separate-player@example.com');
  await page.getByPlaceholder('닉네임을 입력하세요').fill('SeparatePlayer');
  await page.getByPlaceholder('비밀번호를 입력하세요').fill('queuemate2');
  await page.locator('.auth-form button[type="submit"]').click();
  await expect(page).toHaveURL(/\/app\/home/);
  await page.locator('.side-nav').getByRole('link', { name: '메시지', exact: true }).click();
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  await expect(page.getByRole('log')).not.toContainText('첫 번째 계정에만 남길 이야기');
  await expect(contactRow(page, 'SilentJungle').getByLabel('상단 고정', { exact: true })).toHaveCount(0);
  await page.locator('.side-nav').getByRole('link', { name: 'SeparatePlayer 프로필', exact: true }).click();
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await page.getByRole('link', { name: '로그인', exact: true }).click();
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill('demo@queuemate.gg');
  await page.getByPlaceholder('비밀번호를 입력하세요').fill('queuemate1');
  await page.locator('.auth-form button[type="submit"]').click();
  await page.locator('.side-nav').getByRole('link', { name: '메시지', exact: true }).click();
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('첫 번째 계정에만 남길 이야기');
  await expect(contactRow(page, 'SilentJungle').getByLabel('상단 고정', { exact: true })).toBeVisible();
});

test('음성 미리보기는 요청 취소·시간 초과·대화 이동 후 재요청을 처리한다', async ({ page }) => {
  await openMessages(page);
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  await page.clock.install();
  const voice = page.getByRole('region', { name: '음성 대화', exact: true });
  await page.getByRole('button', { name: '통화 시작', exact: true }).click();
  await page.clock.fastForward(5000);
  await expect(voice.getByRole('status')).toHaveText('통화 요청 중 · 00:05');
  await voice.getByRole('button', { name: '요청 취소', exact: true }).click();
  await expect(voice).toHaveCount(0);
  await expect(page.getByRole('button', { name: '통화 시작', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: '통화 시작', exact: true }).click();
  await page.clock.fastForward(30000);
  await expect(voice.getByRole('status')).toHaveText('응답이 없습니다');
  await voice.getByRole('button', { name: '다시 통화하기', exact: true }).click();
  await contactRow(page, 'BlueOcean').locator('.dm-contact-select').click();
  await expect(voice).toHaveCount(0);
  await expect(page.getByRole('button', { name: '통화 시작', exact: true })).toBeEnabled();
  await expect(voice).toHaveCount(0);
});

test('메시지는 태블릿과 데스크톱에서 본문 전체 너비를 사용하고 중복 프로필을 표시하지 않는다', async ({ page }) => {
  await openMessages(page);
  for (const width of [768, 1280, 1920]) {
    await page.setViewportSize({ width, height: 900 });
    const main = (await page.locator('.main').boundingBox())!;
    const messages = (await page.locator('.direct-messages-page').boundingBox())!;
    expect(messages.x, `${width}px 왼쪽 여백`).toBeCloseTo(main.x, 0);
    expect(messages.x + messages.width, `${width}px 오른쪽 여백`).toBeCloseTo(main.x + main.width, 0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `${width}px 가로 넘침`).toBe(true);
  }
  await expect(page.locator('.dm-self')).toHaveCount(0);
});

test('연속 메시지는 작성자를 반복하지 않고 입력창 가까이에 쌓인다', async ({ page }) => {
  await openMessages(page);
  await page.getByRole('button', { name: 'SilentJungle 대화', exact: true }).click();
  const field = page.getByRole('textbox', { name: 'SilentJungle에게 메시지', exact: true });
  await field.fill('첫 번째 문장');
  await field.press('Enter');
  await field.fill('이어서 보낸 문장');
  await field.press('Enter');
  await expect(page.locator('.dm-message-entry.is-grouped p')).toHaveText('이어서 보낸 문장');
  const last = (await page.locator('.dm-message-entry').last().boundingBox())!;
  const compose = (await page.locator('.dm-compose').boundingBox())!;
  expect(compose.y - last.y - last.height).toBeLessThan(50);
});
