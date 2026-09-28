import { expect, test, type Page } from '@playwright/test';
import { login, startRealtimeMatch, manageRecruitment } from './helpers';

async function snapshot(page: Page) {
  return page.evaluate(async () => {
    const apiPath = '/src/api/recruitment.ts', authPath = '/src/api/client.ts';
    const offersPath = '/src/state/duoOffers.ts';
    const owner = (await (await import(authPath)).getMe()).id;
    return { owner, mine: await (await import(apiPath)).myRecruitments(),
      offers: (await import(offersPath)).readDuoOffers(owner),
      messages: JSON.parse(localStorage.getItem(`qm:direct-messages:${owner}`) ?? '{"conversations":{}}') };
  });
}
async function okay(page: Page, id: string) {
  await page.evaluate(async id => {
    // HMR 쿼리를 포함해 앱과 같은 mock 인스턴스에 상대 응답을 전달한다.
    const moduleSource = await (await fetch('/src/mocks/server.ts')).text();
    const path = moduleSource.match(/from "(\/src\/mocks\/recruitment[^"]*)"/)![1], auth = '/src/api/client.ts';
    (await import(path)).receiveDuoOkay((await (await import(auth)).getMe()).id, id);
  }, id);
}
async function send(page: Page, nickname: string) {
  await page.getByRole('button', { name: `${nickname} 매칭 글 상세`, exact: true }).click();
  await page.getByRole('button', { name: '같이 할래요', exact: true }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toContainText(nickname);
}

test('여러 오케이를 보내도 매칭을 유지하고 먼저 서로 수락한 한 명과만 메시지로 연결한다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  const before = (await snapshot(page)).mine[0];
  await send(page, 'PlayMaker'); await send(page, 'GankFlow');
  const sent = await snapshot(page);
  expect(sent.offers.map((o: any) => o.status)).toEqual(['SENT', 'SENT']);
  expect(sent.mine[0]).toMatchObject({ status: 'OPEN', createdAt: before.createdAt, proposalId: null });
  expect(sent.mine[0].members).toHaveLength(1);
  expect(Object.values(sent.messages.conversations).flatMap((c: any) => c.messages).filter((m: any) => m.kind === 'MATCH')).toHaveLength(0);
  await expect(page.getByRole('timer', { name: '매칭 시작 후', exact: true })).toBeVisible();
  await expect(page.locator('.board-proposal')).toHaveCount(0);
  await okay(page, sent.offers[1].id); await okay(page, sent.offers[0].id); await okay(page, sent.offers[1].id);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toContainText('GankFlow');
  await expect(page).toHaveURL(/\/app\/home$/);
  const matched = await snapshot(page);
  expect(matched.offers.map((o: any) => o.status)).toEqual(['CANCELLED', 'MATCHED']);
  expect(matched.mine[0].status).toBe('MATCHED');
  expect(Object.values(matched.messages.conversations).flatMap((c: any) => c.messages).filter((m: any) => m.kind === 'MATCH')).toHaveLength(1);
  await page.locator('.sidebar').getByRole('button', { name: '알림', exact: true }).click();
  const notifications = page.getByRole('dialog', { name: '알림', exact: true });
  await expect(notifications.getByText('GankFlow님도 오케이했어요', { exact: true })).toHaveCount(1);
  await notifications.getByRole('button', { name: /GankFlow님도 오케이했어요/ }).click();
  await expect(page).toHaveURL(new RegExp(`/app/messages\\?user=${sent.offers[1].peer.userId}$`));
  await expect(page.getByRole('note')).toContainText('매칭 성사');
  await page.getByRole('textbox', { name: 'GankFlow에게 메시지', exact: true }).fill('같이 시작해요');
  await page.getByRole('textbox', { name: 'GankFlow에게 메시지', exact: true }).press('Enter');
  await expect(page.locator('.dm-message-entry')).toContainText('같이 시작해요');
  await page.getByRole('button', { name: '통화 시작', exact: true }).click();
  await expect(page.getByRole('region', { name: '음성 대화', exact: true })).toBeVisible();
});

test('자동 매칭도 응답을 기다리면서 새 상대를 발견하고 다른 화면에서 성사 알림을 받는다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page);
  await page.clock.fastForward(7000);
  await expect(page.locator('.duo-offer')).toHaveCount(1);
  await page.locator('.duo-offer').getByRole('button', { name: '같이 할래요' }).click();
  await page.clock.fastForward(6500);
  await expect(page.locator('.duo-offer')).toHaveCount(1);
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  expect((await snapshot(page)).mine[0].status).toBe('OPEN');
  await page.locator('.side-nav a[href="/app/me"]').click();
  await page.clock.fastForward(22000);
  await expect.poll(async () => (await snapshot(page)).offers.filter((o: any) => o.status === 'MATCHED').length).toBe(1);
  await expect(page).toHaveURL(/\/app\/me$/);
});

