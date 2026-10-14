import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { createPost, enter, lolPost, roomMembers, uniqueTitle } from './support/domain';
import { collectPeerConnections, describePeers, expectInboundAudio, peerAudio, type Side } from './support/voice';

/**
 * 시나리오 15 — 음성이 흐르는 방에서 **나갔다가 다시 들어오면 다시 이어져야 한다**(2026-10-13 소유자 보고 — "webrtc 로 음성통화 할 때 나갔다가 다시 들어오면 연결이 안 된다").
 * 두 사람이 방에서 마이크를 켜 양쪽으로 소리가 흐른 뒤 ① 파티원(B)이 "방 나가기" → 남은 쪽(A)의 그 연결이 닫히고 좌석이 하나 줄어야 한다 →
 * B 가 게시판 카드의 [참가] → 포지션 → "참여하기" 로 다시 들어와 마이크를 켜면 **양쪽에 다시 소리가 흐르고 채팅도 켜져야 한다**(열린 peer 연결은 양쪽 하나씩 — 옛 연결은 닫혀 있다).
 * ② 같은 사람이 **한 번 더** 나갔다 들어와도 같아야 한다(두 번째 재입장 — 첫 재입장에서 남은 상태가 쌓이지 않는가).
 * 방장은 재입장을 볼 수 없다 — 확정 전에 방장이 나가면 방이 닫히고 글이 만료되며(시나리오 3), 확정 뒤에는 아무도 들어올 수 없다(시나리오 4 — 409 POST_NOT_RECRUITING).
 * 시그널은 `POST /rooms/{roomId}/signals` ↔ `WEBRTC_SIGNAL`, 연결 규칙은 `src/webrtc/WebRtcPartyClient.ts` 머리 주석 · 방 세션은 `state/RoomSessionContext.tsx`(`leave` → `lose(LEFT)` → `roomId = null` → `PartySessionContext` 가 클라이언트를 닫는다).
 * 글은 API 로 만들고(`voice: REQUIRED`) 들어가는 쪽만 화면으로 한다 — 보는 것은 음성과 재입장이다(글 쓰기 팝업은 시나리오 10 이 본다).
 */
