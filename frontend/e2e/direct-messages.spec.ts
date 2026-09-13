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

test('친구와 최근 팀원을 중복 없이 합치고 대화 검색과 읽음 상태를 반영한다', async ({ page }) => {
  await openMessages(page);
  await expect(page.locator('.dm-contact-top b').filter({ hasText: /^GankFlow$/ })).toHaveCount(1);
  await expect(page.locator('.dm-contact-top b').filter({ hasText: /^BlueOcean$/ })).toBeVisible();
  await page.getByRole('searchbox', { name: '대화 검색' }).fill('Gank');
  await expect(page.locator('.dm-contact')).toHaveCount(1);
  await expect(page.locator('.dm-contact-top b')).toHaveText('GankFlow');
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

test('친구 고정과 해제는 목록 순서에 반영되고 다시 로그인해도 유지된다', async ({ page }) => {
  await openMessages(page);
  await page.getByRole('button', { name: 'GankFlow 상단 고정', exact: true }).click();
  await expect(page.locator('.dm-contact-top b').first()).toHaveText('GankFlow');
  await expect(page.getByRole('button', { name: 'GankFlow 고정 해제', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await openMessages(page);
  await expect(page.locator('.dm-contact-top b').first()).toHaveText('GankFlow');
  await page.getByRole('button', { name: 'GankFlow 고정 해제', exact: true }).click();
  await expect(page.getByRole('button', { name: 'GankFlow 상단 고정', exact: true })).toHaveAttribute('aria-pressed', 'false');
});

test('대화별 초안을 유지하고 Enter 전송과 Shift+Enter 줄바꿈을 저장한다', async ({ page }) => {
  await openMessages(page);
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  const field = page.getByRole('textbox', { name: 'GankFlow에게 메시지', exact: true });
  await field.fill('  ');
  await expect(page.getByRole('button', { name: '메시지 보내기', exact: true })).toBeDisabled();
  await field.fill('아직 작성 중인 이야기');
  await page.locator('.dm-contact-select').filter({ has: page.locator('b', { hasText: /^BlueOcean$/ }) }).click();
  await expect(page.getByRole('textbox', { name: 'BlueOcean에게 메시지' })).toHaveValue('');
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  await expect(field).toHaveValue('아직 작성 중인 이야기');
  await field.fill('오늘도 같이 할까요?');
  await field.press('Shift+Enter');
  await field.pressSequentially('20:00');
  await expect(field).toHaveValue('오늘도 같이 할까요?\n20:00');
  await field.press('Enter');
  await expect(field).toHaveValue('');
  await expect(page.getByRole('log').locator('.dm-message.is-own p')).toHaveText('오늘도 같이 할까요?\n20:00');
  await openMessages(page);
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  await expect(page.getByRole('log').locator('.dm-message.is-own p')).toHaveText('오늘도 같이 할까요?\n20:00');
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
  await expect(page.getByRole('dialog')).toContainText('BlueOcean');
});

test('360px 모바일에서 목록과 대화를 오가고 메뉴와 새 대화 버튼이 겹치지 않는다', async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 780 });
  await openMessages(page);
  const menu = await page.getByRole('button', { name: '메뉴 열기', exact: true }).boundingBox();
  const compose = await page.locator('.dm-list-header').getByRole('button', { name: '새 대화', exact: true }).boundingBox();
  expect(compose!.x + compose!.width).toBeLessThanOrEqual(menu!.x);
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  await expect(page.locator('.dm-sidebar')).toBeHidden();
  const field = page.getByRole('textbox', { name: 'GankFlow에게 메시지' });
  await expect(field).toBeInViewport();
  await field.fill('모바일에서도 이어가요');
  await page.getByRole('button', { name: '메시지 보내기', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('모바일에서도 이어가요');
  await page.getByRole('button', { name: '대화 목록으로', exact: true }).click();
  await expect(page.locator('.dm-sidebar')).toBeVisible();
  await expect(page.getByRole('button', { name: 'GankFlow 대화', exact: true })).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('친구 요청 상대에게 바로 진입하고 받은 요청을 수락한다', async ({ page }) => {
  await openMessages(page);
  await page.locator('.dm-contact-select').filter({ has: page.locator('b', { hasText: /^HealingYou$/ }) }).click();
  await expect(page).toHaveURL(/user=u-healingyou/);
  await page.getByRole('button', { name: '요청 수락', exact: true }).click();
  await expect(page.locator('.dm-thread-person span')).toHaveText('친구');
  await expect(page.getByRole('button', { name: 'HealingYou 상단 고정', exact: true })).toBeVisible();
});

test('대화 목록이 길어도 태블릿과 데스크톱의 메시지 입력란은 화면 안에 남는다', async ({ page }, testInfo) => {
  await openMessages(page);
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  for (const width of [768, 1024, 1280]) {
    await page.setViewportSize({ width, height: 844 });
    await expect(page.getByRole('textbox', { name: 'GankFlow에게 메시지', exact: true }), `${width}px 입력란`).toBeInViewport();
    await expect(page.getByRole('button', { name: '메시지 보내기', exact: true }), `${width}px 전송 버튼`).toBeInViewport();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath(`messages-${width}.png`) });
  }
});

test('같은 브라우저에서 계정을 바꿔도 대화와 고정 상태가 섞이지 않는다', async ({ page }) => {
  await openMessages(page);
  await page.getByRole('button', { name: 'GankFlow 상단 고정', exact: true }).click();
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  await page.getByRole('textbox', { name: 'GankFlow에게 메시지', exact: true }).fill('첫 번째 계정에만 남길 이야기');
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
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  await expect(page.getByRole('log')).not.toContainText('첫 번째 계정에만 남길 이야기');
  await expect(page.getByRole('button', { name: 'GankFlow 상단 고정', exact: true })).toHaveAttribute('aria-pressed', 'false');
  await page.locator('.side-nav').getByRole('link', { name: 'SeparatePlayer 프로필', exact: true }).click();
  await page.getByRole('button', { name: '로그아웃', exact: true }).click();
  await page.getByRole('link', { name: '로그인', exact: true }).click();
  await page.getByPlaceholder('이메일 주소를 입력하세요').fill('demo@queuemate.gg');
  await page.getByPlaceholder('비밀번호를 입력하세요').fill('queuemate1');
  await page.locator('.auth-form button[type="submit"]').click();
  await page.locator('.side-nav').getByRole('link', { name: '메시지', exact: true }).click();
  await page.getByRole('button', { name: 'GankFlow 대화', exact: true }).click();
  await expect(page.getByRole('log')).toContainText('첫 번째 계정에만 남길 이야기');
  await expect(page.getByRole('button', { name: 'GankFlow 고정 해제', exact: true })).toHaveAttribute('aria-pressed', 'true');
});