for (const action of ['오케이 취소', '잠시 멈춤', '조건 수정', '매칭 종료'] as const) {
  test(`${action} 후 늦게 온 응답은 매칭을 성사시키지 않는다`, async ({ page }) => {
    await page.clock.install(); await login(page); await startRealtimeMatch(page); await send(page, 'PlayMaker');
    const offer = (await snapshot(page)).offers[0];
    if (action === '오케이 취소') await page.getByRole('button', { name: 'PlayMaker 오케이 취소', exact: true }).click();
    else {
      await manageRecruitment(page, action);
      if (action === '조건 수정') {
        await page.getByLabel('한마디').fill('조건을 새로 확인해 주세요');
        await page.getByRole('button', { name: '매칭 조건 저장', exact: true }).click();
      }
    }
    await okay(page, offer.id);
    const state = await snapshot(page);
    expect(state.offers[0].status).toBe('CANCELLED');
    expect(state.mine[0].status).not.toBe('MATCHED');
    expect(Object.values(state.messages.conversations).flatMap((c: any) => c.messages).some((m: any) => m.kind === 'MATCH')).toBe(false);
  });
}

test('상대가 먼저 오케이를 해도 내가 수락하기 전에는 매칭 중이다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page); await page.clock.fastForward(7000);
  await expect(page.locator('.duo-offer')).toHaveCount(1);
  const offer = (await snapshot(page)).offers[0]; await okay(page, offer.id);
  await expect(page.locator('.duo-offer')).toContainText('먼저 오케이를 보냈어요');
  expect((await snapshot(page)).mine[0].status).toBe('OPEN');
  await page.locator('.duo-offer').getByRole('button', { name: '같이 할래요' }).click();
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toBeVisible();
});

test('모바일에서도 후보·오케이·매칭 상태를 팝업 없이 표시한다', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.clock.install(); await login(page); await startRealtimeMatch(page); await page.clock.fastForward(7000);
  await expect(page.locator('.duo-offer')).toHaveCount(1);
  await page.locator('.duo-offer').getByRole('button', { name: '같이 할래요' }).click();
  await expect(page.getByRole('region', { name: '보낸 오케이' })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});


test('예약도 오케이 대기 동안 매칭을 유지하고 상호 수락 후에만 마감한다', async ({ page }) => {
  await page.clock.install(); await login(page);
  await page.getByRole('tab', { name: '예약 매칭', exact: true }).click();
  await expect(page.locator('.recruitment-composer-shell')).toBeVisible();
  await page.getByRole('button', { name: '매칭 시작', exact: true }).click();
  await expect(page.locator('.my-recruitment')).toBeVisible();
  await send(page, 'PlayMaker');
  const state = await snapshot(page);
  expect(state.mine[0]).toMatchObject({ type: 'RESERVATION', status: 'OPEN' });
  await okay(page, state.offers[0].id);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toBeVisible();
  expect((await snapshot(page)).mine[0].status).toBe('MATCHED');
});

test('메시지 저장이 실패하면 매칭을 유지하고 저장 복구 후 한 번만 성사시킨다', async ({ page }) => {
  await page.clock.install(); await login(page); await startRealtimeMatch(page); await send(page, 'PlayMaker');
  await page.evaluate(() => {
    const original = Storage.prototype.setItem;
    (window as any).__restoreDuoStorage = () => { Storage.prototype.setItem = original; };
    Storage.prototype.setItem = function(key, value) {
      if (key.startsWith('qm:direct-messages:')) throw new DOMException('test quota', 'QuotaExceededError');
      return original.call(this, key, value);
    };
  });
  await page.clock.fastForward(22000);
  const failed = await snapshot(page);
  expect(failed.mine[0].status).toBe('OPEN'); expect(failed.offers[0].status).toBe('SENT');
  await page.evaluate(() => (window as any).__restoreDuoStorage());
  await page.clock.fastForward(3000);
  await expect(page.getByRole('region', { name: '매칭 성사', exact: true })).toBeVisible();
  const recovered = await snapshot(page);
  expect(Object.values(recovered.messages.conversations).flatMap((c: any) => c.messages).filter((m: any) => m.kind === 'MATCH')).toHaveLength(1);
});
