import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import type { QmUser } from './support/api';
import { aramPost, createPost, enter, roomMembers, uniqueTitle } from './support/domain';
import { chatSubscriberCount } from './support/chatRedis';

/**
 * 시나리오 25 — 방 텍스트 채팅이 **`chat` 서비스 소켓**으로 간다(2026-10-13 ⑦ — WebRTC DataChannel 에서 바꿨다 · `matching/docs/11_DECISION_LOG.md` D-58).
 * 근거 — `src/chat/RoomChatSocket.ts`(같은 출처 `ws://{host}/ws/chat/rooms/{roomId}` · 봉투 SEND/MSG/ACK/ERROR · **내 글도 서버가 뿌린 MSG 로 받아 그린다** · 500자에서 자른다 · 1·2·4·8·15초 재접속) ·
 * `src/state/PartySessionContext.tsx`(소켓의 수명 = 방 세션 · ERROR 토스트 TOO_FAST "너무 빨리 보내고 있어요…" · TOO_LONG "메시지는 500자까지…") ·
 * `src/pages/PartyRoomPage.tsx`(`canChat = chatStatus === 'open'` — 혼자여도 보낼 수 있다 · 전송 버튼 title "채팅 서버에 연결 중이에요" / "채팅 연결이 끊겨 다시 잇는 중이에요") ·
 * `src/rooms/RoomConversationChat.tsx`(log "방 메시지" · textbox "방에 메시지 보내기" · button "메시지 보내기" · Enter 전송 · Shift+Enter 줄바꿈) ·
 * `chat/CLAUDE.md` §3.1(`from` = 세션의 userId · `nick` 은 브라우저 값 · 500자 · 소켓마다 초당 5개 토큰 버킷) · §4(저장 없음 C-1 · 보낸 서버도 구독으로 받아 쏜다 C-2 · 비구성원 403 C-3/C-5 · 패턴 구독 금지 C-4).
 *
 * 방은 포지션 없는 방(칼바람 — `aramPost`)이라 입장이 단순하다. 음성(마이크)은 켜지 않는다. 소켓 프레임은 Playwright 의 `page.on('websocket')` 으로 **본다**(가로채지 않는다 — 진짜 chat 8083 에 붙는다).
 * Redis 는 `PUBSUB NUMSUB` 로 **읽기만** 한다(`support/chatRedis.ts`) — A · B 가 모두 Vite 프록시(8083 한 인스턴스)로 붙으므로 둘이 있어도 구독은 1 이고, 마지막 소켓이 끊기면 0 이어야 한다(C-4).
 * 다중 인스턴스(8084)는 시나리오 26 이 본다. 소켓 수준의 403 · ACK · EMPTY · TOO_LONG · BAD_JSON · TOO_FAST 는 메인 세션의 `chat-check.mjs` 가 본다 — 여기는 **화면을 통해 보이는 것**만 본다.
 */

/** 한 페이지가 연 방 채팅 소켓들 — 보낸 · 받은 프레임과 닫힘. 페이지를 열기 **전에** 걸어야 첫 소켓부터 잡는다. */
interface ChatSocketLog { url: string; sent: string[]; received: string[]; closed: boolean }
function watchChatSockets(page: Page): ChatSocketLog[] {
  const list: ChatSocketLog[] = [];
  page.on('websocket', ws => {
    if (!ws.url().includes('/ws/chat/rooms/')) return;
    const entry: ChatSocketLog = { url: ws.url(), sent: [], received: [], closed: false };
    list.push(entry);
    ws.on('framesent', frame => entry.sent.push(String(frame.payload)));
    ws.on('framereceived', frame => entry.received.push(String(frame.payload)));
    ws.on('close', () => { entry.closed = true; });
  });
  return list;
}
const latest = (list: ChatSocketLog[]) => list[list.length - 1];
const parse = (frame: string): Record<string, unknown> => { try { return JSON.parse(frame) as Record<string, unknown>; } catch { return {}; } };
/** 그 소켓이 받은 `MSG` 프레임 가운데 본문이 `text` 인 것. */
const receivedMsgs = (entry: ChatSocketLog | undefined, text: string) => (entry?.received ?? []).map(parse).filter(m => m.type === 'MSG' && m.text === text);

