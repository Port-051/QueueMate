import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('자동 추천의 소개를 읽는 동안 매칭 타이머와 수락 버튼을 유지한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.clock.fastForward(7000);
  const proposal = page.locator('.duo-offer');
  const introduction = proposal.locator('.duo-offer-copy');
  await expect(introduction.locator('.row-introduction-stats')).toContainText('승률');
  await expect(introduction.locator('.row-introduction-stats')).toContainText('KDA');
  await expect(proposal.locator('.match-condition-summary')).toBeVisible();
  const timer = page.getByRole('timer', { name: '매칭 시작 후', exact: true });
  const before = await timer.innerText();
  await page.clock.fastForward(2000); await expect(timer).not.toHaveText(before);
  const accept = proposal.getByRole('button', { name: '같이 할래요' });
  await expect(accept).toBeEnabled(); await accept.click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('추천을 건너뛰어도 매칭을 유지하고 다음 상대를 보여준다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.clock.fastForward(7000);
  const proposal = page.locator('.duo-offer');
  const first = await proposal.getAttribute('aria-label');
  await proposal.getByRole('button', { name: '다음에', exact: true }).click();
  await expect(proposal).toHaveCount(0);
  await page.clock.fastForward(7000);
  await expect(proposal).toHaveCount(1); await expect(proposal).not.toHaveAttribute('aria-label', first!);
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toHaveCount(0);
  await expect(page.locator('.my-recruitment')).toContainText('매칭 중');
});

test('방장은 수락 전에 신청자의 공개 조건과 자기소개를 확인하고 거절할 수 있다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page);
  await page.evaluate(async () => {
    const dbPath = '/src/mocks/db.ts'; const apiPath = '/src/api/recruitment.ts';
    const { db } = await import(/* @vite-ignore */ dbPath);
    const api = await import(/* @vite-ignore */ apiPath);
    const host = (await api.myRecruitments())[0];
    const me = db.me;
    const hostRequest = db.matchRequests.get(host.id);
    try {
      // 단일 로그인 데모에 두 번째 사용자의 원본만 구성하고 방장 요청은 즉시 복원한다.
      db.matchRequests.delete(host.id);
      db.me = { id: 'applicant-without-record', nickname: '새 신청자', avatarUrl: null };
      const source = await api.createRecruitment({
        type: 'REALTIME', condition: { ...host.condition, keyCondition: { type: 'POSITION', value: 'JUNGLE' } },
        preferences: { ...host.preferences, ownTier: 'GOLD' }, description: '차분하게 함께해요', autoMatch: false,
        availableFrom: null, availableTo: null, playAmount: null,
      });
      if (hostRequest) db.matchRequests.set(host.id, hostRequest);
      await api.joinRecruitment(host.id, source.id);
    } finally {
      if (hostRequest) db.matchRequests.set(host.id, hostRequest);
      db.me = me;
    }
  });
  const applicant = page.locator('.recruitment-applicant');
  await expect(applicant).toContainText('새 신청자');
  await expect(applicant.locator('.participant-facts')).toContainText('정글');
  await expect(applicant.locator('.participant-facts')).toContainText('골드');
  await expect(applicant.locator('.row-introduction-stats')).toHaveCount(0);
  await applicant.getByLabel('새 신청자 소개 보기').click();
  await expect(applicant).toContainText('차분하게 함께해요');
  await expect(applicant.locator('.recent-results > span')).toHaveCount(20);
  await expect(applicant.locator('.recent-results .unknown')).toHaveCount(20);
  await expect(applicant.getByRole('button', { name: '거절', exact: true })).toBeEnabled();
  await expect(applicant.getByRole('button', { name: '함께하기', exact: true })).toBeEnabled();
  await applicant.getByRole('button', { name: '거절', exact: true }).click();
  await expect(applicant).toHaveCount(0);
  await expect(page.locator('.my-recruitment')).toContainText('매칭 중');
  await expect(page.locator('.compact-party')).toHaveCount(0);
});