test('시나리오 15 — 음성 중 나갔다가 다시 들어오면 다시 이어진다(파티원 · 방장)', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  for (const user of [a, b]) await user.context.addInitScript(collectPeerConnections);
  const title = uniqueTitle('s15');
  const post = await createPost(a, lolPost(title, { voice: 'REQUIRED' }));
  const roomId = String(post.postId);

  const room = (page: Page) => page.getByRole('region', { name: '방', exact: true });
  const seats = (page: Page, n: number) => room(page).getByRole('list', { name: `파티원 ${n} / 5` });
  const pressMic = async (page: Page) => {
    await page.getByRole('button', { name: '음소거 해제' }).click({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: '음소거', exact: true })).toBeVisible({ timeout: 20_000 });
  };
  /** "방 나가기" 버튼 → 확인 창 → "방 나가기" → 홈(방 패널이 닫힌다). */
  const leaveByUi = async (page: Page) => {
    await page.getByRole('button', { name: '방 나가기', exact: true }).click();
    await page.getByRole('dialog', { name: '방에서 나갈까요?' }).getByRole('button', { name: '방 나가기', exact: true }).click();
    await page.waitForURL(/\/app\/home$/);
  };
  /** 게시판 카드의 [참가] → 참여 창에서 남은 포지션 하나 → "참여하기" → 방 화면. */
  const joinByCard = async (page: Page, position: string) => {
    const card = page.locator(`article[aria-label="${title} 방 정보"]`);
    await expect(card).toBeVisible({ timeout: 20_000 });
    await card.getByRole('button', { name: '참가', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '방에 참가하기' });
    await dialog.getByRole('radiogroup', { name: '내 포지션' }).getByRole('radio', { name: position }).check({ force: true });
    await dialog.getByRole('button', { name: '참가하기' }).click();
    await page.waitForURL(new RegExp(`/app/party/${roomId}$`));
    await expect(room(page).getByRole('heading', { level: 1, name: title })).toBeVisible();
  };
  /** 남은 쪽에서 나간 사람과의 연결이 정리됐는가 — 열린 peer 연결 0 개(닫힌 것은 세지 않는다). */
  const expectNoOpenPeers = async (side: Side) => {
    await expect.poll(async () => (await peerAudio(side.page)).length, { message: `${side.name} 에 나간 사람과의 연결이 남아 있다`, timeout: 15_000 }).toBe(0);
  };
  const chatEnabled = (page: Page) => expect(page.getByRole('textbox', { name: '방에 메시지 보내기' })).toBeEnabled({ timeout: 20_000 });

  const pageA = await crew.appPage('a', `/app/party/${roomId}`);
  await expect(room(pageA).getByRole('heading', { level: 1, name: title })).toBeVisible();
  const entered = await enter(b, roomId, 'TOP');
  expect([200, 201], `B 입장 ${entered.status} ${JSON.stringify(entered.body)}`).toContain(entered.status);
  const pageB = await crew.appPage('b', `/app/party/${roomId}`);
  await expect(room(pageB).getByRole('heading', { level: 1, name: title })).toBeVisible();
  const sideA: Side = { name: 'A', page: pageA };
  const sideB: Side = { name: 'B', page: pageB };
  const both = [sideA, sideB];

  await test.step('둘이 마이크 켜기 → 양쪽으로 소리가 흐른다 · 채팅 켜짐', async () => {
    await pressMic(pageA);
    await pressMic(pageB);
    for (const side of both) await expectInboundAudio(side, 1, both);
    for (const side of both) await chatEnabled(side.page);
  });

  await test.step('① 파티원 B 가 "방 나가기" → A 의 좌석 1 / 5 · A 에 열린 연결 없음', async () => {
    await leaveByUi(pageB);
    await expect(seats(pageA, 1)).toBeVisible({ timeout: 20_000 });
    await expectNoOpenPeers(sideA);
    expect((await roomMembers(a, roomId)).members).toEqual([a.userId]);
  });

  await test.step('① B 가 카드 [참가] → 탑 → 다시 들어와 마이크 켜기 → 양쪽에 다시 소리가 흐른다 · 채팅 켜짐', async () => {
    await joinByCard(pageB, '탑');
    await expect(seats(pageA, 2)).toBeVisible({ timeout: 20_000 });
    await pressMic(pageB);
    test.info().annotations.push({ type: '① 재입장 직후 연결', description: `A:\n${await describePeers(pageA)}\nB:\n${await describePeers(pageB)}` });
    for (const side of both) await expectInboundAudio(side, 1, both);
    for (const side of both) await chatEnabled(side.page);
  });

  await test.step('② B 가 한 번 더 나가기 → A 의 좌석 1 / 5 · 열린 연결 없음', async () => {
    await leaveByUi(pageB);
    await expect(seats(pageA, 1)).toBeVisible({ timeout: 20_000 });
    await expectNoOpenPeers(sideA);
  });

  await test.step('② B 가 두 번째로 다시 들어와 마이크 켜기 → 양쪽에 다시 소리가 흐른다 · 채팅 켜짐', async () => {
    await joinByCard(pageB, '탑');
    await expect(seats(pageA, 2)).toBeVisible({ timeout: 20_000 });
    await pressMic(pageB);
    test.info().annotations.push({ type: '② 두 번째 재입장 직후 연결', description: `A:
${await describePeers(pageA)}
B:
${await describePeers(pageB)}` });
    for (const side of both) await expectInboundAudio(side, 1, both);
    for (const side of both) await chatEnabled(side.page);
  });
});

/**
 * 변형 — 소유자가 본 "나갔다가 다시 들어오면 연결이 안 된다" 가 아래 가운데 어느 것인지 가른다(위 시나리오는 UI 로 천천히 나갔다 들어오는 길이다).
 * 둘이 마이크를 켜 양쪽으로 소리가 흐르는 방을 만든 뒤 각 변형을 한다. 준비는 API(글 · 입장)로, 보는 것은 화면과 peer 통계다.
 */
