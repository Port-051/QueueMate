import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('실시간 매칭: 조건 설정 → 대기 → 제안 수락 → 파티룸', async ({ page }) => {
  await login(page);
  await startRealtimeMatch(page);

  await expect(page.getByRole('heading', { name: '지금 매칭', exact: true })).toBeVisible();
  await expect(page.getByText('대기 시간')).toBeVisible();

  // 제안 도착
  await expect(page).toHaveURL(/\/app\/proposals\//, { timeout: 30_000 });
  await expect(page.getByRole('heading', { name: '조건에 맞는 팀원을 찾았어요' })).toBeVisible();
  // 같은 팀 팀원 목록 하나만 있고, 상대팀 구획이나 VS 표기는 없다
  await expect(page.locator('.member-grid')).toHaveCount(1);
  await expect(page.getByText('VS', { exact: true })).toHaveCount(0);

  await page.getByRole('button', { name: /수락하고 파티룸 입장/ }).click();

  // 모두 수락해야 파티가 확정된다(INV-4)
  await expect(page).toHaveURL(/\/app\/party\//, { timeout: 30_000 });
  await expect(page.getByText('파티 채팅')).toBeVisible();
  await page.locator('.side-nav a[href="/app/home"]').click();
  const history = page.getByRole('region', { name: '지난 매칭', exact: true });
  await expect(history).toContainText('매칭 성사');
  await expect(history.locator('.match-history-row')).toHaveCount(1);
});

test('활성 매칭이 있으면 새 매칭을 시작할 수 없다 (INV-1)', async ({ page }) => {
  await login(page);
  await startRealtimeMatch(page);

  await page.getByRole('button', { name: 'League of Legends 매칭', exact: true }).click();
  await expect(page.getByText('이미 진행 중인 매칭이 있습니다')).toBeVisible();
  await expect(page.getByRole('button', { name: '매칭 시작' })).toBeDisabled();
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await expect(page.getByRole('button', { name: '예약 등록' })).toBeEnabled();
});

test('홈에서 매칭을 취소하면 현재 매칭 카드가 사라진다', async ({ page }) => {
  await login(page);
  await startRealtimeMatch(page);

  await page.getByRole('button', { name: '매칭 취소', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/home/);
  await expect(page.locator('.home-current')).toHaveCount(0);
  const history = page.getByRole('region', { name: '지난 매칭', exact: true });
  await expect(history).toContainText('취소됨');
  await expect(history).toContainText('League of Legends');
  await page.locator('.side-nav a[href="/app/recent"]').click();
  await page.locator('.side-nav a[href="/app/home"]').click();
  await expect(history.locator('.match-history-row')).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'League of Legends 매칭', exact: true })).toBeEnabled();
});

test('파티룸에서 채팅을 보내고 준비 상태를 바꾼다', async ({ page }) => {
  await login(page);
  await startRealtimeMatch(page);
  await expect(page).toHaveURL(/\/app\/proposals\//, { timeout: 30_000 });
  await page.getByRole('button', { name: /수락하고 파티룸 입장/ }).click();
  await expect(page).toHaveURL(/\/app\/party\//, { timeout: 30_000 });

  await page.getByPlaceholder('메시지를 입력하세요').fill('안녕하세요! 잘 부탁드립니다');
  await page.getByPlaceholder('메시지를 입력하세요').press('Enter');
  await expect(page.locator('.chat-line p').filter({ hasText: '안녕하세요! 잘 부탁드립니다' }).first()).toBeVisible();

  await page.getByRole('button', { name: '게임 준비 완료' }).click();
  await expect(page.getByRole('button', { name: '준비 해제' })).toBeVisible();
});
