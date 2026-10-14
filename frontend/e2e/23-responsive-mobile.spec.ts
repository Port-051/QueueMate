import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { aramPost, createPost, enter, myRoom, uniqueTitle } from './support/domain';

/** 가로로 넘치지 않는가(페이지 자체 — 필터 줄 같은 안쪽 스크롤은 된다). */
const noHorizontalOverflow = (page: Page) => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth);
/** 보이는 화면 안에 있는가. */
async function inViewport(page: Page, locator: ReturnType<Page['locator']>): Promise<boolean> {
  const box = await locator.boundingBox();
  const size = page.viewportSize()!;
  return Boolean(box && box.x >= 0 && box.y >= 0 && box.x + box.width <= size.width && box.y + box.height <= size.height);
}

/**
 * 시나리오 23 — 폰 폭(360px)의 방 패널 · "게시판으로"(방에서 나가지 않는다) · "방으로 돌아가기" · 통화 버튼이 닿는가 · 가로 넘침 없음 · 1440px 은 게시판 옆 패널.
 * 근거 — `frontend/CLAUDE.md` §3-23(1100px 아래는 패널이 덮고 "게시판으로" 는 패널만 숨긴다 · 게시판의 "방으로 돌아가기" 가 다시 연다) · `src/pages/HomePage.tsx` · `src/rooms/room-panel.css`(`@media (max-width:1099.98px)`) ·
 * `src/components/AppShell.tsx`(767px 아래는 사이드바 대신 "메뉴 열기") · mentor-room-ux "방 패널 너비 조절"(모바일은 손잡이 없음).
 */
test('시나리오 23 — 360px 방 패널이 게시판을 덮고 "게시판으로" 는 방에서 나가지 않는다 · 버튼이 닿는다 · 가로 넘침 없음', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  const post = await createPost(a, aramPost(uniqueTitle('s23')));
  const roomId = String(post.postId);
  expect((await enter(b, roomId)).status).toBe(201);

  const page = await b.context.newPage();
  await page.setViewportSize({ width: 360, height: 740 });
  await page.goto(`/app/party/${roomId}`);
  const panel = page.getByRole('region', { name: '방', exact: true });
  const board = page.locator('.board-split-board');

  await test.step('방 패널이 화면을 덮는다 — 게시판은 읽기 · 초점에서 빠지고(aria-hidden) "게시판으로" · "방에서 나가지 않아요" 가 보인다 · 너비 손잡이는 없다', async () => {
    await expect(panel.getByRole('heading', { level: 1, name: post.title })).toBeVisible();
    await expect(board).toHaveAttribute('aria-hidden', 'true');
    await expect(page.getByRole('button', { name: '게시판으로' })).toBeVisible();
    await expect(panel.getByText('방에서 나가지 않아요')).toBeVisible();
    await expect(page.getByRole('separator', { name: '방 패널 너비 조절' })).toHaveCount(0);
    expect(await noHorizontalOverflow(page)).toBe(true);
  });

  await test.step('마이크 · 방 나가기 버튼이 화면 안에 있고 누를 수 있다(나가기는 창만 열고 돌아간다) · 좌석 줄 2 / 5', async () => {
    const mic = panel.getByRole('button', { name: '음소거 해제' });
    const leave = panel.getByRole('button', { name: '방 나가기' });
    await expect(mic).toBeVisible();
    await expect(leave).toBeVisible();
    expect(await inViewport(page, mic)).toBe(true);
    expect(await inViewport(page, leave)).toBe(true);
    await expect(panel.getByRole('list', { name: '파티원 2 / 5' })).toBeVisible();
    await leave.click();
    const confirm = page.getByRole('dialog', { name: '방에서 나갈까요?' });
    await expect(confirm).toBeVisible();
    await confirm.getByRole('button', { name: '취소' }).click();
    await expect(confirm).toBeHidden();
    expect(await myRoom(b)).toBe(roomId);
  });

  await test.step('"게시판으로" → /app/home · 패널이 사라진다 · 여전히 방 안(GET /rooms/me) · 떠 있는 "방으로 돌아가기" 줄 · 내 방 카드는 "참가 중"', async () => {
    await page.getByRole('button', { name: '게시판으로' }).click();
    await page.waitForURL(/\/app\/home$/);
    await expect(panel).toHaveCount(0);
    await expect(board).not.toHaveAttribute('aria-hidden', 'true');
    expect(await myRoom(b)).toBe(roomId);
    const banner = page.locator('.room-return-banner');
    await expect(banner).toContainText('지금 방에 들어가 있어요');
    const card = page.getByRole('article', { name: `${post.title} 방 정보`, exact: true });
    await expect(card.locator('.room-join-button')).toHaveText('참가 중');
    await expect(card.locator('.room-join-button')).toBeDisabled();
    expect(await noHorizontalOverflow(page)).toBe(true);
    // 폰 폭의 게시판 — 사이드바 대신 "메뉴 열기" · 누르면 메뉴 창(게임 선택 · 주 메뉴) · 닫기.
    await page.getByRole('button', { name: '메뉴 열기' }).click();
    const menu = page.getByRole('dialog', { name: '메뉴' });
    await expect(menu.getByRole('navigation', { name: '주 메뉴' })).toBeVisible();
    await expect(menu.getByRole('group', { name: '게임 선택' })).toBeVisible();
    await menu.getByRole('button', { name: '메뉴 닫기' }).click();
    await expect(menu).toBeHidden();
    await banner.getByRole('link', { name: '방으로 돌아가기' }).click();
    await page.waitForURL(new RegExp(`/app/party/${roomId}$`));
    await expect(panel.getByRole('heading', { level: 1, name: post.title })).toBeVisible();
  });

  await test.step('다른 화면(친구 · 내 정보)에서도 방 안내 띠 · 가로 넘침 없음', async () => {
    await page.goto('/app/friends');
    await expect(page.getByRole('status').filter({ hasText: '방에 들어가 있어요' }).getByRole('link', { name: '방으로 돌아가기' })).toBeVisible();
    expect(await noHorizontalOverflow(page)).toBe(true);
    await page.goto('/app/me');
    expect(await noHorizontalOverflow(page)).toBe(true);
    await page.goto('/login');
    // 로그인된 사람은 앱으로 — 비로그인 로그인 화면의 폭은 시나리오 24 가 1440 에서 본다.
    await page.waitForURL(/\/(app\/home|onboarding)$/);
  });

  await test.step('1440px — 게시판과 방 패널이 나란히 · 게시판은 읽을 수 있다(aria-hidden 없음) · "게시판으로" 는 없다', async () => {
    const wide = await crew.appPage('a', `/app/party/${roomId}`);
    await expect(wide.getByRole('region', { name: '방', exact: true })).toBeVisible();
    await expect(wide.getByRole('region', { name: '방 목록', exact: true })).toBeVisible();
    await expect(wide.locator('.board-split-board')).not.toHaveAttribute('aria-hidden', 'true');
    await expect(wide.getByRole('button', { name: '게시판으로' })).toBeHidden();
    await expect(wide.getByRole('separator', { name: '방 패널 너비 조절' })).toBeVisible();
    expect(await noHorizontalOverflow(wide)).toBe(true);
  });
});