async function voiceRoom(crew: import('./support/fixtures').Crew, scenario: string) {
  const a = await crew.user('a');
  const b = await crew.user('b');
  for (const user of [a, b]) await user.context.addInitScript(collectPeerConnections);
  const title = uniqueTitle(scenario);
  const post = await createPost(a, lolPost(title, { voice: 'REQUIRED' }));
  const roomId = String(post.postId);
  const room = (page: Page) => page.getByRole('region', { name: '방', exact: true });
  const pressMic = async (page: Page) => {
    await page.getByRole('button', { name: '음소거 해제' }).click({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: '음소거', exact: true })).toBeVisible({ timeout: 20_000 });
  };
  const pageA = await crew.appPage('a', `/app/party/${roomId}`);
  await expect(room(pageA).getByRole('heading', { level: 1, name: title })).toBeVisible();
  const entered = await enter(b, roomId, 'TOP');
  expect([200, 201], `B 입장 ${entered.status} ${JSON.stringify(entered.body)}`).toContain(entered.status);
  const pageB = await crew.appPage('b', `/app/party/${roomId}`);
  await expect(room(pageB).getByRole('heading', { level: 1, name: title })).toBeVisible();
  const sideA: Side = { name: 'A', page: pageA };
  const sideB: Side = { name: 'B', page: pageB };
  const both = [sideA, sideB];
  await pressMic(pageA);
  await pressMic(pageB);
  for (const side of both) await expectInboundAudio(side, 1, both);
  return { a, b, title, roomId, pageA, pageB, sideA, sideB, both, room, pressMic };
}
const chatEnabledOn = (page: Page) => expect(page.getByRole('textbox', { name: '방에 메시지 보내기' })).toBeEnabled({ timeout: 20_000 });

test('시나리오 15-b — 나가자마자(1초 안에) 다시 들어와도 다시 이어진다', async ({ crew }) => {
  const { b, roomId, pageA, pageB, both, room, pressMic } = await voiceRoom(crew, 's15b');
  // UI 로 나가고(확인 창) 홈이 열리는 즉시 API 로 다시 들어가 방 화면을 연다 — 상대(A)가 LEFT 와 ENTERED 를 거의 동시에 받는 길.
  await pageB.getByRole('button', { name: '방 나가기', exact: true }).click();
  await pageB.getByRole('dialog', { name: '방에서 나갈까요?' }).getByRole('button', { name: '방 나가기', exact: true }).click();
  await pageB.waitForURL(/\/app\/home$/);
  const again = await enter(b, roomId, 'TOP');
  expect([200, 201], `B 재입장 ${again.status} ${JSON.stringify(again.body)}`).toContain(again.status);
  await pageB.goto(`/app/party/${roomId}`);
  await expect(room(pageB).getByRole('heading', { level: 1 })).toBeVisible();
  await expect(room(pageA).getByRole('list', { name: '파티원 2 / 5' })).toBeVisible({ timeout: 20_000 });
  await pressMic(pageB);
  test.info().annotations.push({ type: '빠른 재입장 직후 연결', description: `A:\n${await describePeers(pageA)}\nB:\n${await describePeers(pageB)}` });
  for (const side of both) await expectInboundAudio(side, 1, both);
  for (const side of both) await chatEnabledOn(side.page);
});

test('시나리오 15-c — 음성 중에 방 화면을 새로 고치면 다시 이어진다(상대는 옛 연결을 버린다)', async ({ crew }) => {
  const { pageA, pageB, both, room, pressMic } = await voiceRoom(crew, 's15c');
  await pageB.reload();
  await expect(room(pageB).getByRole('heading', { level: 1 })).toBeVisible({ timeout: 20_000 });
  await pressMic(pageB);   // 새로 고치면 마이크 스트림이 없어진다 — 다시 켠다
  test.info().annotations.push({ type: '새로 고침 직후 연결', description: `A:\n${await describePeers(pageA)}\nB:\n${await describePeers(pageB)}` });
  // A 쪽에는 B 와의 연결이 하나만 열려 있어야 한다(옛 연결은 probe 뒤 닫힌다 — WebRtcPartyClient 머리 주석 2) · 양쪽 다시 흐른다
  for (const side of both) await expectInboundAudio(side, 1, both, 30_000);
  for (const side of both) await chatEnabledOn(side.page);
});

