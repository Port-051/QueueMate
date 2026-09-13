import { expect, test } from '@playwright/test';
import { login, startRealtimeMatch } from './helpers';

test('자동 매칭 상대의 소개를 읽어도 수락 타이머와 버튼이 유지된다', async ({ page }) => {
  await page.clock.install();
  await login(page); await startRealtimeMatch(page, true);
  const proposal = page.locator('.board-proposal');
  await expect(proposal).toBeVisible();
  const introduction = proposal.locator('.participant-introduction');
  await expect(introduction.locator('.row-introduction-stats')).toContainText('승률');
  await expect(introduction.locator('.row-introduction-stats')).toContainText('KDA');
  await introduction.locator('summary').click();
  await expect(introduction.locator('.recent-results > span')).toHaveCount(20);
  await expect(introduction).toContainText('티어·전적 직접 입력');
  const timer = proposal.getByRole('timer', { name: '수락 응답 남은 초' });
  const before = Number(await timer.innerText());
  await page.clock.fastForward(2000);
  await expect.poll(async () => Number(await timer.innerText())).toBeLessThan(before);
  await expect(timer).toBeInViewport();
  const accept = proposal.getByRole('button', { name: '함께할게요' });
  await expect(accept).toBeInViewport();
  await expect(accept).toBeEnabled();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await accept.click();
  await expect(page.locator('.compact-party')).toBeVisible();
});

test('공개 소개가 없는 제안 상대에게 전적을 만들지 않고 거절할 수 있다', async ({ page }) => {
  await login(page); await startRealtimeMatch(page, true);
  const proposal = page.locator('.board-proposal');
  await expect(proposal).toBeVisible();
  await page.evaluate(async () => {
    const dbPath = '/src/mocks/db.ts'; const busPath = '/src/mocks/bus.ts';
    const { db } = await import(/* @vite-ignore */ dbPath);
    const { emitMockEvent } = await import(/* @vite-ignore */ busPath);
    const pending = [...db.proposals.values()].find((item: any) => item.view.status === 'PENDING') as any;
    pending.view.members = pending.view.members.map((member: any) => member.userId === db.me.id ? member : { ...member, userId: 'unlisted-player', nickname: '소개 없는 팀원' });
    emitMockEvent('MATCH_PROPOSAL_CREATED', { proposal: structuredClone(pending.view) });
  });
  await expect(proposal).toContainText('소개 없는 팀원');
  await expect(proposal).toContainText('공개된 소개 정보가 없습니다.');
  await expect(proposal.locator('.row-introduction-stats')).toHaveCount(0);
  await expect(proposal.locator('.participant-introduction summary')).toHaveCount(0);
  await expect(proposal.getByRole('button', { name: '함께할게요' })).toBeEnabled();
  await proposal.getByRole('button', { name: '거절', exact: true }).click();
  await expect(proposal).toHaveCount(0);
  await expect(page.locator('.compact-party')).toHaveCount(0);
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
  await expect(page.locator('.my-recruitment')).toContainText('모집 중');
  await expect(page.locator('.compact-party')).toHaveCount(0);
});
