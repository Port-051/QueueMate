import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { aramPost, createPost, enter, uniqueTitle } from './support/domain';
import { chatSubscriberCount } from './support/chatRedis';

/**
 * 시나리오 26 — 방 채팅이 **chat 인스턴스 사이를 Redis Pub/Sub 으로 건너간다**(`chat/CLAUDE.md` §1 그림 · §3.1 "보낸 서버도 구독으로 받아 쏜다" C-2 · D-18 "어느 인스턴스에 붙든 Redis 가 뿌린다 — sticky session 없음").
 * 로컬 스택 — A 는 앱 화면(Vite 5173 → `/ws/chat` 프록시 → chat **8083**), B 는 같은 출처(`http://localhost:5173`)의 빈 페이지에서 **날 WebSocket 으로 chat 8084** 에 붙는다
 * (두 인스턴스는 같은 Redis 6380 을 본다 · 8084 의 `ALLOWED_ORIGINS` 에 5173 이 있고 쿠키 `qm_access` 는 호스트 단위라 포트가 달라도 실린다).
 *
 * 보는 것 — ① A 의 화면 글이 8084 의 B 소켓에 MSG 로 온다(`from` = A 의 번호 · `nick` = A 의 닉네임) ② B 가 날 소켓으로 보낸 SEND 가 A 의 화면에 **서버가 아는 B 의 닉네임**으로 그려진다
 * (B 가 실어 보낸 가짜 `nick` 은 봉투에는 그대로 실리지만 — §3.1 소유자 결정 — 화면의 이름은 `from` 으로 찾은 서버 닉네임이 먼저다, `PartyRoomPage` `chatName`)
 * ③ 날 소켓으로 501자 → 서버가 `ERROR TOO_LONG` 으로 거절하고 A 화면에 없다(서버 상한 — 시나리오 25 ⑤ 는 클라이언트가 자르는 쪽) · 500자 정각은 들어간다
 * ④ 둘이 붙어 있는 동안 `PUBSUB NUMSUB` = 2(인스턴스마다 구독 하나) · B 의 날 소켓이 닫히면 1 · A 가 나가면 0.
 * 소켓 수준의 403 · 그 밖의 ERROR 코드는 메인 세션의 `chat-check.mjs` 가 본다. Redis 는 `PUBSUB NUMSUB` 로 **읽기만** 한다(`support/chatRedis.ts`).
 */

/** B 의 빈 페이지에 두는 날 소켓의 자리(브라우저 `window` 안). */
interface RawSocketWindow { __qmRaw?: WebSocket; __qmFrames?: string[]; __qmClose?: number | null }
const RAW_URL = (roomId: string) => `ws://localhost:8084/ws/chat/rooms/${encodeURIComponent(roomId)}`;