test('시나리오 25 — 방 채팅은 chat 서버로 간다 · 혼자여도 보낸다 · 내 글은 서버 echo 로 한 번 · 기록 없음 · 순서 · 이름 · 500자 · 초당 5개 · 비구성원 · 새로 고침 · XSS · 나가면 구독 해제', async ({ crew }) => {
  test.setTimeout(240_000);
  const a = await crew.user('a');
  const b = await crew.user('b');
  const c = await crew.user('c');
  const post = await createPost(a, aramPost(uniqueTitle('s25')));
  const roomId = String(post.postId);

  const room = (page: Page) => page.getByRole('region', { name: '방', exact: true });
  const log = (page: Page) => room(page).getByRole('log', { name: '방 메시지', exact: true });
  const input = (page: Page) => room(page).getByRole('textbox', { name: '방에 메시지 보내기' });
  const sendButton = (page: Page) => room(page).getByRole('button', { name: '메시지 보내기', exact: true });
  const bubbles = (page: Page) => log(page).locator('.room-conversation-message');
  const texts = (page: Page) => bubbles(page).locator('p').allTextContents();
  const errorToast = (page: Page, text: string) => page.locator('.toast-host .toast.error', { hasText: text });
  const dialogs: string[] = [];

  /** 방 화면을 연다 — `Crew#appPage` 와 같되 소켓 감시 · alert 감시를 **먼저** 건다. */
  const openRoom = async (user: QmUser) => {
    const page = await user.context.newPage();
    const sockets = watchChatSockets(page);
    page.on('dialog', dialog => { dialogs.push(`${user.nickname}: ${dialog.type()} ${dialog.message()}`); void dialog.dismiss(); });
    await page.goto(`/app/party/${roomId}`);
    const size = page.viewportSize() ?? { width: 1440, height: 900 };
    await page.mouse.move(size.width / 2, size.height / 2);
    return { page, sockets };
  };
  const sendByUi = async (page: Page, text: string) => {
    await input(page).fill(text);
    await expect(sendButton(page)).toBeEnabled({ timeout: 20_000 });
    await sendButton(page).click();
  };
  /** "방 나가기" → 확인 창 → "방 나가기" → 홈(방 패널이 닫힌다 — 시나리오 15 와 같다). */
  const leaveByUi = async (page: Page) => {
    await page.getByRole('button', { name: '방 나가기', exact: true }).click();
    await page.getByRole('dialog', { name: '방에서 나갈까요?' }).getByRole('button', { name: '방 나가기', exact: true }).click();
    await page.waitForURL(/\/app\/home$/);
  };

  const { page: pageA, sockets: socketsA } = await openRoom(a);
  const lineA = uniqueTitle('A 혼자 보냄');
  await test.step('① 혼자 — 소켓이 열리면 상대가 없어도 전송 버튼이 눌린다(title 없음) · 내 글은 서버의 MSG echo 로 그려지고 딱 한 번이다(SEND cid = ACK cid = MSG cid · from = 내 번호)', async () => {
    await expect(log(pageA).getByText('인사를 건네보세요!', { exact: true })).toBeVisible();
    await expect(sendButton(pageA)).toHaveCount(0);
    await input(pageA).fill(lineA);
    await expect(sendButton(pageA)).toBeEnabled({ timeout: 20_000 });
    expect(await sendButton(pageA).getAttribute('title'), '열린 소켓이면 보낼 수 없는 이유(title)가 없다').toBeNull();
    await sendButton(pageA).click();
    await expect(log(pageA).getByText(lineA, { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(input(pageA)).toHaveValue('');
    await expect(log(pageA).getByText('인사를 건네보세요!', { exact: true })).toHaveCount(0);
    // 프레임으로 본다 — 보낸 SEND 하나 · 돌아온 ACK 하나 · 방에 뿌려진 MSG 하나(같은 cid). 낙관적으로 먼저 그리지 않으니 MSG 가 없으면 글도 없다.
    const socket = latest(socketsA);
    expect(socket, 'chat 소켓이 하나는 열렸어야 한다').toBeTruthy();
    const sent = socket.sent.map(parse).filter(m => m.type === 'SEND' && m.text === lineA);
    expect(sent).toHaveLength(1);
    const cid = sent[0].cid;
    expect(typeof cid).toBe('string');
    await expect.poll(() => socket.received.map(parse).filter(m => m.type === 'ACK' && m.cid === cid).length).toBe(1);
    const msgs = receivedMsgs(socket, lineA);
    expect(msgs).toHaveLength(1);
    expect(msgs[0].cid).toBe(cid);
    expect(msgs[0].from, 'from 은 세션의 사용자 번호(브라우저 값이 아니다)').toBe(a.userId);
    expect(msgs[0].roomId).toBe(roomId);
    // 잠시 뒤에도 한 번 — 중복 렌더 없음.
    await pageA.waitForTimeout(1_000);
    await expect(log(pageA).getByText(lineA, { exact: true })).toHaveCount(1);
    expect(socket.closed).toBe(false);
  });

  let pageB!: Page;
  let socketsB!: ChatSocketLog[];
  await test.step('② B 가 들어온다 → B 의 기록은 비어 있다(들어오기 전의 글은 없다 — 저장 없음 D-58) · B 의 소켓이 열리면 바로 보낼 수 있다', async () => {
    expect((await enter(b, roomId)).status).toBe(201);
    ({ page: pageB, sockets: socketsB } = await openRoom(b));
    await expect(log(pageB).getByText('인사를 건네보세요!', { exact: true })).toBeVisible({ timeout: 20_000 });
    await expect(log(pageB).getByText(lineA, { exact: true })).toHaveCount(0);
    await input(pageB).fill('x');
    await expect(sendButton(pageB)).toBeEnabled({ timeout: 20_000 });
    await input(pageB).fill('');
    await expect(sendButton(pageB)).toHaveCount(0);
    // 들어온 뒤에도 과거 글이 흘러 들어오지 않는다.
    await pageB.waitForTimeout(1_000);
    await expect(log(pageB).getByText(lineA, { exact: true })).toHaveCount(0);
    expect(receivedMsgs(latest(socketsB), lineA)).toHaveLength(0);
  });

  const lineB = uniqueTitle('B 가 답함');
  await test.step('⑪ B → A — Shift+Enter 는 줄바꿈이라 보내지 않는다 · Enter 로 보낸다 · 양쪽에 같은 글', async () => {
    await input(pageB).fill('줄 하나');
    await input(pageB).press('Shift+Enter');
    await expect(input(pageB)).toHaveValue('줄 하나\n');
    await pageB.waitForTimeout(500);
    await expect(log(pageA).getByText('줄 하나', { exact: true })).toHaveCount(0);
    expect(latest(socketsB).sent.map(parse).filter(m => m.type === 'SEND')).toHaveLength(0);
    await input(pageB).fill(lineB);
    await input(pageB).press('Enter');
    await expect(log(pageB).getByText(lineB, { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(log(pageA).getByText(lineB, { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(input(pageB)).toHaveValue('');
    await expect(input(pageB)).toBeFocused();
  });

  await test.step('④ 보낸 사람 이름은 서버가 아는 닉네임 — 내 글은 내 닉네임 · 상대의 글은 상대의 닉네임(양쪽 화면 모두)', async () => {
    const nameOf = (page: Page, text: string) => bubbles(page).filter({ hasText: text }).locator('.room-profile-name-button');
    await expect(nameOf(pageA, lineA)).toHaveText(a.nickname);
    await expect(nameOf(pageA, lineB)).toHaveText(b.nickname);
    await expect(nameOf(pageB, lineB)).toHaveText(b.nickname);
    // B 는 lineA 를 못 봤다(②) — B 화면의 A 이름은 다음 글(③)에서 본다.
    const nickB = receivedMsgs(latest(socketsB), lineB)[0];
    expect(nickB?.from).toBe(b.userId);
    expect(nickB?.nick, '봉투의 nick 은 브라우저가 실어 보낸 내 닉네임').toBe(b.nickname);
  });

  await test.step('③ 순서 — A1 B1 A2 B2 A3 를 쉬지 않고 번갈아 보내면 양쪽 기록의 순서가 같고 각자의 글은 보낸 차례대로다(길이 하나 — Redis 채널)', async () => {
    const tag = Date.now().toString(36);
    const seq = [['a', `A1 ${tag}`], ['b', `B1 ${tag}`], ['a', `A2 ${tag}`], ['b', `B2 ${tag}`], ['a', `A3 ${tag}`]] as const;
    const beforeA = await bubbles(pageA).count();
    const beforeB = await bubbles(pageB).count();
    for (const [who, text] of seq) {
      const page = who === 'a' ? pageA : pageB;
      await input(page).fill(text);
      await input(page).press('Enter');
    }
    await expect(bubbles(pageA)).toHaveCount(beforeA + 5, { timeout: 15_000 });
    await expect(bubbles(pageB)).toHaveCount(beforeB + 5, { timeout: 15_000 });
    const onA = (await texts(pageA)).filter(t => t.endsWith(tag));
    const onB = (await texts(pageB)).filter(t => t.endsWith(tag));
    expect(onA).toHaveLength(5);
    expect(onB, '두 화면이 같은 순서로 받는다').toEqual(onA);
    expect(onA.filter(t => t.startsWith('A'))).toEqual([`A1 ${tag}`, `A2 ${tag}`, `A3 ${tag}`]);
    expect(onA.filter(t => t.startsWith('B'))).toEqual([`B1 ${tag}`, `B2 ${tag}`]);
    // ④ 의 나머지 — B 화면에서 A 의 글 이름.
    await expect(bubbles(pageB).filter({ hasText: `A1 ${tag}` }).locator('.room-profile-name-button')).toHaveText(a.nickname);
  });

  await test.step('⑤ 501자 — 클라이언트가 500자로 **잘라서** 보낸다(RoomChatSocket.send 의 slice) → 거절 안내 없이 500자가 양쪽에 보인다(서버 상한 500 과 같아 TOO_LONG 이 나지 않는다)', async () => {
    const marker = `${uniqueTitle('s25 긴 글')} `;
    const long = marker + 'x'.repeat(501 - marker.length);
    expect(long).toHaveLength(501);
    await sendByUi(pageA, long);
    const onB = bubbles(pageB).filter({ hasText: marker }).locator('p');
    await expect(onB).toHaveCount(1, { timeout: 10_000 });
    expect((await onB.first().textContent())?.length, '잘린 글은 정확히 500자').toBe(500);
    expect((await bubbles(pageA).filter({ hasText: marker }).locator('p').first().textContent())?.length).toBe(500);
    const sent = latest(socketsA).sent.map(parse).filter(m => m.type === 'SEND' && typeof m.text === 'string' && (m.text as string).startsWith(marker));
    expect(sent).toHaveLength(1);
    expect((sent[0].text as string).length, '소켓으로 나간 본문도 500자').toBe(500);
    await expect(errorToast(pageA, '메시지는 500자까지')).toHaveCount(0);
    expect(latest(socketsA).received.map(parse).filter(m => m.type === 'ERROR')).toHaveLength(0);
  });

  await test.step('⑥ 초당 5개 — 1초 안에 12개를 쏘면 일부가 TOO_FAST 로 거절되고(토스트 "너무 빨리 보내고 있어요…") 받아들여진 것은 순서대로 양쪽에 · 소켓은 그대로 열려 있다', async () => {
    // 앞 단계에서 보낸 글 때문에 통이 비어 있을 수 있다 — 1.5초 쉬어 통(5개 · 초당 5 보충)을 채운 뒤 쏜다(2026-10-13 첫 실행에서 9/12 거절로 겪었다).
    await pageA.waitForTimeout(1_500);
    const tag = `빠른 ${Date.now().toString(36)}`;
    const count = 12;
    const beforeErrors = latest(socketsA).received.map(parse).filter(m => m.type === 'ERROR').length;
    // Playwright 의 fill · press 는 한 번에 수십 ms 라 12개가 1초를 넘기고 그 사이 통(초당 5개)이 다시 차 거절이 안 날 수 있다.
    // 그래서 **진짜 textarea 에** 브라우저 안에서 input 이벤트(React 의 value tracker 를 지나는 네이티브 setter) + Enter keydown 을 연달아 준다 — 앱의 onChange · onKeyDown · send 가 그대로 돈다.
    await pageA.evaluate(async ({ tag, count }) => {
      const textarea = document.querySelector<HTMLTextAreaElement>('textarea[aria-label="방에 메시지 보내기"]');
      if (!textarea) throw new Error('채팅 입력칸이 없다');
      const setValue = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')?.set;
      if (!setValue) throw new Error('textarea value setter 가 없다');
      for (let i = 1; i <= count; i += 1) {
        setValue.call(textarea, `${tag} ${String(i).padStart(2, '0')}`);
        textarea.dispatchEvent(new Event('input', { bubbles: true }));
        await new Promise(resolve => setTimeout(resolve, 0));   // React 가 draft 를 반영할 틈(동기 레인은 마이크로태스크에서 flush 된다)
        textarea.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));
        await new Promise(resolve => setTimeout(resolve, 0));
      }
    }, { tag, count });
    const socket = latest(socketsA);
    const cids = new Set(socket.sent.map(parse).filter(m => m.type === 'SEND' && typeof m.text === 'string' && (m.text as string).startsWith(tag)).map(m => m.cid as string));
    expect(cids.size, '12개가 모두 소켓으로 나갔다(클라이언트는 막지 않는다)').toBe(count);
    await expect(errorToast(pageA, '너무 빨리 보내고 있어요').first()).toBeVisible({ timeout: 5_000 });
    // 12개 전부에 답(ACK 또는 ERROR)이 돌아온 뒤에 센다 — 답은 그 소켓에만 바로 온다.
    const replies = () => socket.received.map(parse).filter(m => (m.type === 'ACK' || m.type === 'ERROR') && cids.has(m.cid as string));
    await expect.poll(() => replies().length, { timeout: 5_000 }).toBe(count);
    const errors = replies().filter(m => m.type === 'ERROR');
    expect(errors.every(m => m.code === 'TOO_FAST'), `거절 이유는 전부 TOO_FAST: ${JSON.stringify(errors)}`).toBe(true);
    expect(socket.received.map(parse).filter(m => m.type === 'ERROR').length - beforeErrors).toBe(errors.length);
    const rejected = errors.length;
    expect(rejected).toBeGreaterThanOrEqual(1);
    expect(rejected).toBeLessThanOrEqual(count - 5);
    // 받아들여진 것 = 보낸 수 − 거절 수 · 양쪽에 같은 수가 보낸 차례대로.
    await expect.poll(async () => (await texts(pageA)).filter(t => t.startsWith(tag)).length, { timeout: 10_000 }).toBe(count - rejected);
    await expect.poll(async () => (await texts(pageB)).filter(t => t.startsWith(tag)).length, { timeout: 10_000 }).toBe(count - rejected);
    const onA = (await texts(pageA)).filter(t => t.startsWith(tag));
    const onB = (await texts(pageB)).filter(t => t.startsWith(tag));
    expect(onB).toEqual(onA);
    const numbers = onA.map(t => Number(t.slice(-2)));
    expect([...numbers].sort((x, y) => x - y), '받아들여진 글은 보낸 차례대로').toEqual(numbers);
    expect(numbers.slice(0, 5), '통이 가득한 처음 다섯은 들어간다').toEqual([1, 2, 3, 4, 5]);
    // 소켓은 끊기지 않았다 — 1.5초 뒤(통이 다시 찬 뒤) 보통 글이 그대로 간다.
    expect(socket.closed).toBe(false);
    await pageA.waitForTimeout(1_500);
    const after = uniqueTitle('통이 찬 뒤');
    await sendByUi(pageA, after);
    await expect(log(pageB).getByText(after, { exact: true })).toBeVisible({ timeout: 10_000 });
    expect(socketsA.length, '새 소켓을 열지 않았다(재접속 없음)').toBe(1);
  });

  await test.step('⑦ 방에 없는 C 가 /app/party/{roomId} 를 열면 방 화면이 홈으로 보낸다(403 NOT_IN_ROOM) · 채팅 기록 없음 · 열렸던 소켓은 전부 닫히고 MSG 를 하나도 받지 못한다', async () => {
    const members = await roomMembers(c, roomId);
    expect(members.status).toBe(403);
    expect(members.code).toBe('NOT_IN_ROOM');
    const { page: pageC, sockets: socketsC } = await openRoom(c);
    await pageC.waitForURL(/\/app\/home$/, { timeout: 20_000 });
    await expect(pageC.locator('.toast-host .toast', { hasText: '이 방에 들어와 있지 않아요' })).toBeVisible();
    await expect(pageC.getByRole('log', { name: '방 메시지', exact: true })).toHaveCount(0);
    // 방 세션이 경로의 방을 먼저 받아들여 소켓을 열려고 할 수 있다(핸드셰이크 403 → close 1006). 그래도 "이 방에 없다" 를 알면 소켓 · 재접속 타이머가 닫혀야 한다.
    await pageC.waitForTimeout(2_500);
    const attempts = socketsC.length;
    expect(attempts).toBeLessThanOrEqual(2);
    for (const socket of socketsC) {
      expect(socket.closed, `C 의 소켓은 닫혀 있어야 한다: ${socket.url}`).toBe(true);
      expect(socket.received.map(parse).filter(m => m.type === 'MSG')).toHaveLength(0);
    }
    await pageC.waitForTimeout(2_500);
    expect(socketsC.length, '홈으로 간 뒤 재접속을 계속하지 않는다').toBe(attempts);
    // C 가 보지 못한 사이 A 가 보낸 글도 C 에 닿지 않는다.
    const hidden = uniqueTitle('C 는 못 본다');
    await sendByUi(pageA, hidden);
    await expect(log(pageB).getByText(hidden, { exact: true })).toBeVisible({ timeout: 10_000 });
    expect(socketsC.flatMap(s => receivedMsgs(s, hidden))).toHaveLength(0);
    await pageC.close();
  });

  await test.step('⑧ 새로 고치면 기록이 빈다(저장 없음) · 새 소켓이 열리면 다시 보낼 수 있고 옛 소켓은 닫혔다', async () => {
    const before = socketsB.length;
    await pageB.reload();
    await expect(log(pageB).getByText('인사를 건네보세요!', { exact: true })).toBeVisible({ timeout: 20_000 });
    await expect(log(pageB).getByText(lineB, { exact: true })).toHaveCount(0);
    const again = uniqueTitle('다시 왔어요');
    await sendByUi(pageB, again);
    await expect(log(pageA).getByText(again, { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(log(pageB).getByText(again, { exact: true })).toHaveCount(1);
    expect(socketsB.length).toBeGreaterThan(before);
    // 새로 고침으로 페이지가 사라지면 Playwright 의 websocket close 이벤트가 안 올 수 있어(2026-10-13 겪음) 옛 소켓의 닫힘은 단정하지 않는다. Redis 구독 수는 **인스턴스 단위**다
    // (한 인스턴스는 방마다 한 번 구독 — 소켓 수가 아니다) — A · B 가 같은 8083 이라 그대로 1 이어야 한다(둘 다 끊기면 0 — ⑩).
    await expect.poll(() => chatSubscriberCount(roomId), { timeout: 10_000 }).toBe(1);
    // 서버 어디에도 채팅이 없다 — 방 안 사람 목록은 id · 포지션뿐이다.
    const members = await a.get(`/rooms/${roomId}/members`);
    expect(JSON.stringify(members.body)).not.toContain(lineA);
  });

  await test.step('⑨ HTML · 스크립트 글은 글자 그대로 — 요소가 만들어지지 않고(img 0) alert 도 뜨지 않는다', async () => {
    const payload = `<img src=x onerror=alert(1)> ${Date.now().toString(36)}`;
    await sendByUi(pageA, payload);
    await expect(log(pageB).getByText(payload, { exact: true })).toBeVisible({ timeout: 10_000 });
    await expect(log(pageA).getByText(payload, { exact: true })).toBeVisible();
    await expect(log(pageA).locator('img')).toHaveCount(0);
    await expect(log(pageB).locator('img')).toHaveCount(0);
    await expect(log(pageB).locator('script')).toHaveCount(0);
    await pageB.waitForTimeout(500);
    expect(dialogs, 'alert · confirm 이 하나도 뜨지 않았다').toEqual([]);
  });

  await test.step('⑩ 나가면 소켓이 닫힌다 — 둘이 한 인스턴스에 있으면 Redis 구독은 1 · B 가 나가도 1 · 마지막 A 가 나가면 0(UNSUBSCRIBE · C-4)', async () => {
    await expect.poll(() => chatSubscriberCount(roomId), { timeout: 10_000 }).toBe(1);
    const socketB = latest(socketsB);
    await leaveByUi(pageB);
    await expect.poll(() => socketB.closed, { timeout: 10_000 }).toBe(true);
    await expect(pageB.getByRole('log', { name: '방 메시지', exact: true })).toHaveCount(0);
    // A 는 아직 보낼 수 있고 B 에게는 닿지 않는다.
    const afterB = uniqueTitle('B 가 나간 뒤');
    await sendByUi(pageA, afterB);
    await expect(log(pageA).getByText(afterB, { exact: true })).toBeVisible({ timeout: 10_000 });
    expect(receivedMsgs(socketB, afterB)).toHaveLength(0);
    await expect.poll(() => chatSubscriberCount(roomId), { timeout: 10_000 }).toBe(1);
    const socketA = latest(socketsA);
    await leaveByUi(pageA);   // 방장이 확정 전에 나가면 방이 닫히고 글은 만료된다(시나리오 3) — 이 방의 마지막 소켓
    await expect.poll(() => socketA.closed, { timeout: 10_000 }).toBe(true);
    await expect.poll(() => chatSubscriberCount(roomId), { timeout: 15_000 }).toBe(0);
    // 방을 나간 뒤 다시 여는 소켓이 없다 — 서버 구독 수 0 이 2초 뒤에도 그대로(브라우저 쪽 장부의 close 이벤트는 새로 고침 · StrictMode 이중 마운트 때문에 빠질 수 있어 서버 쪽으로 본다 — 2026-10-13).
    await pageB.waitForTimeout(2_000);
    expect(await chatSubscriberCount(roomId), '방을 나간 뒤 다시 연 소켓이 없다(구독 0 유지)').toBe(0);
  });
});