test('시나리오 15-d — 방 화면을 떠나(레일의 친구) 돌아와도 음성은 그대로다 · 방 안에서 레일 이동은 나가기가 아니다', async ({ crew }) => {
  const { title, pageA, pageB, both, room } = await voiceRoom(crew, 's15d');
  // 레일 링크로 옮긴다(앱 안 이동 — `page.goto` 는 앱을 통째로 다시 띄워 마이크 스트림이 없어진다 · 2026-10-13 그 길로 잘못 재서 한 번 실패했다 — 새로 고침은 15-c 가 본다).
  await pageB.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '친구', exact: true }).click();
  await pageB.waitForURL(/\/app\/friends/);
  // 방 세션은 앱 전체라 연결은 그대로다 — A 는 여전히 소리를 받는다
  await expectInboundAudio({ name: 'A', page: pageA }, 1, both);
  await pageB.goBack();
  await expect(room(pageB).getByRole('heading', { level: 1, name: title })).toBeVisible({ timeout: 10_000 });
  // 돌아온 뒤에도 마이크는 켜진 채다(버튼이 "음소거" — 다시 켤 필요가 없다)
  await expect(pageB.getByRole('button', { name: '음소거', exact: true })).toBeVisible({ timeout: 10_000 });
  for (const side of both) await expectInboundAudio(side, 1, both);
  for (const side of both) await chatEnabledOn(side.page);
});

test('시나리오 15-e — 음성 중에 다른 방으로 옮기면(방 이동 — 나가고 참가하기) 새 방의 사람과 이어진다', async ({ crew }) => {
  const { a, roomId, pageA, pageB, pressMic, room } = await voiceRoom(crew, 's15e');
  const c = await crew.user('c');
  await c.context.addInitScript(collectPeerConnections);
  const targetTitle = uniqueTitle('s15e-to');
  const target = await createPost(c, lolPost(targetTitle, { voice: 'REQUIRED' }));
  const pageC = await crew.appPage('c', `/app/party/${target.postId}`);
  await expect(room(pageC).getByRole('heading', { level: 1, name: targetTitle })).toBeVisible();
  await pressMic(pageC);
  // B — 게시판(레일의 홈)에서 C 의 방 카드 [참가] → "다른 방에 참가할까요?" → 탑 → "나가고 참가하기"(팀원 작업 — 시나리오 13 과 같은 길)
  await pageB.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name: '홈', exact: true }).click();
  const card = pageB.getByRole('article', { name: `${targetTitle} 방 정보`, exact: true });
  await expect(card).toBeVisible({ timeout: 20_000 });
  await card.getByRole('button', { name: '참가', exact: true }).click();
  const dialog = pageB.getByRole('dialog', { name: '다른 방에 참가할까요?', exact: true });
  await dialog.getByRole('radio', { name: '탑', exact: true }).check();
  await dialog.getByRole('button', { name: '나가고 참가하기', exact: true }).click();
  await pageB.waitForURL(new RegExp(`/app/party/${target.postId}$`));
  await expect(room(pageB).getByRole('heading', { level: 1, name: targetTitle })).toBeVisible();
  // 옛 방(A)은 B 를 잃는다 · B 의 옛 연결은 닫힌다
  await expect(room(pageA).getByRole('list', { name: '파티원 1 / 5' })).toBeVisible({ timeout: 20_000 });
  await expect.poll(async () => (await peerAudio(pageA)).length, { timeout: 15_000 }).toBe(0);
  expect((await roomMembers(a, roomId)).members).toEqual([a.userId]);
  // 새 방 — B 는 마이크를 다시 켜야 한다(방이 바뀌면 음성 세션을 새로 만든다) → B ↔ C 양쪽으로 흐른다
  await pressMic(pageB);
  const sideB: Side = { name: 'B', page: pageB };
  const sideC: Side = { name: 'C', page: pageC };
  test.info().annotations.push({ type: '방 이동 직후 연결', description: `B:\n${await describePeers(pageB)}\nC:\n${await describePeers(pageC)}` });
  for (const side of [sideB, sideC]) await expectInboundAudio(side, 1, [sideB, sideC]);
  for (const side of [sideB, sideC]) await chatEnabledOn(side.page);
});