test('시나리오 26 — 다중 인스턴스: 8083 화면의 글이 8084 날 소켓에 · 8084 의 SEND 가 8083 화면에 서버 닉네임으로 · 구독 수 2 → 1 → 0 · 날 소켓 501자는 TOO_LONG', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  const post = await createPost(a, aramPost(uniqueTitle('s26')));
  const roomId = String(post.postId);
  expect((await enter(b, roomId)).status).toBe(201);

  const room = (page: Page) => page.getByRole('region', { name: '방', exact: true });
  const log = (page: Page) => room(page).getByRole('log', { name: '방 메시지', exact: true });
  const input = (page: Page) => room(page).getByRole('textbox', { name: '방에 메시지 보내기' });
  const sendButton = (page: Page) => room(page).getByRole('button', { name: '메시지 보내기', exact: true });
  const bubbles = (page: Page) => log(page).locator('.room-conversation-message');

  const pageA = await crew.appPage('a', `/app/party/${roomId}`);
  // 앱이 아닌 문서(출처 5173) — 앱 소켓(8083)이 생기지 않아 B 쪽 소켓은 8084 하나뿐이다. **`route.fulfill` 로 지어낸 빈 페이지(`b.page()`)에서는 브라우저 WebSocket 핸드셰이크가
  // 1006 으로 실패한다**(2026-10-13 겪음 — REST fetch 는 되는데 소켓은 안 된다). Vite 가 실제로 내주는 정적 문서(`/robots.txt`)를 연다.
  const pageB = await b.context.newPage();
  await pageB.goto('/robots.txt');
  const frames = () => pageB.evaluate(() => ((window as unknown as RawSocketWindow).__qmFrames ?? []).map(f => { try { return JSON.parse(f) as Record<string, unknown>; } catch { return {}; } }));
  const rawSend = (payload: Record<string, unknown>) => pageB.evaluate(p => { (window as unknown as RawSocketWindow).__qmRaw?.send(JSON.stringify(p)); }, payload);

  await test.step('준비 — A 의 소켓(8083)이 열려 보낼 수 있다 · B 는 8084 에 날 소켓을 연다(구성원이라 101) → 구독 수 2', async () => {
    await input(pageA).fill('x');
    await expect(sendButton(pageA)).toBeEnabled({ timeout: 20_000 });
    await input(pageA).fill('');
    await expect.poll(() => chatSubscriberCount(roomId), { timeout: 10_000 }).toBe(1);
    await pageB.evaluate(url => new Promise<void>((resolve, reject) => {
      const w = window as unknown as RawSocketWindow;
      const ws = new WebSocket(url);
      w.__qmRaw = ws; w.__qmFrames = []; w.__qmClose = null;
      ws.onmessage = event => { w.__qmFrames?.push(String(event.data)); };
      ws.onopen = () => resolve();
      ws.onclose = event => { w.__qmClose = event.code; reject(new Error(`8084 소켓이 닫혔다 code=${event.code}`)); };
      setTimeout(() => reject(new Error('8084 소켓이 10초 안에 열리지 않았다')), 10_000);
    }), RAW_URL(roomId));
    await expect.poll(() => chatSubscriberCount(roomId), { timeout: 10_000 }).toBe(2);
  });

  const lineA = uniqueTitle('A 가 8083 에서');
  await test.step('① A 의 화면 글 → Redis → 8084 의 B 소켓에 MSG(from = A · nick = A 의 닉네임 · roomId) 가 딱 한 번 온다', async () => {
    await input(pageA).fill(lineA);
    await sendButton(pageA).click();
    await expect(log(pageA).getByText(lineA, { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect.poll(async () => (await frames()).filter(m => m.type === 'MSG' && m.text === lineA).length, { timeout: 10_000 }).toBe(1);
    const msg = (await frames()).find(m => m.type === 'MSG' && m.text === lineA)!;
    expect(msg.from).toBe(a.userId);
    expect(msg.nick).toBe(a.nickname);
    expect(msg.roomId).toBe(roomId);
    expect(typeof msg.sentAt).toBe('string');
    expect((await frames()).filter(m => m.type === 'ACK'), 'ACK 는 보낸 소켓에만 — B 의 소켓엔 A 의 ACK 가 없다').toHaveLength(0);
  });

  const lineB = uniqueTitle('B 가 8084 에서');
  await test.step('② B 의 날 SEND(가짜 nick) → 8084 ACK → Redis → 8083 의 A 화면에 **서버가 아는 B 의 닉네임**으로 · B 소켓에도 자기 MSG 가 한 번(from = B)', async () => {
    const cid = `e2e-${Date.now().toString(36)}`;
    await rawSend({ type: 'SEND', cid, nick: '가짜이름', text: lineB });
    await expect.poll(async () => (await frames()).filter(m => m.type === 'ACK' && m.cid === cid).length, { timeout: 10_000 }).toBe(1);
    await expect(log(pageA).getByText(lineB, { exact: true })).toBeVisible({ timeout: 10_000 });
    const bubble = bubbles(pageA).filter({ hasText: lineB });
    await expect(bubble).toHaveCount(1);
    await expect(bubble.locator('.room-profile-name-button'), '이름은 from(B 의 번호)으로 찾은 서버 닉네임 — 실어 보낸 "가짜이름" 이 아니다').toHaveText(b.nickname);
    const echo = (await frames()).filter(m => m.type === 'MSG' && m.text === lineB);
    expect(echo).toHaveLength(1);
    expect(echo[0].from, 'from 은 세션의 B 번호').toBe(b.userId);
    expect(echo[0].cid).toBe(cid);
    expect(echo[0].nick, '봉투의 nick 은 브라우저 값 그대로(서버는 길이만 본다 — §3.1)').toBe('가짜이름');
  });

  await test.step('③ 날 소켓으로 501자 → ERROR TOO_LONG(서버 상한 500) · A 화면에 없다 · 소켓은 그대로 · 500자 정각은 들어간다', async () => {
    const cid = `e2e-long-${Date.now().toString(36)}`;
    const marker = `s26long ${Date.now().toString(36)} `;
    await rawSend({ type: 'SEND', cid, nick: b.nickname, text: marker + 'y'.repeat(501 - marker.length) });
    await expect.poll(async () => (await frames()).filter(m => m.type === 'ERROR' && m.cid === cid).map(m => m.code), { timeout: 10_000 }).toEqual(['TOO_LONG']);
    await pageA.waitForTimeout(1_000);
    await expect(bubbles(pageA).filter({ hasText: marker })).toHaveCount(0);
    expect(await pageB.evaluate(() => (window as unknown as RawSocketWindow).__qmRaw?.readyState), '소켓은 열린 채(OPEN = 1)').toBe(1);
    // 500자 정각은 들어간다.
    const okCid = `e2e-max-${Date.now().toString(36)}`;
    const okMarker = `s26max ${Date.now().toString(36)} `;
    await rawSend({ type: 'SEND', cid: okCid, nick: b.nickname, text: okMarker + 'z'.repeat(500 - okMarker.length) });
    await expect.poll(async () => (await frames()).filter(m => m.type === 'ACK' && m.cid === okCid).length, { timeout: 10_000 }).toBe(1);
    await expect(bubbles(pageA).filter({ hasText: okMarker })).toHaveCount(1, { timeout: 10_000 });
    expect((await bubbles(pageA).filter({ hasText: okMarker }).locator('p').first().textContent())?.length).toBe(500);
  });

  await test.step('④ B 의 날 소켓을 닫으면 8084 가 UNSUBSCRIBE → 구독 수 1 · 그 뒤 A 의 글은 B 에 닿지 않는다 · A 가 나가면 0', async () => {
    await pageB.evaluate(() => { (window as unknown as RawSocketWindow).__qmRaw?.close(1000, 'leave'); });
    await expect.poll(() => pageB.evaluate(() => (window as unknown as RawSocketWindow).__qmClose), { timeout: 10_000 }).toBe(1000);
    await expect.poll(() => chatSubscriberCount(roomId), { timeout: 10_000 }).toBe(1);
    const afterClose = uniqueTitle('B 소켓이 닫힌 뒤');
    const framesBefore = (await frames()).length;
    await input(pageA).fill(afterClose);
    await sendButton(pageA).click();
    await expect(log(pageA).getByText(afterClose, { exact: true })).toBeVisible({ timeout: 10_000 });
    await pageA.waitForTimeout(1_000);
    expect((await frames()).length).toBe(framesBefore);
    // A(방장)가 나간다 → 이 방의 마지막 소켓 → 구독 0.
    await pageA.getByRole('button', { name: '방 나가기', exact: true }).click();
    await pageA.getByRole('dialog', { name: '방에서 나갈까요?' }).getByRole('button', { name: '방 나가기', exact: true }).click();
    await pageA.waitForURL(/\/app\/home$/);
    await expect.poll(() => chatSubscriberCount(roomId), { timeout: 15_000 }).toBe(0);
  });
});
