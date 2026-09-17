import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('프로필 편집을 취소하면 변경되지 않고, 사진을 저장하면 사이드바에도 반영된다', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav').getByRole('link', { name: '프로필', exact: true }).click();
  const rename = page.getByRole('button', { name: '닉네임 변경', exact: true });
  await rename.click();
  await page.getByRole('textbox', { name: '닉네임', exact: true }).fill('저장하지않을이름');
  await page.keyboard.press('Escape');
  await expect(rename).toBeFocused();
  await expect(page.getByRole('heading', { name: 'QueueMaster', exact: true })).toBeVisible();
  await rename.click();
  await expect(page.getByRole('textbox', { name: '닉네임', exact: true })).toHaveValue('QueueMaster');
  await expect(page.getByRole('button', { name: '변경 사항 저장', exact: true })).toBeDisabled();
  await page.keyboard.press('Escape');

  const photo = page.getByRole('button', { name: '프로필 사진 변경', exact: true });
  const before = await photo.locator('img').getAttribute('src');
  await photo.click();
  await page.getByRole('button', { name: '아바타 2', exact: true }).click();
  await page.getByRole('button', { name: '취소', exact: true }).click();
  await expect(photo.locator('img')).toHaveAttribute('src', before!);
  await photo.click();
  await page.getByRole('button', { name: '아바타 2', exact: true }).click();
  await page.getByRole('button', { name: '저장', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(photo.locator('img')).toHaveAttribute('src', '/avatars/avatar-02.webp');
  await expect(page.locator('.sidebar .nav-profile img')).toHaveAttribute('src', '/avatars/avatar-02.webp');
});

test('게임 ID 등록 취소는 원래 게임 버튼으로 돌아가고 등록 후 연결 해제할 수 있다', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav').getByRole('link', { name: '프로필', exact: true }).click();
  const register = page.getByRole('button', { name: '배틀그라운드 ID 등록', exact: true });
  await register.click();
  await expect(page.getByRole('button', { name: 'ID 등록', exact: true })).toBeDisabled();
  await page.getByRole('textbox', { name: '게임 ID', exact: true }).fill('DiscardedID');
  await page.keyboard.press('Escape');
  await expect(register).toBeFocused();
  await register.click();
  await expect(page.getByRole('textbox', { name: '게임 ID', exact: true })).toHaveValue('');
  await page.getByRole('textbox', { name: '게임 ID', exact: true }).fill('QueuePlayer');
  await page.getByRole('button', { name: 'ID 등록', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  const account = page.locator('.account-row').filter({ hasText: '배틀그라운드' });
  await expect(account).toContainText('QueuePlayer');
  await account.getByRole('button', { name: /연결 해제/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: '연결 해제', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(account).not.toContainText('QueuePlayer');
  await expect(register).toBeVisible();
});

test('업로드한 프로필 사진과 연동한 솔로·자유 랭크를 표시한다', async ({ page }) => {
  await login(page);
  await page.locator('.side-nav').getByRole('link', { name: '프로필', exact: true }).click();
  const record = page.getByRole('region', { name: '롤 전적 정보' });
  await expect(record).toContainText('골드 2');
  await expect(record).toContainText('플래티넘 4');
  await page.getByRole('button', { name: '프로필 사진 변경' }).click();
  await page.locator('input[type="file"]').setInputFiles('public/avatars/avatar-02.webp');
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.profile-photo img')).toHaveAttribute('src', /^data:image\/png/);
});
